package app.revanced.manager.util

import android.content.Context
import android.content.Intent
import app.revanced.manager.MainActivity
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

object GeneratedApkInstallHandler {
    private val pendingApks = ConcurrentLinkedQueue<File>()

    fun enqueue(apk: File) {
        require(apk.exists() && apk.isFile) { "APK file does not exist: ${apk.absolutePath}" }
        require(apk.extension.equals("apk", ignoreCase = true)) { "File is not an APK: ${apk.name}" }
        pendingApks.add(apk)
    }

    fun requestForegroundInstall(context: Context, apk: File) {
        enqueue(apk)
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_INSTALL_GENERATED_APK
                putExtra(EXTRA_GENERATED_APK_PATH, apk.absolutePath)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
    }

    fun drainPending(): List<File> = buildList {
        while (true) {
            add(pendingApks.poll() ?: break)
        }
    }

    fun fileFromIntent(intent: Intent?): File? {
        if (intent?.action != ACTION_INSTALL_GENERATED_APK) return null
        val path = intent.getStringExtra(EXTRA_GENERATED_APK_PATH)?.takeIf { it.isNotBlank() } ?: return null
        return File(path)
    }

    const val ACTION_INSTALL_GENERATED_APK = "app.revanced.manager.action.INSTALL_GENERATED_APK"
    const val EXTRA_GENERATED_APK_PATH = "app.revanced.manager.extra.GENERATED_APK_PATH"
}
