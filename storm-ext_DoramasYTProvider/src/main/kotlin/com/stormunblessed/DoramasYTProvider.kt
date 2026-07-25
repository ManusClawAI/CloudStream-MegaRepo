package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.*
import kotlin.collections.ArrayList

class DoramasYTProvider : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Especial")) TvType.OVA
            else if (t.contains("Pelicula")) TvType.Movie
            else TvType.AsianDrama
        }
        fun getDubStatus(title: String): DubStatus {
            return if (title.contains("Latino") || title.contains("Castellano"))
                DubStatus.Dubbed
            else DubStatus.Subbed
        }

        var latestCookie: Map<String, String> = emptyMap()
        var latestToken = ""
    }

    override var mainUrl = "https://doramasyt.com"
    override var name = "DoramasYT"
    override var lang = "mx"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.AsianDrama,
    )

    private suspend fun getToken(url: String): Map<String, String> {
        val maintas = app.get(url, headers = mapOf(
            "Host" to "www.doramasyt.com",
            "User-Agent" to USER_AGENT,
            "Accept" to "application/json, text/javascript, */*; q=0.01",
            "Accept-Language" to "en-US,en;q=0.5",
            "Referer" to "https://www.doramasyt.com/buscar?q=",
            "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
            "X-Requested-With" to "XMLHttpRequest",
            "Origin" to mainUrl,
            "DNT" to "1",
            "Alt-Used" to "www.doramasyt.com",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "same-origin",
            "TE" to "trailers"
        ))
        val token = maintas.document.selectFirst("html head meta[name=csrf-token]")?.attr("content") ?: ""
        val cookies = maintas.cookies
        latestToken = token
        latestCookie = cookies
        return latestCookie
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val urls = listOf(
            Pair("$mainUrl/emision", "En emisión"),
            Pair("$mainUrl/doramas", "Doramas"),
            Pair("$mainUrl/doramas?categoria=pelicula", "Peliculas")
        )
        val items = ArrayList<HomePageList>()
        items.add(
            HomePageList(
                "Capítulos actualizados",
                app.get(mainUrl, timeout = 120).document.select("li.col article").mapNotNull { el ->
                    val title = el.selectFirst("h3")?.text() ?: return@mapNotNull null
                    val poster = el.selectFirst("img")?.attr("data-src") ?: el.selectFirst("img")?.attr("src") ?: ""
                    val epRegex = Regex("episodio-(\\d+)")
                    val url = el.selectFirst("a")?.attr("href")
                        ?.replace("ver/", "dorama/")
                        ?.replace(epRegex, "sub-espanol") ?: return@mapNotNull null
                    val epNum = title.substringAfter("Capítulo").trim().toIntOrNull()
                    newAnimeSearchResponse(title, url) {
                        this.posterUrl = fixUrl(poster)
                        addDubStatus(getDubStatus(title), epNum)
                    }
                }, true)
        )

        urls.amap { (url, name) ->
            val home = app.get(url).document.select("li.col").mapNotNull { el ->
                val title = el.selectFirst("h3")?.text() ?: return@mapNotNull null
                val poster = el.selectFirst("img")?.attr("data-src") ?: el.selectFirst("img")?.attr("src") ?: ""
                val href = el.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                newAnimeSearchResponse(title, fixUrl(href)) {
                    this.posterUrl = fixUrl(poster)
                    addDubStatus(getDubStatus(title))
                }
            }
            items.add(HomePageList(name, home))
        }

        if (items.isEmpty()) throw ErrorLoadingException()
        return newHomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/buscar?q=$query", timeout = 120).document.select("li.col").mapNotNull { el ->
            val title = el.selectFirst("h3")?.text() ?: return@mapNotNull null
            val href = el.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val image = el.selectFirst("img")?.attr("data-src") ?: el.selectFirst("img")?.attr("src") ?: ""
            newAnimeSearchResponse(title, href, TvType.AsianDrama) {
                this.posterUrl = image
                this.dubStatus = if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                    DubStatus.Dubbed
                ) else EnumSet.of(DubStatus.Subbed)
            }
        }
    }

    data class CapList(
        @JsonProperty("eps") val eps: List<Ep>,
    )

    data class Ep(
        val num: Int?,
    )

    override suspend fun load(url: String): LoadResponse {
        getToken(url)
        val doc = app.get(url, timeout = 120).document
        val poster = doc.selectFirst("img.rounded-3")?.attr("data-src") ?: doc.selectFirst("img.rounded-3")?.attr("src") ?: ""
        val backimage = doc.selectFirst("img.w-100")?.attr("data-src") ?: doc.selectFirst("img.w-100")?.attr("src") ?: ""
        val title = doc.selectFirst(".fs-2")?.text() ?: "Dorama"
        val type = doc.selectFirst("div.bg-transparent > dl:nth-child(1) > dd")?.text() ?: "Serie"
        val description = doc.selectFirst("div.mb-3")?.text()?.replace("Ver menos", "") ?: ""
        val genres = doc.select(".my-4 > div a span").map { it.text() }
        val status = when (doc.selectFirst("div.col:nth-child(1) > div:nth-child(1) > div")?.text()) {
            "Estreno" -> ShowStatus.Ongoing
            "Finalizado" -> ShowStatus.Completed
            else -> null
        }
        val caplist = doc.selectFirst(".caplist")?.attr("data-ajax") ?: throw ErrorLoadingException("Intenta de nuevo")

        val capJson = app.post(caplist,
            headers = mapOf(
                "Host" to "www.doramasyt.com",
                "User-Agent" to USER_AGENT,
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "Accept-Language" to "en-US,en;q=0.5",
                "Referer" to url,
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "X-Requested-With" to "XMLHttpRequest",
                "Origin" to mainUrl,
                "DNT" to "1",
                "Alt-Used" to "www.doramasyt.com",
                "Connection" to "keep-alive",
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "same-origin",
                "TE" to "trailers"
            ),
            cookies = latestCookie,
            data = mapOf("_token" to latestToken)).parsed<CapList>()
        val epList = capJson.eps.mapNotNull { ep ->
            val epNum = ep.num ?: return@mapNotNull null
            val epUrl = "${url.replace("-sub-espanol", "").replace("/dorama/", "/ver/")}-episodio-$epNum"
            newEpisode(epUrl) {
                this.episode = epNum
            }
        }

        return newAnimeLoadResponse(title, url, getType(type)) {
            posterUrl = poster
            backgroundPosterUrl = backimage
            addEpisodes(DubStatus.Subbed, epList)
            showStatus = status
            plot = description
            tags = genres
        }
    }

    suspend fun customLoadExtractor(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        loadExtractor(url
            .replaceFirst("https://hglink.to", "https://streamwish.to")
            .replaceFirst("https://swdyu.com", "https://streamwish.to")
            .replaceFirst("https://mivalyo.com", "https://vidhidepro.com")
            .replaceFirst("https://filemoon.link", "https://filemoon.sx")
            .replaceFirst("https://sblona.com", "https://watchsb.com")
            , referer, subtitleCallback, callback)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("#myTab li").amap {
            val encodedurl = it.select(".play-video").attr("data-player")
            val urlDecoded = base64Decode(encodedurl)
            if (urlDecoded.startsWith("http")) {
                val url = urlDecoded.replace("https://monoschinos2.com/reproductor?url=", "")
                customLoadExtractor(url, mainUrl, subtitleCallback, callback)
            } else {
                app.get("$mainUrl/reproductor?video=$encodedurl").document.selectFirst("iframe")?.attr("src")?.let {
                    customLoadExtractor(it, mainUrl, subtitleCallback, callback)
                }
            }
        }
        return true
    }
}
