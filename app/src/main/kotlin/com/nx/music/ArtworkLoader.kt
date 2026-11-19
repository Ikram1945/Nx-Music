package com.nx.music

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever

/**
 * Utilitas untuk mengekstraks sampul album (artwork) dari file audio,
 * dengan **downscale sampling** agar hemat memori dan CPU.
 *
 * Sebelumnya artwork didekode dalam resolusi penuh (bisa sangat besar),
 * lalu di-scaled — boros memori dan bikin lag. Sekarang didekode langsung
 * dengan [BitmapFactory.Options.inSampleSize] ke ukuran target.
 */
object ArtworkLoader {

    /** Ukuran sisi terpanjang default (dipakai oleh pemanggil lama). */
    private const val DEFAULT_MAX_SIDE = 512

    /**
     * Membaca sampul album dengan ukuran target default (512 px).
     *
     * @param path lokasi file audio
     * @return bitmap artwork (sudah di-downscale), atau `null`
     */
    @JvmStatic
    fun loadArtwork(path: String?): Bitmap? = loadArtwork(path, DEFAULT_MAX_SIDE)

    /**
     * Membaca sampul album dan menurunkan resolusinya sehingga sisi terpanjang
     * tidak melebihi [maxSide]. Memakai sampling saat decode, sehingga
     * gambar besar tidak pernah didekode penuh (hemat memori).
     *
     * @param path lokasi file audio
     * @param maxSide ukuran maksimum sisi terpanjang (piksel)
     * @return bitmap artwork, atau `null` jika tidak ditemukan
     */
    @JvmStatic
    fun loadArtwork(path: String?, maxSide: Int): Bitmap? {
        if (path.isNullOrEmpty()) return null

        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val data = retriever.embeddedPicture ?: return null

            val opts = BitmapFactory.Options()
            opts.inSampleSize = computeSampleSize(data, maxSide)
            BitmapFactory.decodeByteArray(data, 0, data.size, opts)
        } catch (e: Exception) {
            // file rusak / tidak bisa dibaca
            null
        } finally {
            try {
                retriever?.release()
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * Menghitung `inSampleSize` (pangkat dua) agar sisi terpanjang
     * mendekati [maxSide], dengan melakukan decode bounds ringan
     * (tanpa mengalokasikan pixel) terlebih dahulu.
     */
    private fun computeSampleSize(data: ByteArray, maxSide: Int): Int {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)

        val longSide = maxOf(bounds.outWidth, bounds.outHeight)
        if (longSide <= 0) return 1

        var sample = 1
        while (longSide / sample > maxSide) {
            sample *= 2
        }
        return sample
    }
}
