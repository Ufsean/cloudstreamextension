package com.oploverz

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.*

open class Qiwi : ExtractorApi() {
    override val name = "Qiwi"
    override val mainUrl = "https://qiwi.gg"
    override val requiresReferer = true

    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, referer = referer).document
        val title = document.select("title").text()
        val source = document.select("video source").attr("src")

        callback.invoke(
                newExtractorLink(this.name, this.name, source) {
                    this.quality = getIndexQuality(title)
                    this.headers = mapOf("Range" to "bytes=0-")
                    this.referer = "$mainUrl/"
                }
        )
    }

    private fun getIndexQuality(str: String): Int {
        return Regex("(\\d{3,4})[pP]").find(str)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Qualities.Unknown.value
    }
}

open class Blogger : ExtractorApi() {
    override val name = "Blogger"
    override val mainUrl = "https://www.blogger.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        with(app.get(url, referer = referer).document) {
            this.select("script").map { script ->
                if (script.data().contains("\"streams\":[")) {
                    val data = script.data().substringAfter("\"streams\":[").substringBefore("]")
                    tryParseJson<List<ResponseSource>>("[$data]")?.map { source ->
                        val quality = when (source.format_id) {
                            18 -> 360
                            22 -> 720
                            else -> Qualities.Unknown.value
                        }
                        callback.invoke(
                            newExtractorLink(
                                name,
                                name,
                                source.play_url,
                                type = ExtractorLinkType.VIDEO
                            )
                        )
                    }
                }
            }
        }
    }

    private data class ResponseSource(
        @JsonProperty("play_url") val play_url: String,
        @JsonProperty("format_id") val format_id: Int
    )
}
