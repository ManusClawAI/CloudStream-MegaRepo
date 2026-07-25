package com.bnyro

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

open class Southpark : MainAPI() {
    override var name = "Southpark"
    override var mainUrl = "https://www.southpark.de"
    open val streamsUrl = "https://topaz.viacomcbs.digital"
    override val supportedTypes = setOf(TvType.TvSeries)

    override val hasMainPage: Boolean = true
    override val mainPage: List<MainPageData> = mainPageOf(
        "25eeb8de-ed8e-11e0-aca6-0026b9414f30" to "Season 1",
        "25eeb97e-ed8e-11e0-aca6-0026b9414f30" to "Season 2",
        "25eeba14-ed8e-11e0-aca6-0026b9414f30" to "Seasons 3",
        "25eebaaa-ed8e-11e0-aca6-0026b9414f30" to "Season 4",
        "25eebb4a-ed8e-11e0-aca6-0026b9414f30" to "Season 5",
        "25eebbea-ed8e-11e0-aca6-0026b9414f30" to "Season 6",
        "25eebc76-ed8e-11e0-aca6-0026b9414f30" to "Season 7",
        "25eebd16-ed8e-11e0-aca6-0026b9414f30" to "Season 8",
        "25eec14e-ed8e-11e0-aca6-0026b9414f30" to "Season 9",
        "25eebdac-ed8e-11e0-aca6-0026b9414f30" to "Season 10",
        "25eebe42-ed8e-11e0-aca6-0026b9414f30" to "Season 11",
        "25eebeec-ed8e-11e0-aca6-0026b9414f30" to "Season 12",
        "25eebf82-ed8e-11e0-aca6-0026b9414f30" to "Season 13",
        "25eec018-ed8e-11e0-aca6-0026b9414f30" to "Season 14",
        "25eec0b8-ed8e-11e0-aca6-0026b9414f30" to "Season 15",
        "dc5c66e0-4a7c-4e74-8ef0-dff684353b8e" to "Season 16",
        "63a32034-1ea6-492d-b95b-9433e3f62f8d" to "Season 17",
        "c6cbd5e3-7eae-4cc3-94b7-119c8d412f99" to "Season 18",
        "19eabca7-b00f-4a06-ac66-c9529cef2834" to "Season 19",
        "879fd28e-c96b-4f9d-a437-e05c1bcf80aa" to "Season 20",
        "fb76a7de-1c31-4186-abf9-2be2e3af4312" to "Season 21",
        "896b2aa3-ac17-11e8-b956-70df2f866ace" to "Season 22",
        "26f238f8-b354-11e9-9fb2-70df2f866ace" to "Season 23",
        "3ab1469d-f582-11ea-834d-70df2f866ace" to "Season 24",
        "f7f6dc67-7d50-11ec-a4f1-70df2f866ace" to "Season 25",
        "948aa4df-9036-11ed-ad8e-0e40cf2fc285" to "Season 26",
        "40a55f82-213c-11f0-b405-16fff45bc035" to "Season 27",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val resp =
            app.get("$mainUrl/en/api/context/mgid%3Aarc%3Aseason%3Asouthpark.intl%3A${request.data}/episode/1/1")
                .parsed<ContextResp>()

        return newHomePageResponse(request, resp.items.map { it.toSearchResponse() })
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val resp =
            app.get("$mainUrl/api/search?q=$query&activeTab=Episode&searchFilter=site&pageNumber=0&rowsPerPage=20")
                .parsed<SearchResp>()

        return resp.response.items.map { it.toSearchResponse() }
    }

