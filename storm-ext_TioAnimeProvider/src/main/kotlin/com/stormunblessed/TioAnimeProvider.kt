package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import java.util.*
import kotlin.collections.ArrayList

class TioAnimeProvider : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return when {
                t.contains("OVA") || t.contains("Especial") -> TvType.OVA
                t.contains("Película") -> TvType.AnimeMovie
                else -> TvType.Anime
            }
        }
    }

    override var mainUrl = "https://tioanime.com"
    override var name = "TioAnime"
    override var lang = "es"
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
            Pair("$mainUrl/directorio?year=1950%2C2022&status=2&sort=recent", "Animes"),
            Pair("$mainUrl/directorio?year=1950%2C2022&status=1&sort=recent", "En Emisión"),
            Pair("$mainUrl/directorio?type[]=1&year=1950%2C2022&status=2&sort=recent", "Películas"),
        )
        val items = ArrayList<HomePageList>()
        items.add(
            HomePageList(
                "Últimos episodios",
                app.get(mainUrl).document.select("ul.episodes li article").mapNotNull { el ->
                    val epLink = el.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                    val epNum = Regex("-(\\d+)$").find(epLink)?.groupValues?.get(1)?.toIntOrNull()
                        ?: return@mapNotNull null
                    val poster = el.selectFirst("figure img")?.attr("src")
                    val title = el.selectFirst("h3.title")?.text()
                        ?.replace(Regex("\\s+\\d+$"), "")
                        ?.trimEnd() ?: return@mapNotNull null
                    val animeLink = fixUrl(epLink.replace(Regex("-\\d+$"), "").replace("ver/", "anime/"))
                    val dubstat = if (title.contains("Latino") || title.contains("Castellano"))
                        DubStatus.Dubbed
                    else DubStatus.Subbed
                    newAnimeSearchResponse(title, animeLink) {
                        this.posterUrl = fixUrl(poster ?: "")
                        addDubStatus(dubstat, epNum)
                    }
                })
        )
        urls.amap { (url, name) ->
            val doc = app.get(url).document
            val home = doc.select("ul.animes li article").mapNotNull { el ->
                val title = el.selectFirst("h3.title")?.text() ?: return@mapNotNull null
                val poster = el.selectFirst("figure img")?.attr("src")
                newAnimeSearchResponse(
                    title,
                    fixUrl(el.selectFirst("a")?.attr("href") ?: return@mapNotNull null),
                    TvType.Anime,
                ) {
                    this.posterUrl = fixUrl(poster ?: "")
                    this.dubStatus = if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(DubStatus.Subbed)
                }
            }
            items.add(HomePageList(name, home))
        }
        if (items.isEmpty()) throw ErrorLoadingException()
        return newHomePageResponse(items)
    }

    data class SearchObject(
        @JsonProperty("id") val id: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("last_id") val lastId: String?,
        @JsonProperty("slug") val slug: String
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.post("$mainUrl/api/search",
            data = mapOf("value" to query)
        ).text
        val json = parseJson<List<SearchObject>>(response)
        return json.map { searchr ->
            newAnimeSearchResponse(
                searchr.title,
                "$mainUrl/anime/${searchr.slug}",
                TvType.Anime
            ) {
                this.posterUrl = fixUrl("$mainUrl/uploads/portadas/${searchr.id}.jpg")
                this.dubStatus = if (searchr.title.contains("Latino") || searchr.title.contains("Castellano")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(DubStatus.Subbed)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val episodes = ArrayList<Episode>()
        val title = doc.selectFirst("h1.title")?.text()
            ?: return newAnimeLoadResponse("TioAnime", url, TvType.Anime)
        val poster = doc.selectFirst("div.thumb img")?.attr("src")
        val description = doc.selectFirst("p.sinopsis")?.text()
        val typeText = doc.selectFirst("span.anime-type-peli")?.text() ?: "Anime"
        val status = when (doc.selectFirst("div.thumb a.btn.status i")?.text()) {
            "En emision" -> ShowStatus.Ongoing
            "Finalizado" -> ShowStatus.Completed
            else -> null
        }
        val genre = doc.select("p.genres a").mapNotNull { it.text().trim().takeIf { g -> g.isNotEmpty() } }
        val year = doc.selectFirst("span.year")?.text()?.toIntOrNull()

        doc.select("script").forEach { script ->
            val data = script.data()
            if (data.contains("var episodes = [")) {
                val epData = data.substringAfter("var episodes = [").substringBefore("];")
                epData.split(",").forEach { epStr ->
                    val epNum = epStr.trim().toIntOrNull() ?: return@forEach
                    val link = url.replace("/anime/", "/ver/") + "-$epNum"
                    episodes.add(newEpisode(link) {
                        this.name = "Capítulo $epNum"
                        this.episode = epNum
                    })
                }
            }
        }
        return newAnimeLoadResponse(title, url, getType(typeText)) {
            posterUrl = fixUrl(poster ?: "")
            addEpisodes(DubStatus.Subbed, episodes.reversed())
            showStatus = status
            plot = description
            tags = genre
            this.year = year
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("script").amap { script ->
            if (script.data().contains("var videos =")) {
                val videos = script.data().replace("\\/", "/")
                fetchUrls(videos).map {
                    it.replace("https://embedsb.com/e/", "https://watchsb.com/e/")
                        .replace("https://ok.ru", "http://ok.ru")
                }.amap {
                    loadExtractor(it, subtitleCallback, callback)
                }
            }
        }
        return true
    }
}
