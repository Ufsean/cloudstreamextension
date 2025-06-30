package com.layaranime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Kelas LayarAnime adalah implementasi dari MainAPI untuk mengambil data anime dari situs layaranime.com.
 * 
 * Fungsinya sebagai berikut:
 * - Mendefinisikan URL utama, nama, bahasa, dan tipe konten yang didukung (Anime, AnimeMovie, OVA).
 * - Menyediakan fungsi utilitas untuk menentukan tipe anime (getType) dan status penayangan (getStatus) berdasarkan string yang diambil dari situs.
 * - Mendefinisikan halaman utama (mainPage) yang berisi beberapa kategori anime.
 * - Mengambil dan mem-parsing halaman utama (getMainPage) untuk menampilkan daftar anime terbaru.
 * - Mengubah elemen HTML artikel menjadi objek AnimeSearchResponse (toSearchResult).
 * - Melakukan pencarian anime berdasarkan query (search).
 * - Mengambil detail lengkap dari sebuah anime, termasuk judul, poster, tahun, tipe, status, sinopsis, genre, dan daftar episode (load).
 * - Mengambil link streaming dari halaman episode, memproses data JSON yang ada di atribut x-data, dan mengekstrak semua link video yang tersedia (loadLinks).
 * 
 * Kelas ini digunakan oleh plugin Cloudstream untuk menampilkan dan memutar anime dari layaranime.com.
 */
class LayarAnime : MainAPI() {
    // URL utama situs
    override var mainUrl = "https://layaranime.com"
    // Nama provider
    override var name = "LayarAnime"
    // Menyediakan halaman utama
    override val hasMainPage = true
    // Bahasa konten
    override var lang = "id"
    // Mendukung fitur download
    override val hasDownloadSupport = true

    // Tipe konten yang didukung
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    companion object {
        /**
         * Fungsi untuk menentukan tipe anime berdasarkan string dari situs.
         * Jika string mengandung "Series" maka dianggap Anime, "Movie" dianggap AnimeMovie,
         * "OVA" atau "Special" dianggap OVA, selain itu default ke Anime.
         */
        fun getType(t: String?): TvType {
            if (t == null) return TvType.Anime
            return when {
                t.contains("Series", true) -> TvType.Anime
                t.contains("Movie", true) -> TvType.AnimeMovie
                t.contains("OVA", true) || t.contains("Special", true) -> TvType.OVA
                else -> TvType.Anime
            }
        }

        /**
         * Fungsi untuk menentukan status penayangan anime.
         * Jika string mengandung "Ongoing" maka status Ongoing, selain itu Completed.
         */
        fun getStatus(t: String?): ShowStatus {
            if (t == null) return ShowStatus.Completed
            return when {
                t.contains("Ongoing", true) -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    // Daftar halaman utama beserta labelnya
    override val mainPage =
            mainPageOf(
                    "/anime-episode-terbaru/page/" to "Anime Episode Terbaru",
                    "/donghua-episode-terbaru/page/" to "Donghua Episode Terbaru",
                    "/" to "Anime Terbaru"
            )

    /**
     * Mengambil daftar anime dari halaman utama berdasarkan kategori dan halaman.
     */
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl${request.data}$page"
        val document = app.get(url).document
        val home = document.select("article").map { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    /**
     * Mengubah elemen HTML artikel menjadi objek AnimeSearchResponse.
     */
    private fun Element.toSearchResult(): AnimeSearchResponse {
        val href = this.selectFirst("a")!!.attr("href")
        val title = this.selectFirst("h4")!!.text()
        val posterUrl = this.selectFirst("img")?.attr("src")
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    /**
     * Melakukan pencarian anime berdasarkan query.
     */
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        return document.select("article").map {
            it.toSearchResult()
        }
    }

    /**
     * Mengambil detail lengkap dari sebuah anime, termasuk judul, poster, tahun, tipe, status, sinopsis, genre, dan daftar episode.
     */
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.font-bold")?.text()?.replace("Nonton ", "") ?: ""
        val poster = document.selectFirst("div.flex-col img")?.attr("src")
        val year = document.selectFirst("a[href*='/tahun/']")?.text()?.trim()?.toIntOrNull()
        val typeText = document.select("a[href*='/type/']")?.text()
        val type = getType(typeText)

        val statusText = document.selectFirst("div.col-span-5 > span")?.text()
        val status = getStatus(statusText)

        val plot = document.selectFirst("blockquote > div")?.text()
        val tags = document.select("div.col-span-5 a[href*='/genre/']").map { it.text() }

        val episodes = document.select("div.episode a").map {
            newEpisode(it.attr("href")) {
                name = it.text()
            }
        }.reversed()

        return newAnimeLoadResponse(title, url, type) {
            posterUrl = poster
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
            this.plot = plot
            this.tags = tags
        }
    }

    /**
     * Mengambil link streaming dari halaman episode.
     * Data link diambil dari atribut x-data pada elemen div#player, lalu di-parse dari JSON.
     * Semua link video yang ditemukan akan diproses oleh loadExtractor.
     */
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
                        sources.add(url)
                    }
                }
            } catch (e: Exception) {
                // Jika gagal parse, tidak melakukan apa-apa
            }
        }

        sources.amap {
            loadExtractor(it, data, subtitleCallback, callback)
        }

        return true
    }
}
