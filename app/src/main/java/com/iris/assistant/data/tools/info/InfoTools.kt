package com.iris.assistant.data.tools.info

import com.iris.assistant.BuildConfig
import com.iris.assistant.domain.tools.JarvisTool
import com.iris.assistant.domain.tools.ToolResult
import com.iris.assistant.util.Constants
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "InfoTools"

// UNTESTED — verify API responses before use

// ---------------------------------------------------------------------------
// get_weather
// ---------------------------------------------------------------------------

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

            val request = Request.Builder().url(url).get().build()
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string()
                ?: return@runCatching ToolResult.Error("Hava durumu API'sinden boş yanıt (HTTP ${response.code})")

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
                append("$city, $country'de şu an $tempC°C, $condition. ")
                append("En yüksek: $maxTemp°C, en düşük: $minTemp°C. ")
                append("Hissedilen: $feelsLike°C, Nem: %$humidity, Rüzgar: $windKph km/s. ")
                append("Gün doğumu: $sunrise, Gün batımı: $sunset.")
            }

            ToolResult.Success(
                displayText = displayText,
                data = mapOf(
                    "city" to city, "country" to country,
                    "temp_c" to tempC.toString(), "condition" to condition,
                    "humidity" to humidity.toString(), "wind_kph" to windKph.toString(),
                    "feelslike_c" to feelsLike.toString(),
                    "max_temp_c" to maxTemp.toString(), "min_temp_c" to minTemp.toString()
                )
            )
        }.getOrElse { e ->
            ToolResult.Error("Hava durumu alınamadı: ${e.message}", e)
        }
    }
}

// ---------------------------------------------------------------------------
// web_search
// ---------------------------------------------------------------------------

@Singleton
class WebSearchTool @Inject constructor(
    private val okHttpClient: OkHttpClient
) : JarvisTool {

    override val name        = "web_search"
    override val description = "Searches the web for current information. Use when the user asks about recent events, facts, or anything that may have changed after Gemini's training cutoff."
    override val parameters  = JSONObject("""
        {
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "Search query — what to search for"
                },
                "count": {
                    "type": "integer",
                    "description": "Number of results to return (1-10, default 3)",
                    "default": 3
                }
            },
            "required": ["query"]
        }
    """.trimIndent())
    override val requiredPermission: String? = null

    override suspend fun execute(args: JSONObject): ToolResult {
        val query = args.optString("query").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("Arama sorgusu belirtilmedi.")
        val count = args.optInt("count", 3).coerceIn(1, 10)

        val apiKey = BuildConfig.TAVILY_API_KEY
        if (apiKey.isBlank()) return ToolResult.Error("TAVILY_API_KEY eksik.")

        return runCatching {
            val requestBody = JSONObject()
                .put("api_key", apiKey)
                .put("query", query)
                .put("search_depth", "basic")
                .put("include_answer", false)
                .put("max_results", count)
                .toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(Constants.TAVILY_SEARCH_ENDPOINT)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string()
                ?: return@runCatching ToolResult.Error("Arama API'sinden boş yanıt (HTTP ${response.code})")

            if (!response.isSuccessful) {
                val errMsg = try {
                    JSONObject(body).optString("message", body)
                } catch (_: Exception) { body }
                return@runCatching ToolResult.Error("Arama yapılamadı: $errMsg")
            }

            val json = JSONObject(body)
            val results = json.optJSONArray("results")

            if (results == null || results.length() == 0) {
                return@runCatching ToolResult.Success("Sonuç bulunamadı.", data = emptyMap())
            }

            val items = mutableListOf<String>()
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val title = item.optString("title", "")
                val link = item.optString("url", "")
                val content = item.optString("content", "")
                items.add("${i + 1}. $title\n$content\n$link")
            }

            ToolResult.Success(
                displayText = items.joinToString("\n\n"),
                data = mapOf("results_count" to items.size.toString())
            )
        }.getOrElse { e ->
            ToolResult.Error("Arama yapılamadı: ${e.message}", e)
        }
    }
}

// ---------------------------------------------------------------------------
// get_news
// ---------------------------------------------------------------------------

@Singleton
class GetNewsTool @Inject constructor(
    private val okHttpClient: OkHttpClient
) : JarvisTool {

    override val name        = "get_news"
    override val description = "Returns latest news headlines. Optionally filter by category (business, entertainment, health, science, sports, technology) or search by keyword."
    override val parameters  = JSONObject("""
        {
            "type": "object",
            "properties": {
                "category": {
                    "type": "string",
                    "description": "News category: business, entertainment, health, science, sports, technology (optional)"
                },
                "query": {
                    "type": "string",
                    "description": "Keyword to search for in news (optional)"
                },
                "count": {
                    "type": "integer",
                    "description": "Number of news items to return (1-10, default 5)",
                    "default": 5
                }
            }
        }
    """.trimIndent())
    override val requiredPermission: String? = null

    override suspend fun execute(args: JSONObject): ToolResult {
        val apiKey = BuildConfig.NEWS_API_KEY
        if (apiKey.isBlank()) return ToolResult.Error("NEWS_API_KEY eksik — local.properties'e ekleyin.")

        val query = args.optString("query").takeIf { it.isNotBlank() }
        val category = args.optString("category").takeIf { it.isNotBlank() }
        val count = args.optInt("count", 5).coerceIn(1, 10)

        return runCatching {
            val urlBuilder = StringBuilder()

            if (query != null) {
                urlBuilder.append("${Constants.NEWS_API_ENDPOINT}/everything" +
                    "?q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                    "&language=tr&pageSize=$count&sortBy=publishedAt&apiKey=$apiKey")
            } else {
                urlBuilder.append("${Constants.NEWS_API_ENDPOINT}/top-headlines" +
                    "?country=${Constants.NEWS_API_COUNTRY}&pageSize=$count&apiKey=$apiKey")
                if (category != null) {
                    urlBuilder.append("&category=${java.net.URLEncoder.encode(category, "UTF-8")}")
                }
            }

            val request = Request.Builder().url(urlBuilder.toString()).get().build()
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string()
                ?: return@runCatching ToolResult.Error("Haber API'sinden boş yanıt (HTTP ${response.code})")

            if (!response.isSuccessful) {
                val errMsg = try {
                    JSONObject(body).optString("message", body)
                } catch (_: Exception) { body }
                return@runCatching ToolResult.Error("Haberler alınamadı: $errMsg")
            }

            val json = JSONObject(body)
            val articles = json.optJSONArray("articles")

            if (articles == null || articles.length() == 0) {
                return@runCatching ToolResult.Success("Haber bulunamadı.", data = emptyMap())
            }

            val newsList = mutableListOf<String>()
            for (i in 0 until articles.length()) {
                val article = articles.getJSONObject(i)
                val title = article.optString("title", "Başlıksız")
                val source = article.optJSONObject("source")?.optString("name") ?: ""
                val desc = article.optString("description", "").takeIf { it.isNotBlank() && it != title }
                val url = article.optString("url", "")
                newsList.add("${i + 1}. $title${if (source.isNotBlank()) " ($source)" else ""}" +
                    "${if (desc != null) "\n$desc" else ""}")
            }

            ToolResult.Success(
                displayText = newsList.joinToString("\n\n"),
                data = mapOf("articles_count" to newsList.size.toString())
            )
        }.getOrElse { e ->
            ToolResult.Error("Haberler alınamadı: ${e.message}", e)
        }
    }
}
