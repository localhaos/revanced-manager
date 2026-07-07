package app.revanced.manager.patcher.patch

import android.os.Parcelable
import app.revanced.patcher.patch.Patch
import app.revanced.patcher.patch.loadPatches
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.io.File
import java.io.IOException
import java.util.jar.JarFile

@Parcelize
data class PatchBundle(val patchesJar: String) : Parcelable {
    /**
     * The [java.util.jar.Manifest] of [patchesJar].
     */
    @IgnoredOnParcel
    private val manifest by lazy {
        try {
            JarFile(patchesJar).use { it.manifest }
        } catch (_: IOException) {
            null
        }
    }

    @IgnoredOnParcel
    val manifestAttributes by lazy {
        if (manifest != null)
            ManifestAttributes(
                name = readManifestAttribute("name"),
                version = readManifestAttribute("version"),
                description = readManifestAttribute("description"),
                source = readManifestAttribute("source"),
                author = readManifestAttribute("author"),
                contact = readManifestAttribute("contact"),
                website = readManifestAttribute("website"),
                license = readManifestAttribute("license")
            ) else
            null
    }

    private fun readManifestAttribute(name: String) = manifest?.mainAttributes?.getValue(name)
        ?.takeIf { it.isNotBlank() } // If empty, set it to null instead.

    data class ManifestAttributes(
        val name: String?,
        val version: String?,
        val description: String?,
        val source: String?,
        val author: String?,
        val contact: String?,
        val website: String?,
        val license: String?
    )

    object Loader {
        private fun patches(bundles: Iterable<PatchBundle>) = buildMap {
            bundles.forEach { bundle ->
                val file = File(bundle.patchesJar)
                val preflightError = file.preflightPatchBundleError()
                if (preflightError != null) {
                    this[bundle] = Result.failure(preflightError)
                    return@forEach
                }

                try {
                    var failure: Throwable? = null
                    val loaded = loadPatches(
                        file,
                        onFailedToLoad = { _, throwable -> failure = throwable }
                    ).patchesByFile[file]

                    when {
                        failure != null -> this[bundle] = Result.failure(failure!!)
                        loaded != null -> this[bundle] = Result.success(loaded)
                        else -> this[bundle] = Result.failure(
                            IOException("Patch bundle '${file.name}' did not produce patch metadata")
                        )
                    }
                } catch (throwable: Throwable) {
                    this[bundle] = Result.failure(throwable)
                }
            }
        }

        fun metadata(bundles: Iterable<PatchBundle>): Map<PatchBundle, Result<Set<PatchInfo>>> =
            patches(bundles).mapValues { (_, result) ->
                result.map { patches ->
                    patches.mapTo(
                        HashSet(patches.size),
                        ::PatchInfo
                    )
                }
            }

        fun patches(bundles: Iterable<PatchBundle>, packageName: String): Map<PatchBundle, Set<Patch>> =
            patches(bundles).mapValues { (_, result) ->
                val patches = result.getOrDefault(emptySet())

                patches.filterTo(HashSet(patches.size)) { patch ->
                    val compatiblePackages = patch.compatiblePackages
                        ?: // The patch has no compatibility constraints, which means it is universal.
                        return@filterTo true

                    if (!compatiblePackages.any { (name, _) -> name == packageName }) {
                        // Patch is not compatible with this package.
                        return@filterTo false
                    }

                    true
                }
            }

        private fun File.preflightPatchBundleError(): Throwable? = when {
            !exists() -> IOException("Patch bundle file is missing: $absolutePath")
            !isFile -> IOException("Patch bundle path is not a file: $absolutePath")
            length() <= 0L -> IOException("Patch bundle file is empty: $absolutePath")
            else -> null
        }
    }
}
