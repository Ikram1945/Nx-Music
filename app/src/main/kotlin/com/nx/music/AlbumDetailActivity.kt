package com.nx.music

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

/**
 * Isi satu album sebagai daftar putar (read-only). Lagu dimuat langsung
 * dari MediaStore lewat [SongLoader.loadSongsInAlbum] — pola sama dengan
 * [FolderDetailActivity], jadi tak ada tombol tambah/ubah/hapus.
 */
class AlbumDetailActivity : AppCompatActivity() {

    private lateinit var albumName: String
    private val songs: MutableList<Song> = mutableListOf()
    private lateinit var adapter: SongAdapter
    private lateinit var txtEmpty: android.widget.TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_album_artist_detail)
        ThemeHelper.applyThemeToActivity(this)

        albumName = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val albumArtist = intent.getStringExtra(EXTRA_ARTIST).orEmpty()

        val txtTitle = findViewById<android.widget.TextView>(R.id.txt_title)
        val txtSubtitle = findViewById<android.widget.TextView>(R.id.txt_subtitle)
        txtEmpty = findViewById(R.id.txt_empty)
        val list = findViewById<android.widget.ListView>(R.id.list_songs)

        txtTitle.text = albumName
        txtSubtitle.text = albumArtist

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // Shuffle di sini hanya memengaruhi pemutaran album ini: playlist aktif
        // diganti isi album saat lagu dipilih (pola FolderDetailActivity).
        val btnShuffle = findViewById<ImageButton>(R.id.btn_shuffle_folder)
        updateShuffleIcon(btnShuffle)
        btnShuffle.setOnClickListener {
            val pc = PlaybackController.create(this)
            pc.isShuffle = !pc.isShuffle
            updateShuffleIcon(btnShuffle)
        }

        adapter = SongAdapter(this, songs)
        list.adapter = adapter

        list.setOnItemClickListener { _, _, position, _ ->
            if (songs.isEmpty()) {
                return@setOnItemClickListener
            }
            val pc = PlaybackController.create(this)
            pc.setPlaylist(songs)   // antrian = isi album ini saja
            pc.playAt(position)
            startActivity(Intent(this, PlayerActivity::class.java))
            finish()
        }

        load()
    }

    /** Keadaan ikon shuffle — pola sama dgn PlayerActivity: alpha penuh/meredup. */
    private fun updateShuffleIcon(btn: ImageButton) {
        val on = PlaybackController.create(this).isShuffle
        btn.setImageAlpha(if (on) 255 else 96)
    }

    override fun onResume() {
        super.onResume()
        val btn = findViewById<ImageButton?>(R.id.btn_shuffle_folder)
        if (btn != null) {
            updateShuffleIcon(btn)
        }
    }

    override fun onDestroy() {
        adapter.shutdown()
        super.onDestroy()
    }

    private fun load() {
        Thread {
            val result = SongLoader.loadSongsInAlbum(this, albumName)
            runOnUiThread {
                // SongAdapter menyalin list di ctor, jadi harus lewat setSongs.
                songs.clear()
                songs.addAll(result)
                adapter.setSongs(songs)
                txtEmpty.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
            }
        }.start()
    }

    companion object {
        const val EXTRA_NAME = "album_name"
        const val EXTRA_ARTIST = "album_artist"
    }
}
