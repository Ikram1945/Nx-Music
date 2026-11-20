package com.nx.music

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Layar Edit Tag: mengubah judul, artis, dan album satu lagu.
 *
 * Yang diubah adalah baris lagu di MediaStore (info perpustakaan), jadi
 * hasilnya langsung terbaca daftar lagu, Album, dan Artis. Tag bawaan di
 * dalam file audio tidak ditulis ulang — aplikasi lain yang memindai ulang
 * file bisa menimpa info ini kembali.
 *
 * Di Android 10+ file milik aplikasi lain hanya boleh diubah setelah user
 * menyetujui dialog izin sistem (RecoverableSecurityException); alur
 * persetujuan itu ditangani di sini, lalu penyimpanan dicoba sekali lagi.
 *
 * Dibuka dari dialog tekan-lama di daftar lagu (SongListActivity) dan
 * mengembalikan judul/artis baru lewat result agar daftar bisa refresh
 * tanpa memuat ulang seluruh MediaStore.
 */
class TagEditorActivity : AppCompatActivity() {

    private lateinit var edtTitle: EditText
    private lateinit var edtArtist: EditText
    private lateinit var edtAlbum: EditText
    private lateinit var btnSave: Button

    private var songPath: String = ""

    /** Nilai yang sedang dicoba disimpan; dipakai ulang saat retry pasca-izin. */
    private var pendingTitle: String = ""
    private var pendingArtist: String = ""
    private var pendingAlbum: String = ""

    /** True setelah user menyetujui dialog izin sistem; mencegah loop retry. */
    private var consentGranted: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tag_editor)
        ThemeHelper.applyThemeToActivity(this)

        songPath = intent.getStringExtra(EXTRA_PATH) ?: ""

        edtTitle = findViewById(R.id.edt_title)
        edtArtist = findViewById(R.id.edt_artist)
        edtAlbum = findViewById(R.id.edt_album)
        btnSave = findViewById(R.id.btn_save)

        edtTitle.setText(intent.getStringExtra(EXTRA_TITLE) ?: "")
        edtArtist.setText(intent.getStringExtra(EXTRA_ARTIST) ?: "")
        findViewById<TextView>(R.id.txt_path).text = songPath

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        btnSave.setOnClickListener { onSaveClicked() }

        // Album tidak disimpan model Song; ambil nilai sekarang dari MediaStore.
        if (songPath.isNotEmpty()) {
            Thread {
                val album = queryAlbum(songPath)
                runOnUiThread {
                    if (!isFinishing && edtAlbum.text.isNullOrEmpty()) {
                        edtAlbum.setText(album ?: "")
                    }
                }
            }.start()
        }
    }

    private fun onSaveClicked() {
        val title = edtTitle.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(this, R.string.tag_title_empty, Toast.LENGTH_SHORT).show()
            edtTitle.requestFocus()
            return
        }
        pendingTitle = title
        pendingArtist = edtArtist.text.toString().trim()
        pendingAlbum = edtAlbum.text.toString().trim()
        saveTag()
    }

    /** Menulis judul/artis/album ke MediaStore di thread latar. */
    private fun saveTag() {
        btnSave.isEnabled = false
        val path = songPath
        val title = pendingTitle
        val artist = pendingArtist
        val album = pendingAlbum
        Thread {
            var consentIntent: android.content.IntentSender? = null
            var success = false
            try {
                val id = findMediaId(path)
                if (id >= 0) {
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.TITLE, title)
                        put(MediaStore.Audio.Media.ARTIST, artist)
                        put(MediaStore.Audio.Media.ALBUM, album)
                    }
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id,
                    )
                    success = contentResolver.update(uri, values, null, null) > 0
                }
            } catch (e: SecurityException) {
                // Android 10+: ubah file milik aplikasi lain butuh persetujuan
                // user lewat dialog sistem. Minta sekali, lalu coba lagi.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    && !consentGranted
                    && e is RecoverableSecurityException
                ) {
                    consentIntent = e.userAction.actionIntent.intentSender
                }
            } catch (e: Exception) {
                success = false
            }
            val sender = consentIntent
            runOnUiThread {
                when {
                    sender != null -> {
                        try {
                            @Suppress("DEPRECATION")
                            startIntentSenderForResult(sender, REQ_CONSENT, null, 0, 0, 0)
                        } catch (ex: Exception) {
                            btnSave.isEnabled = true
                            Toast.makeText(this, R.string.tag_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                    success -> {
                        Toast.makeText(this, R.string.tag_saved, Toast.LENGTH_SHORT).show()
                        val data = Intent()
                            .putExtra(EXTRA_TITLE, title)
                            .putExtra(EXTRA_ARTIST, artist)
                        setResult(Activity.RESULT_OK, data)
                        finish()
                    }
                    else -> {
                        btnSave.isEnabled = true
                        Toast.makeText(this, R.string.tag_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CONSENT) {
            if (resultCode == Activity.RESULT_OK) {
                consentGranted = true
                saveTag()
            } else {
                btnSave.isEnabled = true
                Toast.makeText(this, R.string.tag_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** _ID baris MediaStore untuk path file ini, atau -1 bila tak ditemukan. */
    private fun findMediaId(path: String): Long {
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = MediaStore.Audio.Media.DATA + " = ?"
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, arrayOf(path), null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return -1L
    }

    /** Nama album sekarang untuk path file ini, atau null bila tak ditemukan. */
    private fun queryAlbum(path: String): String? {
        val projection = arrayOf(MediaStore.Audio.Media.ALBUM)
        val selection = MediaStore.Audio.Media.DATA + " = ?"
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, arrayOf(path), null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    companion object {
        const val EXTRA_PATH = "extra_path"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"

        private const val REQ_CONSENT = 300
    }
}
