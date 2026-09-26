package com.nx.music

import android.content.Context
import android.content.SharedPreferences

/**
 * Penyimpanan daftar putar (playlist) pengguna. Menyimpan beberapa daftar lagu,
 * masing-masing berisi kumpulan lagu (disimpan lengkap: path, judul, artis,
 * durasi) agar daftar tetap tampil walau library berubah.
 *
 * Format penyimpanan di [SharedPreferences] (`playlist_prefs`):
 * nama daftar disimpan di `playlist_names` (StringSet), dan isi tiap
 * daftar disimpan di kunci `list_<nama>` sebagai satu String. Lagu
 * dipisah `\u0002`, field dalam lagu dipisah `\u0001`.
 *
 * Mengikuti pola singleton [FavoritesHelper].
 */
class PlaylistHelper private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Nama-nama daftar putar yang tersimpan (urutan tidak dijamin). */
    val playlistNames: List<String>
        get() = ArrayList(LinkedHashSet(prefs.getStringSet(KEY_NAMES, emptySet()) ?: emptySet()))

    /** Lagu-lagu di dalam sebuah daftar putar (urut sesuai urutan tambah). */
    fun getSongs(name: String): MutableList<Song> {
        val result = ArrayList<Song>()
        val raw = prefs.getString(KEY_PREFIX + name, "") ?: return result
        if (raw.isEmpty()) return result
        for (part in raw.split(SONG_SEP)) {
            if (part.isEmpty()) continue
            val f = part.split(FIELD_SEP)
            if (f.size >= 4) {
                try {
                    result.add(Song(f[1], f[2], f[0], f[3].toLong()))
                } catch (ignored: NumberFormatException) {
                    // abaikan entri rusak
                }
            }
        }
        return result
    }

    /** Membuat daftar putar baru. Mengembalikan `false` jika nama sudah ada. */
    fun createPlaylist(name: String?): Boolean {
        if (name == null || name.trim().isEmpty() || nameExists(name)) return false
        prefs.edit().putStringSet(KEY_NAMES, HashSet(playlistNames) + name).apply()
        return true
    }

    private fun nameExists(name: String): Boolean = playlistNames.contains(name)

    /** Menambahkan satu lagu ke daftar putar jika belum ada di dalamnya. */
    fun addSong(name: String, song: Song?): Boolean {
        if (song?.path == null) return false
        val songs = getSongs(name)
        if (songs.any { song.path == it.path }) return false
        songs.add(song)
        saveSongs(name, songs)
        return true
    }

    /** Menghapus satu lagu dari daftar putar (berdasarkan path). */
    fun removeSong(name: String, path: String?): Boolean {
        if (path == null) return false
        val songs = getSongs(name)
        val removed = songs.removeAll { path == it.path }
        if (removed) saveSongs(name, songs)
        return removed
    }

    /** Apakah sebuah lagu (path) ada di dalam daftar putar. */
    fun hasSong(name: String, path: String?): Boolean {
        if (path == null) return false
        return getSongs(name).any { path == it.path }
    }

    /** Menghapus sebuah daftar putar beserta isinya. */
    fun deletePlaylist(name: String) {
        prefs.edit()
            .putStringSet(KEY_NAMES, HashSet(playlistNames) - name)
            .remove(KEY_PREFIX + name)
            .apply()
    }

    private fun saveSongs(name: String, songs: List<Song>) {
        val sb = StringBuilder()
        for (s in songs) {
            if (sb.isNotEmpty()) sb.append(SONG_SEP)
            sb.append(s.path ?: "").append(FIELD_SEP)
                .append(s.title ?: "").append(FIELD_SEP)
                .append(s.artist ?: "").append(FIELD_SEP)
                .append(s.duration)
        }
        prefs.edit().putString(KEY_PREFIX + name, sb.toString()).apply()
    }

    companion object {
        private const val PREFS = "playlist_prefs"
        private const val KEY_NAMES = "playlist_names"
        private const val KEY_PREFIX = "list_"
        private const val SONG_SEP = '\u0002'
        private const val FIELD_SEP = '\u0001'

        @Volatile
        private var instance: PlaylistHelper? = null

        @JvmStatic
        fun getInstance(context: Context): PlaylistHelper =
            instance ?: synchronized(this) { instance ?: PlaylistHelper(context).also { instance = it } }
    }
}
