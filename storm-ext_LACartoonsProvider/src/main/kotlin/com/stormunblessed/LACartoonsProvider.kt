package com.stormunblessed

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class LACartoonsProvider:MainAPI() {
    override var mainUrl = "https://www.lacartoons.com"
    override var name = "LACartoons"
    override var lang = "mx"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Cartoon,
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        "/?Categoria_id=1" to "Nickelodeon",
        "/?Categoria_id=2" to "Cartoon Network",
        "/?Categoria_id=3" to "Fox Kids",
        "/?Categoria_id=4" to "Hanna Barbera",
        "/?Categoria_id=5" to "Disney",
        "/?Categoria_id=6" to "Warner Channel",
        "/?Categoria_id=7" to "Marvel",
        "/?Categoria_id=8" to "Otros",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}&page=$page").document
        val home = document.select(".categorias .conjuntos-series a")
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
        val title = this.selectFirst("p.nombre-serie")?.text() ?: "No title"
        val href = fixUrl(this.attr("href"))
        val posterUrl = fixUrl(this.selectFirst("img")!!.attr("src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?Titulo=$query").document
        val results =
            document.select(".categorias .conjuntos-series a").mapNotNull { it.toSearchResult() }
        return results
    }

    override suspend fun load(url: String): LoadResponse? {
        val regexep = Regex("Capitulo.(\\d+)|Capitulo.(\\d+)\\-")
        val doc = app.get(url).document
        val title = doc.selectFirst("h2.text-center")?.ownText()
        val tags = doc.selectFirst("h2.text-center span")?.text()?.let { listOf<String>(it) } ?: emptyList()
        val description = doc.selectFirst(".informacion-serie-seccion p:contains(Reseña)")?.text()?.substringAfter("Reseña:")?.trim()
        val poster = doc.selectFirst(".imagen-serie img")?.attr("src")
        val backposter = doc.selectFirst("img.fondo-serie-seccion")?.attr("src")
        val episodes = doc.select("ul.listas-de-episodion li").map {
            val href = it.selectFirst("a")?.attr("href")
            val title = it.selectFirst("a")?.text()
            val name = title?.substringAfter("- ")
            val seasonnum = href?.substringAfter("t=")
            val epnum = regexep.find(title!!)?.destructured?.component1()
            newEpisode(
                fixUrl(href!!),
            ){
                this.name = name
                this.season = seasonnum.toString().toIntOrNull()
                this.episode = epnum.toString().toIntOrNull()
            }
        }
        val recommendations = doc.select(".series-recomendadas a").mapNotNull { it.toSearchResult() }
        return newTvSeriesLoadResponse(title!!, url, TvType.Cartoon, episodes){
            this.posterUrl = fixUrl(poster!!)
            this.backgroundPosterUrl = fixUrl(backposter!!)
            this.plot = description
            this.recommendations = recommendations
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = app.get(data).document
        res.select(".serie-video-informacion iframe").map {
            val link = it.attr("src")?.replace("https://short.ink/","https://abysscdn.com/?v=")
            loadExtractor(fixHostsLinks(link!!), data, subtitleCallback, callback)
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