package app.revanced.manager.domain.sources

import app.revanced.manager.network.dto.GitHubRelease
import app.revanced.manager.network.dto.GitHubReleaseAsset
import app.revanced.manager.network.dto.ReVancedAsset
import kotlinx.datetime.LocalDateTime

/**
 * Resolves repository, release and direct patch-bundle URLs into downloadable patch-bundle assets.
 *
 * Supported inputs:
 * - https://github.com/owner/repo
 * - https://github.com/owner/repo/releases
 * - https://github.com/owner/repo/releases/latest
 * - https://github.com/owner/repo/releases/tag/<tag>
 * - https://api.github.com/repos/owner/repo/releases
 * - https://api.github.com/repos/owner/repo/releases/latest
 * - https://api.github.com/repos/owner/repo/releases/tags/<tag>
 * - direct URLs ending with .rvp, .mpp or .jar
 * - direct JSON metadata URLs ending with .json
 * - GitHub blob JSON metadata URLs, converted to raw.githubusercontent.com
 * - Jman-Github/ReVanced-Patch-Bundles generated bundle JSON URLs.
 *
 * Notes:
 * - https://morphe-patches.software/ is a human-facing community index, not a direct bundle endpoint.
 *   Use an actual .mpp, .rvp, .jar or generated bundle JSON URL when bundles are available.
 */
object GitHubBundleAutoFinder {
    enum class BundleKind(val extension: String) {
        ReVanced(".rvp"),
        Morphe(".mpp"),
        LegacyJar(".jar"),
    }

    data class Candidate(
        val owner: String,
        val repo: String,
        val releaseRef: String,
        val apiEndpoint: String,
        val normalizedInput: String,
    )

    data class ResolvedBundle(
        val asset: ReVancedAsset,
        val kind: BundleKind,
        val assetName: String,
        val releaseApiEndpoint: String,
    )

    fun candidateFrom(input: String): Candidate? {
        val normalized = input.trim().trimEnd('/')
        if (normalized.isBlank()) return null
        if (isMorphePatchIndex(normalized)) return null
        if (metadataEndpointFrom(normalized) != null) return null

        githubApiReleaseListRegex.matchEntire(normalized)?.let { match ->
            val owner = match.groupValues[1]
            val repo = match.groupValues[2]
            val releaseRef = "latest"
            return Candidate(owner, repo, releaseRef, githubReleaseApi(owner, repo, releaseRef), normalized)
        }

        githubApiReleaseRegex.matchEntire(normalized)?.let { match ->
            val owner = match.groupValues[1]
            val repo = match.groupValues[2]
            val releaseRef = match.groupValues[3]
            return Candidate(owner, repo, releaseRef, normalized, normalized)
        }

        githubWebReleaseRegex.matchEntire(normalized)?.let { match ->
            val owner = match.groupValues[1]
            val repo = match.groupValues[2]
            val releaseRef = match.groupValues[3].ifBlank { "latest" }
                .replace("tag/", "tags/")
            return Candidate(owner, repo, releaseRef, githubReleaseApi(owner, repo, releaseRef), normalized)
        }

        githubRepoRegex.matchEntire(normalized)?.let { match ->
            val owner = match.groupValues[1]
            val repo = match.groupValues[2]
            val releaseRef = "latest"
            return Candidate(owner, repo, releaseRef, githubReleaseApi(owner, repo, releaseRef), normalized)
        }

        return null
    }

    fun displayNameFrom(input: String): String? {
        val normalized = input.trim().trimEnd('/')
        jmanMetadataDisplayNameFrom(normalized)?.let { return it }
        metadataEndpointFrom(normalized)?.let { endpoint ->
            jmanMetadataDisplayNameFrom(endpoint)?.let { return it }
            return endpoint.substringAfterLast('/').substringBeforeLast('.').toDisplayTitle()
        }

        val candidate = candidateFrom(normalized)
        if (candidate != null) return candidate.repo
            .removeSuffix("-patches")
            .replace('-', ' ')
            .replace('_', ' ')
            .replaceFirstChar { it.uppercaseChar() }
            .let { "${candidate.owner}/$it" }

        return directAssetFromOrNull(normalized)?.version
    }

    fun metadataEndpointFrom(input: String): String? {
        val normalized = input.trim().trimEnd('/')
        if (!normalized.endsWith(".json", ignoreCase = true)) return null

        githubBlobJsonRegex.matchEntire(normalized)?.let { match ->
            val owner = match.groupValues[1]
            val repo = match.groupValues[2]
            val branch = match.groupValues[3]
            val path = match.groupValues[4]
            return "https://raw.githubusercontent.com/$owner/$repo/$branch/$path"
        }

        return normalized.takeIf { directJsonRegex.matches(it) }
    }

    fun directAssetFrom(input: String): ReVancedAsset? {
        if (isMorphePatchIndex(input)) {
            throw IllegalArgumentException(
                "morphe-patches.software is a patch index page, not a downloadable bundle. Open it and add a direct .mpp, .rvp, .jar or generated bundle JSON URL."
            )
        }

        if (isJmanCatalog(input)) {
            throw IllegalArgumentException(
                "Jman patch list catalog is an index, not one bundle. Add one generated bundle JSON URL from the catalog, for example revanced-latest-patches-bundle.json."
            )
        }

        return directAssetFromOrNull(input)
    }

