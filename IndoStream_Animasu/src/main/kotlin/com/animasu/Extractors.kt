package com.animasu

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class Archivd : ExtractorApi() {
    override val name: String = "Archivd"
    override val mainUrl: String = "https://archivd.net"
    override val requiresReferer = true

    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url).document
        val json = res.select("div#app").attr("data-page")
        val video = tryParseJson<Sources>(json)?.props?.datas?.data?.link?.media
        callback.invoke(
            newExtractorLink(
                name,
                name,
                video ?: return,
                INFER_TYPE
            ) {
                this.referer = mainUrl
                this.quality = Qualities.P1080.value
                this.headers = headers
            }
        )
    }

    data class Link(
            @JsonProperty("media") val media: String? = null,
    )

    data class Data(
            @JsonProperty("link") val link: Link? = null,
    )

    data class Datas(
            @JsonProperty("data") val data: Data? = null,
    )

    data class Props(
            @JsonProperty("datas") val datas: Datas? = null,
    )

    data class Sources(
            @JsonProperty("props") val props: Props? = null,
    )
}

open class Newuservideo : ExtractorApi() {
    override val name: String = "Uservideo"
    override val mainUrl: String = "https://uservideo.xyz"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val script = app.get(url).document.selectFirst("script:containsData(hosts =)")?.data()
        val host = script?.substringAfter("hosts = [\"")?.substringBefore("\"];")
        val servers = script?.substringAfter("servers = \"")?.substringBefore("\";")

        val sources = app.get("$host/s/$servers").text.substringAfter("\"sources\":[").substringBefore("],").let {
            AppUtils.tryParseJson<List<Sources>>("[$it]")
        }
        val quality = Regex("(\\d{3,4})[Pp]").find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()

        sources?.map { source ->
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    source.src ?: return@map null
                ) {
                    this.referer = url
                    this.quality = quality ?: Qualities.Unknown.value
                }
            )
        }

    }

    data class Sources(
        @JsonProperty("src") val src: String? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("label") val label: String? = null,
    )

}

class Vidhidepro : Filesim() {
    override val mainUrl = "https://vidhidepro.com"
    override val name = "Vidhidepro"
}

open class Blogger : ExtractorApi() {
    override val name = "Blogger"
    override val mainUrl = "https://www.blogger.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val sources = mutableListOf<ExtractorLink>()
        with(app.get(url).document) {
            this.select("script").map { script ->
                if (script.data().contains("\"streams\":[")) {
                    val data = script.data().substringAfter("\"streams\":[")
                        .substringBefore("]")
                    tryParseJson<List<ResponseSource>>("[$data]")?.map {
                        sources.add(
                            newExtractorLink(
                                name,
                                name,
                                it.play_url,
                            ) {
                                this.referer = "https://www.youtube.com/"
                                this.quality = when (it.format_id) {
                                    18 -> 360
                                    22 -> 720
                                    else -> Qualities.Unknown.value
                                }
                            }
                        )
                    }
                }
            }
        }
        return sources
    }

    private data class ResponseSource(
        @JsonProperty("play_url") val play_url: String,
        @JsonProperty("format_id") val format_id: Int
    )
}