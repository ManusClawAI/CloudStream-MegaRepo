package com.indotv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.CLEARKEY_UUID
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newDrmExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

class IndoTV : MainAPI() {
    override var lang = "id"
    override var mainUrl = "https://raw.githubusercontent.com/TeKuma25/Koleksi-IPTV/main/id.m3u"
    override var name = "Indo IPTV"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Live,
    )

    private suspend fun fetchAllPlaylists(): Playlist {
        val allItems = mutableListOf<PlaylistItem>()
        val content = app.get(mainUrl).text
        val playlist = IptvPlaylistParser().parseM3U(content)
        allItems.addAll(playlist.items)
        return Playlist(allItems)
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = fetchAllPlaylists()
        return newHomePageResponse(
            data.items.groupBy { it.attributes["group-title"] }.map { group ->
                val title = group.key ?: ""
                val show = group.value.map { channel ->
                    val streamurl = channel.url.orEmpty()
                    val channelname = channel.title.orEmpty()
                    val posterurl = channel.attributes["tvg-logo"].orEmpty()
                    val nation = channel.attributes["group-title"].orEmpty()//.toSentenceCase()
                    val key = channel.key.orEmpty()
                    val keyid = channel.keyid.orEmpty()
                    
                    val headers = channel.headers
                        .plusIfAbsent("User-Agent", channel.userAgent)
                        .filterValues { it.isNotBlank() }

                    newLiveSearchResponse(
                        channelname,
                        LoadData(
                            url = streamurl,
                            title = channelname,
                            poster = posterurl,
                            nation = nation,
                            key = key,
                            keyid = keyid,
                            headers = headers
                        ).toJson(),
                        TvType.Live
                    ) {
                        this.posterUrl = posterurl
                        //this.lang = nation
                    }
                }
                HomePageList(title, show, true)
            }, hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val data = fetchAllPlaylists()
        return data.items
            .filter { it.title?.contains(query, true) ?: false }
            .map { channel ->
                val streamurl = channel.url.orEmpty()
                val channelname = channel.title.orEmpty()
                val posterurl = channel.attributes["tvg-logo"].orEmpty()
                val nation = channel.attributes["group-title"].orEmpty()//.toSentenceCase()
                val key = channel.key.orEmpty()
                val keyid = channel.keyid.orEmpty()

                val headers = channel.headers
                    .plusIfAbsent("User-Agent", channel.userAgent)
                    .filterValues { it.isNotBlank() }

                newLiveSearchResponse(
                    channelname,
                    LoadData(
                        url = streamurl,
                        title = channelname,
                        poster = posterurl,
                        nation = nation,
                        key = key,
                        keyid = keyid,
                        headers = headers
                    ).toJson(),
                    TvType.Live
                ) {
                    this.posterUrl = posterurl
                    //this.lang = nation
                }
            }
    }

    override suspend fun load(url: String): LoadResponse {
        val data = parseJson<LoadData>(url)
        return newLiveStreamLoadResponse(data.title, data.url, url) {
            this.posterUrl = data.poster
            this.plot = data.nation
        }
    }

    data class LoadData(
        val url: String,
        val title: String,
        val poster: String,
        val nation: String,
        val key: String,
        val keyid: String,
        val headers: Map<String, String> = emptyMap(),
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<LoadData>(data)
        val headers = loadData.headers.filterValues { it.isNotBlank() }

        if (loadData.url.contains(".m3u8", true)) {
            callback.invoke(
                newExtractorLink(
                    "$name HLS",
                    "$name HLS",
                    loadData.url,
                    ExtractorLinkType.M3U8,
                ) {
                    this.quality = Qualities.Unknown.value
                    this.headers = headers
                }
            )
        } else {
            val key = decodeHex(loadData.key)
            val keyid = decodeHex(loadData.keyid)
            callback.invoke(
                newDrmExtractorLink(
                    "$name DASH",
                    "$name DASH",
                    loadData.url,
                    ExtractorLinkType.DASH,
                    CLEARKEY_UUID
                ) {
                    this.quality = Qualities.Unknown.value
                    this.key = key
                    this.kid = keyid
                    this.headers = headers
                }
            )
        }
        return true
    }
}

data class Playlist(
    val items: List<PlaylistItem> = emptyList(),
)

