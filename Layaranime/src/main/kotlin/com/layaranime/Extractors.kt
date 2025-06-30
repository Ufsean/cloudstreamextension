package com.layaranime

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper

class SemacamCdn : ExtractorApi() {
    override val name = "SemacamCdn"
    override val mainUrl = "https://itadakimasu.semacamcdn.site"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, referer = referer).document
        val m3u8Link = document.selectFirst("video source")?.attr("src")
        if (m3u8Link != null) {
            M3u8Helper.generateM3u8(
                name,
                m3u8Link,
                url
            ).forEach(callback)
        }
    }
}