    private fun Item.toSearchResponse(): SearchResponse {
        return newMovieSearchResponse(
            name = getTitle(meta),
            url = fixUrl(url),
            type = TvType.Movie,
        ) {
            this.posterUrl = fixUrl(media.image.url)
            this.year = meta.date.takeLast(4).toIntOrNull()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val resp = app.get(url).text
            .substringAfterLast("<script type=\"application/ld+json\">")
            .substringBefore("</script>")
            .let { parseJson<EpisodeInfo>(it) }

        return newMovieLoadResponse(
            name = resp.name,
            type = TvType.Movie,
            url = url,
            data = LinkData(resp.id).toJson(),
        ) {
            this.plot = resp.description

            if (resp.video != null) {
                this.posterUrl = fixUrl(resp.video.thumbnailUrl)
                this.year = resp.video.uploadDate.take(4).toIntOrNull()
            } else {
                // currently unavailable or not yet released
                this.comingSoon = true
            }
        }
    }

    private fun getTitle(meta: Meta): String {
        val text =
            meta.header.title as? String ?: (meta.header.title as Map<String, String>)["text"]!!

        return text + " " + meta.subHeader.orEmpty()
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // the url passed is the id of the episode
        val id = parseJson<LinkData>(data).id
        val resp =
            app.get("$streamsUrl/topaz/api/mgid:arc:episode:shared.southpark.gsa.en:$id/mica.json?clientPlatform=mobile")
                .parsed<StreamsResp>()

        callback.invoke(
            newExtractorLink(
                url = resp.stitchedstream.source,
                name = "Southpark HLS",
                source = "Southpark",
                type = ExtractorLinkType.M3U8
            )
        )

        return true
    }

    private data class SearchResp(
        val metadata: Metadata,
        val response: Response,
    )

    private data class Metadata(
        val startingRow: Long,
        val numFound: Long,
    )

    private data class Response(
        val items: List<Item>,
    )

    private data class Item(
        @JsonAlias("itemType")
        val type: String,
        val id: String,
        @JsonProperty("pk_id")
        val pkId: String?,
        val media: Media,
        val meta: Meta,
        val url: String,
    )

    private data class Media(
        val duration: String?,
        val image: Image,
    )

    private data class Image(
        val width: Long,
        val height: Long,
        val aspectRatio: Double,
        val url: String,
    )

    private data class Meta(
        val header: Header,
        val subHeader: String?,
        val date: String,
        val description: String?,
    )

    private data class Header(
        val title: Any,
    )

    private data class ContextResp(
        val isEpisodes: Boolean,
        val items: List<Item>
    )

    private data class StreamsResp(
        @JsonProperty("\$schema")
        val schema: String,
        val version: String,
        val documentid: String,
        val originationdate: String,
        val expirydate: String,
        val expirydateepoch: String,
        val stitchedstream: Stitchedstream,
        val content: List<Content>,
        val imasdk: Imasdk,
        val thumbnails: Thumbnails,
    )

    private data class Stitchedstream(
        val source: String,
        val iframe: Boolean,
        val adstitchingprovider: String,
        val cdn: String,
        val streamingtype: String,
        val minrenditionheight: Long,
        val maxrenditionheight: Long,
        val minrenditionbitrate: Long,
        val maxrenditionbitrate: Long,
        val renditioncount: Long,
        val manifesttype: String,
        val duration: String,
    )

    private data class Content(
        val id: String,
        val chapters: List<Chapter>,
        val duration: String,
    )

    private data class Chapter(
        val sequence: Long,
        val id: String,
        val contentoffset: String,
        val duration: String,
        val streamoffset: String,
    )

    private data class Imasdk(
        val cmsid: String,
        val vid: String,
        val authtoken: String,
        val freewheelurl: String,
        val podservingenabled: Boolean,
    )

    private data class Thumbnails(
        val webvtt: Webvtt,
    )

    private data class Webvtt(
        val aspectratio: String,
        val urltemplate: String,
        val small: String,
        val large: String,
    )

    private data class EpisodeInfo(
        @JsonProperty("@context")
        val context: String,
        @JsonProperty("@type")
        val type: String,
        @JsonProperty("@id")
        val id: String,
        val name: String,
        val description: String,
        val url: String,
        val episodeNumber: Long,
        val partOfSeason: PartOfSeason,
        val partOfSeries: PartOfSeries,
        val releasedEvent: ReleasedEvent,
        val video: Video?,
    )

    private data class PartOfSeason(
        @JsonProperty("@type")
        val type: String,
        val name: String,
        val seasonNumber: Long,
    )

    private data class PartOfSeries(
        @JsonProperty("@type")
        val type: String,
        @JsonProperty("@id")
        val id: String,
        val name: String,
        val url: String,
        val description: String,
        val sameAs: List<String>,
    )

    private data class ReleasedEvent(
        @JsonProperty("@type")
        val type: String,
        val startDate: String,
        val location: Location,
    )

    private data class Location(
        @JsonProperty("@type")
        val type: String,
    )

    private data class Video(
        @JsonProperty("@type")
        val type: String,
        val name: String,
        val description: String,
        val duration: String,
        val thumbnailUrl: String,
        val uploadDate: String,
        val contentUrl: String,
        val transcript: String,
    )

    private data class LinkData(val id: String)
}