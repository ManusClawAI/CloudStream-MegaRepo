package com.lagradost.cloudstream3.animeproviders

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.nicehttp.NiceResponse
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Element
import java.net.URL
import java.time.LocalDate
import java.util.*
import kotlin.collections.ArrayList


class ReyDonghuaProvider : MainAPI() {
    companion object {
        var latestCookie: Map<String, String> = emptyMap()
        var latestToken = ""
    }

    override var mainUrl = "https://reydonghua.org"
    override var name = "ReyDonghua"
    override var lang = "mx"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Anime,
    )

    private suspend fun getToken(url: String): Map<String, String> {
        val maintas = app.get(url)
        val token =
            maintas.document.selectFirst("html head meta[name=csrf-token]")?.attr("content") ?: ""
        val cookies = maintas.cookies
        latestToken = token
        latestCookie = cookies
        return latestCookie
    }

     override val mainPage = mainPageOf(
        "donghuas?fecha=${Calendar.getInstance().get(Calendar.YEAR)}" to "Recientes",
        "donghuas?" to "Donghuas",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}&p=$page").document
        val home = document.select("li.col")
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
        val title = this.select("a div h2").text()
        val href = this.select("a").attr("href")
        val posterUrl = fixUrlNull(this.select("a div.position-relative img").attr("data-src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/buscar?q=$query").document
        val results =
            document.select("li.col").mapNotNull { it.toSearchResult() }
        return results
    }

    data class PaginateUrl(
        @JsonProperty("paginate_url") val paginateUrl: String,
    )

    data class CapList(
        @JsonProperty("caps") val caps: List<Ep>,
    )

    data class Ep(
        val episodio: Int?,
        val url: String?,
    )

    suspend fun getCaps(caplist: String, referer: String): NiceResponse {
        val res = app.post(
            caplist,
            headers = mapOf(
                "Host" to URL(mainUrl).host,
                "User-Agent" to USER_AGENT,
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "Accept-Language" to "en-US,en;q=0.5",
                "Referer" to referer,
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "X-Requested-With" to "XMLHttpRequest",
                "Origin" to mainUrl,
                "Connection" to "keep-alive",
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "same-origin",
                "TE" to "trailers"
            ),
            cookies = latestCookie,
            data = mapOf(
                "_token" to latestToken,
                "p" to "1",
                "order" to "1"
            )
        )
        latestCookie = res.cookies
        return res
    }

    override suspend fun load(url: String): LoadResponse {
        getToken(url)
        val doc = app.get(url, timeout = 120).document
        val caplist = doc.selectFirst(".caplist")!!.attr("data-ajax")
        val poster = doc.selectFirst(".aspecto")!!.attr("data-src")
        val title = doc.selectFirst(".fs-3")!!.text()
        val description = doc.selectFirst("p.text-muted")!!.text()
        val genres = doc.select(".lh-lg a")
            .map { it.text() }
        val status =
            when (doc.selectFirst("div.mb-4 > div:nth-child(1) > div:nth-child(1) > div:nth-child(1) > div:nth-child(2) > div:nth-child(2)")
                ?.text()) {
                "Estreno" -> ShowStatus.Ongoing
                "Finalizado" -> ShowStatus.Completed
                else -> null
            }
        val pagurl = getCaps(caplist, url).parsed<PaginateUrl>()
        val capJson = getCaps(pagurl.paginateUrl, url).parsed<CapList>()
        val epList = capJson.caps.map { epnum ->
            newEpisode(
                epnum.url
            ) {
                this.episode = epnum.episodio
            }
        }
        return newAnimeLoadResponse(title, url, TvType.Anime) {
            posterUrl = poster
            backgroundPosterUrl = poster
            addEpisodes(DubStatus.Subbed, epList)
            showStatus = status
            plot = description
            tags = genres
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("#myTab li").amap {
            val encodedurl = it.select(".play-video").attr("data-player")
            val urlDecoded =
                base64Decode(encodedurl).replace("https://playerwish.com", "https://streamwish.to")
            loadExtractor(fixHostsLinks(urlDecoded), mainUrl, subtitleCallback, callback)
        }
        return true
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