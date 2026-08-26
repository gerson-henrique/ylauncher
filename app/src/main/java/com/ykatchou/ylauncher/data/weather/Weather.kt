package com.ykatchou.ylauncher.data.weather

/**
 * A weather reading, or the absence of one.
 *
 * Nullable throughout for the same reason the system stats are: the widget shows nothing rather
 * than a placeholder number when it has not managed to fetch yet. A temperature invented to fill
 * the space would be indistinguishable from a real reading.
 */
data class Weather(
    val temperatureCelsius: Int,
    val code: Int,
    val isDay: Boolean,
) {
    /**
     * WMO weather codes, collapsed to the handful of states worth telling apart at a glance on a
     * home screen. The full table has 28 entries splitting hairs like "slight" versus "moderate"
     * drizzle, which no one reads a launcher to learn.
     */
    val icon: String
        get() = when (code) {
            0 -> if (isDay) "☀️" else "🌙"
            1, 2 -> if (isDay) "🌤️" else "☁️"
            3 -> "☁️"
            in 45..48 -> "🌫️"
            in 51..67 -> "🌧️"
            in 71..77 -> "🌨️"
            in 80..82 -> "🌧️"
            in 85..86 -> "🌨️"
            in 95..99 -> "⛈️"
            else -> "🌡️"
        }
}
