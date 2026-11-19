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
 * Layar Artis: daftar putar otomatis per artis di perangkat (kolom ARTIST
 * MediaStore, lewat [SongLoader.loadArtists]). Pola sama dengan
 * [FolderActivity]; tombol tukar di header membuka [AlbumActivity].
 */
class ArtistActivity : AppCompatActivity() {

    private lateinit var listArtists: ListView
    private lateinit var txtEmpty: TextView
    private val artists: MutableList<SongLoader.Artist> = mutableListOf()
    private lateinit var adapter: ArtistAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_artist)
        ThemeHelper.applyThemeToActivity(this)

        listArtists = findViewById(R.id.list_playlists)
        txtEmpty = findViewById(R.id.txt_empty)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_switch).setOnClickListener {
            startActivity(Intent(this, AlbumActivity::class.java))
            finish()
        }

        adapter = ArtistAdapter(this)
        listArtists.adapter = adapter
        listArtists.setOnItemClickListener { _, _, position, _ ->
            val a = artists[position]
            startActivity(
                Intent(this, ArtistDetailActivity::class.java)
                    .putExtra(ArtistDetailActivity.EXTRA_NAME, a.name)
                    .putExtra(
                        ArtistDetailActivity.EXTRA_SUBTITLE,
                        getString(R.string.artist_count_detail, a.songCount, a.albumCount),
                    ),
            )
        }

        setupBottomNav()
        load()
    }

    /** Navigasi bawah konsisten; tak ada tab Artis, jadi tab aktif = Perpustakaan. */
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
            val result = SongLoader.loadArtists(this)
            runOnUiThread {
                artists.clear()
                artists.addAll(result)
                adapter.notifyDataSetChanged()
                txtEmpty.visibility = if (artists.isEmpty()) View.VISIBLE else View.GONE
            }
        }.start()
    }

    /** Adapter daftar artis (ikon artis, nama, jumlah lagu + album, tombol putar). */
    private inner class ArtistAdapter(context: Context) : BaseAdapter() {
        private val inflater = LayoutInflater.from(context)

        override fun getCount(): Int = artists.size

        override fun getItem(position: Int): SongLoader.Artist = artists[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: inflater.inflate(R.layout.item_playlist, parent, false)
            val a = getItem(position)

            row.findViewById<ImageView>(R.id.pl_icon).setImageResource(R.drawable.ic_artist)
            row.findViewById<TextView>(R.id.pl_name).text = a.name
            row.findViewById<TextView>(R.id.pl_count).text =
                getString(R.string.artist_count_detail, a.songCount, a.albumCount)
            row.findViewById<ImageButton>(R.id.btn_play_playlist).setOnClickListener {
                playArtist(a.name)
            }
            return row
        }
    }

    /** Putar semua lagu dari artis ini, mulai dari lagu pertama. */
    private fun playArtist(name: String) {
        Thread {
            val songs = SongLoader.loadSongsByArtist(this, name)
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
