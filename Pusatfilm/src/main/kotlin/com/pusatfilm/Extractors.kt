package com.pusatfilm

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

/**
 * Kelas Kotakajaib adalah implementasi dari ExtractorApi yang digunakan untuk mengambil link streaming dari situs kotakajaib.me.
 *
 * Fungsi utama kelas ini adalah:
 * - Mendefinisikan nama extractor ("Kotakajaib"), URL utama, dan bahwa setiap permintaan membutuhkan referer.
 * - Pada fungsi getUrl, kelas ini akan:
 *   1. Melakukan request ke halaman yang diberikan (url) dengan referer jika ada.
 *   2. Mengambil semua elemen <a> di dalam <ul id="dropdown-server"> (biasanya berisi daftar server streaming).
 *   3. Untuk setiap elemen <a>, mengambil atribut "data-frame" (yang berisi link streaming dalam bentuk base64).
 *   4. Mendekode base64 tersebut untuk mendapatkan link asli.
 *   5. Memanggil fungsi loadExtractor untuk memproses link streaming dan mengirimkan hasilnya melalui callback.
 *   6. Juga menangani subtitle jika ada melalui subtitleCallback.
 */
open class Kotakajaib : ExtractorApi() {
    override val name = "Kotakajaib"
    override val mainUrl = "https://kotakajaib.me"
    override val requiresReferer = true

    /**
     * Fungsi ini digunakan untuk mengambil semua link streaming dari halaman kotakajaib.
     * @param url URL halaman yang ingin diambil link streaming-nya.
     * @param referer Referer yang diperlukan untuk request (biasanya URL asal).
     * @param subtitleCallback Callback untuk mengirimkan file subtitle jika ada.
     * @param callback Callback untuk mengirimkan ExtractorLink (link streaming) yang ditemukan.
     */
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Ambil dokumen HTML dari url dengan referer
        app.get(url, referer = referer).document
            // Pilih semua elemen <a> di dalam <ul id="dropdown-server">
            .select("ul#dropdown-server li a")
            // Untuk setiap elemen, lakukan proses secara asynchronous
            .apmap {
                // Ambil link streaming yang masih dalam bentuk base64 dari atribut data-frame
                val frameUrl = base64Decode(it.attr("data-frame"))
                // Proses link streaming menggunakan loadExtractor
                loadExtractor(
                    frameUrl,
                    "$mainUrl/",
                    subtitleCallback,
                    callback
                )
            }
    }
}
