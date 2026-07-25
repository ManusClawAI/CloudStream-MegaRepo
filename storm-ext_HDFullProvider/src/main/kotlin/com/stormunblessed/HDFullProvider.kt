package com.stormunblessed

import android.util.Base64
import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HDFullProvider : MainAPI() {
    override var mainUrl = "https://hdfull.love"
    override var name = "HDFull"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    // usr:yji0r4c6 pass:@1YU1kc1
    companion object {
        var latestCookie: Map<String, String> = mapOf(
            "language" to "es",
            "PHPSESSID" to "hqh4vktr8m29pfd1dsthiatpk0",
            "guid" to "1525945|2fc755227682457813590604c5a6717d",
        )
    }

    private suspend fun ensureLoggedIn(): Map<String, String> {
        val testResp = app.get("$mainUrl/peliculas-estreno", cookies = latestCookie)
        val html = testResp.text
        if (!html.contains("Ingresar") && !testResp.url.contains("/login")) {
            return latestCookie
        }
        Log.d("HDFull", "Session expired, attempting login...")
        try {
            val loginResp = app.post("$mainUrl/a/login",
                data = mapOf("username" to "yji0r4c6", "password" to "@1YU1kc1"),
                referer = mainUrl,
                cookies = latestCookie
            )
            if (loginResp.isSuccessful) {
                latestCookie = latestCookie + loginResp.cookies
                Log.d("HDFull", "Login successful, new cookies: $latestCookie")
            }
        } catch (e: Exception) {
            Log.e("HDFull", "Login failed: ${e.message}")
        }
        return latestCookie
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val cookies = ensureLoggedIn()
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair("Películas Estreno", "$mainUrl/peliculas-estreno"),
            Pair("Películas Actualizadas", "$mainUrl/peliculas-actualizadas"),
            Pair("Series", "$mainUrl/series"),
        )
        urls.amap { (name, url) ->
            val doc = app.get(url, cookies = cookies).document
            val home =
                doc.select("div.center div.view").amap {
                    val title = it.selectFirst("h5.left a.link")?.attr("title")
                    val link = it.selectFirst("h5.left a.link")?.attr("href")
                        ?.replaceFirst("/", "$mainUrl/")
                    val type = if (link!!.contains("/pelicula")) TvType.Movie else TvType.TvSeries
                    val img =
                        it.selectFirst("div.item a.spec-border-ie img.img-preview")?.attr("src")
                    newTvSeriesSearchResponse(title!!, link, type){
                        this.posterUrl = fixUrl(img!!)
                        this.posterHeaders = mapOf("Referer" to "$mainUrl/")
                    }
                }
            items.add(HomePageList(name, home))
        }
        return newHomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val cookies = ensureLoggedIn()
        val url = "$mainUrl/buscar"
        val csfrDoc = app.post(
            url, cookies = cookies, referer = "$mainUrl/buscar", data = mapOf(
                "menu" to "search",
                "query" to query,
            )
        ).document
        val csfr = csfrDoc.selectFirst("input[value*='sid']")!!.attr("value")
        val doc = app.post(
            url, cookies = cookies, referer = "$mainUrl/buscar", data = mapOf(
                "__csrf_magic" to csfr,
                "menu" to "search",
                "query" to query,
            )
        ).document
        return doc.select("div.container div.view").amap {
            val title = it.selectFirst("h5.left a.link")?.attr("title")
            val link = it.selectFirst("h5.left a.link")?.attr("href")
                ?.replaceFirst("/", "$mainUrl/")
            val type = if (link!!.contains("/pelicula")) TvType.Movie else TvType.TvSeries
            val img =
                it.selectFirst("div.item a.spec-border-ie img.img-preview")?.attr("src")
            newTvSeriesSearchResponse(title!!, link, type){
                this.posterUrl = fixUrl(img!!)
                this.posterHeaders = mapOf("Referer" to "$mainUrl/")
            }
        }
    }

    data class EpisodeJson(
        val episode: String?,
        val season: String?,
        @JsonProperty("date_aired") val dateAired: String?,
        val thumbnail: String?,
        val permalink: String?,
        val show: Show?,
        val id: String?,
        val title: Title?,
        val languages: List<String>? = null
    )

    data class Show(
        val title: Title?,
        val id: String?,
        val permalink: String?,
        val thumbnail: String?
    )

    data class Title(
        val es: String?,
        val en: String?
    )

    override suspend fun load(url: String): LoadResponse? {
        val cookies = ensureLoggedIn()
        val doc = app.get(url, cookies = cookies).document
        val tvType = if (url.contains("pelicula")) TvType.Movie else TvType.TvSeries
        val title = doc.selectFirst("div#summary-title")?.text() ?: ""
        val backimage =
            doc.selectFirst("div#summary-fanart-wrapper")!!.attr("style").substringAfter("url(")
                .substringBefore(")").trim()
        val poster =
            doc.selectFirst("div#summary-overview-wrapper div.show-poster img.video-page-thumbnail")!!
                .attr("src")
        val description =
            doc.selectFirst("div#summary-overview-wrapper div.show-overview div.show-overview-text")!!
                .text()
        val tags =
            doc.selectFirst("div#summary-overview-wrapper div.show-details p:contains(Género:)")
                ?.text()?.substringAfter("Género:")
                ?.split(" ")
        val year = doc.selectFirst("div#summary-overview-wrapper div.show-details p")?.text()
            ?.substringAfter(":")?.trim()
            ?.toIntOrNull()
        var episodes = if (tvType == TvType.TvSeries) {
            val sid = doc.select("script").firstOrNull { it.html().contains("var sid =") }!!.html()
                .substringAfter("var sid = '").substringBefore("';")
            doc.select("div#non-mashable div.main-wrapper div.container-wrap div div.container div.span-24 div.flickr")
                .flatMap { seasonDiv ->
                    val seasonNumber = seasonDiv.selectFirst("a img")?.attr("original-title")
                        ?.substringAfter("Temporada")?.trim()?.toIntOrNull()
                    val result = app.post(
                        "$mainUrl/a/episodes", cookies = cookies, data = mapOf(
                            "action" to "season",
                            "start" to "0",
                            "limit" to "0",
                            "show" to sid,
                            "season" to "$seasonNumber",
                            )
                    )
                    val episodesJson = AppUtils.parseJson<List<EpisodeJson>>(result.document.text())
                    episodesJson.amap {
                        val episodeNumber = it.episode?.toIntOrNull()
                        val epTitle = it.title?.es?.trim() ?: "Episodio $episodeNumber"
                        val epurl = "$url/temporada-${it.season}/episodio-${it.episode}"
                        newEpisode(epurl){
                            this.name = epTitle
                            this.season = seasonNumber
                            this.episode = episodeNumber
                        }
                    }
                }
        } else listOf()

        return when (tvType) {
            TvType.TvSeries -> {
                newTvSeriesLoadResponse(title, url, tvType, episodes) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = backimage
                    this.plot = description
                    this.tags = tags
                    this.year = year
                    this.posterHeaders = mapOf("Referer" to "$mainUrl/")
                }
            }
            TvType.Movie -> {
                newMovieLoadResponse(title, url, tvType, url) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = backimage
                    this.plot = description
                    this.tags = tags
                    this.year = year
                    this.posterHeaders = mapOf("Referer" to "$mainUrl/")
                }
            }
            else -> null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val cookies = ensureLoggedIn()
        val doc = app.get(data, cookies = cookies).document

        // Try to find var ad = directly in the page
        var hash = doc.select("script").firstOrNull {
            it.html().contains("var ad =")
        }?.html()?.substringAfter("var ad = '")
            ?.substringBefore("';")

        // If not found, try the AJAX stream endpoint
        if (hash.isNullOrEmpty()) {
            try {
                val streamResp = app.post("$mainUrl/ajax/stream.php",
                    cookies = cookies,
                    data = mapOf("max_id" to "0", "type" to "1"),
                    referer = data
                )
                hash = streamResp.document.select("script").firstOrNull {
                    it.html().contains("var ad =")
                }?.html()?.substringAfter("var ad = '")
                    ?.substringBefore("';")
            } catch (_: Exception) { }
        }

        if (!hash.isNullOrEmpty()) {
            try {
                val json = decodeHash(hash)
                json.amap {
                    val url = fixHostsLinks(getUrlByProvider(it.provider, it.code))
                    if (url.isNotEmpty()) {
                        Log.d("qwerty", "loadLinks: $url")
                        loadSourceNameExtractor(it.lang, url, mainUrl, subtitleCallback, callback)
                    }
                }
            } catch (_: Exception) { }
        }
        return true
    }

    data class ProviderCode(
        val id: String,
        val provider: String,
        val code: String,
        val lang: String,
        val quality: String
    )

    fun decodeHash(str: String): List<ProviderCode> {
        val decodedBytes = Base64.decode(str, Base64.DEFAULT)
        val decodedString = String(decodedBytes)
        val jsonString = decodedString.substrings(14)
        return AppUtils.parseJson<List<ProviderCode>>(jsonString)
    }

    fun String.obfs(key: Int, n: Int = 126): String {
        if (key % 1 != 0 || n % 1 != 0) {
            return this
        }
        val chars = this.toCharArray()
        for (i in chars.indices) {
            val c = chars[i].code
            if (c <= n) {
                chars[i] = ((chars[i].code + key) % n).toChar()
            }
        }
        return chars.concatToString()
    }

    fun String.substrings(key: Int, n: Int = 126): String {
        if (key % 1 != 0 || n % 1 != 0) {
            return this
        }
        return this.obfs(n - key)
    }

    fun getUrlByProvider(providerIdx: String, id: String): String {
        return when (providerIdx) {
            "1" -> "https://powvideo.net/$id"
            "2" -> "https://streamplay.to/$id"
            "3" -> "https://fembed.com/v/$id"
            "4" -> "https://doodstream.com/e/$id"
            "5" -> "https://onlystream.tv/e/$id"
            "6" -> "https://streamtape.com/v/$id"
            "7" -> "https://voe.sx/e/$id"
            "8" -> "https://mixdrop.co/f/$id"
            "9" -> "https://upvideo.to/e/$id"
            "10" -> "https://rubystream.com/e/$id"
            "11" -> "https://vidcloud.co/v/$id"
            "12" -> "https://gamovideo.com/$id"
            "13" -> "https://filemoon.sx/e/$id"
            "14" -> "https://streamwish.to/e/$id"
            "15" -> "https://mixdrop.bz/f/$id"
            "16" -> "https://streamsss.net/e/$id"
            "20" -> "https://ok.ru/video/$id"
            "30" -> "https://vk.com/video_ext.php?oid=$id"
            "40" -> "https://vidmoly.me/w/$id"
            "41" -> "https://vidmoly.com/w/$id"
            else -> ""
        }
    }
}

