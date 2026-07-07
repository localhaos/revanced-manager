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
    }
}

/**
 * Imports a user-selected application archive into a single APK file usable by the patcher.
 *
 * Supported:
 * - Plain APK files. APKs are ZIP files and must have a ZIP magic header.
 * - Bundle-like ZIP containers (.apks/.xapk/.apkm/.zip) when they contain exactly one APK entry.
 * - Anti-split mode for bundle-like containers when exactly one safe base APK can be resolved.
 *
 * Anti-split mode extracts only the canonical base APK candidate and refuses config/split-only bundles.
 * The patcher still receives a single monolithic input file path and never receives split_config APKs.
 *
 * Rejected:
 * - Files without a ZIP/APK magic header.
 * - ZIP containers without AndroidManifest.xml and without a nested APK.
 * - Split-only bundles with no base APK candidate.
 * - Bundles with multiple competing base APK candidates.
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
                        if (entry.isLikelySplitApkEntry()) {
                            AppArchiveImportResult.SplitBundle
                        } else {
                            zip.extractApkEntry(entry, outputApk, AppArchiveImportResult.SourceKind.SingleApkFromBundle)
                        }
                    }

                    else -> {
                        val baseCandidates = nestedApkEntries.filter { it.isLikelyBaseApkEntry() }
                        when {
                            baseCandidates.isEmpty() -> AppArchiveImportResult.SplitBundle
                            baseCandidates.size > 1 -> AppArchiveImportResult.AmbiguousBaseApk
                            else -> zip.extractApkEntry(
                                baseCandidates.single(),
                                outputApk,
                                AppArchiveImportResult.SourceKind.BaseApkFromSplitBundle
                            )
                        }
                    }
                }
            }
        } catch (_: ZipException) {
            AppArchiveImportResult.InvalidArchive
        }
    } finally {
        rawInput.delete()
    }
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

    // ZIP local file header, empty archive header or spanned archive header.
    header.contentEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) ||
            header.contentEquals(byteArrayOf(0x50, 0x4B, 0x05, 0x06)) ||
            header.contentEquals(byteArrayOf(0x50, 0x4B, 0x07, 0x08))
}

private fun ZipEntry.isLikelyBaseApkEntry(): Boolean {
    val normalized = name.substringAfterLast('/').lowercase()
    return normalized == "base.apk" ||
            normalized == "master.apk" ||
            normalized == "universal.apk" ||
            normalized == "standalone.apk" ||
            normalized == "standalone-base.apk" ||
            normalized.startsWith("base-") && normalized.endsWith(".apk")
}

private fun ZipEntry.isLikelySplitApkEntry(): Boolean {
    val normalized = name.substringAfterLast('/').lowercase()
    return normalized.startsWith("split_") ||
            normalized.startsWith("config.") ||
            normalized.contains("split_config") ||
            normalized.contains("dpi") ||
            normalized.contains("lang") ||
            normalized.contains("density") ||
            normalized.contains("abi")
}
