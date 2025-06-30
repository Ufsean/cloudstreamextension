package com.layaranime

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

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
        val response = app.get(url, referer = referer, allowRedirects = false)
        val redirectedUrl = response.headers["Location"]
        if (redirectedUrl != null) {
            loadExtractor(redirectedUrl, url, subtitleCallback, callback)
        }
    }
}
