package com.lagradost.cloudstream3.movieproviders

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jsoup.nodes.Element

class CineHdPlusProvider : MainAPI() {
    override var mainUrl = "https://cinehdplus.org"
    override var name = "CineHdPlus"
    override var lang = "mx"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override val mainPage = mainPageOf(
        "series/" to "Series",
        "series/?sort=popular" to "Series: Populares",
        "peliculas/" to "Peliculas",
        "peliculas/?sort=popular" to "Peliculas: Populares",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page").document
        val home = document.select("div.grid a")
            .mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("img").attr("alt")
        val href = this.attr("href").takeIf{ !it.isNullOrEmpty()} ?: this.select("a").attr("href")
        val posterUrl = fixUrlNull(this.select("img").attr("src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=$query").document
        val results =
            document.select("div.grid div.group").mapNotNull { it.toSearchResult() }
        return results
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val tvType = if (url.contains("/pelicula-")) TvType.Movie else TvType.TvSeries
        val title = doc.selectFirst(".sm\\:text-2xl")?.text()
        val plot = doc.selectFirst("head meta[property=og:description]")?.attr("content")
        val year = doc.selectFirst(".sub-meta span[itemprop=dateCreated]")?.text()?.toIntOrNull()
        val poster = doc.selectFirst("img.absolute")?.attr("src")
        val backimage = doc.selectFirst(".opacity-20")?.attr("src")
        val tags = doc.selectFirst(".details__list li")?.text()?.substringAfter(":")?.split(",")
        val trailer = doc.selectFirst("#OptYt iframe")?.attr("data-src")?.replaceFirst("https://www.youtube.com/embed/","https://www.youtube.com/watch?v=")
        val recommendations = doc.select("div.grid-cols-2:nth-child(2) a").mapNotNull { it.toSearchResult() }
        val episodes = doc.select("div.season-pane").flatMap {
            val season = it.attr("id").replaceFirst("season-content-", "").toIntOrNull()
            it.select("a.group").mapIndexed { idx, it ->
                val url = it.selectFirst("a")?.attr("href")
                val title = it.selectFirst("h3 span")?.text()?.substringAfter("(")?.substringBefore(")")
                val img = it.selectFirst("img.lazyload")?.attr("src")
                newEpisode(url){
                        this.name = title
                        this.season = season
                        this.episode = idx+1
                        this.posterUrl = img
                    }
            }
        }
        return when (tvType) {
            TvType.Movie -> newMovieLoadResponse(title!!, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backimage ?: poster
                this.plot = plot
                this.tags = tags
                this.year = year
                this.recommendations = recommendations
                addTrailer(trailer)
            }
            TvType.TvSeries -> newTvSeriesLoadResponse(
                title!!,
                url, tvType, episodes,
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backimage ?: poster
                this.plot = plot
                this.tags = tags
                this.year = year
                this.recommendations = recommendations
                addTrailer(trailer)
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
        val doc = app.get(data).document
        doc.select("button.player-tab").amap {
            val lang = it.attr("data-lang")
            val frame = it.attr("data-url")
                .substringAfter("player.php?h=").substringBefore("&")
            val doc = app.get(
                "${
                    mainUrl.replaceFirst(
                        "https://",
                        "https://api."
                    )
                }/ir/goto.php?h=$frame"
            ).document
            val form = doc.selectFirst("form")
            val url = form?.selectFirst("input#url")?.attr("value")
            if (url != null) {
                val doc = app.post(
                    "${mainUrl.replaceFirst("https://", "https://api.")}/ir/rd.php",
                    data = mapOf("url" to url)
                ).document
                val form = doc.selectFirst("form")
                val url = form?.selectFirst("input#url")?.attr("value")
                if (url != null) {
                    val doc = app.post(
                        "${
                            mainUrl.replaceFirst(
                                "https://",
                                "https://api."
                            )
                        }/ir/redir_ddh.php", data = mapOf("url" to url, "dl" to "0")
                    ).document
                    val form = doc.selectFirst("form")
                    val url = form?.attr("action")

                    val vid = form?.selectFirst("input#vid")?.attr("value")
                    val hash = form?.selectFirst("input#hash")?.attr("value")
                    if (url != null) {
                        val doc =
                            app.post(url, data = mapOf("vid" to vid!!, "hash" to hash!!)).document
                        val encoded = doc.selectFirst("script:containsData(link =)")?.html()
                            ?.substringAfter("link = '")?.substringBefore("';")
                        val link = base64Decode(encoded!!)
                        loadSourceNameExtractor(
                            lang,
                            fixHostsLinks(link),
                            "$mainUrl/",
                            subtitleCallback,
                            callback
                        )
                    }
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
}