package app.revanced.manager.ui.screen.settings

import app.revanced.manager.domain.sources.Source

internal val Source<*>.displayName: String
    get() = name.ifBlank { "Source #$uid" }
