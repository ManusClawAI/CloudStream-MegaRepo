package com.stormunblessed

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import org.json.JSONObject
import java.util.Calendar

class PelisplusOrgProvider : MainAPI() {
    override var mainUrl = "https://v4.pelis-plus.icu"
    override var name = "PelisplusOrg"
    override var lang = "mx"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val items = ArrayList<HomePageList>()
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val urls = listOf(
            Pair("Películas de Estreno", "$mainUrl/peliculas#Películas de Estreno"),
            Pair("Películas Actualizadas", "$mainUrl/peliculas#Películas Actualizadas"),
//            Pair("Películas Recomendadas", "$mainUrl/peliculas#Películas Recomendadas"),
            Pair("Series de Estreno", "$mainUrl/series#Series de Estreno"),
            Pair("Series Actualizadas", "$mainUrl/series#Series Actualizadas"),
//            Pair("Series Recomendadas", "$mainUrl/series#Series Recomendadas"),
            Pair("Animes Actualizados", "$mainUrl/animes#Animes Actualizados"),
            Pair("Animes Recomendados", "$mainUrl/animes#Animes Recomendados"),
            Pair("Doramas de Estreno", "$mainUrl/doramas#Doramas de Estreno"),
            Pair("Doramas Actualizadas", "$mainUrl/doramas#Doramas Actualizadas"),
//            Pair("Doramas Recomendadas", "$mainUrl/doramas#Doramas Recomendadas"),
        )

