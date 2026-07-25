package com.stormunblessed

import com.lagradost.cloudstream3.APIHolder.getCaptchaToken
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*

open class VidMoly : ExtractorApi() {
    override val name = "VidMoly"
    override val mainUrl = "https://vidmoly.me"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url, referer = referer).document

        val embedUrl = doc.selectFirst("iframe[src*='embed-']")?.attr("abs:src")
            ?: doc.selectFirst("iframe[src*='vidmoly.biz']")?.attr("abs:src")

        val embedDoc = app.get(embedUrl ?: url, referer = url).document

        embedDoc.select("script").find { script ->
            val content = script.html().ifEmpty { script.data() }
            content.contains("sources:") || content.contains("file:")
        }?.let { script ->
            val content = script.html().ifEmpty { script.data() }
            val m3u8 = content.substringAfter("file: '").substringBefore("'")
                .ifEmpty { content.substringAfter("file: \"").substringBefore("\"") }
            if (m3u8.isNotBlank()) {
                M3u8Helper.generateM3u8(
                    this.name,
                    m3u8,
                    "$mainUrl/"
                ).forEach(callback)
            }
        }
    }
}

open class VidMolyCom : VidMoly() {
    override val name = "VidMolyCom"
    override val mainUrl = "https://vidmoly.com"
}
