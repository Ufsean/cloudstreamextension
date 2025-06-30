package com.layaranime

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class LayarAnimeExtractor : ExtractorApi() {
    override val name = "LayarAnime"
    override val mainUrl = "https://layaranime.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url).document
        val playerJson = document.selectFirst("div#player[x-data]")?.attr("x-data")

        if (playerJson != null) {
            val jsonString = playerJson.substringAfter("playerPage(").substringBeforeLast(")")
            val cleanedJson = jsonString.trim('\'').replace("\\/", "/")
            try {
                val videoData = AppUtils.parseJson<Map<String, List<String>>>(cleanedJson)
                videoData.values.flatten().forEach { serverUrl ->
                    if (serverUrl.isNotBlank()) {
                        val iframeUrl = serverUrl.replace(Regex("^.*\\?url="), "")
                        loadExtractor(iframeUrl, url, subtitleCallback, callback)
                    }
                }
            } catch (e: Exception) {
                // Ignore JSON parsing errors
            }
        }
    }
}
