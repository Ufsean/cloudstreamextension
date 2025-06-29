package com.pusatfilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URI
import org.jsoup.nodes.Element

class Pusatfilm : MainAPI() {

    override var mainUrl = "https://pf21.site"

    override var name = "Pusatfilm"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes =
        setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama)

    override val mainPage =
            mainPageOf(
                    "film-terbaru/page/%d/" to "Film Terbaru",
                    "trending/page/%d/" to "Film Trending",
                    "genre/action/page/%d/" to "Film Action",
                    "series-terbaru/page/%d/" to "Series Terbaru",
                    "drama-korea/page/%d/" to "Drama Korea",
                    "west-series/page/%d/" to "West Series",
                    "drama-china/page/%d/" to "Drama China",
            )
    /**
     * Fungsi ini digunakan untuk mengambil data halaman utama (main page) dari website pusatfilm.
     * Fungsi ini akan dipanggil oleh aplikasi ketika ingin menampilkan daftar film/series pada halaman utama.
     *
     * @param page nomor halaman yang ingin diambil (misal: 1 untuk halaman pertama, 2 untuk halaman kedua, dst)
     * @param request berisi data permintaan halaman utama, seperti nama kategori dan format url
     * @return HomePageResponse yang berisi daftar SearchResponse (film/series) untuk ditampilkan di halaman utama
     *
     * Cara kerja:
     * 1. Mengambil data url dari request dan mengganti placeholder dengan nomor halaman.
     * 2. Melakukan request HTTP GET ke url tersebut dan mengambil dokumen HTML-nya.
     * 3. Memilih semua elemen artikel dengan class "item" lalu mengubahnya menjadi SearchResponse menggunakan fungsi toSearchResult().
     * 4. Mengembalikan hasilnya dalam bentuk HomePageResponse.
     */
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = request.data.format(page)
        val document = app.get("$mainUrl/$data").document
        val home = document.select("article.item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    /**
     * Fungsi ekstensi untuk class Element (dari Jsoup) yang digunakan untuk mengubah sebuah elemen HTML artikel film/series
     * menjadi objek SearchResponse yang dapat digunakan oleh aplikasi.
     *
     * Cara kerja fungsi ini:
     * 1. Mengambil judul film/series dari elemen <h2 class="entry-title"><a>...</a></h2>.
     *    Jika judul tidak ditemukan, fungsi akan mengembalikan null.
     * 2. Mengambil link detail (href) dari elemen <a>.
     * 3. Mengambil URL poster dari elemen <img> di dalam <a>, lalu memperbaiki kualitas gambar jika perlu.
     * 4. Mengambil kualitas video dari elemen dengan class "gmr-qual" atau "gmr-quality-item".
     * 5. Jika kualitas kosong (biasanya untuk series/TV), maka fungsi akan mencoba mengambil nomor episode dari judul
     *    atau dari elemen <div class="gmr-numbeps"><span>...</span></div>.
     *    Kemudian, fungsi akan membuat SearchResponse bertipe TvSeries (menggunakan newAnimeSearchResponse).
     * 6. Jika kualitas tidak kosong (biasanya untuk film), maka fungsi akan membuat SearchResponse bertipe Movie
     *    (menggunakan newMovieSearchResponse) dan menambahkan kualitas video.
     *
     * @return SearchResponse? objek hasil parsing, atau null jika data tidak valid.
     */
    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2.entry-title > a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("a > img")?.getImageAttr()).fixImageQuality()
        val quality =
            this.select("div.gmr-qual, div.gmr-quality-item > a").text().trim().replace("-", "")
        return if (quality.isEmpty()) {
            val episode =
                Regex("Episode\\s?([0-9]+)")
                    .find(title)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: this.select("div.gmr-numbeps > span").text().toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addSub(episode)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document =
            app.get("${mainUrl}?s=$query&post_type[]=post&post_type[]=tv", timeout = 50L)
                .document
        val results = document.select("article.item").mapNotNull { it.toSearchResult() }
        return results
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val title = this.selectFirst("a > span.idmuvi-rp-title")?.text()?.trim() ?: return null
        val href = this.selectFirst("a")!!.attr("href")
        val posterUrl = fixUrlNull(this.selectFirst("a > img")?.getImageAttr().fixImageQuality())
        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    /**
     * Fungsi ini melakukan override terhadap fungsi load dari MainAPI.
     * Tujuannya adalah untuk mengambil detail episode dari sebuah TV Series berdasarkan URL yang diberikan.
     *
     * Penjelasan langkah-langkahnya:
     * 1. Memanggil fungsi load dari superclass (super.load(url)) untuk mendapatkan LoadResponse awal.
     * 2. Jika LoadResponse yang didapatkan adalah TvSeriesLoadResponse (berarti konten berupa TV Series),
     *    maka fungsi akan mengambil dokumen HTML dari halaman tersebut.
     * 3. Fungsi kemudian mencari semua elemen <a> yang berada di dalam div dengan class "vid-episodes" atau "gmr-listseries".
     * 4. Setiap elemen <a> tersebut di-mapping menjadi objek Episode dengan mengambil:
     *    - href: URL episode (diperbaiki dengan fixUrl)
     *    - name: judul episode (dari atribut title)
     *    - episode: nomor episode (diambil dari judul dengan regex "Episode (\d+)")
     *    - season: nomor season (diambil dari judul dengan regex "Season (\d+)")
     * 5. Hanya episode yang memiliki nomor episode (episode != null) yang dimasukkan ke dalam daftar episodes.
     * 6. Daftar episodes ini kemudian di-set ke properti episodes pada objek TvSeriesLoadResponse.
     * 7. Fungsi mengembalikan LoadResponse yang sudah diperbarui.
     */
    override suspend fun load(url: String): LoadResponse {
        return super.load(url).apply {
            when (this) {
                is TvSeriesLoadResponse -> {
                    val document = app.get(url).document
                    this.episodes =
                        document.select("div.vid-episodes a, div.gmr-listseries a")
                            .map { eps ->
                                val href = fixUrl(eps.attr("href"))
                                val name = eps.attr("title")
                                val episode =
                                    "Episode\\s*(\\d+)"
                                        .toRegex()
                                        .find(name)
                                        ?.groupValues
                                        ?.get(1)
                                val season =
                                    "Season\\s*(\\d+)"
                                        .toRegex()
                                        .find(name)
                                        ?.groupValues
                                        ?.get(1)
                                Episode(
                                    href,
                                    name,
                                    season = season?.toIntOrNull(),
                                    episode = episode?.toIntOrNull(),
                                )
                            }
                            .filter { it.episode != null }
                }
            }
                        }!!
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        document.select("iframe").getIframeAttr()?.let {
            loadExtractor(it, data, subtitleCallback, callback)
        }
        return true
    }

    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("abs:data-src")
            this.hasAttr("data-lazy-src") -> this.attr("abs:data-lazy-src")
            this.hasAttr("srcset") -> this.attr("abs:srcset").substringBefore(" ")
            else -> this.attr("abs:src")
        }
    }

    private fun Element?.getIframeAttr(): String? {
        return this?.attr("data-litespeed-src").takeIf { it?.isNotEmpty() == true }
            ?: this?.attr("src")
    }

    private fun String?.fixImageQuality(): String? {
        if (this == null) return null
        val regex = Regex("(-\\d*x\\d*)").find(this)?.groupValues?.get(0) ?: return this
        return this.replace(regex, "")
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }

}
