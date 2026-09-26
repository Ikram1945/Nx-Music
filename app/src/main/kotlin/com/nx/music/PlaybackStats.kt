package com.nx.music

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Penyimpanan statistik & riwayat pemutaran (fitur #5).
 * Menyimpan: total jumlah putar, total waktu mendengarkan, jumlah putar per lagu,
 * dan riwayat lagu yang baru diputar — semuanya di SharedPreferences.
 *
 * Data per lagu disimpan sebagai JSONObject keyed by path:
 *   { title, artist, plays, last } (last = epoch ms terakhir diputar)
 * Riwayat disimpan sebagai JSONArray of path, terbaru di depan.
 */
class PlaybackStats private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val totalPlays: Int get() = prefs.getInt(KEY_TOTAL_PLAYS, 0)

    val totalListeningTimeSec: Long get() = prefs.getLong(KEY_TOTAL_TIME_SEC, 0L)

    /** Dicatat saat sebuah lagu mulai diputar. */
    @Synchronized
    fun recordPlayStart(song: Song?) {
        val path = song?.path ?: return
        val now = System.currentTimeMillis()

        val records = readRecords()
        val rec = records.optJSONObject(path)
        var plays = 1
        if (rec == null) {
            records.put(path, JSONObject().apply {
                put("title", song.title ?: "")
                put("artist", song.artist ?: "")
            })
        } else {
            plays = rec.optInt("plays", 0) + 1
        }
        val target = records.optJSONObject(path)!!
        target.put("plays", plays)
        target.put("last", now)

        prefs.edit()
            .putInt(KEY_TOTAL_PLAYS, totalPlays + 1)
            .putString(KEY_RECORDS, records.toString())
            .apply()

        pushHistory(path)
    }

    /** Tambah waktu mendengarkan (detik). Dipanggil berkala dari MusicService. */
    @Synchronized
    fun addListeningTime(seconds: Long) {
        if (seconds <= 0) return
        prefs.edit().putLong(KEY_TOTAL_TIME_SEC, totalListeningTimeSec + seconds).apply()
    }

    /** Menghapus seluruh statistik & riwayat pemutaran (dari layar pengaturan). */
    @Synchronized
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    /** Riwayat pemutaran terbaru (paling baru di depan), dibatasi [MAX_HISTORY]. */
    fun getHistory(): MutableList<PlaybackHistoryEntry> {
        val list = ArrayList<PlaybackHistoryEntry>()
        val history = readHistory()
        val records = readRecords()
        for (i in 0 until history.length()) {
            val path = history.optString(i)
            if (path.isEmpty()) continue
            val rec = records.optJSONObject(path) ?: continue
            list.add(rec.toEntry(path))
        }
        return list
    }

    /** Lagu paling sering diputar, diurutkan menurun, dibatasi [MAX_TOP]. */
    fun getTopPlayed(): MutableList<PlaybackHistoryEntry> {
        val list = ArrayList<PlaybackHistoryEntry>()
        val records = readRecords()
        val names = records.names() ?: return list
        for (i in 0 until names.length()) {
            val path = names.optString(i)
            val rec = records.optJSONObject(path) ?: continue
            list.add(rec.toEntry(path))
        }
        list.sortWith(compareByDescending { it.plays })
        return if (list.size > MAX_TOP) ArrayList(list.subList(0, MAX_TOP)) else list
    }

    /**
     * Riwayat pemutaran sebagai [Song] siap diputar, terbaru di depan.
     * Durasi diisi 0 karena tidak disimpan di riwayat; pemanggil dapat
     * mengisinya dari file bila perlu (di luar thread UI).
     */
    fun getRecentSongs(limit: Int): MutableList<Song> {
        val result = ArrayList<Song>()
        if (limit <= 0) return result
        for (entry in getHistory()) {
            if (result.size >= limit) break
            entry.toSong()?.let { result.add(it) }
        }
        return result
    }

    private fun pushHistory(path: String) {
        val history = readHistory()
        val updated = JSONArray().put(path)
        var i = 0
        while (i < history.length() && updated.length() < MAX_HISTORY) {
            val p = history.optString(i)
            if (p.isNotEmpty() && p != path) updated.put(p)
            i++
        }
        prefs.edit().putString(KEY_HISTORY, updated.toString()).apply()
    }

    private fun readRecords(): JSONObject =
        try {
            JSONObject(prefs.getString(KEY_RECORDS, "{}"))
        } catch (e: JSONException) {
            JSONObject()
        }

    private fun readHistory(): JSONArray =
        try {
            JSONArray(prefs.getString(KEY_HISTORY, "[]"))
        } catch (e: JSONException) {
            JSONArray()
        }

    private fun JSONObject.toEntry(path: String) = PlaybackHistoryEntry(
        optString("title", ""),
        optString("artist", ""),
        path,
        optInt("plays", 0),
        optLong("last", 0L),
    )

    companion object {
        private const val PREFS = "playback_stats_prefs"
        private const val KEY_TOTAL_PLAYS = "total_plays"
        private const val KEY_TOTAL_TIME_SEC = "total_time_sec"
        private const val KEY_RECORDS = "records"
        private const val KEY_HISTORY = "history"

        private const val MAX_HISTORY = 50
        private const val MAX_TOP = 20

        @Volatile
        private var instance: PlaybackStats? = null

        @JvmStatic
        fun getInstance(context: Context): PlaybackStats =
            instance ?: synchronized(this) { instance ?: PlaybackStats(context).also { instance = it } }
    }
}
