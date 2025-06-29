package com.layaranime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class Layaranime : MainAPI() {
    override var mainUrl = "https://layaranime.com"
    override var name = "Layaranime"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Anime)

    override val mainPage = mainPageOf(
        "anime-episode-terbaru/page/%d/" to "Anime Episode Terbaru",
        "donghua-episode-terbaru/page/%d/" to "Donghua Episode Terbaru",
        "page/%d/" to "Anime Terbaru",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl/${request.data.format(page)}"
        val document = app.get(url).document

        val home = document.select("article").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h4 a")?.text() ?: return null
        val href = this.selectFirst("a")?.attr("href") ?: return null
        val posterUrl = this.selectFirst("img")?.attr("src")

        // Distinguish between series and episodes
        return if (href.contains("-episode-")) {
            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = posterUrl
            }
        } else {
            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = posterUrl
            }
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

        val title = document.selectFirst("h1.entry-title")?.text() ?: "No Title"
        val posterUrl = document.selectFirst("div.thumb img")?.attr("src")
        val plot = document.select("div.entry-content p").joinToString("\n") { it.text() }
        val tags = document.select("div.genre-info a").map { it.text() }

        val episodes = document.select("div.eplister li a").map {
            val href = it.attr("href")
            val name = it.selectFirst(".epnum")?.text() ?: it.text()
            val episodeNumber = name.substringAfter("Episode").trim().toIntOrNull()
            Episode(
                data = href,
                name = name,
                episode = episodeNumber
            )
        }.reversed()

        return newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
            this.posterUrl = posterUrl
            this.plot = plot
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        document.select("div.download-eps a").forEach {
            loadExtractor(it.attr("href"), data, subtitleCallback, callback)
        }

        return true
    }
}
