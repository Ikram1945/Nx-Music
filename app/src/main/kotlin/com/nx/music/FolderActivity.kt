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
 * Layar Folder: daftar putar otomatis dari folder musik di perangkat.
 * Tiap folder = satu daftar putar (read-only), sumbernya dari path lagu
 * MediaStore. Terpisah dari [PlaylistActivity] (daftar putar pengguna).
 */
class FolderActivity : AppCompatActivity() {

    private lateinit var listFolders: ListView
    private lateinit var txtEmpty: TextView
    private val folders: MutableList<SongLoader.Folder> = mutableListOf()
    private lateinit var adapter: FolderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder)
        ThemeHelper.applyThemeToActivity(this)

        listFolders = findViewById(R.id.list_playlists)
        txtEmpty = findViewById(R.id.txt_empty)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        adapter = FolderAdapter(this)
        listFolders.adapter = adapter
        listFolders.setOnItemClickListener { _, _, position, _ ->
            val f = folders[position]
            startActivity(
                Intent(this, FolderDetailActivity::class.java)
                    .putExtra(FolderDetailActivity.EXTRA_PATH, f.path)
                    .putExtra(FolderDetailActivity.EXTRA_NAME, f.name),
            )
        }

        setupBottomNav()
        load()
    }

    /** Navigasi bawah konsisten; tab aktif = Folder. */
    private fun setupBottomNav() {
        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        nav.selectedItemId = R.id.nav_folder
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                R.id.nav_library -> {
                    startActivity(Intent(this, SongListActivity::class.java))
                    finish()
                }
                R.id.nav_playlist -> {
                    startActivity(Intent(this, PlaylistActivity::class.java))
                    finish()
                }
            }
            true // nav_folder: tetap di sini
        }
    }

    private fun load() {
        // Query MediaStore di background; library besar tidak membekukan UI.
        Thread {
            val result = SongLoader.loadFolders(this)
            runOnUiThread {
                folders.clear()
                folders.addAll(result)
                adapter.notifyDataSetChanged()
                txtEmpty.visibility = if (folders.isEmpty()) View.VISIBLE else View.GONE
            }
        }.start()
    }

    /** Adapter daftar folder (ikon folder, nama, jumlah lagu, tombol putar). */
    private inner class FolderAdapter(context: Context) : BaseAdapter() {
        private val inflater = LayoutInflater.from(context)

        override fun getCount(): Int = folders.size

        override fun getItem(position: Int): SongLoader.Folder = folders[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: inflater.inflate(R.layout.item_playlist, parent, false)
            val f = getItem(position)

            row.findViewById<ImageView>(R.id.pl_icon).setImageResource(R.drawable.ic_folder)
            row.findViewById<TextView>(R.id.pl_name).text = f.name
            row.findViewById<TextView>(R.id.pl_count).text =
                "${getString(R.string.folder_count_song, f.count)} · ${f.path}"
            row.findViewById<ImageButton>(R.id.btn_play_playlist).setOnClickListener {
                playFolder(f.path)
            }
            return row
        }
    }

    /** Putar semua lagu dalam folder ini, mulai dari lagu pertama. */
    private fun playFolder(dir: String) {
        Thread {
            val songs = SongLoader.loadSongsInFolder(this, dir)
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
