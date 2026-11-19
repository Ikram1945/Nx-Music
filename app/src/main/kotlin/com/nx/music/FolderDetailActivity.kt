package com.nx.music

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

/**
 * Isi satu folder perangkat sebagai daftar putar (read-only). Lagu dimuat
 * langsung dari MediaStore lewat [SongLoader.loadSongsInFolder], bukan
 * dari [PlaylistHelper] — jadi tak ada tombol tambah/ubah/hapus.
 */
class FolderDetailActivity : AppCompatActivity() {

    private lateinit var folderPath: String
    private val songs: MutableList<Song> = mutableListOf()
    private lateinit var adapter: SongAdapter
    private lateinit var txtEmpty: android.widget.TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder_detail)
        ThemeHelper.applyThemeToActivity(this)

        folderPath = intent.getStringExtra(EXTRA_PATH).orEmpty()
        val folderName = intent.getStringExtra(EXTRA_NAME).orEmpty()

        val txtTitle = findViewById<android.widget.TextView>(R.id.txt_title)
        val txtSubtitle = findViewById<android.widget.TextView>(R.id.txt_subtitle)
        txtEmpty = findViewById(R.id.txt_empty)
        val list = findViewById<android.widget.ListView>(R.id.list_songs)

        txtTitle.text = folderName
        txtSubtitle.text = folderPath

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // Shuffle di sini hanya memengaruhi pemutaran folder ini: playlist aktif
        // diganti daftar folder saat lagu dipilih, jadi "lagu berikutnya" tetap
        // di dalam folder, bukan library global.
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
            // Posisi adapter == posisi songs (tanpa filter), jadi index aman.
            val pc = PlaybackController.create(this)
            pc.setPlaylist(songs)   // antrian = isi folder ini saja
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
        // Query MediaStore di background; folder besar tidak membekukan UI.
        Thread {
            val result = SongLoader.loadSongsInFolder(this, folderPath)
            runOnUiThread {
                // SongAdapter menyalin list di ctor, jadi harus lewat setSongs
                // (bukan notifyDataSetChanged) agar isi adapter ikut berubah.
                songs.clear()
                songs.addAll(result)
                adapter.setSongs(songs)
                txtEmpty.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
            }
        }.start()
    }

    companion object {
        const val EXTRA_PATH = "folder_path"
        const val EXTRA_NAME = "folder_name"
    }
}
