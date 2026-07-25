package com.PobreFlix.extractor

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import java.net.URL

object PobreFlixExtractor {

    private val HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Mobile Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "Accept-Language" to "pt-BR",
        "Accept-Encoding" to "identity",
        "Referer" to "https://lospobreflix.site/",
        "Sec-Fetch-Dest" to "iframe",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "cross-site",
        "Upgrade-Insecure-Requests" to "1",
        "Connection" to "keep-alive"
    )

    private val API_HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Mobile Safari/537.36",
        "Accept" to "application/json, text/plain, */*",
        "Accept-Language" to "pt-BR",
        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
        "X-Requested-With" to "XMLHttpRequest",
        "Connection" to "keep-alive"
    )

    private var sessionCookies: String = ""

    private fun getCookieHeader(): Map<String, String> {
        return if (sessionCookies.isNotEmpty()) mapOf("Cookie" to sessionCookies) else emptyMap()
    }

    private fun extractBaseDomain(url: String): String {
        return try {
            val parsedUrl = URL(url)
            "${parsedUrl.protocol}://${parsedUrl.host}"
        } catch (e: Exception) {
            ""
        }
    }

    private fun extractContentIdFromTruncatedJson(html: String, targetSeason: Int, targetEpisode: Int): String? {
        val pattern1 = Regex("\"season\":$targetSeason,\"epi_num\":$targetEpisode[^}]*?\"ID\":(\\d+)")
        val match1 = pattern1.find(html)
        if (match1 != null) {
            return match1.groupValues[1]
        }
        
        val pattern2 = Regex("\"ID\":(\\d+)[^}]*?\"epi_num\":$targetEpisode[^}]*?\"season\":$targetSeason")
        val match2 = pattern2.find(html)
        if (match2 != null) {
            return match2.groupValues[1]
        }
        
        val seasonPattern = Regex("\"$targetSeason\":\\s*\\[([\\s\\S]*?)(?=\\s*,\\s*\"\\d+\"\\s*:|\\s*\\})")
        val seasonMatch = seasonPattern.find(html)
        if (seasonMatch != null) {
            val seasonContent = seasonMatch.groupValues[1]
            val episodePattern = Regex("\"epi_num\":$targetEpisode[^}]*?\"ID\":(\\d+)")
            val episodeMatch = episodePattern.find(seasonContent)
            if (episodeMatch != null) {
                return episodeMatch.groupValues[1]
            }
        }
        
        return null
    }

    suspend fun getStreams(
        tmdbId: Int,
        mediaType: String,
        season: Int,
        episode: Int
    ): List<ExtractorLink> {
        val results = mutableListOf<ExtractorLink>()
        val targetSeason = if (mediaType == "filme") 1 else season
        val targetEpisode = if (mediaType == "filme") 1 else episode

        try {
            val initialDomain = "https://superflixapi.online"
            
            val pageUrl = if (mediaType == "filme") {
                "$initialDomain/filme/$tmdbId"
            } else {
                "$initialDomain/serie/$tmdbId/$targetSeason/$targetEpisode"
            }
            
            val response = app.get(pageUrl, headers = HEADERS + getCookieHeader())
            if (!response.isSuccessful) {
                return emptyList()
            }
            
            val finalUrl = response.url
            val baseUrl = extractBaseDomain(finalUrl)
            
            val updatedApiHeaders = API_HEADERS.toMutableMap()
            updatedApiHeaders["Origin"] = baseUrl
            
            val setCookie = response.headers["set-cookie"]
            if (setCookie != null && setCookie.isNotEmpty()) {
                sessionCookies = setCookie
            }
            
            val html = response.text
            
            val csrfMatch = Regex("CSRF_TOKEN\\s*=\\s*[\"']([^\"']+)[\"']").find(html)
            if (csrfMatch == null) {
                return emptyList()
            }
            val csrfToken = csrfMatch.groupValues[1]
            
            val pageMatch = Regex("PAGE_TOKEN\\s*=\\s*[\"']([^\"']+)[\"']").find(html)
            if (pageMatch == null) {
                return emptyList()
            }
            val pageToken = pageMatch.groupValues[1]
            
            var contentId: String? = null
            
            if (mediaType == "filme") {
                val initialContentMatch = Regex("INITIAL_CONTENT_ID\\s*=\\s*(\\d+)").find(html)
                if (initialContentMatch != null) {
                    contentId = initialContentMatch.groupValues[1]
                }
            } else {
                contentId = extractContentIdFromTruncatedJson(html, targetSeason, targetEpisode)
            }
            
            if (contentId == null) {
                return emptyList()
            }
            
            val optionsParams = mutableMapOf<String, String>()
            optionsParams["contentid"] = contentId
            optionsParams["type"] = if (mediaType == "filme") "filme" else "serie"
            optionsParams["_token"] = csrfToken
            optionsParams["page_token"] = pageToken
            optionsParams["pageToken"] = pageToken
            
            val optionsResponse = app.post(
                url = "$baseUrl/player/options",
                headers = updatedApiHeaders + getCookieHeader() + mapOf("X-Page-Token" to pageToken),
                data = optionsParams
            )
            
            if (!optionsResponse.isSuccessful) {
                return emptyList()
            }
            
            val optionsData = JSONObject(optionsResponse.text)
            val dataObj = optionsData.optJSONObject("data")
            if (dataObj == null) {
                return emptyList()
            }
            
            val optionsArray = dataObj.optJSONArray("options")
            if (optionsArray == null) {
                return emptyList()
            }
            
            for (i in 0 until optionsArray.length()) {
                val option = optionsArray.getJSONObject(i)
                val videoId = option.optString("ID")
                val type = option.optInt("type")
                val serverType = when (type) {
                    1 -> "Dublado"
                    2 -> "Legendado"
                    else -> "Tipo $type"
                }
                
                if (videoId.isEmpty()) {
                    continue
                }
                
                val sourceParams = mutableMapOf<String, String>()
                sourceParams["video_id"] = videoId
                sourceParams["page_token"] = pageToken
                sourceParams["_token"] = csrfToken
                
                val sourceResponse = app.post(
                    url = "$baseUrl/player/source",
                    headers = updatedApiHeaders + getCookieHeader(),
                    data = sourceParams
                )
                
                if (!sourceResponse.isSuccessful) {
                    continue
                }
                
                val sourceData = JSONObject(sourceResponse.text)
                val redirectUrl = sourceData.optJSONObject("data")?.optString("video_url")
                if (redirectUrl.isNullOrEmpty()) {
                    continue
                }
                
                val redirectResponse = app.get(redirectUrl, headers = HEADERS + getCookieHeader())
                val finalVideoUrl = redirectResponse.url
                
                if (finalVideoUrl.contains("blogger.com/video.g") || finalVideoUrl.contains("blogger.com")) {
                    continue
                }
                
                var videoUrl = finalVideoUrl
                var quality = 720
                
                if (videoUrl.contains("2160") || videoUrl.contains("4k")) quality = 2160
                else if (videoUrl.contains("1440")) quality = 1440
                else if (videoUrl.contains("1080")) quality = 1080
                else if (videoUrl.contains("720")) quality = 720
                else if (videoUrl.contains("480")) quality = 480
                
                val title = if (mediaType == "filme") {
                    "Filme $tmdbId"
                } else {
                    "S${targetSeason.toString().padStart(2, '0')}E${targetEpisode.toString().padStart(2, '0')}"
                }
                
                val extractorLink = newExtractorLink(
                    source = "SuperFlixAPI",
                    name = "SuperFlixAPI $serverType",
                    url = videoUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = baseUrl
                    this.quality = quality
                    this.headers = mapOf(
                        "Referer" to baseUrl,
                        "User-Agent" to HEADERS["User-Agent"]!!
                    )
                }
                
                results.add(extractorLink)
            }
            
            return results
            
        } catch (e: Exception) {
            return emptyList()
        }
    }
}