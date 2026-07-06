package app.revanced.manager.domain.shizuku

import android.content.pm.PackageManager
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
    fun read(): ShizukuState = try {
        if (!Shizuku.pingBinder()) return ShizukuState.NotReady

        ShizukuState.Ready(
            uid = Shizuku.getUid(),
            apiVersion = Shizuku.getVersion(),
            permissionGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED,
        )
    } catch (_: IllegalStateException) {
        ShizukuState.NotReady
    }

    fun requestPermission(requestCode: Int) {
        if (!Shizuku.pingBinder()) return
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED &&
            !Shizuku.shouldShowRequestPermissionRationale()
        ) {
            Shizuku.requestPermission(requestCode)
        }
    }
}
