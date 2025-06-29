package com.animasu

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Archivd : ExtractorApi() {
    override val name: String = "Archivd"
    override val mainUrl: String = "https://archivd.net"
    override val requiresReferer = true

    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url).document
        val json = res.select("div#app").attr("data-page")
        val video = AppUtils.tryParseJson<Sources>(json)?.props?.datas?.data?.link?.media
        val link = newExtractorLink(this.name, this.name, video ?: return, INFER_TYPE) {
            this.referer = "$mainUrl/"
            this.quality = Qualities.Unknown.value
        }
        callback.invoke(link)
    }

    data class Link(
            @JsonProperty("media") val media: String? = null,
    )

    data class Data(
            @JsonProperty("link") val link: Link? = null,
    )

    data class Datas(
            @JsonProperty("data") val data: Data? = null,
    )

    data class Props(
            @JsonProperty("datas") val datas: Datas? = null,
    )

    data class Sources(
            @JsonProperty("props") val props: Props? = null,
    )
}

class MegaNzExtractor : ExtractorApi() {
    override val name = "Mega.nz"
    override val mainUrl = "https://mega.nz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Fetch the embed page and extract the video URL(s)
        val req = app.get(url, referer = referer)
        val doc = req.document

        // The video URL might be in a script tag or iframe; parse accordingly
        val iframeSrc = doc.selectFirst("iframe")?.attr("src")
        if (iframeSrc != null) {
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    iframeSrc,
                ) {
                    this.referer = referer ?: mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
            return
        }

        // Fallback: try to find m3u8 URL in scripts
        val scriptText = doc.select("script:containsData(m3u8)").joinToString(" ") { it.data() }
        val m3u8Regex = Regex("""https?://[^\s'"]+\.m3u8[^\s'"]*""")
        val m3u8Url = m3u8Regex.find(scriptText)?.value

        if (m3u8Url != null) {
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    m3u8Url,
                ) {
                    this.referer = referer ?: mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}

class Newuservideo : ExtractorApi() {
    override val name: String = "Uservideo"
    override val mainUrl: String = "https://new.uservideo.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val iframe =
                app.get(url, referer = referer).document.select("iframe#videoFrame").attr("src")
        val doc = app.get(iframe, referer = "$mainUrl/").text
        val json = "VIDEO_CONFIG\\s?=\\s?(.*)".toRegex().find(doc)?.groupValues?.get(1)

        AppUtils.tryParseJson<Sources>(json)?.streams?.map { stream ->
            val quality = when (stream.formatId) {
                18 -> Qualities.P360.value
                22 -> Qualities.P720.value
                else -> Qualities.Unknown.value
            }
            val link = newExtractorLink(this.name, this.name, stream.playUrl ?: return@map, INFER_TYPE) {
                this.referer = "$mainUrl/"
                this.quality = quality
            }
            callback.invoke(link)
        }
    }

    data class Streams(
            @JsonProperty("play_url") val playUrl: String? = null,
            @JsonProperty("format_id") val formatId: Int? = null,
    )

    data class Sources(
            @JsonProperty("streams") val streams: ArrayList<Streams>? = null,
    )
}

class Vidhidepro : Filesim() {
    override val mainUrl = "https://vidhidepro.com"
    override val name = "Vidhidepro"
}

open class Blogger : ExtractorApi() {
    override val name = "Blogger"
    override val mainUrl = "https://www.blogger.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        with(app.get(url).document) {
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
