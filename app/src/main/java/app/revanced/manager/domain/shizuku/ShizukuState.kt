package app.revanced.manager.domain.shizuku

import android.content.pm.PackageManager
import android.os.DeadObjectException
import android.os.RemoteException
import rikka.shizuku.Shizuku

sealed interface ShizukuState {
    data object NotReady : ShizukuState
    data class Ready(
        val uid: Int,
        val apiVersion: Int,
        val permissionGranted: Boolean,
    ) : ShizukuState
}

object ShizukuStateReader {
    fun read(): ShizukuState = runCatching {
        if (!hasLiveBinder()) return ShizukuState.NotReady

        ShizukuState.Ready(
            uid = Shizuku.getUid(),
            apiVersion = Shizuku.getVersion(),
            permissionGranted = checkPermissionGranted(),
        )
    }.getOrElse { error ->
        if (error.isRecoverableBinderFailure()) ShizukuState.NotReady else throw error
    }

    fun requestPermission(requestCode: Int) {
        runCatching {
            if (!hasLiveBinder()) return
            if (checkPermissionGranted()) return
            if (Shizuku.shouldShowRequestPermissionRationale()) return

            Shizuku.requestPermission(requestCode)
        }.getOrElse { error ->
            if (!error.isRecoverableBinderFailure()) throw error
        }
    }

    private fun hasLiveBinder(): Boolean = runCatching {
        Shizuku.pingBinder()
    }.getOrDefault(false)

    private fun checkPermissionGranted(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrElse { error ->
        if (error.isRecoverableBinderFailure()) false else throw error
    }

    private fun Throwable.isRecoverableBinderFailure(): Boolean =
        this is IllegalStateException ||
                this is NullPointerException ||
                this is DeadObjectException ||
                this is RemoteException ||
                message?.contains("asBinder()", ignoreCase = true) == true ||
                message?.contains("null object reference", ignoreCase = true) == true
}
