package app.revanced.manager.util

import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Build
import androidx.compose.runtime.Immutable

@Immutable
data class InstallSourceInfoCompat(
    val installerPackageName: String?,
    val initiatingPackageName: String?,
    val originatingPackageName: String?,
) {
    val isPlayStoreInstall: Boolean
        get() = installerPackageName == PLAY_STORE_PACKAGE ||
                initiatingPackageName == PLAY_STORE_PACKAGE ||
                originatingPackageName == PLAY_STORE_PACKAGE

    val isKnownInstall: Boolean
        get() = installerPackageName != null || initiatingPackageName != null || originatingPackageName != null

    companion object {
        private const val PLAY_STORE_PACKAGE = "com.android.vending"
    }
}

object InstallSourceResolver {
    fun resolve(context: Context, packageName: String): InstallSourceInfoCompat {
        val pm = context.packageManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val source = try {
                pm.getInstallSourceInfo(packageName)
            } catch (_: NameNotFoundException) {
                return InstallSourceInfoCompat(null, null, null)
            }

            InstallSourceInfoCompat(
                installerPackageName = source.installingPackageName,
                initiatingPackageName = source.initiatingPackageName,
                originatingPackageName = source.originatingPackageName,
            )
        } else {
            @Suppress("DEPRECATION")
            InstallSourceInfoCompat(
                installerPackageName = pm.getInstallerPackageName(packageName),
                initiatingPackageName = null,
                originatingPackageName = null,
            )
        }
    }
}
