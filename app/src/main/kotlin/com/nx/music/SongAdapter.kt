package com.nx.music

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ikram.system.TimeUtils
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Adapter untuk menampilkan daftar lagu di ListView.
 * Menyorot lagu yang sedang diputar dengan warna primary.
 * Mendukung pencarian: [filter] membatasi lagu yang tampil
 * sesuai kata kunci pada judul atau artis.
 * Menampilkan tombol favorit (hati) per baris serta filter "hanya favorit".
 */
class SongAdapter(context: Context, songs: List<Song>?) : BaseAdapter() {

    companion object {
        /** Sisi terpanjang thumbnail daftar (px). Cukup untuk baris 56dp. */
        private const val ART_THUMB_SIDE = 160
    }

    private val appContext: Context = context
    private val favorites: FavoritesHelper = FavoritesHelper.getInstance(context)

    /** Semua lagu (sumber asli, tanpa filter). */
    private val originalSongs = ArrayList<Song>()
    /** Lagu yang sedang ditampilkan (setelah difilter). */
    private val filteredSongs = ArrayList<Song>()

    private var highlightIndex = -1
    /** Jika `true`, hanya tampilkan lagu favorit. */
    private var favoritesOnlyMode = false
    /** Kata kunci pencarian terakhir (dipakai ulang saat toggle filter favorit). */
    private var lastQuery: String? = null
    private var favoriteToggleListener: FavoriteToggleListener? = null

    /** Handler main thread untuk memasang bitmap artwork hasil decode background. */
    private val handler = Handler(Looper.getMainLooper())
    /** Worker untuk decode artwork; hindari satu Thread per baris. */
    private var artWorker: ExecutorService? = Executors.newFixedThreadPool(2)
    /** Cache bitmap di memori agar gulir tidak mengekstrak ulang file yang sama. */
    private val artCache = object : LruCache<String, Bitmap>(128) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    init {
        setSongs(songs)
    }

    /** Menerima pemberitahuan saat favorit di-toggle oleh pengguna. */
    fun setOnFavoriteToggleListener(listener: FavoriteToggleListener?) {
        favoriteToggleListener = listener
    }

    /** Menandai indeks lagu yang sedang diputar untuk penyorotan. */
    fun setCurrentIndex(index: Int) {
        highlightIndex = index
        notifyDataSetChanged()
    }

    /** Mengganti daftar lagu yang ditampilkan (menyalin isi list). */
    fun setSongs(songs: List<Song>?) {
        originalSongs.clear()
        if (songs != null) originalSongs.addAll(songs)
        lastQuery = null
        applyFilter(null)
        // Wajib: pemanggil tak selalu memanggil filter()/notify sendiri (mis. layar
        // Folder) -> tanpa ini daftar tampil kosong walau getCount() sudah benar.
        notifyDataSetChanged()
    }

    /**
     * Memfilter daftar lagu berdasarkan kata kunci. Kosong/null menampilkan semua.
     * Mengembalikan jumlah hasil.
     */
    fun filter(query: String?): Int {
        lastQuery = query
        applyFilter(query)
        notifyDataSetChanged()
        return count
    }

    /** Mengaktifkan/mematikan mode "hanya favorit" lalu memfilter ulang. */
    fun setFavoritesOnly(enabled: Boolean) {
        favoritesOnlyMode = enabled
        applyFilter(lastQuery)
        notifyDataSetChanged()
    }

    /** Apakah mode "hanya favorit" sedang aktif. */
    fun isFavoritesOnly(): Boolean = favoritesOnlyMode

    private fun applyFilter(query: String?) {
        filteredSongs.clear()
        val hasQuery = !query.isNullOrBlank()
        val lower = if (hasQuery) query!!.trim().lowercase(Locale.getDefault()) else null

        for (song in originalSongs) {
            if (favoritesOnlyMode && !favorites.isFavorite(song.path)) continue
            if (hasQuery) {
                val title = song.title?.lowercase(Locale.getDefault()) ?: ""
                val artist = song.artist?.lowercase(Locale.getDefault()) ?: ""
                if (!title.contains(lower!!) && !artist.contains(lower)) continue
            }
            filteredSongs.add(song)
        }
    }

