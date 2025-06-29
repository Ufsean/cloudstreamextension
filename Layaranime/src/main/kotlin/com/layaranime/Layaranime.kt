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

        val home = document.select("div.grid article").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h4")?.text()?.trim() ?: return null
        val href = this.selectFirst("a")?.attr("href") ?: return null
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

        val title = document.selectFirst("h1.entry-title")?.text() ?: "No Title"
        val posterUrl = document.selectFirst("div.thumb img")?.attr("src")
        val plot = document.select("div.entry-content p").joinToString("\n") { it.text() }
        val tags = document.select("div.genre-info a").map { it.text() }

        val episodes = document.select("div.grid a").map {
            val href = it.attr("href")
            val name = "Episode ${it.text()}"
            val episodeNumber = it.text().toIntOrNull()
            newEpisode(href) {
                this.name = name
                this.episode = episodeNumber
            }
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

        // Prioritize streaming servers
        document.select("div.player-nav li a").forEach {
            val url = it.attr("data-post")
            if (url.isNotBlank()) {
                loadExtractor(url, data, subtitleCallback, callback)
            }
        }

        // Fallback to download links
        document.select("h4:contains(LINK DOWNLOAD) ~ a").forEach {
            loadExtractor(it.attr("href"), data, subtitleCallback, callback)
        }

        return true
    }
}
