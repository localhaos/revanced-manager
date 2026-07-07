package app.revanced.manager.domain.sources

import androidx.compose.runtime.Stable
import app.revanced.manager.data.redux.ActionContext
import app.revanced.manager.patcher.patch.PatchBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

fun interface Loader<T> {
    fun load(file: File): T
}

typealias PatchBundleSource = Source<PatchBundle>

/**
 * A [PatchBundle] or [app.revanced.manager.downloader.Downloader] source.
 */
@Stable
sealed class Source<T>(
    val name: String,
    val uid: Int,
    error: Throwable?,
    protected val file: File,
    protected val loader: Loader<T>
) {
    val state = when {
        error != null -> State.Failed(error)
        !hasInstalled() -> State.Missing
        else -> try {
            State.Available(loader.load(file))
        } catch (t: Throwable) {
            State.Failed(t)
        }
    }

    val isDefault inline get() = uid == 0
    val loaded get() = @Suppress("UNCHECKED_CAST") (state as? State.Available<T>)?.obj
    val error get() = (state as? State.Failed)?.throwable

    suspend fun ActionContext.deleteLocalFile() = withContext(Dispatchers.IO) {
        file.delete()
    }

    abstract fun copy(error: Throwable? = this.error, name: String = this.name): Source<T>

    protected fun hasInstalled() = file.exists()

    protected fun outputStream(): OutputStream = with(file) {
        // Android 14+ requires dex containers to be readonly.
        try {
            setWritable(true, true)
            outputStream()
        } finally {
            setReadOnly()
        }
    }

    sealed interface State {
        data object Missing : State
        data class Failed(val throwable: Throwable) : State
        data class Available<T>(val obj: T) : State
    }
}

val <T> Source<T>.displayName: String
    get() {
        val loadedName = (loaded as? PatchBundle)?.manifestAttributes?.name.cleanSourceText()
        if (loadedName != null) return loadedName

        val explicitName = name.cleanSourceText()
        if (explicitName != null) return explicitName

        val remoteName = (this as? RemoteSource<*>)?.endpoint?.let(GitHubBundleAutoFinder::displayNameFrom).cleanSourceText()
        if (remoteName != null) return remoteName

        return "Source #$uid"
    }

private fun String?.cleanSourceText(): String? = this
    ?.trim()
    ?.takeUnless { value ->
        value.isBlank() ||
                value.equals("null", ignoreCase = true) ||
                value.equals("vnull", ignoreCase = true) ||
                value.equals("Bez nazwy", ignoreCase = true) ||
                value.equals("No name", ignoreCase = true)
    }

object Extensions {
    val <T> Source<T>.asRemoteOrNull inline get() = this as? RemoteSource<T>
    val PatchBundleSource.version get() = loaded?.manifestAttributes?.version?.cleanPatchVersion()
}

private fun String?.cleanPatchVersion(): String? = this
    ?.trim()
    ?.removePrefix("v")
    ?.takeUnless { value -> value.isBlank() || value.equals("null", ignoreCase = true) }
}
