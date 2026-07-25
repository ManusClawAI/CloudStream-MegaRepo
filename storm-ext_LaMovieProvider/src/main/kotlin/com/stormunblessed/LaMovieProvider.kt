package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LaMovieProvider : MainAPI() {
    override var mainUrl = "https://lamovie.org"
    override var name = "LaMovie"
    override var lang = "mx"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
    )

    private val apiUrl = "$mainUrl/wp-api/v1"
    private val imageUrl = "$mainUrl/wp-content/uploads"

    override val mainPage = mainPageOf(
        "movies&orderBy=latest" to "Peliculas",
        "movies&orderBy=popular" to "Peliculas: Populares",
        "tvshows&orderBy=latest" to "Series",
        "tvshows&orderBy=popular" to "Series: Populares",
        "animes&orderBy=latest" to "Animes",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val postType = request.data.substringBefore("&")
        val orderBy = request.data.substringAfter("&orderBy=", "latest")
        val res = app.get(
            "$apiUrl/listing/$postType?page=$page&orderBy=$orderBy&order=desc&postType=$postType&postsPerPage=20"
        ).parsedSafe<LaMovieListingResponse>()
        val posts = res?.data?.posts ?: emptyList()
        val hasNext = (res?.data?.pagination?.currentPage ?: 1) < (res?.data?.pagination?.lastPage ?: 1)
        val home = posts.mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            list = HomePageList(request.name, home, isHorizontalImages = false),
            hasNext = hasNext
        )
    }

    private fun LaMoviePost.toSearchResult(): SearchResponse? {
        val title = this.title ?: return null
        val slug = this.slug ?: return null
        val pathSlug = when (this.type) {
            "tvshows" -> "series"
            "animes" -> "animes"
            "novels" -> "novelas"
            else -> "peliculas"
        }
        val href = "$mainUrl/$pathSlug/$slug"
        val posterUrl = this.images?.poster?.let { "$imageUrl$it" }
        val type = when (this.type) {
            "tvshows" -> TvType.TvSeries
            "animes" -> TvType.Anime
            else -> TvType.Movie
        }
        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val res = app.get("$apiUrl/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&postType=any&postsPerPage=26")
            .parsedSafe<LaMovieListingResponse>()
        return res?.data?.posts?.mapNotNull { it.toSearchResult() } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val slug = url.substringAfterLast("/")
        val typeSlug = url.substringAfter("$mainUrl/").substringBefore("/")
        val postType = when (typeSlug) {
            "series" -> "tvshows"
            "animes" -> "animes"
            "novelas" -> "novels"
            else -> "movies"
        }
        val res = app.get("$apiUrl/single/$postType?slug=$slug&postType=$postType")
            .parsedSafe<LaMovieSingleResponse>() ?: return null
        val post = res.data ?: return null
        val postId = post.id ?: return null
        val title = post.title ?: return null
        val poster = post.images?.poster?.let { "$imageUrl$it" }
        val backdrop = post.images?.backdrop?.let { "$imageUrl$it" }
        val plot = post.overview
        val year = post.releaseDate?.substringBefore("-")?.toIntOrNull()
        val trailer = post.trailer?.let { "https://www.youtube.com/watch?v=$it" }
        val isTvShow = postType == "tvshows" || postType == "animes" || postType == "novels"

        return if (isTvShow) {
            val episodes = mutableListOf<Episode>()
            val firstSeasonRes = app.get(
                "$apiUrl/single/episodes/list?_id=$postId&season=1&page=1&postsPerPage=200"
            ).parsedSafe<LaMovieEpisodesResponse>()
            val seasons = firstSeasonRes?.data?.seasons ?: listOf("1")
            firstSeasonRes?.data?.posts?.forEach { ep ->
                episodes.add(
                    newEpisode("$postId|${ep.id ?: postId}") {
                        this.name = ep.title
                        this.season = ep.seasonNumber ?: 1
                        this.episode = ep.episodeNumber
                        this.posterUrl = ep.stillPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                        this.description = ep.overview
                    }
                )
            }
            for (seasonNum in seasons) {
                if (seasonNum == "1") continue
                val episodesRes = app.get(
                    "$apiUrl/single/episodes/list?_id=$postId&season=$seasonNum&page=1&postsPerPage=200"
                ).parsedSafe<LaMovieEpisodesResponse>()
                episodesRes?.data?.posts?.forEach { ep ->
                    episodes.add(
                        newEpisode("$postId|${ep.id ?: postId}") {
                            this.name = ep.title
                            this.season = ep.seasonNumber ?: seasonNum.toIntOrNull()
                            this.episode = ep.episodeNumber
                            this.posterUrl = ep.stillPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                            this.description = ep.overview
                        }
                    )
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop ?: poster
                this.plot = plot
                this.year = year
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, "$postId") {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop ?: poster
                this.plot = plot
                this.year = year
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val postId = if (data.contains("|")) data.substringAfter("|") else data
        val playerRes = app.get("$apiUrl/player?postId=$postId&demo=0")
            .parsedSafe<LaMoviePlayerResponse>() ?: return false
        val embeds = playerRes.data?.embeds?.takeIf { it.isNotEmpty() } ?: return false

        embeds.amap { embed ->
            val embedUrl = embed.url?.let { if (it.startsWith("http")) it else "$mainUrl$it" } ?: return@amap
            val lang = embed.lang ?: "Unknown"
            val server = embed.server ?: "Online"
            loadSourceNameExtractor(
                "$lang[$server]",
                embedUrl,
                "$mainUrl/",
                subtitleCallback,
                callback
            )
        }
        return true
    }

    data class LaMovieListingResponse(
        @JsonProperty("error") val error: Boolean?,
        @JsonProperty("data") val data: LaMovieListingData?
    )

    data class LaMovieListingData(
        @JsonProperty("posts") val posts: List<LaMoviePost>?,
        @JsonProperty("pagination") val pagination: LaMoviePagination?
    )

    data class LaMoviePost(
        @JsonProperty("_id") val id: Int?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("original_title") val originalTitle: String?,
        @JsonProperty("slug") val slug: String?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("overview") val overview: String?,
        @JsonProperty("release_date") val releaseDate: String?,
        @JsonProperty("images") val images: LaMovieImages?,
        @JsonProperty("trailer") val trailer: String?,
        @JsonProperty("rating") val rating: String?,
    )

    data class LaMovieImages(
        @JsonProperty("poster") val poster: String?,
        @JsonProperty("backdrop") val backdrop: String?
    )

    data class LaMoviePagination(
        @JsonProperty("current_page") val currentPage: Int?,
        @JsonProperty("last_page") val lastPage: Int?
    )

    data class LaMovieSingleResponse(
        @JsonProperty("error") val error: Boolean?,
        @JsonProperty("data") val data: LaMoviePost?
    )

    data class LaMovieEpisodesResponse(
        @JsonProperty("error") val error: Boolean?,
        @JsonProperty("data") val data: LaMovieEpisodesData?
    )

    data class LaMovieEpisodesData(
        @JsonProperty("posts") val posts: List<LaMovieEpisode>?,
        @JsonProperty("seasons") val seasons: List<String>?
    )

    data class LaMovieEpisode(
        @JsonProperty("_id") val id: Int?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("season_number") val seasonNumber: Int?,
        @JsonProperty("episode_number") val episodeNumber: Int?,
        @JsonProperty("still_path") val stillPath: String?,
        @JsonProperty("overview") val overview: String?
    )

    data class LaMoviePlayerResponse(
        @JsonProperty("error") val error: Boolean?,
        @JsonProperty("data") val data: LaMoviePlayerData?
    )

    data class LaMoviePlayerData(
        @JsonProperty("embeds") val embeds: List<LaMovieEmbed>?
    )

    data class LaMovieEmbed(
        @JsonProperty("url") val url: String?,
        @JsonProperty("server") val server: String?,
        @JsonProperty("lang") val lang: String?,
        @JsonProperty("quality") val quality: String?
    )
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
