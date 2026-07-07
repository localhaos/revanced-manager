package app.revanced.manager.ui.screen.settings

import app.revanced.manager.network.downloader.DownloaderPackage
import app.revanced.manager.patcher.patch.PatchBundle

internal val Any?.version: String?
    get() = when (this) {
        is DownloaderPackage -> version
        is PatchBundle -> manifestAttributes?.version
        else -> null
    }
