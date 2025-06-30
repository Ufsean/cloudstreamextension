package com.layaranime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class LayarAnime : MainAPI() {
    override var mainUrl = "https://layaranime.com"
    override var name = "LayarAnime"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    companion object {
        fun getType(t: String?): TvType {
            if (t == null) return TvType.Anime
            return when {
                t.contains("Series", true) -> TvType.Anime
                t.contains("Movie", true) -> TvType.AnimeMovie
                t.contains("OVA", true) || t.contains("Special", true) -> TvType.OVA
                else -> TvType.Anime
            }
        }

        fun getStatus(t: String?): ShowStatus {
            if (t == null) return ShowStatus.Completed
            return when {
                t.contains("Ongoing", true) -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    override val mainPage =
            mainPageOf(
                    "anime-episode-terbaru/page/" to "Anime Episode Terbaru",
                    "donghua-episode-terbaru/page/" to "Donghua Episode Terbaru",
                    "page/" to "Anime Terbaru"
            )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl/${request.data}$page"
        val document = app.get(url).document
        val home = document.select("article").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val href = this.selectFirst("a")?.attr("href") ?: return null
        val title = this.selectFirst("h4")?.text()?.trim() ?: return null
        if (title.isBlank()) return null
        val posterUrl = this.selectFirst("img")?.attr("src")

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        return document.select("article").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.font-bold")?.text()?.replace("Nonton ", "") ?: ""
        val poster = document.selectFirst("div.flex-col img")?.attr("src")
        val year = document.selectFirst("a[href*='/tahun/']")?.text()?.trim()?.toIntOrNull()
        
        val genres = document.select("div.col-span-5 a[href*='/genre/']").map { it.text() }
        val typeText = genres.firstOrNull { it.equals("Movie", ignoreCase = true) } ?: "Series"
        val type = getType(typeText)

        val statusText = document.selectFirst("div.col-span-5 > span")?.text()
        val status = getStatus(statusText)

        val plot = document.selectFirst("blockquote > div")?.text()

        val episodes = document.select("div.episode a").mapNotNull { el ->
            val epHref = el.attr("href")
            val epName = el.text().trim()
            if (epHref.isBlank() || epName.isBlank()) null
            else newEpisode(epHref) { this.name = epName }
        }.reversed()

        return newAnimeLoadResponse(title, url, type) {
            posterUrl = poster
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
            this.plot = plot
            this.tags = genres
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val sources = mutableListOf<String>()
        val playerJson = document.selectFirst("div#player[x-data]")?.attr("x-data")
        
        if (playerJson != null) {
            val jsonString = playerJson.substringAfter("playerPage(").substringBeforeLast(")")
            val cleanedJson = jsonString.trim('\'').replace("\\/", "/")
            try {
                val videoData = AppUtils.parseJson<Map<String, List<String>>>(cleanedJson)
                videoData.values.flatten().forEach { url ->
                    if (url.isNotBlank()) {
                        val iframeUrl = url.replace(Regex("^.*\\?url="), "")
                        sources.add(iframeUrl)
                    }
                }
            } catch (e: Exception) {
                // Do nothing
            }
        }

        sources.amap {
            loadExtractor(it, data, subtitleCallback, callback)
        }

        return true
    }
}
