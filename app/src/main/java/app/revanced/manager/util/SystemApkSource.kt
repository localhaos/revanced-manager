package app.revanced.manager.util

import android.content.pm.PackageInfo
import java.io.File

/**
 * Copies the installed package base APK from Android's package source directory into a patcher-readable temp file.
 *
 * For split-installed apps, PackageInfo.applicationInfo.sourceDir normally points to base.apk while
 * splitSourceDirs points to config/resource splits. The patcher must receive one APK input, so this helper copies
 * only sourceDir and intentionally ignores splitSourceDirs.
 */
fun copySystemBaseApk(
    packageInfo: PackageInfo,
    outputApk: File,
): Boolean {
    val sourceDir = packageInfo.applicationInfo?.sourceDir ?: return false
    val source = File(sourceDir)
    if (!source.isFile || !source.canRead()) return false

    val result = source.inputStream().use { input ->
        importSingleApkArchive(input, outputApk)
    }

    return result is AppArchiveImportResult.Success
}
