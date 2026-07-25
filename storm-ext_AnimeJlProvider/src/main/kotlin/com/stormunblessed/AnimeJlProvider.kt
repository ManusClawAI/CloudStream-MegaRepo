package com.stormunblessed

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.nicehttp.NiceResponse
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class AnimeJlProvider : MainAPI() {
    private val cloudflareKiller = CloudflareKiller()

    private suspend fun appGetCf(url: String): NiceResponse {
        return app.get(url, interceptor = cloudflareKiller)
    }

    override var mainUrl = "https://www.anime-jl.net"
    override var name = "AnimeJL"
    override var lang = "mx"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Anime,
    )

    override val mainPage = mainPageOf(
        "animes?genre[]=46&order=updated" to "Latino",
        "animes?" to "Animes",
        "animes?tipo[]=7&order=updated" to "Donghuas",
        "animes?tipo[]=3&order=updated" to "Peliculas",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = appGetCf("$mainUrl/${request.data}&page=$page").document
        val home = document.select("ul.ListAnimes li")
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
        val title = this.select("article.Anime h3.Title").text()
        val href = this.select("article.Anime a").attr("href")
        val posterUrl = fixUrlNull(
            this.select("article.Anime a div.Image figure img").attr("src")
        )?.replaceFirst("^/".toRegex(), "$mainUrl/")
        return newAnimeSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.posterHeaders = if (posterUrl?.contains(mainUrl) == true) cloudflareKiller.getCookieHeaders(mainUrl).toMap() else emptyMap()
            addDubStatus(getDubStatus(href))
        }
    }

    private fun getDubStatus(title: String): DubStatus {
        return if (title.contains("-latino") || title.contains("-castellano"))
            DubStatus.Dubbed
        else DubStatus.Subbed
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = appGetCf("$mainUrl/animes?q=$query").document
        val results =
            document.select("ul.ListAnimes li").mapNotNull { it.toSearchResult() }
        return results
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = appGetCf(url).document
        val title = doc.selectFirst("div.Ficha div.Container h1.Title")?.text() ?: ""
        val backimage = doc.selectFirst("div.Ficha div.Bg")!!.attr("style")
            .substringAfter("background-image:url(").substringBefore(")")
        val poster = doc.selectFirst("div.Container div.Image figure img")!!.attr("src")
        val description =
            doc.selectFirst("div.Container main.Main section.WdgtCn div.Description")!!.text()
        val tags =
            doc.select("div.Container main.Main section.WdgtCn nav.Nvgnrs a").map { it.text() }
        val episodes = ArrayList<Episode>()
        val script =
            doc.select("script").firstOrNull { it.html().contains("var episodes =") }?.html()
        if (!script.isNullOrEmpty()) {
            val jsonscript =
                script.substringAfter("episodes = ").substringBefore(";").replace(",]", "]")
            val json = parseJson<List<List<String>>>(jsonscript)
            json.map { list ->
                var epNum = 0
                var epTitle = ""
                var epurl = ""
                var realimg = ""
                list.forEachIndexed { idx, it ->
                    if (idx == 0) {
                        epNum = it.toIntOrNull() ?: 0
                    } else if (idx == 1) {
                        epurl = "$url/$it"
                    } else if (idx == 2) {
                        realimg = "$mainUrl/storage/$it"
                    } else if (idx == 3) {
                        epTitle = it.ifEmpty { "Episodio $epNum" }
                    }
                }
                episodes.add(
                    newEpisode(
                        epurl,
                    ) {
                        this.name = epTitle
                        this.season = 0
                        this.episode = epNum
                        this.posterUrl = realimg
                    }
                )
            }
        }

        return newTvSeriesLoadResponse(
            title,
            url, TvType.Anime, episodes,
        ) {
            this.posterUrl = poster
            this.backgroundPosterUrl = backimage
            this.posterHeaders = if (poster.contains(mainUrl)) cloudflareKiller.getCookieHeaders(mainUrl).toMap() else emptyMap()
            this.plot = description
            this.tags = tags
        }
    }

    suspend fun followRedirectsJS(url: String): Document {
        val redirectRegex = Regex("""window\.location\.href\s*=\s*"([^"]+)";""")
        val doc = app.get(url).document
        val redirectUrl = redirectRegex.find(doc.data())?.groupValues?.get(1)
        if (redirectUrl != null) {
            return followRedirectsJS(redirectUrl)
        } else {
            return doc;
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        appGetCf(data).document.select("script")
            .firstOrNull { it.html().contains("var video = [];") }?.let { frameUrl ->
                fetchUrls(frameUrl.html())
                    .amap {
                        if (it.startsWith("https://holuagency.top/load.php?")) {
                            val doc = followRedirectsJS(it)
                            val form = doc.selectFirst("form#link")
                            val url = form?.attr("action")
                            val token = form?.selectFirst("input[name=token]")?.attr("value")
                            val back = form?.selectFirst("input[name=back]")?.attr("value")
                            val sh = form?.selectFirst("input[name=sh]")?.attr("value")
                            if (url != null) {
                                val doc =
                                    app.post(
                                        url,
                                        data = mapOf(
                                            "token" to token!!,
                                            "back" to back!!,
                                            "sh" to sh!!
                                        )
                                    ).document
                                val containerFrameUrl =
                                    doc.selectFirst("a.cs-share__copy-link")?.attr("href")
                                if (containerFrameUrl != null) {
                                    val doc = app.get(
                                        containerFrameUrl,
                                        cookies = mapOf("t" to token, "b" to back, "s" to sh)
                                    ).document
                                    doc.selectFirst("div#player iframe")?.attr("src")?.let {
                                        loadExtractor(
                                            fixHostsLinks(it),
                                            data,
                                            subtitleCallback,
                                            callback
                                        )
                                    }
                                }
                            }
                        } else {
//                            TODO
//                            https://www.burstcloud.co/embed/8b2eb5f8bb2aef82451b49434eae3a51032d97ec441ad065ec3b7d4353f38ed6/Kimetsu%20No%20Yaiba%20Castillo%20Infinito%20Latino.mp4
                            loadExtractor(
                                fixHostsLinks(it),
                                data,
                                subtitleCallback,
                                callback
                            )
                        }
                    }
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