    /**
     * Memetakan posisi dalam daftar yang tampil ke indeks asli (di playlist).
     * Digunakan saat memutar lagu hasil pencarian.
     */
    fun getOriginalPosition(filteredPosition: Int): Int {
        if (filteredPosition < 0 || filteredPosition >= filteredSongs.size) {
            return filteredPosition
        }
        return originalSongs.indexOf(filteredSongs[filteredPosition])
    }

    override fun getCount(): Int = filteredSongs.size

    override fun getItem(position: Int): Song = filteredSongs[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var row = convertView
        if (row == null || row.findViewById<View>(R.id.item_title) == null) {
            row = LayoutInflater.from(appContext).inflate(R.layout.item_song, parent, false)
        }

        val song = getItem(position)

        val itemTitle: TextView = row!!.findViewById(R.id.item_title)
        val itemArtist: TextView = row.findViewById(R.id.item_artist)
        val itemDuration: TextView = row.findViewById(R.id.item_duration)
        val itemPlaying: TextView = row.findViewById(R.id.item_playing)
        val btnFavorite: ImageButton = row.findViewById(R.id.btn_favorite)

        itemTitle.text = song.title ?: ""

        val artist = if (song.artist.isNullOrBlank()) {
            appContext.getString(R.string.unknown_artist)
        } else {
            song.artist ?: ""
        }
        itemArtist.text = artist

        itemDuration.text = TimeUtils.format(song.duration)

        // sorot lagu yang sedang diputar
        val isCurrent = position == highlightIndex
        itemTitle.setTextColor(
            ContextCompat.getColor(appContext, if (isCurrent) R.color.primary else R.color.on_surface)
        )
        itemPlaying.visibility = if (isCurrent) View.VISIBLE else View.GONE

        bindArtwork(row.findViewById(R.id.item_art), song.path)

        // tombol favorit: hati terisi (navy) jika favorit, outline (abu) jika tidak
        val isFav = favorites.isFavorite(song.path)
        btnFavorite.setImageResource(
            if (isFav) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        )
        btnFavorite.setColorFilter(
            ContextCompat.getColor(
                appContext,
                if (isFav) R.color.primary else R.color.on_surface_variant
            )
        )
        btnFavorite.contentDescription = appContext.getString(
            if (isFav) R.string.remove_favorite else R.string.add_favorite
        )
        btnFavorite.setOnClickListener {
            favorites.toggle(song.path)
            favoriteToggleListener?.onFavoriteToggled(song)
        }

        return row
    }

    /**
     * Memuat thumbnail sampul album di background lalu memasangnya bila baris
     * belum didaur ulang untuk lagu lain. Placeholder ikon note dipasang lebih
     * dulu supaya baris daur ulang tidak sempat menampilkan artwork lagu lama.
     *
     * Decode dijalankan pada worker (bukan satu Thread per baris)
     * dan hasilnya di-cache, supaya `getView` tetap ringan saat digulir.
     */
    private fun bindArtwork(art: ImageView, path: String?) {
        art.tag = path
        val cached = if (path != null) artCache.get(path) else null
        if (cached != null) {
            applyArtwork(art, cached)
            return
        }
        showPlaceholder(art)
        val worker = artWorker ?: return
        if (worker.isShutdown) return
        worker.execute {
            val bitmap = ArtworkLoader.loadArtwork(path, ART_THUMB_SIDE)
            if (bitmap == null) return@execute
            if (path != null) artCache.put(path, bitmap)
            handler.post {
                if (path == art.tag) {
                    applyArtwork(art, bitmap)
                }
            }
        }
    }

    private fun applyArtwork(art: ImageView, bitmap: Bitmap) {
        art.imageTintList = null
        art.setImageBitmap(bitmap)
        art.scaleType = ImageView.ScaleType.CENTER_CROP
        art.setPadding(0, 0, 0, 0)
    }

    private fun showPlaceholder(art: ImageView) {
        art.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(appContext, R.color.white)
        )
        art.setImageResource(R.drawable.ic_music_note)
        art.scaleType = ImageView.ScaleType.CENTER_INSIDE
        val pad = Math.round(16 * appContext.resources.displayMetrics.density)
        art.setPadding(pad, pad, pad, pad)
    }

    /** Menghentikan worker artwork. Panggil saat activity pemakai memang dibuang. */
    fun shutdown() {
        artWorker?.shutdown()
        artWorker = null
    }
}
