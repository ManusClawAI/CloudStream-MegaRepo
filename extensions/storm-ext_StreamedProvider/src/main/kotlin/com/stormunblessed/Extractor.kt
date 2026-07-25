package com.stormunblessed

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class EmbedSports : ExtractorApi() {
    override val name = "EmbedSports"
    override val mainUrl = "https://embedsports.top"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?
    ): List<ExtractorLink> {
        val jsToClickPlay = """
                (() => {
                    clearTimeout(window.timeout)
                    window.timeout = setTimeout(() => {
                        const btn = document.querySelector('.jw-icon-display');
                        if (btn) { btn.click(); return "clicked"; }
                        return "button not found";
                    }, 2000);
                    return window.location;
                })();
            """.trimIndent()
        val m3u8Resolver = WebViewResolver(
            interceptUrl = Regex("""playlist\.m3u8$"""),
            additionalUrls = listOf(Regex("""playlist\.m3u8$""")),
            script = jsToClickPlay,
//            scriptCallback = { result -> Log.d("qwerty", "JS Result: $result") },
            useOkhttp = false,
            timeout = 50_000L
        )
        val fallbackM3u8 = app.get(
            url = url,
            referer = mainUrl,
            interceptor = m3u8Resolver
        )
        if (fallbackM3u8.url.contains("m3u8")) {
            val extractor = newExtractorLink(
                name,
                name,
                fallbackM3u8.url,
            ) {
                this.type = ExtractorLinkType.M3U8
                this.referer = "$mainUrl/"
            }
            return listOf(extractor)
        } else {
            return listOf()
        }
    }
}