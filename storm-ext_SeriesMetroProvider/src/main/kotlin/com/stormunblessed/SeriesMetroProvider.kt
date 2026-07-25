package com.stormunblessed

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlin.math.log

class SeriesMetroProvider: MainAPI() {
    override var mainUrl = "https://www3.seriesmetro.net"
    override var name = "SeriesMetro"
    override var lang = "mx"

    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.TvSeries,
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val soup = app.get(mainUrl).document
        val list = listOf(
            Pair("Últimas series agregadas",".series article"),
            Pair("Ultimas peliculas agregadas", ".movies article")
        )

        /*
        val newseries = soup.select(".section.episodes article").map {
            val title = it.selectFirst(".entry-header .tvshow")!!.text()
            val poster = it.selectFirst(".post-thumbnail figure img")!!.attr("src")
            val href = it.selectFirst("a.lnk-blk")!!.attr("href").replace(Regex("(-(\\d+)x(\\d+).*)"),"")
                .replace("/episode","/serie")
            TvSeriesSearchResponse(
                title,
                fixUrl(href),
                this.name,
                TvType.TvSeries,
                fixUrl(poster),
                null,
                null
            )
        }

        items.add(HomePageList("Agregados Recientemente", newseries, true))
        */

        list.map { (name, csselement) ->
            val home = soup.select(csselement).map {
                val title = it.selectFirst(".entry-title")!!.text()
                val poster = it.selectFirst(".post-thumbnail figure img")!!.attr("src")
                val href = it.selectFirst("a.lnk-blk")!!.attr("href")
                newTvSeriesSearchResponse(title, fixUrl(href), if (href.contains("pelicula")) TvType.Movie else TvType.TvSeries){
                    this.posterUrl = fixUrl(poster)
                }
            }
            items.add(HomePageList(name, home))
        }
        return newHomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        return document.select("div#aa-movies article.post").amap {
            val title = it.selectFirst(".entry-title")!!.text()
            val href = it.selectFirst("a.lnk-blk")!!.attr("href")
            val image = it.selectFirst(".post-thumbnail figure img")!!.attr("src")
            newTvSeriesSearchResponse(title, fixUrl(href), TvType.TvSeries){
                this.posterUrl = fixUrl(image)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url, allowRedirects = false).document
        val title = soup.selectFirst("article.post header.entry-header h1")?.text() ?: ""
        val year = soup.selectFirst("article.post header.entry-header span.year")?.text()?.toIntOrNull()
        val description = soup.select("article.post aside div.description p").text().trim()

        var poster = soup.selectFirst("article.post .post-thumbnail figure img")?.attr("src")
        if (poster?.contains("data:image") == true) {
            poster = soup.selectFirst("article.post .post-thumbnail figure img")?.attr("data-lazy-src").toString()
        }
        val backposter = soup.selectFirst("div.bghd img.TPostBg")?.attr("src") ?: poster
        val tags = soup.select("span.genres a").map { it.text() }
        val datapost = soup.select("div#aa-wp header.section-header li.sel-temp a").attr("data-post")
//        val dataobject = soup.select("div.widget .aa-cn").attr("data-object")
        val dataseason = soup.select("div#aa-wp header.section-header li.sel-temp a").map {
            it.attr("data-season")
        }
        val tvType = if (url.contains("pelicula")) TvType.Movie else TvType.TvSeries
        val episodes = ArrayList<Episode>()
        val episs = dataseason.amap { season ->
            val response = app.post("$mainUrl/wp-admin/admin-ajax.php", data =
            mapOf(
                "action" to "action_select_season",
                "season" to season,
                "post" to datapost
            ), headers = mapOf(
                "Origin" to mainUrl,
                "Referer" to url,
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
            )).document
            response.select("a").map {
                val link = it.attr("href")
                val aa = link.replace("-capitulo-","x")
                val regexseasonepnum = Regex("((\\d+)x(\\d+))")
                val test = regexseasonepnum.find(aa)?.destructured?.component1() ?: ""
                val seasonid = test.let { str ->
                    str.split("x").mapNotNull { subStr -> subStr.toIntOrNull() }
                }
                val isValid = seasonid.size == 2
                val episode = if (isValid) seasonid.getOrNull(1) else null
                val seasonint = if (isValid) seasonid.getOrNull(0) else null
                episodes.add(newEpisode(link){
                    this.season = seasonint
                    this.episode = episode
                })
            }
        }
        val recommendations =
            soup.select(".serie.sm").mapNotNull { element ->
                val recTitle = element.selectFirst(".entry-title")!!.text() ?: return@mapNotNull null
                var image = element.selectFirst(".post-thumbnail figure img")!!.attr("data-lazy-src")
                if (image.isEmpty())   {
                    image = element.selectFirst(".post-thumbnail figure img")!!.attr("src")
                }
                val recUrl = fixUrl(element.selectFirst("a.lnk-blk")!!.attr("href"))
                newTvSeriesSearchResponse(recTitle, recUrl, TvType.Movie){
                    this.posterUrl = fixUrl(image)
                }
            }

        return when (tvType) {
            TvType.TvSeries -> {
                newTvSeriesLoadResponse(title,
                    url, TvType.TvSeries, episodes,){
                    this.posterUrl = fixUrl(poster ?: "")
                    this.plot = description
                    this.backgroundPosterUrl = fixUrl(backposter ?: "")
                    this.year = year
                    this.tags = tags
                    this.recommendations = recommendations
                }
            }

            TvType.Movie -> {
                newMovieLoadResponse(title, url, tvType, url) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = fixUrl(backposter ?: "")
                    this.plot = description
                    this.tags = tags
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
        val soup = app.get(data).document
        val dataop = soup.select("aside#aa-options div.video").mapNotNull { fixUrlNull(it.select("iframe").attr("data-src")) }.toList()
        dataop.amap { framelink ->
                val response = app.get(framelink, headers = mapOf(
                    "Referer" to data,
                )).document
                val trueembedlink = response.select(".Video iframe").attr("src")
                loadExtractor(trueembedlink, subtitleCallback, callback)

        }
        return true
    }
}