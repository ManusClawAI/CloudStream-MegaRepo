package com.stormunblessed

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.*
import kotlin.collections.ArrayList

class MundoDonghuaProvider : MainAPI() {

    override var mainUrl = "https://www.mundodonghua.com"
    override var name = "MundoDonghua"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA,
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        items.add(
            HomePageList(
                "Últimos episodios",
                app.get(mainUrl, timeout = 120).document.select("#nuevos-episodios-grid .md-card").mapNotNull { el ->
                    val title = el.selectFirst(".md-card-title")?.text() ?: return@mapNotNull null
                    val poster = el.selectFirst(".md-card-img img")?.attr("src") ?: return@mapNotNull null
                    val epLink = el.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                    val epNum = Regex("/(\\d+)$").find(epLink)?.groupValues?.get(1)?.toIntOrNull()
                    val serieSlug = epLink.substringAfter("/ver/").substringBeforeLast("/")
                    val serieUrl = "$mainUrl/donghua/$serieSlug"
                    val cleanTitle = title.replace(Regex("\\s+\\d+$"), "").trimEnd()
                    val dubstat = if (title.contains("Latino") || title.contains("Castellano")) DubStatus.Dubbed else DubStatus.Subbed
                    newAnimeSearchResponse(cleanTitle, fixUrl(serieUrl)) {
                        this.posterUrl = fixUrl(poster)
                        addDubStatus(dubstat, epNum)
                    }
                })
        )
        items.add(
            HomePageList(
                "Donghuas",
                app.get("$mainUrl/lista-donghuas", timeout = 120).document.select(".md-card").mapNotNull { el ->
                    val title = el.selectFirst(".md-card-title")?.text() ?: return@mapNotNull null
                    val poster = el.selectFirst(".md-card-img img")?.attr("src") ?: ""
                    val href = el.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                    val badge = el.selectFirst(".md-card-badge")?.text() ?: ""
                    val tvType = when {
                        badge.contains("Película") -> TvType.AnimeMovie
                        badge.contains("OVA") || badge.contains("Especial") -> TvType.OVA
                        else -> TvType.Anime
                    }
                    newAnimeSearchResponse(title, fixUrl(href), tvType) {
                        this.posterUrl = fixUrl(poster)
                        this.dubStatus = if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                            DubStatus.Dubbed
                        ) else EnumSet.of(DubStatus.Subbed)
                    }
                })
        )

        if (items.isEmpty()) throw ErrorLoadingException()
        return newHomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/busquedas?donghua=$query", timeout = 120).document.select(".md-card").mapNotNull { el ->
            val title = el.selectFirst(".md-card-title")?.text() ?: return@mapNotNull null
            val href = el.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val image = el.selectFirst(".md-card-img img")?.attr("src")
            val badge = el.selectFirst(".md-card-badge")?.text() ?: ""
            val tvType = when {
                badge.contains("Película") -> TvType.AnimeMovie
                badge.contains("OVA") || badge.contains("Especial") -> TvType.OVA
                else -> TvType.Anime
            }
            newAnimeSearchResponse(title, fixUrl(href), tvType) {
                this.posterUrl = fixUrl(image ?: "")
                this.dubStatus = if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                    DubStatus.Dubbed
                ) else EnumSet.of(DubStatus.Subbed)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, timeout = 120).document
        val poster = doc.selectFirst(".md-detail-poster img")?.attr("src")
            ?: doc.selectFirst("head meta[property=og:image]")?.attr("content") ?: ""
        val title = doc.selectFirst(".md-detail-title")?.text() ?: "Donghua"
        val description = doc.selectFirst(".md-detail-synopsis")?.text() ?: ""
        val genres = doc.select(".md-genre-tag").mapNotNull { it.text().trim().takeIf { g -> g.isNotEmpty() } }
        val status = when {
            doc.selectFirst(".md-emision-badge")?.text()?.contains("Finalizada") == true -> ShowStatus.Completed
            doc.selectFirst(".md-emision-badge")?.text()?.contains("Emisión") == true -> ShowStatus.Ongoing
            else -> null
        }
        val badgeType = doc.selectFirst(".md-card-badge.md-badge-static")?.text() ?: ""
        val tvType = when {
            badgeType.contains("Película") -> TvType.AnimeMovie
            badgeType.contains("OVA") || badgeType.contains("Especial") -> TvType.OVA
            else -> TvType.Anime
        }
        val newEpisodes = ArrayList<Episode>()
        doc.select(".md-episode-item a").mapNotNull { el ->
            val link = el.attr("href")
            val epnum = Regex("/(\\d+)$").find(link)?.groupValues?.get(1)?.toIntOrNull()
            if (link.isNotBlank()) {
                newEpisodes.add(newEpisode(fixUrl(link)) { this.episode = epnum })
            }
        }

        return newAnimeLoadResponse(title, url, tvType) {
            posterUrl = poster
            addEpisodes(DubStatus.Subbed, newEpisodes.sortedBy { it.episode })
            showStatus = status
            plot = description
            tags = genres
        }
    }

    data class Protea(
        @JsonProperty("source") val source: List<Source>,
        @JsonProperty("poster") val poster: String?
    )

    data class Source(
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("default") val default: String?
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val datafix = data.replace("ñ", "%C3%B1")
        val reqHEAD = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.5",
            "X-Requested-With" to "XMLHttpRequest",
            "Referer" to datafix,
            "DNT" to "1",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "same-origin",
            "TE" to "trailers"
        )
        app.get(data).document.select("script").amap { script ->
            if (script.data().contains("eval(function(p,a,c,k,e")) {
                val packedRegex = Regex("eval\\(function\\(p,a,c,k,e,.*\\)\\)")
                packedRegex.findAll(script.data()).map { it.value }.toList().amap {
                    val unpack = getAndUnpack(it).replace("diasfem", "embedsito")
                    fetchUrls(unpack).amap { url ->
                        val newUrl = url.replace("https://sbbrisk.com", "https://watchsb.com")
                        loadExtractor(newUrl, data, subtitleCallback, callback)
                    }
                    if (unpack.contains("protea_tab")) {
                        val protearegex = Regex("protea_tab.*slug.*\\\"(.*)\\\".*,type")
                        val ssee = protearegex.find(unpack)?.destructured?.component1()
                        if (!ssee.isNullOrEmpty()) {
                            val aa = app.get("$mainUrl/api_donghua.php?slug=$ssee", headers = reqHEAD).text
                            val secondK = aa.substringAfter("url\":\"").substringBefore("\"}")
                            val se = "https://www.mdnemonicplayer.xyz/nemonicplayer/dmplayer.php?key=$secondK"
                            val aa3 = app.get(se, headers = reqHEAD, allowRedirects = false).text
                            val idReg = Regex("video.*\\\"(.*?)\\\"")
                            val vidID = idReg.find(aa3)?.destructured?.component1()
                            if (!vidID.isNullOrEmpty()) {
                                val newLink = "https://www.dailymotion.com/embed/video/$vidID"
                                loadExtractor(newLink, subtitleCallback, callback)
                            }
                        }
                    }
                    if (unpack.contains("asura_player")) {
                        val asuraRegex = Regex("file.*\\\"(.*)\\\".*type")
                        val aass = asuraRegex.find(unpack)?.destructured?.component1()
                        if (!aass.isNullOrEmpty()) {
                            val test = app.get(aass).text
                            if (test.contains(Regex("#EXTM3U"))) {
                                generateM3u8("Asura", aass, "").forEach(callback)
                            }
                        }
                    }
                }
            }
        }
        return true
    }
}
