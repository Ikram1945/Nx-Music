package com.nx.music

import android.content.Context
import android.content.Intent
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
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Layar daftar putar. Menampilkan semua daftar putar pengguna, membuat daftar
 * baru, menghapus, mengubah nama, memutar, dan membuka isi daftar.
 */
class PlaylistActivity : AppCompatActivity() {

    private lateinit var playlists: PlaylistHelper
    private lateinit var listPlaylists: ListView
    private lateinit var txtEmpty: TextView
    private val names: MutableList<String> = mutableListOf()
    private lateinit var adapter: PlaylistAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)
        ThemeHelper.applyThemeToActivity(this)

        playlists = PlaylistHelper.getInstance(this)
        listPlaylists = findViewById(R.id.list_playlists)
        txtEmpty = findViewById(R.id.txt_empty)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_add_playlist).setOnClickListener { showCreateDialog() }

        adapter = PlaylistAdapter(this)
        listPlaylists.adapter = adapter

        listPlaylists.setOnItemClickListener { _, _, position, _ ->
            val name = names[position]
            startActivity(
                Intent(this, PlaylistDetailActivity::class.java)
                    .putExtra(PlaylistDetailActivity.EXTRA_NAME, name),
            )
        }

        listPlaylists.setOnItemLongClickListener { _, _, position, _ ->
            showPlaylistOptions(names[position])
            true
        }

        setupBottomNav()
    }

    /** Navigasi bawah konsisten dengan beranda; tab aktif = Daftar Putar. */
    private fun setupBottomNav() {
        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        nav.selectedItemId = R.id.nav_playlist
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
                R.id.nav_folder -> {
                    startActivity(Intent(this, FolderActivity::class.java))
                    finish()
                }
            }
            true // nav_playlist: tetap di sini
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        names.clear()
        names.addAll(playlists.playlistNames)
        adapter.notifyDataSetChanged()
        txtEmpty.visibility = if (names.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showCreateDialog() {
        val input = android.widget.EditText(this)
        input.setHint(R.string.playlist_name_hint)
        AlertDialog.Builder(this)
            .setTitle(R.string.playlist_new)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (playlists.createPlaylist(name)) {
                    reload()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showPlaylistOptions(name: String) {
        val options = arrayOf(
            getString(R.string.playlist_play),
            getString(R.string.playlist_rename),
            getString(R.string.playlist_delete),
        )
        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> playPlaylist(name)
                    1 -> showRenameDialog(name)
                    else -> confirmDelete(name)
                }
            }
            .show()
    }

    private fun playPlaylist(name: String) {
        val songs = playlists.getSongs(name)
        if (songs.isEmpty()) {
            return
        }
        PlaybackController.create(this).setPlaylist(songs)
        PlaybackController.create(this).playOrResume()
        finish()
    }

    private fun showRenameDialog(oldName: String) {
        val input = android.widget.EditText(this)
        input.setHint(R.string.playlist_name_hint)
        AlertDialog.Builder(this)
            .setTitle(R.string.playlist_rename)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty() || newName == oldName
                    || playlists.playlistNames.contains(newName)
                ) {
                    return@setPositiveButton
                }
                // salin isi lama ke nama baru lalu hapus nama lama
                val songs = playlists.getSongs(oldName)
                playlists.deletePlaylist(oldName)
                if (playlists.createPlaylist(newName)) {
                    for (s in songs) {
                        playlists.addSong(newName, s)
                    }
                }
                reload()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(name: String) {
        AlertDialog.Builder(this)
            .setMessage(R.string.playlist_delete_confirm)
            .setPositiveButton(R.string.playlist_delete) { _, _ ->
                playlists.deletePlaylist(name)
                reload()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Adapter daftar putar (nama + jumlah lagu + tombol putar). */
    private inner class PlaylistAdapter(context: Context) : BaseAdapter() {
        private val inflater = LayoutInflater.from(context)

        override fun getCount(): Int = names.size

        override fun getItem(position: Int): String = names[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: inflater.inflate(R.layout.item_playlist, parent, false)
            val name = getItem(position)

            row.findViewById<TextView>(R.id.pl_name).text = name
            val count = playlists.getSongs(name).size
            row.findViewById<TextView>(R.id.pl_count).text =
                getString(R.string.playlist_count_song, count)
            row.findViewById<ImageButton>(R.id.btn_play_playlist).setOnClickListener {
                playPlaylist(name)
            }
            return row
        }
    }
}