    private fun directAssetFromOrNull(input: String): ReVancedAsset? {
        val normalized = input.trim()
        val assetName = normalized.substringAfterLast('/').substringBefore('?').substringBefore('#')
        val kind = assetName.bundleKind() ?: return null
        if (!directBundleRegex.matches(normalized)) return null

        val tag = githubReleaseAssetRegex.matchEntire(normalized)?.groupValues?.getOrNull(3)
            ?: assetName.substringBeforeLast(kind.extension, assetName)
                .ifBlank { assetName }

        return ReVancedAsset(
            downloadUrl = normalized,
            createdAt = directBundleCreatedAt,
            signatureDownloadUrl = null,
            description = "Direct ${kind.extension} patch bundle",
            version = tag,
        )
    }

    fun resolveRelease(release: GitHubRelease, releaseApiEndpoint: String): ResolvedBundle {
        val rankedAsset = release.assets
            .mapNotNull { asset -> asset.bundleKind()?.let { kind -> kind to asset } }
            .sortedBy { (kind, _) -> kind.rank }
            .firstOrNull()
            ?: throw NoSuchElementException("GitHub release '${release.tagName}' has no .rvp, .mpp or .jar patch bundle asset")

        val (kind, asset) = rankedAsset
        return ResolvedBundle(
            asset = release.toReVancedAsset { candidate -> candidate.name == asset.name },
            kind = kind,
            assetName = asset.name,
            releaseApiEndpoint = releaseApiEndpoint,
        )
    }

    fun supports(input: String) = candidateFrom(input) != null || directAssetFrom(input) != null || metadataEndpointFrom(input) != null

    fun isMorphePatchIndex(input: String): Boolean = morphePatchIndexRegex.matches(input.trim().trimEnd('/'))

    fun isJmanCatalog(input: String): Boolean = jmanCatalogRegex.matches(input.trim().trimEnd('/'))

    private fun jmanMetadataDisplayNameFrom(input: String): String? {
        val endpoint = metadataEndpointFrom(input) ?: input.trim().trimEnd('/')
        val match = jmanRawBundleRegex.matchEntire(endpoint) ?: return null
        val fileName = match.groupValues[2]
        return "Jman/${fileName.removeSuffix("-patches-bundle").toDisplayTitle()}"
    }

    private fun String.toDisplayTitle(): String = split('-', '_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { it.uppercaseChar() } }

    private fun githubReleaseApi(owner: String, repo: String, releaseRef: String) =
        "https://api.github.com/repos/$owner/$repo/releases/$releaseRef"

    private fun GitHubReleaseAsset.bundleKind(): BundleKind? = name.bundleKind()

    private fun String.bundleKind(): BundleKind? {
        val value = substringBefore('?').substringBefore('#')
        return BundleKind.entries.firstOrNull { value.endsWith(it.extension, ignoreCase = true) }
    }

    private val BundleKind.rank get() = when (this) {
        BundleKind.ReVanced -> 0
        BundleKind.Morphe -> 1
        BundleKind.LegacyJar -> 2
    }

    private val directBundleCreatedAt = LocalDateTime(1970, 1, 1, 0, 0)

    private val githubRepoRegex = Regex(
        pattern = "^https://github\\.com/([^/]+)/([^/#?]+)(?:[?#].*)?$",
        option = RegexOption.IGNORE_CASE,
    )

    private val githubWebReleaseRegex = Regex(
        pattern = "^https://github\\.com/([^/]+)/([^/]+)/(?:releases(?:/(latest|tag/[^?#]+))?)(?:[?#].*)?$",
        option = RegexOption.IGNORE_CASE,
    )

    private val githubApiReleaseListRegex = Regex(
        pattern = "^https://api\\.github\\.com/repos/([^/]+)/([^/]+)/releases(?:[?#].*)?$",
        option = RegexOption.IGNORE_CASE,
    )

    private val githubApiReleaseRegex = Regex(
        pattern = "^https://api\\.github\\.com/repos/([^/]+)/([^/]+)/releases/(latest|tags/[^?#]+)(?:[?#].*)?$",
        option = RegexOption.IGNORE_CASE,
    )

    private val githubReleaseAssetRegex = Regex(
        pattern = "^https://github\\.com/([^/]+)/([^/]+)/releases/download/([^/]+)/([^?#]+)(?:[?#].*)?$",
        option = RegexOption.IGNORE_CASE,
    )

    private val githubBlobJsonRegex = Regex(
        pattern = "^https://github\\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.+\\.json)(?:[?#].*)?$",
        option = RegexOption.IGNORE_CASE,
    )

    private val directBundleRegex = Regex(
        pattern = "^https?://.+\\.(rvp|mpp|jar)(?:[?#].*)?$",
        option = RegexOption.IGNORE_CASE,
    )

    private val directJsonRegex = Regex(
        pattern = "^https?://.+\\.json(?:[?#].*)?$",
        option = RegexOption.IGNORE_CASE,
    )

    private val jmanRawBundleRegex = Regex(
        pattern = "^https://raw\\.githubusercontent\\.com/Jman-Github/ReVanced-Patch-Bundles/bundles/patch-bundles/([^/]+)/([^/]+)\\.json$",
        option = RegexOption.IGNORE_CASE,
    )

    private val jmanCatalogRegex = Regex(
        pattern = "^https://github\\.com/Jman-Github/ReVanced-Patch-Bundles/(?:blob/)?bundles/patch-bundles/PATCH-LIST-CATALOG\\.md(?:[?#].*)?$",
        option = RegexOption.IGNORE_CASE,
    )

    private val morphePatchIndexRegex = Regex(
        pattern = "^https?://(?:www\\.)?morphe-patches\\.software(?:/.*)?$",
        option = RegexOption.IGNORE_CASE,
    )
}
