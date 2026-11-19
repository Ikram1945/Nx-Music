package com.nx.music

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Layar Album: daftar putar otomatis dari album di perangkat (kolom ALBUM
 * MediaStore, lewat [SongLoader.loadAlbums]). Pola sama dengan
 * [FolderActivity]; tombol tukar di header membuka [ArtistActivity].
 */
class AlbumActivity : AppCompatActivity() {

    private lateinit var listAlbums: ListView
    private lateinit var txtEmpty: TextView
    private val albums: MutableList<SongLoader.Album> = mutableListOf()
    private lateinit var adapter: AlbumAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_album)
        ThemeHelper.applyThemeToActivity(this)

        listAlbums = findViewById(R.id.list_playlists)
        txtEmpty = findViewById(R.id.txt_empty)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_switch).setOnClickListener {
            startActivity(Intent(this, ArtistActivity::class.java))
            finish()
        }

        adapter = AlbumAdapter(this)
        listAlbums.adapter = adapter
        listAlbums.setOnItemClickListener { _, _, position, _ ->
            val a = albums[position]
            startActivity(
                Intent(this, AlbumDetailActivity::class.java)
                    .putExtra(AlbumDetailActivity.EXTRA_NAME, a.name)
                    .putExtra(AlbumDetailActivity.EXTRA_ARTIST, a.artist),
            )
        }

        setupBottomNav()
        load()
    }

    /** Navigasi bawah konsisten; tak ada tab Album, jadi tab aktif = Perpustakaan. */
    private fun setupBottomNav() {
        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        nav.selectedItemId = R.id.nav_library
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                R.id.nav_playlist -> {
                    startActivity(Intent(this, PlaylistActivity::class.java))
                    finish()
                }
                R.id.nav_folder -> {
                    startActivity(Intent(this, FolderActivity::class.java))
                    finish()
                }
            }
            true // nav_library: tetap di sini
        }
    }

    private fun load() {
        // Query MediaStore di background; library besar tidak membekukan UI.
        Thread {
            val result = SongLoader.loadAlbums(this)
            runOnUiThread {
                albums.clear()
                albums.addAll(result)
                adapter.notifyDataSetChanged()
                txtEmpty.visibility = if (albums.isEmpty()) View.VISIBLE else View.GONE
            }
        }.start()
    }

    /** Adapter daftar album (ikon album, nama, jumlah lagu + artis, tombol putar). */
    private inner class AlbumAdapter(context: Context) : BaseAdapter() {
        private val inflater = LayoutInflater.from(context)

        override fun getCount(): Int = albums.size

        override fun getItem(position: Int): SongLoader.Album = albums[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: inflater.inflate(R.layout.item_playlist, parent, false)
            val a = getItem(position)

            row.findViewById<ImageView>(R.id.pl_icon).setImageResource(R.drawable.ic_album)
            row.findViewById<TextView>(R.id.pl_name).text = a.name
            row.findViewById<TextView>(R.id.pl_count).text =
                "${getString(R.string.folder_count_song, a.count)} · ${a.artist}"
            row.findViewById<ImageButton>(R.id.btn_play_playlist).setOnClickListener {
                playAlbum(a.name)
            }
            return row
        }
    }

    /** Putar semua lagu dalam album ini, mulai dari lagu pertama. */
    private fun playAlbum(name: String) {
        Thread {
            val songs = SongLoader.loadSongsInAlbum(this, name)
            runOnUiThread {
                if (songs.isEmpty()) {
                    return@runOnUiThread
                }
                val pc = PlaybackController.create(this)
                pc.setPlaylist(songs)
                pc.playAt(0)
                startActivity(Intent(this, PlayerActivity::class.java))
                finish()
            }
        }.start()
    }
}
