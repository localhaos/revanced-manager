package app.revanced.manager.util

import android.content.Context
import android.content.Intent
import app.revanced.manager.MainActivity
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

object GeneratedApkInstallHandler {
    private const val PREFS_NAME = "generated_apk_install_handler"
    private const val PENDING_APK_PATHS = "pending_apk_paths"
    private const val DELIMITER = "\n"

    private val pendingApks = ConcurrentLinkedQueue<File>()

    fun enqueue(context: Context, apk: File) {
        val checked = apk.checkedApk()
        pendingApks.add(checked)
        persist(context.applicationContext)
    }

    fun requestForegroundInstall(context: Context, apk: File) {
        enqueue(context, apk)
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

    fun restore(context: Context) {
        val restored = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PENDING_APK_PATHS, null)
            ?.split(DELIMITER)
            ?.asSequence()
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.map(::File)
            ?.filter { it.exists() && it.isFile && it.extension.equals("apk", ignoreCase = true) }
            ?.toList()
            .orEmpty()

        restored.forEach(pendingApks::add)
        persist(context.applicationContext)
    }

    fun drainPending(context: Context): List<File> {
        restore(context)

        val drained = buildList {
            while (true) {
                add(pendingApks.poll() ?: break)
            }
        }.distinctBy(File::getAbsolutePath)

        persist(context.applicationContext)
        return drained
    }

    fun fileFromIntent(intent: Intent?): File? {
        if (intent?.action != ACTION_INSTALL_GENERATED_APK) return null
        val path = intent.getStringExtra(EXTRA_GENERATED_APK_PATH)?.takeIf { it.isNotBlank() } ?: return null
        return File(path)
    }

    private fun persist(context: Context) {
        val paths = pendingApks
            .asSequence()
            .filter { it.exists() && it.isFile && it.extension.equals("apk", ignoreCase = true) }
            .map(File::getAbsolutePath)
            .distinct()
            .joinToString(DELIMITER)

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PENDING_APK_PATHS, paths)
            .apply()
    }

    private fun File.checkedApk(): File {
        require(exists() && isFile) { "APK file does not exist: $absolutePath" }
        require(extension.equals("apk", ignoreCase = true)) { "File is not an APK: $name" }
        return this
    }

    const val ACTION_INSTALL_GENERATED_APK = "app.revanced.manager.action.INSTALL_GENERATED_APK"
    const val EXTRA_GENERATED_APK_PATH = "app.revanced.manager.extra.GENERATED_APK_PATH"
}
