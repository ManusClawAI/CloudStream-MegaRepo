package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class DocumaniaTVProvider : MainAPI() {
    override var mainUrl = "https://www.documaniatv.com"
    override var name = "DocumaniaTV"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Documentary,
        TvType.Movie,
    )

    override val mainPage = mainPageOf(
        "" to "Novedades",
        "top" to "Mas Vistos",
        "series" to "Series",
    )

    private fun getImageUrl(path: String): String {
        return if (path.startsWith("http")) path else "$mainUrl$path"
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = when (request.data) {
            "top" -> "$mainUrl/top-documentales.html"
            "series" -> if (page <= 1) "$mainUrl/top-series-documentales-1.html" else "$mainUrl/top-series-documentales-$page.html"
            else -> if (page <= 1) "$mainUrl/documentales-nuevos.html" else "$mainUrl/documentales-nuevos.html?&page=$page"
        }
        val document = app.get(url, headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )).document
        val items = if (request.data == "series") {
            document.select("a[href*='/documentales/']").filter { element ->
                element.selectFirst(".pm-video-thumb") != null
            }.mapNotNull { element ->
                val href = element.attr("abs:href")
                if (href.isEmpty() || !href.contains("/documentales/")) return@mapNotNull null
                val title = element.selectFirst("h3")?.text()?.trim()
                    ?: element.attr("title").removePrefix("Serie documental ").trim()
                val poster = element.selectFirst("img")?.let { img ->
                    val src = img.attr("src").ifEmpty { img.attr("data-src") }
                    if (src.isNotEmpty()) getImageUrl(src) else null
                }
                newMovieSearchResponse(title, href, TvType.Documentary) {
                    this.posterUrl = poster
                }
            }
        } else {
            document.select(".pm-video-thumb").mapNotNull { element ->
                val link = element.selectFirst("a") ?: return@mapNotNull null
                val href = link.attr("abs:href")
                if (href.isEmpty()) return@mapNotNull null
                val title = link.attr("title").ifEmpty { link.selectFirst("img")?.attr("alt") ?: link.text().trim() }
                val poster = link.selectFirst("img")?.let { img ->
                    val src = img.attr("src").ifEmpty { img.attr("data-src") }
                    if (src.isNotEmpty()) getImageUrl(src) else null
                }
                newMovieSearchResponse(title, href, TvType.Documentary) {
                    this.posterUrl = poster
                }
            }
        }
        val hasNext = document.selectFirst("ul.pagination li.active + li a") != null
            || document.selectFirst("a[rel=next]") != null
            || document.selectFirst("ul.pagination.pagination-arrows li:last-child a") != null
        return newHomePageResponse(
            list = HomePageList(request.name, items, isHorizontalImages = true),
            hasNext = hasNext
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.post("$mainUrl/ajax_search.php",
            data = mapOf("queryString" to query),
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "X-Requested-With" to "XMLHttpRequest"
            )
        ).document
        return document.select("li").mapNotNull { element ->
            val link = element.selectFirst("a") ?: return@mapNotNull null
            val href = link.attr("abs:href")
            if (href.isEmpty() || !href.contains("video_")) return@mapNotNull null
            val title = link.text().trim()
            val videoId = element.attr("data-video-id").ifEmpty {
                href.substringAfter("video_").substringBefore(".")
            }
            val poster = "$mainUrl/uploads/thumbs/$videoId-1.webp"
            newMovieSearchResponse(title, href, TvType.Documentary) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )).document

        val isSeriesPage = url.contains("/documentales/") && !url.contains("video_")

        if (isSeriesPage) {
            val seriesTitle = document.selectFirst("h1")?.text()?.trim()
                ?: url.substringAfter("/documentales/").substringBefore("/").replace("-", " ").replaceFirstChar { it.uppercase() }
            val poster = document.selectFirst("img")?.let { img ->
                val src = img.attr("src").ifEmpty { img.attr("data-src") }
                if (src.isNotEmpty()) getImageUrl(src) else null
            }
            val seenUrls = mutableSetOf<String>()
            val episodes = document.select("h3 a[href*='video_']").reversed().mapIndexedNotNull { index, element ->
                val href = element.attr("abs:href")
                if (href.isEmpty() || !seenUrls.add(href)) return@mapIndexedNotNull null
                val epTitle = element.text().trim().ifEmpty { "Episode ${index + 1}" }
                val epVideoId = href.substringAfter("video_").substringBefore(".")
                val epPoster = "$mainUrl/uploads/thumbs/$epVideoId-1.webp"
                newEpisode(href) {
                    this.name = epTitle
                    this.episode = index + 1
                    this.posterUrl = epPoster
                }
            }
            if (episodes.isEmpty()) return null
            return newTvSeriesLoadResponse(seriesTitle, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = poster
            }
        }

        val videoDataScript = document.select("script").firstOrNull { script ->
            script.html().contains("pm_video_data")
        }?.html() ?: return null

        val videoId = Regex("""uniq_id:\s*"([^"]+)"""").find(videoDataScript)?.groupValues?.get(1) ?: return null
        val title = Regex("""title:\s*'([^']+)"""").find(videoDataScript)?.groupValues?.get(1)
            ?: document.selectFirst("h1 span")?.text()?.trim()
            ?: return null
        val category = Regex("""category_str:\s*"([^"]+)"""").find(videoDataScript)?.groupValues?.get(1)
        val poster = document.selectFirst("img[data-video_thumb_url]")?.attr("src")
            ?: document.selectFirst("div.pm-video-thumb img")?.attr("src")
            ?: "https://www.documaniatv.com/uploads/thumbs/$videoId-1.webp"
        val description = document.selectFirst("div.pm-video-description")?.text()?.trim()
        val year = Regex("""publish_date_str:\s*"(\d{4})""").find(videoDataScript)?.groupValues?.get(1)?.toIntOrNull()

        return newMovieLoadResponse(title, url, TvType.Documentary, url) {
            this.posterUrl = poster
            this.backgroundPosterUrl = poster
            this.plot = description
            this.year = year
            this.tags = category?.let { listOf(it) }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val pageResponse = app.get(data, headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        ))
        val document = pageResponse.document
        
        val videoDataScript = document.select("script").firstOrNull { script ->
            script.html().contains("pm_video_data")
        }?.html() ?: return false

        val videoId = Regex("""uniq_id:\s*"([^"]+)"""").find(videoDataScript)?.groupValues?.get(1) ?: return false

        val embedResponse = app.get("https://www.documaniatv.com/embed/$videoId", headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        ))
        val embedCookies = embedResponse.headers.filter { it.first.lowercase() == "set-cookie" }
            .map { it.second.substringBefore(";") }
            .joinToString("; ")

        val jsonResponse = app.get("https://www.documaniatv.com/json/$videoId",
            headers = mapOf(
                "Referer" to "https://www.documaniatv.com/embed/$videoId",
                "X-Requested-With" to "XMLHttpRequest",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Cookie" to embedCookies
            )
        ).text

        var videoUrl = Regex(""""src"\s*:\s*"([^"]+)"""").find(jsonResponse)?.groupValues?.get(1)
            ?: Regex("""file:\s*"([^"]+)"""").find(jsonResponse)?.groupValues?.get(1)
            ?: Regex("""https?://[^\s"']+\.mp4[^\s"']*""").find(jsonResponse)?.value

        if (videoUrl == null) {
            val playerJsResponse = app.get("https://www.documaniatv.com/docuplayer/v1/$videoId.js",
                headers = mapOf(
                    "Referer" to "https://www.documaniatv.com/embed/$videoId",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Cookie" to embedCookies
                )
            ).text
            videoUrl = Regex(""""src"\s*:\s*"([^"]+)"""").find(playerJsResponse)?.groupValues?.get(1)
                ?: Regex("""file:\s*"([^"]+)"""").find(playerJsResponse)?.groupValues?.get(1)
                ?: Regex("""https?://[^\s"']+\.mp4[^\s"']*""").find(playerJsResponse)?.value
        }

        if (videoUrl != null) {
            videoUrl = videoUrl.replace("\\/", "/")
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = videoUrl,
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.P720.value
                    this.headers = mapOf(
                        "Referer" to "$mainUrl/",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )
                }
            )
        }

        return videoUrl != null
    }
}
