package com.layaranime

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URLDecoder

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
        // The initial URL is an iframe pointing to semacamcdn, which then redirects.
        // We need to get the redirect location (filemoon.in) and pass it to the main extractor handler.
        val decodedUrl = URLDecoder.decode(url.substringAfter("?url="), "UTF-8")
        val response = app.get(decodedUrl, referer = referer, allowRedirects = false)
        val redirectedUrl = response.headers["Location"]
        if (redirectedUrl != null) {
            // Pass the final URL (e.g., filemoon) to the main extractor loader
            loadExtractor(redirectedUrl, url, subtitleCallback, callback)
        }
    }
}
