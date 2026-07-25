package com.lagradost.cloudstream3.movieproviders

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.stormunblessed.APIMatch
import com.stormunblessed.MatchesResult
import com.stormunblessed.SourceResult
import com.stormunblessed.defaultPoster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jsoup.nodes.Element

class StreamedProvider : MainAPI() {
    override var mainUrl = "https://streamed.pk"
    override var name = "Streamed Sports"
    override var lang = "en"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Live,
    )

    override val mainPage = mainPageOf(
        "/api/matches/live/popular" to "Live Popular",
        "/api/matches/live" to "Live",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val res = app.get("$mainUrl${request.data}")
        val document = res.parsed<MatchesResult>()
        val home = document.map { it.toSearchResult() }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = false
        )
    }

    private fun APIMatch.toSearchResult(): SearchResponse {
        val title = this.title
        val href = this.toJson()
        val posterUrl = if (this.poster != null) "$mainUrl${this.poster}" else defaultPoster
        return newLiveSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

//    override suspend fun search(query: String): List<SearchResponse> {
//        val document = app.get("${mainUrl}/?s=$query").document
//        val results =
//            document.select("div.container div.card__cover").mapNotNull { it.toSearchResult() }
//        return results
//    }

    override suspend fun load(url: String): LoadResponse? {
        val info = AppUtils.parseJson<APIMatch>(url)
        return newLiveStreamLoadResponse(info.title, url, url) {
            this.posterUrl = if (info.poster != null) "$mainUrl${info.poster}" else defaultPoster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val info = AppUtils.parseJson<APIMatch>(data)
        info.sources.amap {
            val doc = app.get("$mainUrl/api/stream/${it.source}/${it.id}").parsed<SourceResult>()
            val sorted = doc.sortedByDescending { it.language.equals("Spanish")  || it.hd}
            sorted.amap {
                val lang = if (it.language.isNotBlank()) "-${it.language}" else it.language
                loadSourceNameExtractor(
                    "${it.source}$lang",
                    it.embedUrl,
                    "$mainUrl/",
                    subtitleCallback,
                    callback
                )
            }
        }
        return true
    }
}

suspend fun loadSourceNameExtractor(
    source: String,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
) {
    loadExtractor(url, referer, subtitleCallback) { link ->
        CoroutineScope(Dispatchers.IO).launch {
            callback.invoke(
                newExtractorLink(
                    "$source[${link.source}]",
                    "$source[${link.source}]",
                    link.url,
                ) {
                    this.quality = link.quality
                    this.type = link.type
                    this.referer = link.referer
                    this.headers = link.headers
                    this.extractorData = link.extractorData
                }
            )
        }
    }
}