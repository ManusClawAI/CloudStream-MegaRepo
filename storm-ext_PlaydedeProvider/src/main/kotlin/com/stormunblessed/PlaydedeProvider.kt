package com.lagradost.cloudstream3.movieproviders

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URL

class PlaydedeProvider : TmdbProvider() {
    override var mainUrl = "https://playdede.in"
    override var name = "Playdede"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val instantLinkLoading = true
    override val useMetaLoadResponse = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
    )
    // usr=gjvzhllq pass=gjvzhllq1
    val defaultCookies = mapOf("utoken" to "w9K2uGAKjMvDeRBUgACEtaQ5ZD77W")

    data class TMDBMovie(
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // site playdede.in appears to be down/redirected to enlaces.ly
        // try alternative domains if available
        val sites = listOf(mainUrl, "https://playdede.su", "https://playdede.city")
        val res = parseJson<LinkData>(data)
        var path: String? = null
        for (site in sites) {
            try {
                val doc = app.get("$site/search?s=${res.tmdbId}").document
                path = doc.selectFirst("#\\ archive-content article a")?.attr("href")
                if (!path.isNullOrBlank() && path.endsWith(res.tmdbId.toString())) {
                    mainUrl = site
                    break
                }
                val source = if (res.season == null) "movie" else "tv"
                val info = app.get(
                    "https://api.themoviedb.org/3/$source/${res.tmdbId}?api_key=1865f43a0549ca50d341dd9ab8b29f49&language=es-ES"
                ).parsed<TMDBMovie>()
                val search = if (res.season == null) info.title else info.name
                path = app.get(
                    "$site/search?s=$search"
                ).document.selectFirst("#\\ archive-content article a")?.attr("href")
                if (!path.isNullOrBlank()) {
                    mainUrl = site
                    break
                }
            } catch (_: Exception) { }
        }
        if (!path.isNullOrBlank()) {
            val url = if (path.startsWith("pelicula")) "$mainUrl/$path" else "$mainUrl/episodios/${path.substringAfter("/")}-${res.season}x${res.episode}/"
            val doc = app.get(url).document
            doc.select("div.playerItem").amap {
                val lang = it.attr("data-lang")
                val playerId = it.attr("data-loadplayer")
                val link = app.get("$mainUrl/embed.php?id=$playerId&width=1080&height=480").document.selectFirst("iframe")?.attr("src")
                if (!link.isNullOrBlank()) {
                    loadSourceNameExtractor(lang, fixHostsLinks(link), "$mainUrl/", subtitleCallback, callback)
                }
            }
        }
        return true
    }
}

data class LinkData(
        @JsonProperty("movieName") val title: String? = null,
        @JsonProperty("imdbID") val imdbId: String? = null,
        @JsonProperty("tmdbID") val tmdbId: Int? = null,
        @JsonProperty("season") val season: Int? = null,
        @JsonProperty("episode") val episode: Int? = null,
    )
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
        .replaceFirst("https://powvideo.org", "https://powvideo.net")
        .replaceFirst("https://vidmoly.me", "https://vidmoly.com")
        .replaceFirst("https://mixdrop.bz", "https://mixdrop.co")
        .replaceFirst("https://streamtape.com", "https://streamtape.to")
        .replaceFirst("https://gamovideo.com", "https://gamovideo.net")
}
