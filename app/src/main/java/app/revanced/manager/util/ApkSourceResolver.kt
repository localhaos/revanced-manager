package app.revanced.manager.util

import android.content.pm.PackageInfo
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.ZipFile

/**
 * Resolves user-selected app files from storage.
 *
 * Supported inputs:
 * - single APK files
 * - APK-like files with a wrong extension or MIME type, detected by ZIP magic header
 * - APK bundles packaged as ZIP containers: .apks, .xapk, .apkm
 *
 * Anti-split behavior:
 * - split-only APKs are rejected as patch sources
 * - bundle containers are scanned for a non-split base APK
 * - split config APKs are never selected as the patch source
 */
object ApkSourceResolver {
    fun resolve(
        input: InputStream,
        outputFile: File,
        scratchDir: File,
        pm: PM,
        expectedPackageName: String? = null,
    ): PackageInfo? {
        scratchDir.mkdirs()
        outputFile.parentFile?.mkdirs()

        val inputFile = File(scratchDir, "selected_source.input").also(File::delete)
        input.use { stream -> Files.copy(stream, inputFile.toPath()) }

        try {
            if (!inputFile.hasZipMagicHeader()) return null

            pm.getPackageInfo(inputFile)?.let { info ->
                if (expectedPackageName != null && info.packageName != expectedPackageName) return null
                if (info.isSplitApk()) return null

                inputFile.copyTo(outputFile, overwrite = true)
                return info
            }

            return resolveFromBundle(
                bundleFile = inputFile,
                outputFile = outputFile,
                scratchDir = scratchDir,
                pm = pm,
                expectedPackageName = expectedPackageName
            )
        } finally {
            inputFile.delete()
        }
    }

    private fun resolveFromBundle(
        bundleFile: File,
        outputFile: File,
        scratchDir: File,
        pm: PM,
        expectedPackageName: String?,
    ): PackageInfo? {
        if (!bundleFile.hasZipMagicHeader()) return null

        val candidatesDir = File(scratchDir, "selected_source_bundle").also {
            it.deleteRecursively()
            it.mkdirs()
        }

        try {
            val candidates = ZipFile(bundleFile).use { zip ->
                zip.entries().asSequence()
                    .filter { entry ->
                        !entry.isDirectory &&
                            entry.name.endsWith(".apk", ignoreCase = true) &&
                            !entry.name.contains("..")
                    }
                    .mapIndexedNotNull { index, entry ->
                        val name = entry.name.substringAfterLast('/')
                        val candidate = File(candidatesDir, "candidate_${index}_$name")
                        zip.getInputStream(entry).use { input -> Files.copy(input, candidate.toPath()) }
                        candidate.takeIf { it.hasZipMagicHeader() }
                    }
                    .toList()
            }

            val ranked = candidates
                .mapNotNull { file ->
                    val info = pm.getPackageInfo(file) ?: return@mapNotNull null
                    if (expectedPackageName != null && info.packageName != expectedPackageName) return@mapNotNull null
                    if (info.isSplitApk()) return@mapNotNull null
                    Candidate(file, info, file.bundleBaseScore())
                }
                .sortedBy { it.score }

            val selected = ranked.firstOrNull() ?: return null
            selected.file.copyTo(outputFile, overwrite = true)
            return selected.info
        } finally {
            candidatesDir.deleteRecursively()
        }
    }

    private data class Candidate(
        val file: File,
        val info: PackageInfo,
        val score: Int,
    )

    private fun File.bundleBaseScore(): Int {
        val name = name.lowercase()
        return when {
            name == "base.apk" -> 0
            name.endsWith("/base.apk") -> 0
            "base" in name -> 1
            "universal" in name -> 2
            else -> 10
        }
    }

    private fun File.hasZipMagicHeader(): Boolean {
        if (!isFile || length() < 4) return false

        val header = ByteArray(4)
        inputStream().use { input ->
            if (input.read(header) != header.size) return false
        }

        return header[0] == 0x50.toByte() &&
            header[1] == 0x4B.toByte() &&
            header[2] in setOf(0x03.toByte(), 0x05.toByte(), 0x07.toByte()) &&
            header[3] in setOf(0x04.toByte(), 0x06.toByte(), 0x08.toByte())
    }
}
