
package com.arabseed
import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
class GameHubExtractor : ExtractorApi() {
    override var name = "سيرفر عرب سيد"
    override var mainUrl = "https://m.reviewrate.net"
    override val requiresReferer = true



    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {


            val qualityStr = url.substringAfter("#quality=", "").substringBefore("#")

            val cleanUrl = url.substringBefore("#quality=")

            val displayName = if (qualityStr.isNotBlank()) "$name - ${qualityStr}p" else name

            val initialResponse = app.get(cleanUrl, referer = referer ?: mainUrl)
            val html = initialResponse.text

            val csrfToken = html.let { Regex("""['"]csrf_token['"]\s*:\s*['"]([^'"]+)['"]""").find(it)?.groupValues?.get(1) }

            if (csrfToken.isNullOrBlank()) {

                Regex("""https?://[^\s"']+\.(m3u8|mp4|mkv)""").findAll(html).forEach { m ->
                    val link = m.value

                    callback.invoke(
                        newExtractorLink(
                            source = this.name,
                            name = displayName,
                            url = link,
                            type = if (link.endsWith("m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.referer = cleanUrl
                        }
                    )
                }
                return
            }

            val objId = cleanUrl.substringAfter("embed-", "").substringBefore(".html")
            if (objId.isBlank()) {

            }

            val ajaxUrl = "${mainUrl.trimEnd('/')}/get__watch__server/"

            val postResponse = app.post(
                ajaxUrl,
                headers = mapOf(
                    "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to cleanUrl,
                    "Origin" to mainUrl
                ),
                data = mapOf(
                    "post_id" to objId,
                    "csrf_token" to csrfToken
                )
            ).text

            Regex("""src=["'](https?://[^"']+)["']""").findAll(postResponse).forEach { match ->
                val iframeUrl = match.groupValues[1]

                loadExtractor(iframeUrl, cleanUrl, subtitleCallback, callback)
            }

            Regex("""https?://[^\s"']+\.m3u8""").findAll(postResponse).forEach { m ->
                val m3u8 = m.value

                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = "$displayName M3U8", // <-- تم التعديل هنا ليظهر الاسم مع الجودة
                        url = m3u8,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = cleanUrl
                        if (qualityStr.isNotBlank()) this.quality = getQualityFromName(qualityStr)
                    }
                )
            }
        } catch (e: Exception) {

        }
    }
}