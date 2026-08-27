package com.ykatchou.ylauncher.data.weather

import android.content.Context
import android.location.LocationManager
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.ykatchou.ylauncher.util.YLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches the current conditions from Open-Meteo.
 *
 * Open-Meteo because it needs no API key and no account — a key would have to live in the repo or
 * in the user's hands, and this is a home-screen readout, not a product. One HTTP call, parsed by
 * hand: pulling in a JSON library and a client would cost more than the four fields being read.
 *
 * This exists to replace the stock weather app, which was measured holding ~740 MB — 384 MB of it
 * in graphics buffers — to display a temperature.
 */
@Singleton
class WeatherRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private var cached: Weather? = null
    private var cachedAt = 0L

    /** Last successful reading, if it is still fresh enough to show. */
    fun cachedOrNull(): Weather? =
        cached?.takeIf { System.currentTimeMillis() - cachedAt < CACHE_MS }

    suspend fun fetch(): Weather? = withContext(Dispatchers.IO) {
        cachedOrNull()?.let { return@withContext it }

        val (lat, lon) = lastKnownLocation() ?: return@withContext null
        try {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,is_day",
            )
            val body = (url.openConnection() as HttpURLConnection).run {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                try {
                    if (responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                    inputStream.bufferedReader().readText()
                } finally {
                    disconnect()
                }
            }
            val current = JSONObject(body).getJSONObject("current")
            Weather(
                temperatureCelsius = current.getDouble("temperature_2m").toInt(),
                code = current.getInt("weather_code"),
                isDay = current.getInt("is_day") == 1,
            ).also {
                cached = it
                cachedAt = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            YLogger.e(TAG, "weather fetch failed", e)
            null
        }
    }

    /**
     * Uses the last fix the system already has rather than requesting a new one. A launcher has no
     * business waking the GPS: the reading only has to be right to within a city.
     */
    private fun lastKnownLocation(): Pair<Double, Double>? {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null

        return try {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            manager.getProviders(true)
                .asSequence()
                .mapNotNull { @Suppress("MissingPermission") manager.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
                ?.let { it.latitude to it.longitude }
        } catch (e: Exception) {
            YLogger.e(TAG, "location lookup failed", e)
            null
        }
    }

    private companion object {
        const val TAG = "WeatherRepository"
        const val TIMEOUT_MS = 8000
        /** Conditions do not change fast enough to justify hitting the network more often. */
        const val CACHE_MS = 15L * 60 * 1000
    }
}
