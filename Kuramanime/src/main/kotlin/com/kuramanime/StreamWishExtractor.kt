package com.kuramanime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.Jsoup

class StreamWishExtractor : ExtractorApi() {
    override val name = "StreamWish"
    override val mainUrl = "https://streamwish.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val doc = app.get(url, referer = referer).document
        val script = doc.select("script:containsData(sources:)").first()?.data()
            ?: return null

        val masterUrl = Regex("""file:\s*"(.*?)"""").find(script)?.groupValues?.get(1)
            ?: return null

        return listOf(
            ExtractorLink(
                name,
                name,
                masterUrl,
                referer,
                Qualities.Unknown.value,
            )
        )
    }
}
