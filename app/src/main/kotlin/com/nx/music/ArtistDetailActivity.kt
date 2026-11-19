package com.nx.music

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

/**
 * Semua lagu dari satu artis sebagai daftar putar (read-only). Lagu dimuat
 * langsung dari MediaStore lewat [SongLoader.loadSongsByArtist] — pola sama
 * dengan [FolderDetailActivity], jadi tak ada tombol tambah/ubah/hapus.
 */
class ArtistDetailActivity : AppCompatActivity() {

    private lateinit var artistName: String
    private val songs: MutableList<Song> = mutableListOf()
    private lateinit var adapter: SongAdapter
    private lateinit var txtEmpty: android.widget.TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_album_artist_detail)
        ThemeHelper.applyThemeToActivity(this)

        artistName = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val subtitle = intent.getStringExtra(EXTRA_SUBTITLE).orEmpty()

        val txtTitle = findViewById<android.widget.TextView>(R.id.txt_title)
        val txtSubtitle = findViewById<android.widget.TextView>(R.id.txt_subtitle)
        txtEmpty = findViewById(R.id.txt_empty)
        val list = findViewById<android.widget.ListView>(R.id.list_songs)

        txtTitle.text = artistName
        txtSubtitle.text = subtitle

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // Shuffle di sini hanya memengaruhi pemutaran artis ini: playlist aktif
        // diganti lagu-lagu artis ini saat lagu dipilih (pola FolderDetailActivity).
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
            pc.setPlaylist(songs)   // antrian = lagu artis ini saja
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
            val result = SongLoader.loadSongsByArtist(this, artistName)
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
        const val EXTRA_NAME = "artist_name"
        const val EXTRA_SUBTITLE = "artist_subtitle"
    }
}
