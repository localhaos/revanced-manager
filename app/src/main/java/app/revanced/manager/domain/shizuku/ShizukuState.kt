package app.revanced.manager.domain.shizuku

import android.content.pm.PackageManager
import android.os.DeadObjectException
import android.os.RemoteException
import java.lang.reflect.InvocationTargetException

sealed interface ShizukuState {
    data object NotReady : ShizukuState
    data class Ready(
        val uid: Int,
        val apiVersion: Int,
        val permissionGranted: Boolean,
    ) : ShizukuState
}

object ShizukuStateReader {
    fun read(): ShizukuState {
        if (!ShizukuSafeApi.pingBinder()) return ShizukuState.NotReady

        val uid = ShizukuSafeApi.getUid() ?: return ShizukuState.NotReady
        val apiVersion = ShizukuSafeApi.getVersion() ?: return ShizukuState.NotReady

        return ShizukuState.Ready(
            uid = uid,
            apiVersion = apiVersion,
            permissionGranted = ShizukuSafeApi.checkSelfPermission() == PackageManager.PERMISSION_GRANTED,
        )
    }

    fun requestPermission(requestCode: Int) {
        if (!ShizukuSafeApi.pingBinder()) return
        if (ShizukuSafeApi.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return
        if (ShizukuSafeApi.shouldShowRequestPermissionRationale() == true) return

        ShizukuSafeApi.requestPermission(requestCode)
    }
}

private object ShizukuSafeApi {
    private val shizukuClass: Class<*>? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        runCatching { Class.forName("rikka.shizuku.Shizuku") }.getOrNull()
    }

    fun pingBinder(): Boolean = callBoolean("pingBinder") ?: false
    fun getUid(): Int? = callInt("getUid")
    fun getVersion(): Int? = callInt("getVersion")
    fun checkSelfPermission(): Int = callInt("checkSelfPermission") ?: PackageManager.PERMISSION_DENIED
    fun shouldShowRequestPermissionRationale(): Boolean? = callBoolean("shouldShowRequestPermissionRationale")
    fun requestPermission(requestCode: Int) {
        callUnit("requestPermission", Int::class.javaPrimitiveType, requestCode)
    }

    private fun callBoolean(methodName: String): Boolean? = safeCall(methodName) as? Boolean
    private fun callInt(methodName: String): Int? = safeCall(methodName) as? Int

    private fun callUnit(methodName: String, parameterType: Class<*>?, argument: Any) {
        safeCall(methodName, parameterType, argument)
    }

    private fun safeCall(methodName: String, parameterType: Class<*>? = null, argument: Any? = null): Any? =
        runCatching {
            val clazz = shizukuClass ?: return null
            val method = if (parameterType != null) clazz.getMethod(methodName, parameterType) else clazz.getMethod(methodName)
            if (parameterType != null) method.invoke(null, argument) else method.invoke(null)
        }.getOrElse { error ->
            val root = if (error is InvocationTargetException) error.targetException ?: error else error
            if (root.isRecoverableBinderFailure()) null else throw root
        }

    private fun Throwable.isRecoverableBinderFailure(): Boolean =
        this is IllegalStateException ||
                this is NullPointerException ||
                this is DeadObjectException ||
                this is RemoteException ||
                this is SecurityException ||
                message?.contains("asBinder()", ignoreCase = true) == true ||
                message?.contains("null object reference", ignoreCase = true) == true ||
                message?.contains("binder", ignoreCase = true) == true
}
