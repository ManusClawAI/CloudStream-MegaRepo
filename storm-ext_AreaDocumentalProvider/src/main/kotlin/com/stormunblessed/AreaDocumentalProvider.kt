package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class AreaDocumentalProvider : MainAPI() {
    override var mainUrl = "https://www.area-documental.com"
    override var name = "Area Documental"
    override var lang = "mx"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Documentary,
        TvType.Movie,
        TvType.TvSeries,
    )

    override val mainPage = mainPageOf(
        "" to "Destacados",
        "reciente" to "Recientes",
        "titulo" to "Por Titulo",
        "visto" to "Mas Vistos",
        "anio" to "Por Año",
    )

    private fun getResultsUrl(sort: String, page: Int, query: String = ""): String {
        val pageParam = if (page > 0) "&page=$page" else ""
        return when (sort) {
            "reciente" -> "$mainUrl/resultados-reciente.php?buscar=$query$pageParam"
            "titulo" -> "$mainUrl/resultados-titulo.php?buscar=$query$pageParam"
            "visto" -> "$mainUrl/resultados-visto.php?buscar=$query$pageParam"
            "anio" -> "$mainUrl/resultados-anio.php?buscar=$query$pageParam"
            else -> "$mainUrl/resultados.php?buscar=$query$pageParam"
        }
    }

    private fun getImageUrl(img: org.jsoup.nodes.Element): String? {
        val src = img.attr("src").ifEmpty { img.attr("data-src") }
        return if (src.isNotEmpty()) {
            if (src.startsWith("http")) src else "$mainUrl$src"
        } else null
    }

    private fun getFullUrl(href: String): String {
        return if (href.startsWith("http")) href else "$mainUrl$href"
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = getResultsUrl(request.data, page - 1)
        val document = app.get(url).document
        val items = document.select("div.wthree-news-left").mapNotNull { element ->
            val titleElement = element.selectFirst("h2 a") ?: return@mapNotNull null
            val title = titleElement.text().trim()
            val href = getFullUrl(titleElement.attr("href"))
            val poster = element.selectFirst("div.imagen img")?.let { getImageUrl(it) }
            val yearText = element.selectFirst("h2")?.nextElementSibling()?.text() ?: ""
            val year = Regex("(\\d{4})").find(yearText)?.value?.toIntOrNull()
            newMovieSearchResponse(title, href, TvType.Documentary) {
                this.posterUrl = poster
                this.year = year
            }
        }
        val hasNext = document.selectFirst("a[href*='page=${page}']") != null
        return newHomePageResponse(
            list = HomePageList(request.name, items, isHorizontalImages = false),
            hasNext = hasNext
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/resultados.php?buscar=${java.net.URLEncoder.encode(query, "UTF-8")}"
        val document = app.get(url).document
        return document.select("div.wthree-news-left").mapNotNull { element ->
            val titleElement = element.selectFirst("h2 a") ?: return@mapNotNull null
            val title = titleElement.text().trim()
            val href = getFullUrl(titleElement.attr("href"))
            val poster = element.selectFirst("div.imagen img")?.let { getImageUrl(it) }
            newMovieSearchResponse(title, href, TvType.Documentary) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("meta[itemprop='name']")?.attr("content")?.trim()
            ?: document.select("h1").lastOrNull()?.text()?.trim()
            ?: return null
        val poster = document.selectFirst("div#imagen img")?.let { getImageUrl(it) }
        val description = document.selectFirst("div.comments")?.text()?.trim()
        val yearMeta = document.selectFirst("meta[itemprop='dateCreated']")
        val year = yearMeta?.attr("content")?.take(4)?.toIntOrNull()
        val duration = document.selectFirst("time[itemprop='duration']")?.attr("datetime")
        val genre = document.selectFirst("meta[itemprop='genre']")?.attr("content")

        val seriesLinks = document.select("a[href*='resultados-serie.php']")
        val seriesLink = seriesLinks.firstOrNull {
            val href = it.attr("href")
            href.contains("buscar=") && href.substringAfter("buscar=").isNotEmpty()
        }
        val seriesName = seriesLink?.text()?.trim()
        val isSeries = !seriesName.isNullOrEmpty()

        return if (isSeries) {
            val seriesUrl = "$mainUrl/resultados-serie.php?buscar=${java.net.URLEncoder.encode(seriesName!!, "UTF-8")}"
            val seriesDoc = app.get(seriesUrl).document
            val episodes = seriesDoc.select("div.wthree-news-left").mapNotNull { element ->
                val epTitle = element.selectFirst("h2 a")?.text()?.trim() ?: return@mapNotNull null
                val epHref = getFullUrl(element.selectFirst("h2 a")?.attr("href") ?: return@mapNotNull null)
                val epPoster = element.selectFirst("div.imagen img")?.let { getImageUrl(it) }
                newEpisode(epHref) {
                    this.name = epTitle
                    this.posterUrl = epPoster
                    this.episode = Regex("Ep\\s*(\\d+)").find(epTitle)?.groupValues?.get(1)?.toIntOrNull()
                }
            }
            if (episodes.isEmpty()) {
                newMovieLoadResponse(title, url, TvType.Documentary, url) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = poster
                    this.plot = description
                    this.year = year
                    this.tags = genre?.let { listOf(it) }
                    this.duration = duration?.let { parseDuration(it) }
                }
            } else {
                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = poster
                    this.plot = description
                    this.year = year
                    this.tags = genre?.let { listOf(it) }
                }
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Documentary, url) {
                this.posterUrl = poster
                this.backgroundPosterUrl = poster
                this.plot = description
                this.year = year
                this.tags = genre?.let { listOf(it) }
                this.duration = duration?.let { parseDuration(it) }
            }
        }
    }

    private fun parseDuration(duration: String): Int? {
        val regex = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?")
        val match = regex.find(duration) ?: return null
        val hours = match.groupValues[1].toIntOrNull() ?: 0
        val minutes = match.groupValues[2].toIntOrNull() ?: 0
        val seconds = match.groupValues[3].toIntOrNull() ?: 0
        return hours * 3600 + minutes * 60 + seconds
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val titulo = data.substringAfter("titulo=").substringBefore("&")
        val videoReferer = "$mainUrl/video/$titulo/"
        
        val response = app.get(data)
        val document = response.document
        val cookies = response.headers.values("Set-Cookie")
        val phpSessionId = cookies.firstOrNull { it.contains("PHPSESSID") }
            ?.substringAfter("PHPSESSID=")?.substringBefore(";")

        val scriptContent = document.select("script").firstOrNull { script ->
            script.html().contains("jwplayer('myElement').setup")
        }?.html() ?: return false

        val videoUrl = Regex("""file:\s*"([^"]+)"""").find(scriptContent)?.groupValues?.get(1)

        if (videoUrl != null) {
            val headerMap = mutableMapOf(
                "Referer" to videoReferer,
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            if (phpSessionId != null) {
                headerMap["Cookie"] = "PHPSESSID=$phpSessionId"
            }
            
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = videoUrl,
                ) {
                    this.referer = videoReferer
                    this.quality = Qualities.P720.value
                    this.headers = headerMap
                }
            )
        }

        val subtitleRegex = Regex("""file:\s*"([^"]+\.srt[^"]*)".*?label:\s*"([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
        subtitleRegex.findAll(scriptContent).forEach { match ->
            val subUrl = match.groupValues[1]
            val subLang = match.groupValues[2]
            val fullSubUrl = if (subUrl.startsWith("http")) subUrl else "$mainUrl$subUrl"
            subtitleCallback.invoke(
                SubtitleFile(
                    lang = subLang,
                    url = fullSubUrl,
                )
            )
        }

        return true
    }
}
