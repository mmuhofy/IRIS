package com.iris.assistant.data.tools.info

import com.iris.assistant.BuildConfig
import com.iris.assistant.domain.tools.JarvisTool
import com.iris.assistant.domain.tools.ToolResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "InfoTools"

// UNTESTED — verify API responses before use

// ---------------------------------------------------------------------------
// get_weather
// ---------------------------------------------------------------------------

/**
 * Returns current weather + today's forecast for a given location.
 *
 * Parameters:
 *   location (string, required): City name or "lat,lon" coordinates
 *
 * Uses WeatherAPI.com (free tier: 1M calls/month).
 * API key from BuildConfig.WEATHER_API_KEY (local.properties / CI env).
 *
 * Example Gemini interaction:
 *   User: "İstanbul'da hava nasıl?"
 *   Gemini: function_call { name: "get_weather", args: { "location": "İstanbul" } }
 *   Tool:   ToolResult.Success("İstanbul'da şu an 22°C, Parçalı Bulutlu. En yüksek: 26°C, en düşük: 17°C.")
 */
@Singleton
class GetWeatherTool @Inject constructor(
    private val okHttpClient: OkHttpClient
) : JarvisTool {

    override val name        = "get_weather"
    override val description = "Returns current weather and today's forecast for a given city or coordinates."
    override val parameters  = JSONObject("""
        {
            "type": "object",
            "properties": {
                "location": {
                    "type": "string",
                    "description": "City name (e.g. İstanbul, Ankara) or latitude,longitude (e.g. 41.01,28.97)"
                }
            },
            "required": ["location"]
        }
    """.trimIndent())
    override val requiredPermission: String? = null

    override suspend fun execute(args: JSONObject): ToolResult {
        val location = args.optString("location").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("Konum belirtilmedi. Şehir adı veya enlem,boylam girin.")

        val apiKey = BuildConfig.WEATHER_API_KEY
        if (apiKey.isBlank()) return ToolResult.Error("WEATHER_API_KEY eksik — local.properties'e ekleyin.")

        return runCatching {
            val url = "https://api.weatherapi.com/v1/forecast.json" +
                "?key=$apiKey&q=${java.net.URLEncoder.encode(location, "UTF-8")}&days=1&lang=tr"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string()
                ?: return@runCatching ToolResult.Error("Hava durumu API'sinden boş yanıt alındı (HTTP ${response.code})")

            if (!response.isSuccessful) {
                val errMsg = try {
                    JSONObject(body).optJSONObject("error")?.optString("message") ?: body
                } catch (_: Exception) { body }
                return@runCatching ToolResult.Error("Hava durumu alınamadı: $errMsg")
            }

            val json = JSONObject(body)

            val city = json.getJSONObject("location").getString("name")
            val country = json.getJSONObject("location").getString("country")

            val current = json.getJSONObject("current")
            val tempC = current.getDouble("temp_c")
            val condition = current.getJSONObject("condition").getString("text")
            val humidity = current.getInt("humidity")
            val windKph = current.getDouble("wind_kph")
            val feelsLike = current.getDouble("feelslike_c")

            val forecastDay = json.getJSONObject("forecast")
                .getJSONArray("forecastday")
                .getJSONObject(0)
                .getJSONObject("day")
            val maxTemp = forecastDay.getDouble("maxtemp_c")
            val minTemp = forecastDay.getDouble("mintemp_c")
            val sunrise = forecastDay.getString("sunrise")
            val sunset = forecastDay.getString("sunset")

            val displayText = buildString {
                append("$city, $country'de şu an $tempC°C, $condition.")
                append(" En yüksek: $maxTemp°C, en düşük: $minTemp°C.")
                append(" Hissedilen: $feelsLike°C, Nem: %$humidity, Rüzgar: $windKph km/s.")
                append(" Gün doğumu: $sunrise, Gün batımı: $sunset.")
            }

            ToolResult.Success(
                displayText = displayText,
                data = mapOf(
                    "city" to city,
                    "country" to country,
                    "temp_c" to tempC.toString(),
                    "condition" to condition,
                    "humidity" to humidity.toString(),
                    "wind_kph" to windKph.toString(),
                    "feelslike_c" to feelsLike.toString(),
                    "max_temp_c" to maxTemp.toString(),
                    "min_temp_c" to minTemp.toString()
                )
            )
        }.getOrElse { e ->
            ToolResult.Error("Hava durumu alınamadı: ${e.message}", e)
        }
    }
}
