package app.revanced.manager.patcher.patch

/**
 * Patch bundle runtime family.
 *
 * Universal ReVanced Manager separates bundles into three families:
 * - REVANCED: API v4 ReVanced-compatible .rvp/.jar bundles.
 * - MORPHE: Morphe .mpp bundles.
 * - AMPLE: AmpleReVanced .arp bundles.
 */
enum class PatchBundleType {
    REVANCED,
    MORPHE,
    AMPLE,
}
