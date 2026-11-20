package com.nx.music

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Layar statistik & riwayat pemutaran (fitur #5).
 * Menampilkan total putar, total waktu mendengarkan, lagu paling sering diputar,
 * lagu paling lama didengar, dan riwayat lagu yang baru diputar.
 * Data dibaca dari [PlaybackStats].
 */
class StatisticsActivity : AppCompatActivity() {

    private lateinit var txtTotalPlays: TextView
    private lateinit var txtTotalTime: TextView
    private lateinit var txtUniqueSongs: TextView
    private lateinit var txtUniqueArtists: TextView
    private lateinit var txtAvgListen: TextView
    private lateinit var txtTopHeader: TextView
    private lateinit var txtLongestHeader: TextView
    private lateinit var txtTopArtistsHeader: TextView
    private lateinit var txtTopAlbumsHeader: TextView
    private lateinit var txtHistoryHeader: TextView
    private lateinit var weekContainer: LinearLayout
    private lateinit var topContainer: LinearLayout
    private lateinit var longestContainer: LinearLayout
    private lateinit var artistsContainer: LinearLayout
    private lateinit var albumsContainer: LinearLayout
    private lateinit var historyContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics)

        ThemeHelper.applyThemeToActivity(this)

        txtTotalPlays = findViewById(R.id.txt_total_plays)
        txtTotalTime = findViewById(R.id.txt_total_time)
        txtUniqueSongs = findViewById(R.id.txt_unique_songs)
        txtUniqueArtists = findViewById(R.id.txt_unique_artists)
        txtAvgListen = findViewById(R.id.txt_avg_listen)
        txtTopHeader = findViewById(R.id.txt_top_header)
        txtLongestHeader = findViewById(R.id.txt_longest_header)
        txtTopArtistsHeader = findViewById(R.id.txt_top_artists_header)
        txtTopAlbumsHeader = findViewById(R.id.txt_top_albums_header)
        txtHistoryHeader = findViewById(R.id.txt_history_header)
        weekContainer = findViewById(R.id.week_container)
        topContainer = findViewById(R.id.top_container)
        longestContainer = findViewById(R.id.longest_container)
        artistsContainer = findViewById(R.id.artists_container)
        albumsContainer = findViewById(R.id.albums_container)
        historyContainer = findViewById(R.id.history_container)

        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        btnBack.setOnClickListener { finish() }

        populate()
    }

    private fun populate() {
        val stats = PlaybackStats.getInstance(this)

        txtTotalPlays.text = stats.totalPlays.toString()
        txtTotalTime.text = formatDuration(stats.totalListeningTimeSec)

        // Rincian: cakupan data + rata-rata waktu dengar per putaran.
        txtUniqueSongs.text = stats.uniqueSongCount.toString()
        txtUniqueArtists.text = stats.uniqueArtistCount.toString()
        val avgSec = if (stats.totalPlays > 0) {
            stats.totalListeningTimeSec / stats.totalPlays
        } else {
            0L
        }
        txtAvgListen.text = formatAvgDuration(avgSec)

        buildWeekChart(stats.getDailyPlays(7))
        populateArtists(stats.getTopArtists(5))

        // Album teratas perlu query MediaStore (path -> album), jadi jalan di
        // thread latar seperti pola AlbumActivity, lalu hasilnya ke UI.
        Thread {
            val albums = stats.getTopAlbums(SongLoader.loadAlbumByPath(this), 5)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    populateAlbums(albums)
                }
            }
        }.start()

        val top = stats.getTopPlayed()
        val history = stats.getHistory()

        // Batasi tampilan agar layar tak jadi scroll panjang tak berujung.
        val topCount = minOf(top.size, 5)
        val historyCount = minOf(history.size, 10)

        if (topCount == 0) {
            txtTopHeader.visibility = View.GONE
            addEmptyRow(topContainer)
        } else {
            // Bar chart: 5 lagu teratas sebagai bar horizontal proporsional.
            var maxPlays = 1
            for (i in 0 until topCount) {
                maxPlays = maxOf(maxPlays, top[i].plays)
            }
            for (i in 0 until topCount) {
                addTopChartRow(topContainer, top[i], top[i].plays, maxPlays, i)
            }
        }

        populateLongest(stats.getLongestListened(5))

        if (historyCount == 0) {
            txtHistoryHeader.visibility = View.GONE
            addEmptyRow(historyContainer)
        } else {
            for (i in 0 until historyCount) {
                addHistoryRow(historyContainer, i, history[i])
            }
        }
    }

    /** Baris bar chart untuk satu lagu: peringkat + judul + bar proporsional. */
    private fun addTopChartRow(
        parent: LinearLayout,
        entry: PlaybackHistoryEntry,
        plays: Int,
        maxPlays: Int,
        rank: Int,
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }

        // Nomor peringkat dalam lingkaran berwarna tema.
        val rankTv = TextView(this).apply {
            text = (rank + 1).toString()
            setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.white))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_icon_circle)
        }
        row.addView(rankTv, LinearLayout.LayoutParams(dp(26), dp(26)).apply {
            setMargins(0, 0, dp(12), 0)
        })

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Baris atas: judul + jumlah putar
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleTv = TextView(this).apply {
            text = if (entry.title.isEmpty()) getString(R.string.unknown_title) else entry.title
            setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.on_surface))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        head.addView(titleTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val countTv = TextView(this).apply {
            text = getString(R.string.stat_plays_count, plays)
            setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.primary))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        head.addView(
            countTv,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        col.addView(head)

        // Bar: FrameLayout berisi track (latar penuh) + fill (bar kiri proporsional).
        val barArea = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(16)).apply {
                setMargins(0, dp(6), 0, 0)
            }
        }
        col.addView(barArea)

        val track = View(this).apply {
            setBackgroundResource(R.drawable.bg_bar_track)
        }
        barArea.addView(
            track,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val fill = View(this).apply {
            setBackgroundResource(R.drawable.bg_bar_fill)
            // Tiga teratas diberi warna lebih tegas agar mudah dibedakan.
            backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    this@StatisticsActivity,
                    if (rank < 3) R.color.primary else R.color.primary_gradient_end,
                ),
            )
        }
        barArea.addView(
            fill,
            FrameLayout.LayoutParams(dp(6), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START),
        )

        // Lebar bar dihitung setelah area terukur. Sebelumnya dipakai
        // widthPixels dikurangi padding tebakan, dan itu membuat bar meluber
        // keluar kartu karena padding container tidak ikut diperhitungkan.
        barArea.post {
            val available = barArea.width
            if (available <= 0) {
                return@post
            }
            var target = (plays.toFloat() / maxPlays.toFloat() * available).toInt()
            if (target < dp(6)) {
                target = dp(6)
            }
            val lp = fill.layoutParams
            lp.width = target
            fill.layoutParams = lp
        }

        row.addView(col)
        parent.addView(row)
        row.setOnClickListener { playSong(entry) }
    }

    private fun populateLongest(songs: List<PlaybackHistoryEntry>) {
        if (songs.isEmpty()) {
            txtLongestHeader.visibility = View.GONE
            addEmptyRow(longestContainer)
            return
        }
        var maxSec = 1L
        for (e in songs) {
            maxSec = maxOf(maxSec, e.listenSec)
        }
        for (i in songs.indices) {
            addLongestRow(longestContainer, songs[i], maxSec, i)
        }
    }

    /** Baris bar untuk satu lagu terlama didengar: peringkat + judul + total waktu + bar. */
    private fun addLongestRow(
        parent: LinearLayout,
        entry: PlaybackHistoryEntry,
        maxSec: Long,
        rank: Int,
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }

        val rankTv = TextView(this).apply {
            text = (rank + 1).toString()
            setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.white))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_icon_circle)
        }
        row.addView(rankTv, LinearLayout.LayoutParams(dp(26), dp(26)).apply {
            setMargins(0, 0, dp(12), 0)
        })

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleTv = TextView(this).apply {
            text = if (entry.title.isEmpty()) getString(R.string.unknown_title) else entry.title
            setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.on_surface))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        head.addView(titleTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val timeTv = TextView(this).apply {
            text = formatListenDuration(entry.listenSec)
            setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.primary))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        head.addView(timeTv)
        col.addView(head)

        val subTv = TextView(this).apply {
            text = if (entry.artist.isEmpty()) getString(R.string.unknown_artist) else entry.artist
            setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.on_surface_variant))
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        col.addView(subTv)

        val barArea = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(16)).apply {
                setMargins(0, dp(6), 0, 0)
            }
        }
        val track = View(this).apply {
            setBackgroundResource(R.drawable.bg_bar_track)
        }
        barArea.addView(
            track,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        val fill = View(this).apply {
            setBackgroundResource(R.drawable.bg_bar_fill)
            backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    this@StatisticsActivity,
                    if (rank < 3) R.color.primary else R.color.primary_gradient_end,
                ),
            )
        }
        barArea.addView(
            fill,
            FrameLayout.LayoutParams(dp(6), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START),
        )
        barArea.post {
            val available = barArea.width
            if (available > 0) {
                var target = (entry.listenSec.toFloat() / maxSec.toFloat() * available).toInt()
                if (target < dp(6)) {
                    target = dp(6)
                }
                val lp = fill.layoutParams
                lp.width = target
                fill.layoutParams = lp
            }
        }
        col.addView(barArea)

        row.addView(col)
        parent.addView(row)
        row.setOnClickListener { playSong(entry) }
    }

    /** Total waktu dengar satu lagu: jam+mnt bila >= 1 jam, mnt+dtk di bawahnya, dtk bila < 1 mnt. */
    private fun formatListenDuration(totalSec: Long): String {
        val hours = totalSec / 3600L
        val minutes = (totalSec % 3600L) / 60L
        val seconds = totalSec % 60L
        return when {
            hours > 0 -> getString(R.string.stat_time_hours, hours, minutes)
            minutes > 0 -> if (seconds > 0) {
                getString(R.string.stat_avg_min_sec, minutes, seconds)
            } else {
                getString(R.string.stat_time_minutes, minutes)
            }
            else -> getString(R.string.stat_seconds, seconds)
        }
    }

    private fun populateArtists(artists: List<GroupStat>) {
        if (artists.isEmpty()) {
            txtTopArtistsHeader.visibility = View.GONE
            addEmptyRow(artistsContainer)
            return
        }
        var maxPlays = 1
        for (g in artists) {
            maxPlays = maxOf(maxPlays, g.plays)
        }
        for (i in artists.indices) {
            addGroupBarRow(artistsContainer, artists[i], maxPlays, i)
        }
    }

    private fun populateAlbums(albums: List<GroupStat>) {
        if (albums.isEmpty()) {
            txtTopAlbumsHeader.visibility = View.GONE
            addEmptyRow(albumsContainer)
            return
        }
        var maxPlays = 1
        for (g in albums) {
            maxPlays = maxOf(maxPlays, g.plays)
        }
        for (i in albums.indices) {
            addGroupBarRow(albumsContainer, albums[i], maxPlays, i)
        }
    }

    /** Baris bar untuk satu kelompok (artis/album): peringkat + nama + bar. */
    private fun addGroupBarRow(parent: LinearLayout, group: GroupStat, maxPlays: Int, rank: Int) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }

        val rankTv = TextView(this).apply {
            text = (rank + 1).toString()
            setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.white))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_icon_circle)
        }
        row.addView(rankTv, LinearLayout.LayoutParams(dp(26), dp(26)).apply {
            setMargins(0, 0, dp(12), 0)
        })

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val nameTv = TextView(this).apply {
            text = group.name
            setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.on_surface))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        head.addView(nameTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val countTv = TextView(this).apply {
            text = getString(R.string.stat_plays_count, group.plays)
            setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.primary))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        head.addView(countTv)
        col.addView(head)

        val subTv = TextView(this).apply {
            text = getString(R.string.playlist_count_song, group.songCount)
            setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.on_surface_variant))
            textSize = 12f
        }
        col.addView(subTv)

        val barArea = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(16)).apply {
                setMargins(0, dp(6), 0, 0)
            }
        }
        val track = View(this).apply {
            setBackgroundResource(R.drawable.bg_bar_track)
        }
        barArea.addView(
            track,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        val fill = View(this).apply {
            setBackgroundResource(R.drawable.bg_bar_fill)
            backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    this@StatisticsActivity,
                    if (rank < 3) R.color.primary else R.color.primary_gradient_end,
                ),
            )
        }
        barArea.addView(
            fill,
            FrameLayout.LayoutParams(dp(6), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START),
        )
        barArea.post {
            val available = barArea.width
            if (available > 0) {
                var target = (group.plays.toFloat() / maxPlays.toFloat() * available).toInt()
                if (target < dp(6)) {
                    target = dp(6)
                }
                val lp = fill.layoutParams
                lp.width = target
                fill.layoutParams = lp
            }
        }
        col.addView(barArea)

        row.addView(col)
        parent.addView(row)
    }

    /** Grafik kolom jumlah putar per hari untuk 7 hari terakhir. */
    private fun buildWeekChart(days: List<DayPlays>) {
        var maxPlays = 1
        for (d in days) {
            maxPlays = maxOf(maxPlays, d.plays)
        }
        for (day in days) {
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            weekContainer.addView(
                col,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )

            val valueTv = TextView(this).apply {
                text = day.plays.toString()
                setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.primary))
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            col.addView(valueTv)

            val barArea = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(72)).apply {
                    setMargins(0, dp(4), 0, dp(4))
                }
            }
            val track = View(this).apply {
                setBackgroundResource(R.drawable.bg_bar_track)
            }
            barArea.addView(
                track,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            val fill = View(this).apply {
                setBackgroundResource(R.drawable.bg_bar_fill)
            }
            barArea.addView(
                fill,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    0,
                    Gravity.BOTTOM,
                ),
            )
            barArea.post {
                val available = barArea.height
                if (available > 0) {
                    var target = (day.plays.toFloat() / maxPlays.toFloat() * available).toInt()
                    if (day.plays > 0 && target < dp(3)) {
                        target = dp(3)
                    }
                    val lp = fill.layoutParams
                    lp.height = target
                    fill.layoutParams = lp
                }
            }
            col.addView(barArea)

            val dayTv = TextView(this).apply {
                text = day.dayName
                setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.on_surface))
                textSize = 11f
                gravity = Gravity.CENTER
            }
            col.addView(dayTv)

            val dateTv = TextView(this).apply {
                text = day.dateLabel
                setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.on_surface_variant))
                textSize = 10f
                gravity = Gravity.CENTER
            }
            col.addView(dateTv)
        }
    }

    /** Rata-rata dengar per putar; detik ditampilkan bila di bawah 1 menit. */
    private fun formatAvgDuration(totalSec: Long): String {
        if (totalSec < 60) {
            return getString(R.string.stat_seconds, totalSec)
        }
        val minutes = totalSec / 60L
        val seconds = totalSec % 60L
        return if (seconds > 0) {
            getString(R.string.stat_avg_min_sec, minutes, seconds)
        } else {
            getString(R.string.stat_time_minutes, minutes)
        }
    }

    private fun addHistoryRow(parent: LinearLayout, index: Int, entry: PlaybackHistoryEntry) {
        val row = makeRow(index, entry.title, entry.artist, formatRelativeTime(entry.lastPlayedAt))
        row.setOnClickListener { playSong(entry) }
        parent.addView(row)
    }

    private fun makeRow(index: Int, title: String?, subtitle: String?, meta: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            // Ripple bawaan tema (bukan list_selector_background) agar efek ketuk
            // konsisten dengan daftar lain di aplikasi ini.
            val tv = TypedValue()
            if (theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
                setBackgroundResource(tv.resourceId)
            }
        }

        val idx = TextView(this).apply {
            text = (index + 1).toString()
            setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.white))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_icon_circle)
        }
        row.addView(idx, LinearLayout.LayoutParams(dp(24), dp(24)).apply {
            setMargins(0, 0, dp(12), 0)
        })

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleTv = TextView(this).apply {
            text = if (title.isNullOrEmpty()) getString(R.string.unknown_title) else title
            setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.on_surface))
            textSize = 15f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        texts.addView(titleTv)

        val subtitleTv = TextView(this).apply {
            text = if (subtitle.isNullOrEmpty()) getString(R.string.unknown_artist) else subtitle
            setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.on_surface_variant))
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        texts.addView(subtitleTv)

        row.addView(texts)

        val metaTv = TextView(this).apply {
            text = meta
            setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.primary))
            textSize = 13f
        }
        row.addView(metaTv)

        return row
    }

    private fun addEmptyRow(parent: LinearLayout) {
        val empty = TextView(this).apply {
            setText(R.string.stat_empty)
            setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.on_surface_variant))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(24))
        }
        parent.addView(empty)
    }

    private fun playSong(entry: PlaybackHistoryEntry) {
        // Cari lagu di playlist berdasarkan path, lalu putar.
        val playlist = PlaybackController.getInstance()?.playlist ?: return
        for (i in playlist.indices) {
            val s = playlist[i]
            if (s.path == entry.path) {
                PlaybackController.getInstance().playAt(i)
                startActivity(Intent(this, PlayerActivity::class.java))
                finish()
                return
            }
        }
    }

    private fun formatDuration(totalSec: Long): String {
        val hours = totalSec / 3600L
        val minutes = (totalSec % 3600L) / 60L
        return if (hours > 0) {
            getString(R.string.stat_time_hours, hours, minutes)
        } else {
            getString(R.string.stat_time_minutes, minutes)
        }
    }

    private fun formatRelativeTime(epochMs: Long): String {
        val diff = System.currentTimeMillis() - epochMs
        val minutes = diff / 60000L
        if (minutes < 1) {
            return getString(R.string.stat_just_now)
        } else if (minutes < 60) {
            return getString(R.string.stat_minutes_ago, minutes)
        }
        val hours = minutes / 60L
        if (hours < 24) {
            return getString(R.string.stat_hours_ago, hours)
        }
        val days = hours / 24L
        return getString(R.string.stat_days_ago, days)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
