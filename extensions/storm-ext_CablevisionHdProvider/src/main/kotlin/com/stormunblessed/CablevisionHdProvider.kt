package com.stormunblessed

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URL

class CablevisionHdProvider : MainAPI() {

    override var mainUrl = "https://www.cablevisionhd.com"
    override var name = "CablevisionHd"
    override var lang = "mx"

    override val hasQuickSearch = true
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Live,
    )

    private fun decodeBase64UntilUnchanged(encodedString: String): String {
        var decodedString = encodedString
        var previousDecodedString = ""
        while (decodedString != previousDecodedString) {
            previousDecodedString = decodedString
            decodedString = try {
                val decodedBytes = Base64.decode(decodedString, Base64.DEFAULT)
                String(decodedBytes)
            } catch (e: IllegalArgumentException) {
                break
            }
        }
        return decodedString
    }

    val nowAllowed = setOf(
        "Únete al chat",
        "Donar con Paypal",
        "Lizard Premium",
        "Vuelvete Premium (No ADS)",
        "Únete a Whatsapp",
        "Únete a Telegram",
        "¿Nos invitas el cafe?",
        "Mundo Latam",
    )

    val deportesCat = setOf(
        "TUDN",
        "WWE",
        "Afizzionados",
        "Gol Perú",
        "Gol TV",
        "TNT SPORTS",
        "Fox Sports Premium",
        "TYC Sports",
        "Movistar Deportes (Perú)",
        "Movistar La Liga",
        "Movistar Liga De Campeones",
        "Dazn F1",
        "Dazn La Liga",
        "Bein La Liga",
        "Bein Sports Extra",
        "Directv Sports",
        "Directv Sports 2",
        "Directv Sports Plus",
        "Espn Deportes",
        "Espn Extra",
        "Espn Premium",
        "Espn",
        "Espn 2",
        "Espn 3",
        "Espn 4",
        "Espn Mexico",
        "Espn 2 Mexico",
        "Espn 3 Mexico",
        "Fox Deportes",
        "Fox Sports",
        "Fox Sports 2",
        "Fox Sports 3",
        "Fox Sports Mexico",
        "Fox Sports 2 Mexico",
        "Fox Sports 3 Mexico",
    )

    val entretenimientoCat = setOf(
        "Telefe",
        "El Trece",
        "Televisión Pública",
        "Telemundo Puerto rico",
        "Univisión",
        "Univisión Tlnovelas",
        "Pasiones",
        "Caracol",
        "RCN",
        "Latina",
        "America TV",
        "Willax TV",
        "ATV",
        "Las Estrellas",
        "Tl Novelas",
        "Galavision",
        "Azteca 7",
        "Azteca Uno",
        "Canal 5",
        "Distrito Comedia",
    )

    val noticiasCat = setOf(
        "Telemundo 51",
    )

    val peliculasCat = setOf(
        "Movistar Accion",
        "Movistar Drama",
        "Universal Channel",
        "TNT",
        "TNT Series",
        "Star Channel",
        "Star Action",
        "Star Series",
        "Cinemax",
        "Space",
        "Syfy",
        "Warner Channel",
        "Warner Channel (México)",
        "Cinecanal",
        "FX",
        "AXN",
        "AMC",
        "Studio Universal",
        "Multipremier",
        "Golden",
        "Golden Plus",
        "Golden Edge",
        "Golden Premier",
        "Golden Premier 2",
        "Sony",
        "DHE",
        "NEXT HD",
    )

    val infantilCat = setOf(
        "Cartoon Network",
        "Tooncast",
        "Cartoonito",
        "Disney Channel",
        "Disney JR",
        "Nick",
    )

    val educacionCat = setOf(
        "Discovery Channel",
        "Discovery World",
        "Discovery Theater",
        "Discovery Science",
        "Discovery Familia",
        "History",
        "History 2",
        "Animal Planet",
        "Nat Geo",
        "Nat Geo Mundo",
    )

    val dos47Cat = setOf(
        "24/7",
    )

    private fun channelText(el: org.jsoup.nodes.Element): String {
        return el.selectFirst("p")?.text()?.trim() ?: ""
    }

    private fun buildChannel(el: org.jsoup.nodes.Element): LiveSearchResponse? {
        val title = channelText(el)
        if (title.isBlank()) return null
        val img = el.selectFirst("img")?.attr("src") ?: ""
        val link = el.attr("href")
        if (link.isBlank()) return null
        return newLiveSearchResponse(title, link, TvType.Live) {
            this.posterUrl = fixUrl(img)
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(mainUrl).document
        val allChannels = doc.select("a.channel-card").mapNotNull { el ->
            buildChannel(el)
        }

        val categoryMap = mapOf(
            "Deportes" to deportesCat,
            "Entretenimiento" to entretenimientoCat,
            "Noticias" to noticiasCat,
            "Peliculas" to peliculasCat,
            "Infantil" to infantilCat,
            "Educacion" to educacionCat,
            "24/7" to dos47Cat,
        )

        val items = ArrayList<HomePageList>()
        for ((catName, catSet) in categoryMap) {
            val filtered = allChannels.filter { ch ->
                catSet.any { ch.name.contains(it, ignoreCase = true) }
            }
            if (filtered.isNotEmpty()) {
                items.add(HomePageList(catName, filtered, true))
            }
        }

        items.add(HomePageList("Todos", allChannels, true))

        if (items.isEmpty()) throw ErrorLoadingException()
        return newHomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get(mainUrl).document
        return doc.select("a.channel-card").mapNotNull { el ->
            val title = channelText(el)
            if (title.isBlank()) return@mapNotNull null
            if (!title.contains(query, ignoreCase = true)) return@mapNotNull null
            if (nowAllowed.any { title.contains(it, ignoreCase = true) }) return@mapNotNull null
            buildChannel(el)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val poster =
            doc.selectFirst("div.info-logo img")
                ?.attr("src") ?: ""
        val title =
            doc.selectFirst("head meta[property=og:title]")
                ?.attr("content")
                ?: ""
        val desc =
            doc.selectFirst("head meta[property=og:description]")
                ?.attr("content")
                ?: ""

        return newMovieLoadResponse(
            title,
            url, TvType.Live, url
        ) {
            this.posterUrl = fixUrl(poster)
            this.backgroundPosterUrl = fixUrl(poster)
            this.plot = desc
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("a.option[href*=\"/stream\"]").amap {
            val streamLink = it.attr("href")
            val name = it.text().ifBlank { "Opción" }
            val streamPage = app.get(
                streamLink, headers = mapOf(
                    "Host" to "www.cablevisionhd.com",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:101.0) Gecko/20100101 Firefox/101.0",
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                    "Accept-Language" to "en-US,en;q=0.5",
                    "Referer" to data,
                    "Alt-Used" to "www.cablevisionhd.com",
                    "Connection" to "keep-alive",
                    "Cookie" to "TawkConnectionTime=0; twk_idm_key=qMfE5UE9JTs3JUBCtVUR1",
                    "Upgrade-Insecure-Requests" to "1",
                    "Sec-Fetch-Dest" to "iframe",
                    "Sec-Fetch-Mode" to "navigate",
                    "Sec-Fetch-Site" to "same-origin",
                )
            ).document
            val iframeSrc = streamPage.selectFirst("iframe")?.attr("src") ?: return@amap
            val finalPage = app.get(
                iframeSrc, headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:101.0) Gecko/20100101 Firefox/101.0",
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                    "Accept-Language" to "en-US,en;q=0.5",
                    "Referer" to mainUrl,
                    "Connection" to "keep-alive",
                    "Upgrade-Insecure-Requests" to "1",
                    "Sec-Fetch-Dest" to "iframe",
                    "Sec-Fetch-Mode" to "navigate",
                    "Sec-Fetch-Site" to "cross-site",
                )
            ).document
            finalPage.select("script").amap {
                val script = it.html()
                when {
                    script.contains("function(p,a,c,k,e,d)") -> {
                        val jsUnpacker = JsUnpacker(script)
                        if (jsUnpacker.detect()) {
                            val regex = """MARIOCSCryptOld\("(.*?)"\)""".toRegex()
                            val match = regex.find(jsUnpacker.unpack() ?: "")
                            val hash = match?.groupValues?.get(1) ?: ""
                            val extractedurl = decodeBase64UntilUnchanged(hash)
                            if (extractedurl.isNotBlank()) {
                                callback(newExtractorLink(name, name, extractedurl))
                            }
                        }
                    }
                    script.trim().startsWith("jwplayer.key = '") -> {
                        val url = script.substringAfter("setupPlayer(\"").substringBefore("\");")
                        callback(newExtractorLink(name, name, url))
                    }
                    script.trim().startsWith("var src = \"") -> {
                        val url = fixUrl(
                            script.substringAfter("var src = \"").substringBefore("\";")
                                .replace("\\/", "/").replace("\\:", ":")
                        )
                        callback(newExtractorLink(name, name, url))
                    }
                    script.trim().startsWith("var playbackURL = ") -> {
                        script.substringAfter("atob(\"").substringBefore("\")").let {
                            val extractedurl = decodeBase64UntilUnchanged(it)
                            if (extractedurl.isNotBlank()) {
                                callback(newExtractorLink(name, name, extractedurl))
                            }
                        }
                    }
                }
            }
        }
        return true
    }

    fun getBaseUrl(urlString: String): String {
        val url = URL(urlString)
        return "${url.protocol}://${url.host}"
    }

    fun getHostUrl(urlString: String): String {
        val url = URL(urlString)
        return url.host
    }
}
