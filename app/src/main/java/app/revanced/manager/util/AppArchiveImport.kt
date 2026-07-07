package app.revanced.manager.util

import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
        MergedSplitBundle,
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
 * - Experimental APK merge mode: base APK + safe split payload entries are packed into one APK.
 *
 * APK merge mode intentionally does not run aapt/aapt2 and does not merge split resource tables.
 * It skips AndroidManifest.xml, resources.arsc, res/**, signatures, framework APKs and overlay APKs from splits.
 * It can still recover useful dex/native/assets payloads from split bundles for patcher input.
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

                    else -> zip.resolveAndMergeSplitBundle(nestedApkEntries, outputApk)
                }
            }
        } catch (_: ZipException) {
            AppArchiveImportResult.InvalidArchive
        }
    } finally {
        rawInput.delete()
    }
}

private fun ZipFile.resolveAndMergeSplitBundle(
    apkEntries: List<ZipEntry>,
    outputApk: File,
): AppArchiveImportResult {
    val base = selectBaseApkCandidate(apkEntries) ?: return AppArchiveImportResult.SplitBundle
    val splitEntries = apkEntries.filter { entry ->
        entry.name != base.name && !entry.isRejectedFrameworkEntry()
    }

    val merged = mergeApkEntries(
        baseEntry = base,
        splitEntries = splitEntries,
        outputApk = outputApk,
    )

    if (merged == AppArchiveImportResult.Success(AppArchiveImportResult.SourceKind.MergedSplitBundle)) {
        return merged
    }

    return extractApkEntry(base, outputApk, AppArchiveImportResult.SourceKind.UnsplitRecoveredBaseApk)
}

private fun selectBaseApkCandidate(apkEntries: List<ZipEntry>): ZipEntry? {
    val rankedCandidates = apkEntries
        .filterNot { it.isRejectedFrameworkEntry() }
        .mapNotNull { entry -> entry.unsplitRank()?.let { rank -> rank to entry } }
        .sortedWith(compareBy<Pair<Int, ZipEntry>> { it.first }.thenByDescending { it.second.size })

    val preferredRank = rankedCandidates.firstOrNull()?.first
    val preferred = rankedCandidates.filter { it.first == preferredRank }.map { it.second }

    if (preferred.size == 1) return preferred.single()
    if (preferred.size > 1) return null

    val nonSplit = apkEntries.filterNot { it.isRejectedFrameworkOrSplitEntry() }
    return when (nonSplit.size) {
        1 -> nonSplit.single()
        else -> null
    }
}

private fun ZipFile.mergeApkEntries(
    baseEntry: ZipEntry,
    splitEntries: List<ZipEntry>,
    outputApk: File,
): AppArchiveImportResult {
    val written = linkedSetOf<String>()
    var nextDexIndex = 1
    var copiedSplitPayload = false

    outputApk.delete()

    try {
        ZipOutputStream(outputApk.outputStream().buffered()).use { output ->
            getInputStream(baseEntry).use { baseInput ->
                ZipInputStream(baseInput.buffered()).use { baseZip ->
                    while (true) {
                        val entry = baseZip.nextEntry ?: break
                        if (!entry.isDirectory && entry.name.isMergeableBaseEntry()) {
                            output.copyZipEntry(entry.name, baseZip)
                            written += entry.name
                            if (entry.name.isDexEntry()) {
                                nextDexIndex = maxOf(nextDexIndex, entry.name.dexIndex() + 1)
                            }
                        }
                        baseZip.closeEntry()
                    }
                }
            }

            splitEntries.forEach { splitApkEntry ->
                getInputStream(splitApkEntry).use { splitInput ->
                    ZipInputStream(splitInput.buffered()).use { splitZip ->
                        while (true) {
                            val entry = splitZip.nextEntry ?: break
                            if (!entry.isDirectory && entry.name.isMergeableSplitPayloadEntry()) {
                                val targetName = if (entry.name.isDexEntry()) {
                                    "classes${nextDexIndex++}.dex"
                                } else {
                                    entry.name
                                }

                                if (targetName !in written) {
                                    output.copyZipEntry(targetName, splitZip)
                                    written += targetName
                                    copiedSplitPayload = true
                                }
                            }
                            splitZip.closeEntry()
                        }
                    }
                }
            }
        }
    } catch (_: ZipException) {
        outputApk.delete()
        return AppArchiveImportResult.InvalidArchive
    }

    return if (!outputApk.hasZipMagicHeader()) {
        outputApk.delete()
        AppArchiveImportResult.InvalidMagicHeader
    } else if (copiedSplitPayload) {
        AppArchiveImportResult.Success(AppArchiveImportResult.SourceKind.MergedSplitBundle)
    } else {
        AppArchiveImportResult.Success(AppArchiveImportResult.SourceKind.UnsplitRecoveredBaseApk)
    }
}

private fun ZipOutputStream.copyZipEntry(
    name: String,
    input: InputStream,
) {
    putNextEntry(ZipEntry(name))
    input.copyTo(this)
    closeEntry()
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

private fun ZipEntry.isRejectedFrameworkEntry(): Boolean {
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
            normalized.startsWith("assetpack")
}

private fun ZipEntry.isRejectedFrameworkOrSplitEntry(): Boolean =
    isRejectedFrameworkEntry() || name.substringAfterLast('/').lowercase().isSplitLikeApkName()

private fun String.isSplitLikeApkName(): Boolean = startsWith("split_") ||
        startsWith("config.") ||
        startsWith("feature_") ||
        contains("split_config") ||
        contains("dpi") ||
        contains("lang") ||
        contains("locale") ||
        contains("density") ||
        contains("abi") ||
        contains("arm64") ||
        contains("armeabi") ||
        contains("x86") ||
        contains("hdpi") ||
        contains("xhdpi") ||
        contains("xxhdpi") ||
        contains("xxxhdpi")

private fun String.isMergeableBaseEntry(): Boolean = !isSignatureEntry()

private fun String.isMergeableSplitPayloadEntry(): Boolean = isDexEntry() ||
        startsWith("lib/") ||
        startsWith("assets/") ||
        startsWith("kotlin/") ||
        startsWith("META-INF/services/")

private fun String.isSignatureEntry(): Boolean = startsWith("META-INF/") &&
        (endsWith(".RSA", ignoreCase = true) ||
                endsWith(".DSA", ignoreCase = true) ||
                endsWith(".EC", ignoreCase = true) ||
                endsWith(".SF", ignoreCase = true) ||
                substringAfterLast('/') == "MANIFEST.MF")

private fun String.isDexEntry(): Boolean = matches(Regex("classes(\\d*)\\.dex"))

private fun String.dexIndex(): Int = removePrefix("classes")
    .removeSuffix(".dex")
    .ifBlank { "1" }
    .toIntOrNull() ?: 1
