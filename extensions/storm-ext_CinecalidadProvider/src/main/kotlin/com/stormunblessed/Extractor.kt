package com.stormunblessed

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.fixUrl
import com.lagradost.cloudstream3.utils.getAndUnpack
import org.jsoup.nodes.Element

class Vimeos : ExtractorApi() {
    override val name = "Vimeos"
    override val mainUrl = "https://vimeos.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(getEmbedUrl(url), referer = referer).document
        val unpackedJs = unpackJs(doc).toString()
        val videoUrl =
            Regex("""file:\s*"([^"]+\.m3u8[^"]*)"""").find(unpackedJs)?.groupValues?.get(1)
        if (videoUrl != null) {
            M3u8Helper.generateM3u8(
                this.name,
                fixUrl(videoUrl),
                "$mainUrl/",
            ).forEach(callback)
        }
    }
    private fun unpackJs(script: Element): String? {
        return script.select("script").find { it.data().contains("eval(function(p,a,c,k,e,d)") }
            ?.data()?.let { getAndUnpack(it) }
    }
    private fun getEmbedUrl(url: String): String {
        return if (!url.contains("/embed-")) {
            val videoId = url.substringAfter("$mainUrl/")
            "$mainUrl/embed-$videoId"
        } else {
            url
        }
    }
}

class GoodstreamExtractor : ExtractorApi() {
    override var name = "Goodstream"
    override val mainUrl = "https://goodstream.one"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        app.get(url).document.select("script").map { script ->
            if (script.data().contains(Regex("sources: \\[\\{file:"))) {
                val urlRegex = Regex("""file:\s*"([^"]+\.m3u8[^"]*)"""")
                urlRegex.find(script.data())?.groupValues?.get(1).let { link ->
                    M3u8Helper.generateM3u8(
                        this.name,
                        fixUrl(link!!),
                        "$mainUrl/",
                    ).forEach(callback)
                }
            }
        }
    }
}