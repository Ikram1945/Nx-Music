package com.nx.music

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ikram.system.TimeUtils

/**
 * Layar isi sebuah daftar putar: menampilkan lagu-lagu di dalamnya, memutar,
 * menambah lagu dari library, dan menghapus lagu dari daftar.
 */
class PlaylistDetailActivity : AppCompatActivity() {

    private lateinit var playlists: PlaylistHelper
    private var playlistName: String = ""
    private val songs: MutableList<Song> = mutableListOf()
    private lateinit var adapter: SongAdapterSongs
    private lateinit var txtEmpty: TextView
    private lateinit var txtTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_detail)
        ThemeHelper.applyThemeToActivity(this)

        playlistName = intent.getStringExtra(EXTRA_NAME) ?: ""
        playlists = PlaylistHelper.getInstance(this)

        txtTitle = findViewById(R.id.txt_title)
        txtEmpty = findViewById(R.id.txt_empty)
        val list = findViewById<ListView>(R.id.list_songs)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_add_song).setOnClickListener { showAddSongDialog() }

        adapter = SongAdapterSongs(this)
        list.adapter = adapter

        list.setOnItemClickListener { _, _, position, _ ->
            val p = playlists.getSongs(playlistName)
            if (p.isEmpty()) {
                return@setOnItemClickListener
            }
            PlaybackController.create(this).setPlaylist(p)
            PlaybackController.create(this).playAt(position)
            finish()
        }

        reload()
    }

    private fun reload() {
        txtTitle.text = playlistName
        songs.clear()
        songs.addAll(playlists.getSongs(playlistName))
        adapter.notifyDataSetChanged()
        txtEmpty.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Dialog memilih lagu dari library untuk ditambahkan ke daftar putar. */
    private fun showAddSongDialog() {
        val loading = AlertDialog.Builder(this)
            .setTitle(R.string.playlist_add_song)
            .setMessage(R.string.loading)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        loading.show()
        Thread {
            val library = SongLoader.loadSongs(this)
            runOnUiThread {
                loading.dismiss()
                // hanya tampilkan lagu yang belum ada di daftar
                val candidates = library.filter { !playlists.hasSong(playlistName, it.path) }
                if (candidates.isEmpty()) {
                    return@runOnUiThread
                }
                val items = candidates.map { it.title + " — " + (it.artist ?: "") }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle(R.string.playlist_add_song)
                    .setItems(items) { _, which ->
                        playlists.addSong(playlistName, candidates[which])
                        reload()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }.start()
    }

    /** Adapter daftar lagu dalam playlist (judul, artis, durasi, tombol hapus). */
    private inner class SongAdapterSongs(context: Context) : BaseAdapter() {
        private val inflater = LayoutInflater.from(context)

        override fun getCount(): Int = songs.size

        override fun getItem(position: Int): Song = songs[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: inflater.inflate(R.layout.item_playlist_song, parent, false)
            val song = getItem(position)

            row.findViewById<TextView>(R.id.pls_title).text = song.title ?: ""
            row.findViewById<TextView>(R.id.pls_artist).text =
                if (song.artist.isNullOrBlank()) getString(R.string.unknown_artist) else song.artist ?: ""
            row.findViewById<TextView>(R.id.pls_duration).text = TimeUtils.format(song.duration)

            row.findViewById<ImageButton>(R.id.btn_remove_song).setOnClickListener {
                playlists.removeSong(playlistName, song.path)
                reload()
            }
            return row
        }
    }

    companion object {
        const val EXTRA_NAME = "playlist_name"
    }
}
