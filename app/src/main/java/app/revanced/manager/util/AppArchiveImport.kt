package app.revanced.manager.util

import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

sealed interface AppArchiveImportResult {
    data object InvalidMagicHeader : AppArchiveImportResult
    data object InvalidArchive : AppArchiveImportResult
    data object NoApkEntry : AppArchiveImportResult
    data object SplitBundle : AppArchiveImportResult
    data object AmbiguousBaseApk : AppArchiveImportResult
    data class Success(val sourceKind: SourceKind) : AppArchiveImportResult

    enum class SourceKind {
        PlainApk,
        SingleApkFromBundle,
        BaseApkFromSplitBundle,
        UnsplitRecoveredBaseApk,
    }
}

/**
 * Imports a user-selected application archive into a single APK file usable by the patcher.
 *
 * Supported:
 * - Plain APK files. APKs are ZIP files and must have a ZIP magic header.
 * - Bundle-like ZIP containers (.apks/.xapk/.apkm/.zip) when they contain exactly one APK entry.
 * - Anti-split mode for bundle-like containers when exactly one safe base APK can be resolved.
 * - Unsplit recovery mode for split bundles when one preferred base/universal/standalone APK can be selected.
 *
 * This does not merge resource tables from split_config APKs. It intentionally recovers a single patcher input APK
 * and refuses split-only, framework-only, overlay-only or ambiguous bundles.
 */
fun importSingleApkArchive(
    input: InputStream,
    outputApk: File,
): AppArchiveImportResult {
    outputApk.parentFile?.mkdirs()

    val rawInput = File(outputApk.parentFile ?: outputApk.absoluteFile.parentFile, "${outputApk.name}.raw")
    outputApk.delete()
    rawInput.delete()

    try {
        input.use { source -> rawInput.outputStream().use { target -> source.copyTo(target) } }

        if (!rawInput.hasZipMagicHeader()) return AppArchiveImportResult.InvalidMagicHeader

        return try {
            ZipFile(rawInput).use { zip ->
                val entries = zip.entries().asSequence()
                    .filterNot { it.isDirectory }
                    .toList()

                val nestedApkEntries = entries.filter { entry ->
                    entry.name.endsWith(".apk", ignoreCase = true)
                }

                val isPlainApk = nestedApkEntries.isEmpty() && entries.any { entry ->
                    entry.name == "AndroidManifest.xml"
                }

                when {
                    isPlainApk -> {
                        rawInput.copyTo(outputApk, overwrite = true)
                        AppArchiveImportResult.Success(AppArchiveImportResult.SourceKind.PlainApk)
                    }

                    nestedApkEntries.isEmpty() -> AppArchiveImportResult.NoApkEntry

                    nestedApkEntries.size == 1 -> {
                        val entry = nestedApkEntries.single()
                        if (entry.isRejectedFrameworkOrSplitEntry()) {
                            AppArchiveImportResult.SplitBundle
                        } else {
                            zip.extractApkEntry(entry, outputApk, AppArchiveImportResult.SourceKind.SingleApkFromBundle)
                        }
                    }

                    else -> zip.resolveUnsplitCandidate(nestedApkEntries, outputApk)
                }
            }
        } catch (_: ZipException) {
            AppArchiveImportResult.InvalidArchive
        }
    } finally {
        rawInput.delete()
    }
}

private fun ZipFile.resolveUnsplitCandidate(
    apkEntries: List<ZipEntry>,
    outputApk: File,
): AppArchiveImportResult {
    val rankedCandidates = apkEntries
        .filterNot { it.isRejectedFrameworkOrSplitEntry() }
        .mapNotNull { entry -> entry.unsplitRank()?.let { rank -> rank to entry } }
        .sortedWith(compareBy<Pair<Int, ZipEntry>> { it.first }.thenByDescending { it.second.size })

    val preferredRank = rankedCandidates.firstOrNull()?.first
    val preferred = rankedCandidates.filter { it.first == preferredRank }.map { it.second }

    val selected = when {
        preferred.isEmpty() -> {
            val nonSplit = apkEntries.filterNot { it.isRejectedFrameworkOrSplitEntry() }
            when (nonSplit.size) {
                0 -> return AppArchiveImportResult.SplitBundle
                1 -> nonSplit.single()
                else -> return AppArchiveImportResult.AmbiguousBaseApk
            }
        }

        preferred.size == 1 -> preferred.single()
        else -> return AppArchiveImportResult.AmbiguousBaseApk
    }

    return extractApkEntry(selected, outputApk, AppArchiveImportResult.SourceKind.UnsplitRecoveredBaseApk)
}

private fun ZipFile.extractApkEntry(
    entry: ZipEntry,
    outputApk: File,
    sourceKind: AppArchiveImportResult.SourceKind,
): AppArchiveImportResult {
    getInputStream(entry).use { source -> outputApk.outputStream().use { target -> source.copyTo(target) } }
    return if (!outputApk.hasZipMagicHeader()) {
        outputApk.delete()
        AppArchiveImportResult.InvalidMagicHeader
    } else {
        AppArchiveImportResult.Success(sourceKind)
    }
}

private fun File.hasZipMagicHeader(): Boolean = inputStream().use { input ->
    val header = ByteArray(4)
    if (input.read(header) != header.size) return@use false

    header.contentEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) ||
            header.contentEquals(byteArrayOf(0x50, 0x4B, 0x05, 0x06)) ||
            header.contentEquals(byteArrayOf(0x50, 0x4B, 0x07, 0x08))
}

private fun ZipEntry.unsplitRank(): Int? {
    val normalized = name.substringAfterLast('/').lowercase()
    return when {
        normalized == "universal.apk" -> 0
        normalized == "standalone.apk" -> 1
        normalized == "standalone-base.apk" -> 2
        normalized == "base.apk" -> 3
        normalized == "master.apk" -> 4
        normalized.startsWith("base-") && normalized.endsWith(".apk") -> 5
        normalized.endsWith("-base.apk") -> 6
        normalized.contains("base") && normalized.endsWith(".apk") -> 7
        else -> null
    }
}

private fun ZipEntry.isRejectedFrameworkOrSplitEntry(): Boolean {
    val normalizedPath = name.lowercase()
    val normalized = normalizedPath.substringAfterLast('/')

    return normalized == "framework-res.apk" ||
            normalized == "android.apk" ||
            normalized == "framework.apk" ||
            normalized == "overlay.apk" ||
            normalizedPath.contains("/system/framework/") ||
            normalizedPath.contains("/system/product/overlay/") ||
            normalizedPath.contains("/system/vendor/overlay/") ||
            normalizedPath.contains("/framework/") ||
            normalizedPath.contains("/overlay/") ||
            normalized.startsWith("split_") ||
            normalized.startsWith("config.") ||
            normalized.startsWith("feature_") ||
            normalized.startsWith("assetpack") ||
            normalized.contains("split_config") ||
            normalized.contains("dpi") ||
            normalized.contains("lang") ||
            normalized.contains("locale") ||
            normalized.contains("density") ||
            normalized.contains("abi") ||
            normalized.contains("arm64") ||
            normalized.contains("armeabi") ||
            normalized.contains("x86") ||
            normalized.contains("hdpi") ||
            normalized.contains("xhdpi") ||
            normalized.contains("xxhdpi") ||
            normalized.contains("xxxhdpi")
}