data class PlaylistItem(
    val title: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val url: String? = null,
    val userAgent: String? = null,
    val key: String? = null,
    val keyid: String? = null,
)

class IptvPlaylistParser {    
    fun parseM3U(content: String): Playlist {
        return parseM3U(content.byteInputStream())
    }
    
    @Throws(PlaylistParserException::class)
    fun parseM3U(input: InputStream): Playlist {
        val reader = input.bufferedReader()

        if (!reader.readLine().isExtendedM3u()) {
            throw PlaylistParserException.InvalidHeader()
        }

        val playlistItems = mutableListOf<PlaylistItem>()

        var currentTitle: String? = null
        var currentAttributes: Map<String, String> = emptyMap()
        var currentUserAgent: String? = null
        var currentReferrer: String? = null
        var currentHeaders: Map<String, String> = emptyMap()
        var currentKey: String? = null
        var currentKeyId: String? = null

        reader.forEachLine { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEachLine

            when {                
                line.startsWith("#KODIPROP:inputstream.adaptive.license_key=", true) -> {
                    val license = line.substringAfter("=")
                    val parts = license.split(":")
                    if (parts.size == 2) {
                        currentKeyId = parts[0]
                        currentKey = parts[1]
                    }
                }
                
                line.startsWith(EXT_INF, true) -> {
                    currentTitle = line.getTitle()
                    currentAttributes = line.getAttributes()
                    currentUserAgent = null
                    currentReferrer = null
                    currentHeaders = emptyMap()
                }
                
                line.startsWith(EXT_VLC_OPT, true) -> {
                    val opt = line.removePrefix("$EXT_VLC_OPT:")
                    val eqIdx = opt.indexOf('=')
                    if (eqIdx > 0) {
                        val kRaw = opt.substring(0, eqIdx).trim()
                        val vRaw = opt.substring(eqIdx + 1).trim().replaceQuotesAndTrim()
                        if (kRaw.startsWith("http-", true)) {
                            val headerKey = normalizeHeaderKey(kRaw)
                            currentHeaders = currentHeaders + mapOf(headerKey to vRaw)
                            
                            when (headerKey) {
                                "User-Agent" -> currentUserAgent = vRaw
                                "Referer" -> currentReferrer = vRaw
                            }
                        }
                    }
                }
                
                !line.startsWith("#") -> {
                    val url = line.getUrl()
                    
                    val inlineParams = line.getUrlParameters()
                    var urlHeaders = currentHeaders
                    inlineParams.forEach { (k, v) ->
                        val key = normalizeHeaderKey(k)
                        urlHeaders = urlHeaders + mapOf(key to v)
                    }

                    val combinedUserAgent = inlineParams["User-Agent"] ?: currentUserAgent
                    if (!combinedUserAgent.isNullOrBlank()) {
                        urlHeaders = urlHeaders + mapOf("User-Agent" to combinedUserAgent)
                    }

                    if (currentReferrer != null && !urlHeaders.containsKey("Referer")) {
                        urlHeaders = urlHeaders + mapOf("Referer" to currentReferrer!!)
                    }

                    val key = line.getUrlParameter("key") ?: currentKey
                    val keyid = line.getUrlParameter("keyid") ?: currentKeyId

                    if (currentTitle != null && url != null) {
                        val finalAttributes = currentAttributes.toMutableMap().apply {
                            if (!key.isNullOrEmpty()) put("key", key)
                            if (!keyid.isNullOrEmpty()) put("keyid", keyid)
                        }

                        playlistItems.add(
                            PlaylistItem(
                                title = currentTitle ?: "",
                                attributes = finalAttributes,
                                url = url,
                                userAgent = combinedUserAgent,
                                headers = urlHeaders,
                                key = key,
                                keyid = keyid
                            )
                        )
                    }
                    
                    currentTitle = null
                    currentAttributes = emptyMap()
                    currentUserAgent = null
                    currentReferrer = null
                    currentHeaders = emptyMap()
                    currentKey = null
                    currentKeyId = null
                }
            }
        }

        return Playlist(playlistItems)
    }    

    private fun String.replaceQuotesAndTrim(): String {
        return replace("\"", "").trim()
    }

    private fun String.isExtendedM3u(): Boolean = startsWith(EXT_M3U, ignoreCase = true)

    private fun String.getTitle(): String? {
        return split(",").lastOrNull()?.replaceQuotesAndTrim()
    }

