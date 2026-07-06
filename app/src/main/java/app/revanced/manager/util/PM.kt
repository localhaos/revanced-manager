package app.revanced.manager.util

import android.annotation.SuppressLint
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.PackageManager.PackageInfoFlags
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.domain.repository.PatchBundleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import ru.solrudev.ackpine.session.await
import ru.solrudev.ackpine.session.parameters.Confirmation
import ru.solrudev.ackpine.uninstaller.PackageUninstaller
import ru.solrudev.ackpine.uninstaller.createSession
import ru.solrudev.ackpine.uninstaller.parameters.UninstallParametersDsl
import java.io.File

@Immutable
@Parcelize
data class AppInfo(
    val packageName: String,
    val patches: Int?,
    val packageInfo: PackageInfo?
) : Parcelable

@SuppressLint("QueryPermissionsNeeded")
class PM(
    private val app: Application,
    patchBundleRepository: PatchBundleRepository,
    prefs: PreferencesManager,
    private val uninstaller: PackageUninstaller
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    val appList = combine(
        patchBundleRepository.bundleInfoFlow,
        prefs.disableUniversalPatchCheck.flow
    ) { bundles, showInstalled ->
        val compatibleApps = scope.async {
            val compatiblePackages = bundles
                .flatMap { (_, bundle) -> bundle.patches }
                .flatMap { it.compatiblePackages.orEmpty() }
                .groupingBy { it.packageName }
                .eachCount()

            compatiblePackages.keys.map { pkg ->
                getPackageInfo(pkg)?.let { packageInfo ->
                    AppInfo(
                        pkg,
                        compatiblePackages[pkg],
                        packageInfo
                    )
                } ?: AppInfo(
                    pkg,
                    compatiblePackages[pkg],
                    null
                )
            }
        }

        val installedApps = if (showInstalled) {
            scope.async {
                getInstalledPackages().map { packageInfo ->
                    AppInfo(packageInfo.packageName, 0, packageInfo)
                }
            }
        } else null

        val compatible = compatibleApps.await()

        if (compatible.isNotEmpty()) {
            val base = if (installedApps != null) {
                (compatible + installedApps.await()).distinctBy { it.packageName }
            } else {
                compatible
            }

            base.sortedWith(
                compareByDescending<AppInfo> {
                    it.packageInfo != null && (it.patches ?: 0) > 0
                }.thenByDescending {
                    it.patches
                }.thenBy {
                    it.packageInfo?.label()
                }.thenBy { it.packageName }
            )
        } else {
            emptyList()
        }
    }.flowOn(Dispatchers.IO)

    private fun getInstalledPackages(flags: Int = 0): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            app.packageManager.getInstalledPackages(PackageInfoFlags.of(flags.toLong()))
        else
            app.packageManager.getInstalledPackages(flags)

    fun getPackageInfo(packageName: String, flags: Int = 0): PackageInfo? =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                app.packageManager.getPackageInfo(packageName, PackageInfoFlags.of(flags.toLong()))
            else
                app.packageManager.getPackageInfo(packageName, flags)
        } catch (_: NameNotFoundException) {
            null
        }

    fun getPackageInfo(file: File): PackageInfo? {
        val path = file.absolutePath
        val pkgInfo = app.packageManager.getPackageArchiveInfo(path, 0) ?: return null

        // This is needed in order to load label and icon.
        pkgInfo.applicationInfo!!.apply {
            sourceDir = path
            publicSourceDir = path
        }

        return pkgInfo
    }

    fun PackageInfo.label() = this.applicationInfo!!.loadLabel(app.packageManager).toString()
    fun getResources(packageInfo: PackageInfo) = app.packageManager.getResourcesForApplication(packageInfo.applicationInfo!!)

    fun getVersionCode(packageInfo: PackageInfo) = PackageInfoCompat.getLongVersionCode(packageInfo)

    suspend fun uninstallPackage(pkg: String, config: UninstallParametersDsl.() -> Unit = {}) = withContext(Dispatchers.IO) {
        uninstaller.createSession(pkg) {
            confirmation = Confirmation.IMMEDIATE
            config()
        }.await()
    }

    fun installPackage(apk: File) {
        require(apk.exists() && apk.isFile) { "APK file does not exist: ${apk.absolutePath}" }
        require(apk.extension.equals("apk", ignoreCase = true)) { "File is not an APK: ${apk.name}" }
        if (!canInstallPackages()) {
            openUnknownAppInstallSettings()
            throw IllegalStateException("Package installation permission is not granted. Enable unknown app installs for ${app.packageName} and retry installation.")
        }

        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", apk)
        val installIntent = createApkInstallIntent(uri, Intent.ACTION_INSTALL_PACKAGE)
        val viewIntent = createApkInstallIntent(uri, Intent.ACTION_VIEW)

        grantPackageInstallerReadAccess(uri, installIntent)
        grantPackageInstallerReadAccess(uri, viewIntent)

        val activityIntent = when {
            installIntent.hasResolvedActivity() -> installIntent
            viewIntent.hasResolvedActivity() -> viewIntent
            else -> throw IllegalStateException("No package installer is available")
        }

        val chooser = Intent.createChooser(activityIntent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(viewIntent, installIntent))
        }

        try {
            app.startActivity(chooser)
        } catch (error: ActivityNotFoundException) {
            throw IllegalStateException("No package installer is available", error)
        }
    }

    fun openUnknownAppInstallSettings() {
        val appSettingsUri = Uri.parse("package:${app.packageName}")
        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, appSettingsUri),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )

        val intent = intents.firstOrNull { it.hasResolvedActivity() }
            ?: throw IllegalStateException("No settings activity is available")

        app.startActivity(intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }

    private fun createApkInstallIntent(uri: Uri, action: String) = Intent(action).apply {
        setDataAndType(uri, APK_MIME_TYPE)
        putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        putExtra(Intent.EXTRA_RETURN_RESULT, true)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = android.content.ClipData.newUri(app.contentResolver, "APK", uri)
    }

    private fun grantPackageInstallerReadAccess(uri: Uri, intent: Intent) {
        app.packageManager.queryIntentActivities(intent, 0).forEach { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.packageName ?: return@forEach
            app.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun Intent.hasResolvedActivity() = resolveActivity(app.packageManager) != null

    fun launch(pkg: String) = app.packageManager.getLaunchIntentForPackage(pkg)?.let {
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(it)
    }

    fun canInstallPackages() = app.packageManager.canRequestPackageInstalls()

    companion object {
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}

/**
 * Returns true if this package is a split APK (i.e. has multiple split source dirs).
 * Split APKs cannot be used directly as a patch source.
 */
fun PackageInfo.isSplitApk(): Boolean =
    !applicationInfo?.splitSourceDirs.isNullOrEmpty() || !splitNames.isNullOrEmpty()
