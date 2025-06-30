package com.kuramanime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.Jsoup

class VidGuardExtractor : ExtractorApi() {
    override val name = "VidGuard"
    override val mainUrl = "https://vidguard.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
    ): List<ExtractorLink>? {
        val doc = app.get(url, referer = referer).document
        val script = doc.select("script:containsData(player.on)").first()?.data()
            ?: return null

        val masterUrl = Regex("""sources:\s*\["(.*?)"]""").find(script)?.groupValues?.get(1)
            ?: return null

        return listOf(
            newExtractorLink(
                name,
                name,
                masterUrl,
            ) {
                this.referer = referer ?: ""
                this.quality = Qualities.Unknown.value
            }
        )
    }
}