        urls.amap { (name, url) ->
            val doc = app.get(url, referer = "$mainUrl/").document
            val home =
                doc.selectXpath("//*[@id=\"$name\"]")?.select("a")?.map {
                    val title = it.selectFirst("span.overflow-hidden")?.text()
                    val link = it.attr("href")
                    val img =
                        it.selectFirst("div.w-full img.w-full")?.attr("src")
                    val year = it.selectFirst("div:nth-child(1) > div:nth-child(1) > div:nth-child(2) > div:nth-child(1) > span:nth-child(1)")?.text()
                        ?.toIntOrNull()
                    newTvSeriesSearchResponse(title!!, link!!, TvType.TvSeries){
                        this.posterUrl = fixUrl(img!!)
                        this.year = year
                    }
                }
            if(!home.isNullOrEmpty()){
                items.add(HomePageList(name, home))
            }
        }
        return newHomePageResponse(items)
    }


    data class ApiResponse(
        val videos: List<Video>,
    )
    data class Video(
        val id: Int,
        val slug: String,
        val titulo: String,
        val original: String,
        val modo: Int,
        val tipo: Int,
        val score: Int,
        val votes: String,
        val calidad: String,
        val descripcion: String,
        val imagen: String,
        val portada: String,
        val tmdb_id: String,
        val trailer: String,
        val release_date: String,
        val duracion: String,
        val status: String,
        val created_at: String,
        val updated_at: String
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val bodyMap = mapOf(
            "query" to query,
            "genre" to "",
            "year" to "",
            "format" to "",
            "page" to 1,
            "pageSize" to 42
        )

        val jsonBody = JSONObject(bodyMap).toString()
        val mediaType = "text/plain; charset=UTF-8".toMediaType()

        val headers = mapOf(
            "Referer" to "$mainUrl/directorio?search=$query",
            "Next-Action" to "8c9b580858b66ac9488313744447683fd72f2644",
            "Next-Router-State-Tree" to "%5B%22%22%2C%7B%22children%22%3A%5B%22directorio%22%2C%7B%22children%22%3A%5B%5B%22options%22%2C%22%22%2C%22oc%22%5D%2C%7B%22children%22%3A%5B%22__PAGE__%3F%7B%5C%22search%5C%22%3A%5C%22calamar%5C%22%7D%22%2C%7B%7D%2C%22%2Fdirectorio%3Fsearch%3Dcalamar%22%2C%22refresh%22%5D%7D%5D%7D%5D%7D%2Cnull%2Cnull%2Ctrue%5D",
        )

        val requestBody = RequestBody.create(mediaType, "[$jsonBody]")

        val doc = app.post(
            url = "$mainUrl/directorio?search=${query}r",
            headers = headers,
            requestBody = requestBody
        ).document

        doc.body().html().substringAfter("1:")
        val result = AppUtils.tryParseJson<ApiResponse>(doc.body().html().substringAfter("1:"))

        return result?.videos?.amap {
            val title = it.titulo
            val typeName = if(it.modo == 1) "pelicula" else "serie"
            val link = "$mainUrl/$typeName/${it.slug}"
            val img = "https://image.tmdb.org/t/p/w185/${it.imagen}.jpg"
            val year = it.release_date.substringBefore("-").toIntOrNull()
            newTvSeriesSearchResponse(title!!, link!!, TvType.TvSeries){
                this.posterUrl = fixUrl(img!!)
                this.year = year
            }
        }.orEmpty()
    }

    data class Capitulo(
        val titulo: String? = null,
        val descripcion: String? = null,
        val temporada: Int? = null,
        val capitulo: Int? = null,
        val imagen: String? = null,
    )

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val tvType = if (url.contains("pelicula")) TvType.Movie else TvType.TvSeries
        if(tvType.equals(TvType.Movie)){
            val info = doc.select("div.flex.w-full.h-fit.flex-col")
            val title = info.select("div div.flex div.flex span.font-semibold").first()?.text() ?: ""
            val year = title.substringAfter("(").substringBefore(")").toIntOrNull()
            val poster = info.select("div div.object-cover img").first()?.attr("src")
            val backimage = doc.selectXpath("/html/body/div/div/main/div[1]/div[1]/div/div[2]/div[1]/img").attr("src")
            val description = info.select("span.w-full.text-textsec.text-sm").first()?.text() ?: ""
            val tags = info.select("div.flex-wrap.gap-3.mt-4.hidden a").map{ it?.text().orEmpty()}
            return newMovieLoadResponse(title, url, tvType, url) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = backimage ?: poster
                    this.plot = description
                    this.tags = tags
                    this.year = year
                }
        }else{
            val title = doc.select(".detailsTop_title___bVic").first()?.text() ?: ""
            val year = title.substringAfter("(").substringBefore(")").toIntOrNull()
            val poster = doc.selectFirst(".detailsTop_detailsimage__oA30g")?.attr("src")
            val backimage = doc.selectFirst(".detailsTop_detailsbgimage__Yhf6Y")?.attr("style")?.substringAfter("url(")?.substringBefore(")")
            val description = doc.selectFirst(".detailsBottom_descriptioncontent__eOros > p:nth-child(1)")?.text() ?: ""
            val tags = doc.select("div.detailsBottom_singlecontent__vAIRR:nth-child(4) > span:nth-child(2) span").map{ it?.text().orEmpty().replace(",", "")}
            val json = doc.select("script").firstOrNull {
                it.html().startsWith("self.__next_f.push(") && it.html().contains("\\\"capitulos\\\":[")
            }?.html()?.substringAfter("\\\"capitulos\\\":")?.substringBefore("}],")
                ?.replace("\\\"", "\"")
            val capitulos = AppUtils.tryParseJson<List<Capitulo>>(json)
            var episodes = if (!capitulos.isNullOrEmpty()) {
                    capitulos.amap {
                        val titulo = it.titulo?.replace("$title ", "")
                        val epurl = "$url/$titulo"
                        newEpisode(epurl){
                            this.name = titulo
                            this.season = it.temporada
                            this.episode = it.capitulo
                            this.posterUrl = "https://image.tmdb.org/t/p/w185/${it.imagen}.jpg"
                        }
                    }
            } else listOf()
            return newTvSeriesLoadResponse(
                    title,
                    url, tvType, episodes,
                ) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = backimage ?: poster
                    this.plot = description
                    this.tags = tags
                    this.year = year
                }
        }
    }

    suspend fun customLoadExtractor(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit)
    {
        loadExtractor(url
            .replaceFirst("https://hglink.to", "https://streamwish.to")
            .replaceFirst("https://swdyu.com","https://streamwish.to")
            .replaceFirst("https://mivalyo.com", "https://vidhidepro.com")
            .replaceFirst("https://filemoon.link", "https://filemoon.sx")
            .replaceFirst("https://sblona.com", "https://watchsb.com")
            , referer, subtitleCallback, callback)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val text = doc.select("script").filter {
            it.html().trim().startsWith("self.__next_f.push([1")
        }.joinToString("") {
            it.html().replaceFirst("self.__next_f.push([1,\"", "")
                .replace(""""]\)$""".toRegex(), "")
        }
        fetchLinks(text.replace("\\\"", "\"")).amap {
            loadSourceNameExtractor(it.lang!!, fixHostsLinks(it.url!!), data, subtitleCallback, callback)
        }
        return true
    }

    data class Link(
        val lang: String?,
        val url: String?
    )

    fun fetchLinks(text: String?): List<Link> {
        if (text.isNullOrEmpty()) {
            return listOf()
        }
        val linkRegex =
            Regex(""""enlace":"(https?://(www\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_+.~#?&/=]*))","tipo":\d,"idioma":(\d)""")
        return linkRegex.findAll(text).map { Link(getLang(it.groupValues[4]), it.groupValues[1]) }.toList()
    }

    fun getLang(str: String): String {
        return when (str) {
            "1" -> "Latino"
            "2" -> "Español"
            "3" -> "Subtitulado"
            else -> ""
        }
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