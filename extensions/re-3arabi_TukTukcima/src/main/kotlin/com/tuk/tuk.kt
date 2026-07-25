package com.tuktukhd

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

import java.net.URLEncoder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
class TukTukHd : MainAPI() {
    override var mainUrl = "https://tuktukhd.com"
    override var name = "TukTukcima"
    override val hasMainPage = true
    override var lang = "ar"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    override val mainPage = mainPageOf(
        "$mainUrl/recent/page/" to "المضاف حديثاً",
        "$mainUrl/category/movies-2/page/" to "أحدث الأفلام",
        "$mainUrl/category/series-1/page/" to "أحدث الحلقات",
        "$mainUrl/category/movies-2/%d8%a7%d9%81%d9%84%d8%a7%d9%85-%d9%85%d8%af%d8%a8%d9%84%d8%ac%d8%a9/page/" to "أفلام مدبلجة"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page
        val document = app.get(url).document


        val home = document.select("li.Small--Box, div.Block--Item").mapNotNull {
            toSearchResult(it)
        }
        return newHomePageResponse(request.name, home)
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val linkTag = element.selectFirst("a") ?: return null
        val title = element.selectFirst(".title")?.text() ?: linkTag.attr("title")
        val href = fixUrl(linkTag.attr("href"))

        val imgTag = element.selectFirst("img")
        val posterUrl = imgTag?.attr("data-src").takeIf { !it.isNullOrEmpty() }
            ?: imgTag?.attr("src")

        val isMovie =
            !title.contains("مسلسل") && !title.contains("حلقة") && !href.contains("series")

        return if (isMovie) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }
    }

        override suspend fun search(query: String): List<SearchResponse> {
            return search(query, 1)?.items ?: emptyList()
        }

    override suspend fun search(query: String, page: Int): SearchResponseList? = coroutineScope {

        val encoded = URLEncoder.encode(query, "utf-8")

        val page1 = (page - 1) * 2 + 1
        val page2 = page1 + 1

        val urls = listOf(
            "$mainUrl/?s=$encoded&page=$page1",
            "$mainUrl/?s=$encoded&page=$page2"
        )

        val results = urls.map { url ->
            async {
                runCatching {
                    val doc = app.get(url).document
                    doc.select("div.Block--Item, li.Small--Box")
                        .mapNotNull { toSearchResult(it) }
                }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten()

        val cleaned = mergeSimilarResults(results)

        newSearchResponseList(cleaned, cleaned.isNotEmpty())
    }
    private fun mergeSimilarResults(list: List<SearchResponse>): List<SearchResponse> {

        val grouped = mutableMapOf<String, SearchResponse>()

        for (item in list) {

            val title = item.name

            if (title.contains("فيلم", true) ||
                title.contains("فلم", true) ||
                title.contains("movie", true)
            ) {
                grouped[title] = item
                continue
            }

            val normalized = title
                .replace(Regex("""الحلقة\s*\d+"""), "")
                .replace(Regex("""episode\s*\d+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\d+$"""), "")
                .trim()

            if (!grouped.containsKey(normalized)) {
                grouped[normalized] = item
            }
        }

        return grouped.values.toList()
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val fullTitle = doc.selectFirst("h1.post-title a")?.text() ?: doc.selectFirst("h1")?.text() ?: "Unknown"

        val cleanTitle = fullTitle.replace(Regex("""\s*(الحلقة\s*\d+|مترجم|مدبلج).*"""), "").trim()

        val desc = doc.select(".story p").text()
        val poster = doc.selectFirst(".MainSingle .left .image img")?.attr("src")
        val bgPoster = doc.selectFirst(".homepage__bg")?.attr("style")?.substringAfter("url(")?.substringBefore(")") ?: poster

        val year = doc.select(".RightTaxContent a[href*='release-year']").text().filter { it.isDigit() }.toIntOrNull()
        val ratingText = doc.select(".imdbS strong").text()
        val ratingInt = ratingText.toDoubleOrNull()?.times(1000)?.toInt()


        val isSeries = doc.select(".allepcont, .allseasonss").isNotEmpty()

        if (isSeries) {

            val episodesList = ArrayList<Episode>()
            val seasonElements = doc.select(".allseasonss .Block--Item a")

            if (seasonElements.isNotEmpty()) {

                seasonElements.apmap { seasonEl ->
                    val seasonUrl = fixUrl(seasonEl.attr("href"))
                    val seasonName = seasonEl.select("h3").text()
                    val seasonNum = seasonName.filter { it.isDigit() }.toIntOrNull() ?: 1

                    val seasonDoc = app.get(seasonUrl).document
                    seasonDoc.select(".allepcont a").forEach { ep ->
                        val epTitle = ep.select(".ep-info h2").text()
                        val epHref = fixUrl(ep.attr("href"))
                        val epNum = ep.select(".epnum").text().filter { it.isDigit() }.toIntOrNull()
                        val epThumb = ep.select("img").attr("data-src").ifEmpty { ep.select("img").attr("src") }

                        episodesList.add(
                            newEpisode(epHref) {
                                this.name = epTitle
                                this.episode = epNum
                                this.season = seasonNum
                                this.posterUrl = epThumb
                            }
                        )
                    }
                }
            } else {

                doc.select(".allepcont a").forEach { ep ->
                    val epTitle = ep.select(".ep-info h2").text()
                    val epHref = fixUrl(ep.attr("href"))
                    val epNum = ep.select(".epnum").text().filter { it.isDigit() }.toIntOrNull()
                    val epThumb = ep.select("img").attr("data-src").ifEmpty { ep.select("img").attr("src") }

                    episodesList.add(
                        newEpisode(epHref) {
                            this.name = epTitle
                            this.episode = epNum
                            this.season = 1
                            this.posterUrl = epThumb
                        }
                    )
                }
            }

            val sortedEpisodes = episodesList.sortedWith(compareBy({ it.season }, { it.episode }))

            return newTvSeriesLoadResponse(cleanTitle, url, TvType.TvSeries, sortedEpisodes) {
                this.posterUrl = poster
                this.plot = desc
                this.year = year
                this.rating = ratingInt
            }

        } else {


            return newMovieLoadResponse(cleanTitle, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = desc
                this.year = year
                this.rating = ratingInt
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val iframeCrypt = doc.select("iframe#main-video-frame").attr("data-crypt")

        if (iframeCrypt.isEmpty()) return false

        try {
            val playerUrl = String(Base64.decode(iframeCrypt, Base64.DEFAULT))

            val initialResponse = app.get(
                playerUrl,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36",
                    "Referer" to mainUrl
                )
            )

            val cookies = initialResponse.cookies
            val xsrfToken = cookies["XSRF-TOKEN"]?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            val version = Regex(""""version":"([^"]+)"""").find(initialResponse.text)?.groupValues?.get(1)

            if (xsrfToken != null && version != null) {

                val inertiaHeaders = mapOf(
                    "X-XSRF-TOKEN" to xsrfToken,
                    "X-Inertia" to "true",
                    "X-Inertia-Version" to version,
                    "X-Inertia-Partial-Component" to "files/mirror/video",
                    "X-Inertia-Partial-Data" to "streams", // هذا يسرع الطلب جداً لأنه يطلب الروابط فقط
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to playerUrl,
                    "Content-Type" to "application/json"
                )

                val inertiaResponse = app.get(
                    playerUrl,
                    headers = inertiaHeaders,
                    cookies = cookies
                ).parsed<InertiaResponse>()

                val allLinks = inertiaResponse.props.streams?.data?.flatMap { stream ->
                    stream.mirrors?.mapNotNull { mirror ->
                        mirror.link?.let { link ->
                            if (link.startsWith("//")) "https:$link" else link
                        }
                    } ?: emptyList()
                } ?: emptyList()

                allLinks.apmap { link ->
                    loadExtractor(link, subtitleCallback, callback)
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return true
    }

    data class InertiaResponse(val props: InertiaProps)
    data class InertiaProps(val streams: StreamsData?)
    data class StreamsData(val data: List<StreamItem>?)
    data class StreamItem(val mirrors: List<MirrorItem>?)
    data class MirrorItem(val link: String?)
}