suspend fun loadSourceNameExtractor(
    source: String,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
) {
    loadExtractor(url, referer, subtitleCallback) { link ->
        CoroutineScope(Dispatchers.IO).launch {
            callback.invoke(
                newExtractorLink(
                    "$source[${link.source}]",
                    "$source[${link.source}]",
                    link.url,
                ) {
                    this.quality = link.quality
                    this.type = link.type
                    this.referer = link.referer
                    this.headers = link.headers
                    this.extractorData = link.extractorData
                }
            )
        }
    }
}

fun fixHostsLinks(url: String): String {
    return url
        .replaceFirst("https://hglink.to", "https://streamwish.to")
        .replaceFirst("https://swdyu.com", "https://streamwish.to")
        .replaceFirst("https://cybervynx.com", "https://streamwish.to")
        .replaceFirst("https://dumbalag.com", "https://streamwish.to")
        .replaceFirst("https://mivalyo.com", "https://vidhidepro.com")
        .replaceFirst("https://dinisglows.com", "https://vidhidepro.com")
        .replaceFirst("https://dhtpre.com", "https://vidhidepro.com")
        .replaceFirst("https://filemoon.link", "https://filemoon.sx")
        .replaceFirst("https://sblona.com", "https://watchsb.com")
        .replaceFirst("https://lulu.st", "https://lulustream.com")
        .replaceFirst("https://uqload.io", "https://uqload.com")
        .replaceFirst("https://do7go.com", "https://dood.la")
        .replaceFirst("https://powvideo.net", "https://powwideo.org")
        .replaceFirst("https://vidmoly.com","https://vidmoly.me" )
        .replaceFirst("https://mixdrop.bz", "https://mixdrop.co")
        .replaceFirst("https://streamtape.com", "https://streamtape.to")
        .replaceFirst("https://gamovideo.com", "https://gamovideo.net")
}
