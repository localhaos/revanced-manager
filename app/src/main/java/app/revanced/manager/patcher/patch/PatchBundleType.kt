package app.revanced.manager.patcher.patch

/**
 * Patch bundle runtime family.
 *
 * Keep this model independent from UI labels. It is used to isolate patcher runtime families
 * and to prevent non-ReVanced bundles from affecting ReVanced-specific compatibility logic.
 */
enum class PatchBundleType(
    val primaryExtension: String,
    private val markers: Set<String>,
) {
    REVANCED(
        primaryExtension = ".rvp",
        markers = setOf(".rvp", "revanced"),
    ),
    MORPHE(
        primaryExtension = ".mpp",
        markers = setOf(".mpp", "morphe", "hoo-dles"),
    ),
    AMPLE(
        primaryExtension = ".arp",
        markers = setOf(".arp", "ample", "amplerevanced"),
    );

    fun matches(text: String): Boolean {
        val normalized = text.lowercase()
        return markers.any { marker -> marker in normalized }
    }

    companion object {
        fun detect(vararg values: String?): PatchBundleType {
            val haystack = values
                .filterNotNull()
                .joinToString("\n")
                .lowercase()

            return entries.firstOrNull { it.matches(haystack) } ?: REVANCED
        }
    }
}
