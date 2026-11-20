package com.nx.music

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Ringkasan putar per kelompok (artis/album): nama, total putar, jumlah lagu. */
data class GroupStat(val name: String, val plays: Int, val songCount: Int)

/** Jumlah putar pada satu hari kalender, untuk grafik aktivitas harian. */
data class DayPlays(val dayName: String, val dateLabel: String, val plays: Int)

/**
 * Penyimpanan statistik & riwayat pemutaran (fitur #5).
 * Menyimpan: total jumlah putar, total waktu mendengarkan, jumlah putar per lagu,
 * dan riwayat lagu yang baru diputar — semuanya di SharedPreferences.
 *
 * Data per lagu disimpan sebagai JSONObject keyed by path:
 *   { title, artist, plays, last } (last = epoch ms terakhir diputar)
 * Riwayat disimpan sebagai JSONArray of path, terbaru di depan. ku
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

        // Pencatatan harian untuk grafik aktivitas 7 hari: tambah hari ini,
        // bersihkan kunci berumur lebih dari 30 hari agar penyimpanan ringkas.
        val daily = readDaily()
        val todayKey = dayKey(now)
        daily.put(todayKey, daily.optInt(todayKey, 0) + 1)
        pruneDaily(daily, now)
        prefs.edit().putString(KEY_DAILY_PLAYS, daily.toString()).apply()

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

    /** Jumlah lagu berbeda yang pernah diputar. */
    val uniqueSongCount: Int get() = readRecords().length()

    /** Jumlah artis berbeda dari lagu-lagu yang pernah diputar. */
    val uniqueArtistCount: Int
        get() {
            val seen = HashSet<String>()
            val records = readRecords()
            val names = records.names() ?: return 0
            for (i in 0 until names.length()) {
                val rec = records.optJSONObject(names.optString(i)) ?: continue
                seen.add(normalize(rec.optString("artist", "")))
            }
            return seen.size
        }

    /** Artis paling sering diputar: agregasi jumlah putar per artis. */
    fun getTopArtists(limit: Int = 5): MutableList<GroupStat> =
        aggregate(limit) { _, rec -> displayValue(rec.optString("artist", "")) }

    /**
     * Album paling sering diputar. Nama album tidak disimpan di statistik,
     * jadi pemanggil menyerahkan peta path -> album dari MediaStore
     * (SongLoader.loadAlbumByPath). Lagu beralbum tak dikenal dilewati.
     */
    fun getTopAlbums(albumByPath: Map<String, String>, limit: Int = 5): MutableList<GroupStat> =
        aggregate(limit) { path, _ -> albumByPath[path]?.takeIf { it != "<unknown>" } }

    /** Agregasi jumlah putar + jumlah lagu per kunci kelompok; null = lewati. */
    private fun aggregate(limit: Int, keyOf: (String, JSONObject) -> String?): MutableList<GroupStat> {
        val plays = HashMap<String, Int>()
        val songs = HashMap<String, Int>()
        val records = readRecords()
        val names = records.names() ?: return ArrayList()
        for (i in 0 until names.length()) {
            val path = names.optString(i)
            val rec = records.optJSONObject(path) ?: continue
            val key = keyOf(path, rec) ?: continue
            plays[key] = (plays[key] ?: 0) + rec.optInt("plays", 0)
            songs[key] = (songs[key] ?: 0) + 1
        }
        val list = ArrayList<GroupStat>()
        for ((name, p) in plays) {
            list.add(GroupStat(name, p, songs[name] ?: 0))
        }
        list.sortWith(compareByDescending { it.plays })
        return if (list.size > limit) ArrayList(list.subList(0, limit)) else list
    }

    /**
     * Jumlah putar [days] hari terakhir termasuk hari ini, terlama dulu.
     * Hari tanpa catatan bernilai 0. Pencatatan harian baru berjalan sejak
     * fitur ini ditambahkan, jadi hari-hari sebelumnya tampil 0.
     */
    fun getDailyPlays(days: Int): MutableList<DayPlays> {
        val result = ArrayList<DayPlays>()
        if (days <= 0) return result
        val daily = readDaily()
        val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayFmt = SimpleDateFormat("EEE", Locale("id", "ID"))
        val dateFmt = SimpleDateFormat("d/M", Locale.US)
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -(days - 1))
        for (i in 0 until days) {
            val time = cal.time
            result.add(
                DayPlays(
                    dayFmt.format(time),
                    dateFmt.format(time),
                    daily.optInt(keyFmt.format(time), 0),
                ),
            )
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    private fun readDaily(): JSONObject =
        try {
            JSONObject(prefs.getString(KEY_DAILY_PLAYS, "{}"))
        } catch (e: JSONException) {
            JSONObject()
        }

    private fun dayKey(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(epochMs))

    /** Buang kunci harian yang lebih tua dari 30 hari (dan kunci tak terbaca). */
    private fun pruneDaily(daily: JSONObject, nowMs: Long) {
        val cutoff = nowMs - 30L * 24L * 3600L * 1000L
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val names = daily.names() ?: return
        val remove = ArrayList<String>()
        for (i in 0 until names.length()) {
            val key = names.optString(i)
            val parsed = try {
                sdf.parse(key)?.time
            } catch (e: Exception) {
                null
            }
            if (parsed == null || parsed < cutoff) {
                remove.add(key)
            }
        }
        for (key in remove) {
            daily.remove(key)
        }
    }

    private fun normalize(value: String): String =
        value.trim().lowercase().ifEmpty { "<unknown>" }

    private fun displayValue(value: String): String =
        value.trim().ifEmpty { "<unknown>" }

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
        private const val KEY_DAILY_PLAYS = "daily_plays"

        private const val MAX_HISTORY = 50
        private const val MAX_TOP = 20

        @Volatile
        private var instance: PlaybackStats? = null

        @JvmStatic
        fun getInstance(context: Context): PlaybackStats =
            instance ?: synchronized(this) { instance ?: PlaybackStats(context).also { instance = it } }
    }
}