    private fun String.getUrl(): String? {
        return split("|").firstOrNull()?.replaceQuotesAndTrim()
    }
   
    private fun String.getUrlParameter(key: String): String? {
        val urlRegex = Regex("^(.*)\\|", RegexOption.IGNORE_CASE)
        val keyRegex = Regex("$key=([^&]*)", RegexOption.IGNORE_CASE)
        val paramsString = replace(urlRegex, "").replaceQuotesAndTrim()
        return keyRegex.find(paramsString)?.groups?.get(1)?.value?.let {
            URLDecoder.decode(it, StandardCharsets.UTF_8.name())
        }
    }
    
    private fun String.getUrlParameters(): Map<String, String> {
        val urlRegex = Regex("^(.*)\\|", RegexOption.IGNORE_CASE)
        val headersString = replace(urlRegex, "").replaceQuotesAndTrim()
        if (headersString.isEmpty() || headersString == this) return emptyMap()

        val out = mutableMapOf<String, String>()
        headersString.split("&").forEach { token ->
            val idx = token.indexOf('=')
            if (idx > 0) {
                val rawKey = token.substring(0, idx).trim()
                val rawVal = token.substring(idx + 1).trim()
                val decodedVal = try {
                    URLDecoder.decode(rawVal, StandardCharsets.UTF_8.name())
                } catch (_: Exception) {
                    rawVal
                }
                out[normalizeHeaderKey(rawKey)] = decodedVal.replaceQuotesAndTrim()
            }
        }
        return out
    }
   
    private fun normalizeHeaderKey(key: String): String {
        return when (key.lowercase()) {
            "http-user-agent", "user-agent" -> "User-Agent"
            "http-referrer", "http-referer", "referer", "referrer" -> "Referer"
            "http-origin", "origin" -> "Origin"
            "http-cookie", "cookie" -> "Cookie"
            else -> key
        }
    }

    //private fun String.getAttributes(): Map<String, String> {
    //    val extInfRegex = Regex("(#EXTINF:.?[0-9]+)", RegexOption.IGNORE_CASE)
    //    val attributesString = replace(extInfRegex, "").replaceQuotesAndTrim().split(",").first()
    //    return attributesString.split(Regex("\\s")).mapNotNull {
    //        val pair = it.split("=")
    //        if (pair.size == 2) pair.first() to pair.last().replaceQuotesAndTrim() else null
    //    }.toMap()
    //}
	
	private fun String.getAttributes(): Map<String, String> {
		val extInfRegex = Regex("(#EXTINF:\\s*-?[0-9]+)", RegexOption.IGNORE_CASE)
		val noHeader = replace(extInfRegex, "").trim()		
		val attributesString = noHeader.substringBefore(",")		
		val attrRegex = Regex("""(\S+)=("([^"]*)"|'([^']*)'|(\S+))""")

		val out = mutableMapOf<String, String>()
		for (m in attrRegex.findAll(attributesString)) {
			val key = m.groupValues[1]
			val value =
				m.groups[3]?.value // double quotes
					?: m.groups[4]?.value // single quotes
					?: m.groups[5]?.value // no quotes
					?: ""
			out[key] = value
		}
		return out
	}

    companion object {
        const val EXT_M3U = "#EXTM3U"
        const val EXT_INF = "#EXTINF"
        const val EXT_VLC_OPT = "#EXTVLCOPT"
    }
}

sealed class PlaylistParserException(message: String) : Exception(message) {
    class InvalidHeader :
        PlaylistParserException("Invalid file header. Header doesn't start with #EXTM3U")
}

private fun decodeHex(hexString: String): String {
    val length = hexString.length
    val byteArray = ByteArray(length / 2)
    for (i in 0 until length step 2) {
        byteArray[i / 2] = ((Character.digit(hexString[i], 16) shl 4) +
                Character.digit(hexString[i + 1], 16)).toByte()
    }
    val base64String = Base64.getEncoder().withoutPadding().encodeToString(byteArray)
    return String(base64String.toByteArray(Charsets.UTF_8), Charsets.UTF_8).trim()
}

private fun Map<String, String>.plusIfAbsent(key: String, value: String?): Map<String, String> {
    return if (!this.containsKey(key) && !value.isNullOrBlank()) this + mapOf(key to value) else this
}