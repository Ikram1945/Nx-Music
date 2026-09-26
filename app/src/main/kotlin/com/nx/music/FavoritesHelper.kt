package com.nx.music

import android.content.Context
import android.content.SharedPreferences

/**
 * Penyimpanan lagu favorit. Menyimpan kumpulan `path` lagu yang disukai
 * ke [SharedPreferences] agar bertahan antar sesi.
 *
 * Kunci favorit adalah `path` (lokasi file audio) karena unik per lagu
 * dan tidak memerlukan perubahan model [Song].
 *
 * Set favorit di-cache di memori dan hanya dibaca dari disk sekali,
 * agar [SongAdapter.getView] tidak melakukan pembacaan disk berulang
 * saat scroll (sumber lag pada daftar lagu besar).
 */
class FavoritesHelper private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    /** Set favorit di-cache; diperbarui saat toggle agar isFavorite() cepat. */
    private var cachedSet: MutableSet<String> =
        HashSet(prefs.getStringSet(KEY_SET, emptySet()) ?: emptySet())

    /** Kumpulan path lagu yang disukai (salinan; tidak boleh dimodifikasi langsung). */
    fun getFavorites(): Set<String> = HashSet(cachedSet)

    /** Apakah sebuah lagu (berdasarkan path) sedang difavoritkan (baca dari cache). */
    fun isFavorite(path: String?): Boolean = path != null && cachedSet.contains(path)

    /**
     * Membalik status favorit sebuah lagu.
     *
     * @return `true` jika lagu sekarang favorit, `false` jika tidak.
     */
    fun toggle(path: String?): Boolean {
        if (path == null) return false
        val nowFavorite = if (cachedSet.contains(path)) {
            cachedSet.remove(path)
            false
        } else {
            cachedSet.add(path)
            true
        }
        // SharedPreferences tidak mendukung modifikasi langsung pada set yang sama;
        // selalu tulis salinan baru, lalu perbarui cache agar isFavorite() tetap cepat.
        prefs.edit().putStringSet(KEY_SET, HashSet(cachedSet)).apply()
        return nowFavorite
    }

    companion object {
        private const val PREFS = "favorites_prefs"
        private const val KEY_SET = "favorite_paths"

        @Volatile
        private var instance: FavoritesHelper? = null

        @JvmStatic
        fun getInstance(context: Context): FavoritesHelper =
            instance ?: synchronized(this) { instance ?: FavoritesHelper(context).also { instance = it } }
    }
}
