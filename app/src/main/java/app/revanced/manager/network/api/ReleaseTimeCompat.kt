package app.revanced.manager.network.api

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

internal fun kotlin.time.Instant.toLocalDateTime(timeZone: TimeZone): LocalDateTime {
    val seconds = epochSeconds
    val days = seconds.floorDiv(86_400)
    val year = 1970 + days.floorDiv(365).toInt().coerceAtLeast(0)
    return LocalDateTime(year, 1, 1, 0, 0)
}
