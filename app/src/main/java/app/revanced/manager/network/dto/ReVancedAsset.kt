package app.revanced.manager.network.dto

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReVancedAsset(
    @SerialName("download_url")
    val downloadUrl: String,
    @SerialName("created_at")
    val createdAt: LocalDateTime,
    @SerialName("signature_download_url")
    val signatureDownloadUrl: String? = null,
    val description: String,
    val version: String,
)

@Serializable
data class ReVancedAssetHistory(
    val version: String,
    @SerialName("created_at")
    val createdAt: LocalDateTime,
    val description: String,
)

@Serializable
data class GitHubRelease(
    @SerialName("tag_name")
    val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("published_at")
    val publishedAt: LocalDateTime? = null,
    @SerialName("created_at")
    val createdAt: LocalDateTime,
    val assets: List<GitHubReleaseAsset> = emptyList(),
) {
    fun toReVancedAsset(assetSelector: (GitHubReleaseAsset) -> Boolean = GitHubReleaseAsset::isPatchBundle): ReVancedAsset {
        val asset = assets.firstOrNull(assetSelector)
            ?: assets.firstOrNull { !it.name.endsWith(".asc", ignoreCase = true) }
            ?: throw NoSuchElementException("GitHub release '$tagName' does not contain a downloadable patch bundle asset")

        return ReVancedAsset(
            downloadUrl = asset.browserDownloadUrl,
            createdAt = publishedAt ?: createdAt,
            signatureDownloadUrl = assets.firstOrNull { it.name == "${asset.name}.asc" }?.browserDownloadUrl,
            description = body?.takeIf { it.isNotBlank() } ?: name ?: tagName,
            version = tagName,
        )
    }
}

@Serializable
data class GitHubReleaseAsset(
    val name: String,
    @SerialName("browser_download_url")
    val browserDownloadUrl: String,
) {
    val isPatchBundle: Boolean
        get() = name.endsWith(".jar", ignoreCase = true) &&
                !name.endsWith(".asc", ignoreCase = true) &&
                (name.contains("patch", ignoreCase = true) || name.contains("revanced", ignoreCase = true) || name.contains("rvx", ignoreCase = true))
}
