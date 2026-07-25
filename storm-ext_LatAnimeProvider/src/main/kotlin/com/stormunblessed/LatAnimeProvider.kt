package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.*

class LatAnimeProvider : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Especial")) TvType.OVA
            else if (t.contains("Pelicula")) TvType.AnimeMovie
            else TvType.Anime
        }

        fun getDubStatus(title: String): DubStatus {
            return if (title.contains("Latino") || title.contains("Castellano"))
                DubStatus.Dubbed
            else DubStatus.Subbed
        }
    }

    override var mainUrl = "https://latanime.org"
    override var name = "LatAnime"
    override var lang = "mx"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.OVA,
        TvType.Anime,
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val urls = listOf(
            Pair("$mainUrl/emision", "En emisión"),
            Pair("$mainUrl/animes?fecha=false&genero=false&letra=false&categoria=Película", "Peliculas"),
            Pair("$mainUrl/animes", "Animes"),
        )

        val items = ArrayList<HomePageList>()

        urls.amap { (url, name) ->
            val home = app.get(url).document.select(".col-6.my-3").mapNotNull { el ->
                val title = el.selectFirst("h3.my-1")?.text() ?: return@mapNotNull null
                val imgEl = el.selectFirst("img.img-fluid2")
                val poster = imgEl?.attr("data-src")?.ifEmpty { imgEl.attr("src") } ?: ""
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
        return app.get("$mainUrl/buscar?q=$query").document.select(".col-6.my-3").mapNotNull { el ->
            val title = el.selectFirst("h3.my-1")?.text() ?: return@mapNotNull null
            val href = el.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val imgEl = el.selectFirst("img.img-fluid2")
            val image = imgEl?.attr("data-src")?.ifEmpty { imgEl.attr("src") } ?: ""
            newAnimeSearchResponse(title, fixUrl(href), TvType.Anime) {
                this.posterUrl = fixUrl(image)
                this.dubStatus = if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                    DubStatus.Dubbed
                ) else EnumSet.of(DubStatus.Subbed)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val poster = doc.selectFirst("div.serieimgficha img.img-fluid2")?.attr("src")
            ?: doc.selectFirst("div.serieimgficha img.img-fluid2")?.attr("data-src") ?: ""
        val backimage = doc.selectFirst("div.row div a div img")?.attr("data-src")
            ?: doc.selectFirst("div.row div a div img")?.attr("src") ?: ""
        val title = doc.selectFirst("h2")?.text() ?: return null
        val type = doc.selectFirst(".chapterdetls2")?.text() ?: ""
        val description = doc.selectFirst("p.my-2.opacity-75")?.text()?.replace("Ver menos", "") ?: ""
        val genres = doc.select("div.btn").mapNotNull { it.text().trim().takeIf { g -> g.isNotEmpty() } }
        val status = when (doc.selectFirst("div.serieimgficha div.my-2")?.text()) {
            "Estreno" -> ShowStatus.Ongoing
            "Finalizado" -> ShowStatus.Completed
            else -> null
        }
        val episodes = doc.select("div.row div a[href*=/ver/]").mapNotNull { el ->
            val name = el.selectFirst(".cap-layout")?.text() ?: return@mapNotNull null
            val link = el.attr("href")
            val epThumb = el.selectFirst("img")?.attr("data-src") ?: el.selectFirst("img")?.attr("src") ?: ""
            newEpisode(link) {
                this.name = name
                this.posterUrl = epThumb
            }
        }
        return newAnimeLoadResponse(title, url, getType(title)) {
            posterUrl = poster
            backgroundPosterUrl = backimage
            addEpisodes(DubStatus.Subbed, episodes)
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
            , null, subtitleCallback, callback)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("li#play-video").amap {
            val encodedurl = it.select("a").attr("data-player")
            val urlDecoded = base64Decode(encodedurl)
            val url = urlDecoded
                .replace("https://monoschinos2.com/reproductor?url=", "")
                .replace("https://mojon.latanime.org/aqua/fn?url=", "")
            customLoadExtractor(url, mainUrl, subtitleCallback, callback)
        }
        return true
    }
}
