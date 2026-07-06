package app.revanced.manager.domain.sources

import app.revanced.manager.network.dto.GitHubRelease
import app.revanced.manager.network.dto.GitHubReleaseAsset
import app.revanced.manager.network.dto.ReVancedAsset
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Resolves GitHub repository and release URLs into patch-bundle endpoints.
 *
 * Supported inputs:
 * - https://github.com/owner/repo
 * - https://github.com/owner/repo/releases
 * - https://github.com/owner/repo/releases/latest
 * - https://github.com/owner/repo/releases/tag/<tag>
 * - https://api.github.com/repos/owner/repo/releases/latest
 * - https://api.github.com/repos/owner/repo/releases/tags/<tag>
 * - direct URLs ending with .rvp, .mpp or .jar
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

    fun directAssetFrom(input: String): ReVancedAsset? {
        val normalized = input.trim()
        val assetName = normalized.substringAfterLast('/').substringBefore('?').substringBefore('#')
        val kind = assetName.bundleKind() ?: return null
        if (!directBundleRegex.matches(normalized)) return null

        val tag = githubReleaseAssetRegex.matchEntire(normalized)?.groupValues?.getOrNull(3)
            ?: assetName.substringBeforeLast(kind.extension, assetName)
                .ifBlank { assetName }

        return ReVancedAsset(
            downloadUrl = normalized,
            createdAt = Clock.System.now().toLocalDateTime(TimeZone.UTC),
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

    fun supports(input: String) = candidateFrom(input) != null || directAssetFrom(input) != null

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

    private val githubRepoRegex = Regex(
        pattern = "^https://github\\.com/([^/]+)/([^/#?]+)(?:[?#].*)?$",
        option = RegexOption.IGNORE_CASE,
    )

    private val githubWebReleaseRegex = Regex(
        pattern = "^https://github\\.com/([^/]+)/([^/]+)/(?:releases(?:/(latest|tag/[^?#]+))?)(?:[?#].*)?$",
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

    private val directBundleRegex = Regex(
        pattern = "^https?://.+\\.(rvp|mpp|jar)(?:[?#].*)?$",
        option = RegexOption.IGNORE_CASE,
    )
}
