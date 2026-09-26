package com.nx.music

import android.content.ContentResolver
import android.content.Context
import android.provider.MediaStore

/**
 * Memuat lagu dari [MediaStore] dan mengelompokkannya per folder.
 * Semua anggota statis agar pemanggil Java tetap memakai `SongLoader.loadSongs(...)`.
 */
object SongLoader {

    @JvmStatic
    fun loadSongs(resolver: ContentResolver): MutableList<Song> {
        val songs = ArrayList<Song>()

        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = MediaStore.Audio.Media.IS_MUSIC + " != 0"

        resolver.query(uri, projection, selection, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
                val artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
                val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                val duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))

                songs.add(Song(title, artist, path, duration))
            }
        }

        return songs
    }

    /**
     * Memuat lagu lalu mengurutkannya sesuai preferensi pengaturan (fitur settings).
     * Sort order: 0=default, 1=judul (A-Z), 2=artis, 3=durasi terpendek.
     * Dibaca langsung dari prefs yang sama dengan SettingsActivity (Kotlin tak
     * bisa merujuk kelas Java di build ini — compileKotlin jalan lebih dulu).
     */
    @JvmStatic
    @JvmOverloads
    fun loadSongs(context: Context, sortOrder: Int = getSortOrder(context)): MutableList<Song> {
        val songs = loadSongs(context.contentResolver)
        when (sortOrder) {
            1 -> songs.sortWith(BY_TITLE)
            2 -> songs.sortWith(BY_ARTIST)
            3 -> songs.sortWith(BY_DURATION)
        }
        return songs
    }

    /** Sama dengan SettingsActivity.getSortOrder: prefs `settings_prefs`, kunci `sort_order`. */
    @JvmStatic
    fun getSortOrder(context: Context): Int =
        context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE).getInt("sort_order", 0)

    /** Satu folder di perangkat yang berisi lagu. */
    class Folder(
        @JvmField val path: String,
        @JvmField val name: String,
        @JvmField val count: Int,
    )

    /**
     * Mengelompokkan lagu per folder induknya (dari kolom DATA). Dipakai menu
     * Folder: tiap folder = satu "daftar putar". Urut: nama folder A-Z.
     * Nothing di-cache; daftar folder berubah saat library berubah.
     */
    @JvmStatic
    fun loadFolders(context: Context): MutableList<Folder> {
        val counts = LinkedHashMap<String, Int>()
        for (s in loadSongs(context.contentResolver)) {
            val dir = parentDir(s.path) ?: continue
            counts[dir] = (counts[dir] ?: 0) + 1
        }
        return counts.map { (dir, count) -> Folder(dir, baseName(dir), count) }
            .sortedBy { it.name.lowercase() }
            .toMutableList()
    }

    /** Lagu-lagu di dalam satu folder (parent path cocok persis). Urut judul. */
    @JvmStatic
    fun loadSongsInFolder(context: Context, dir: String): MutableList<Song> {
        val result = loadSongs(context.contentResolver)
            .filter { dir == parentDir(it.path) }
            .toMutableList()
        result.sortWith(BY_TITLE)
        return result
    }

    /** Folder induk dari path file, atau null jika path kosong/tak ada separator. */
    private fun parentDir(path: String?): String? {
        if (path == null) return null
        val i = path.lastIndexOf('/')
        return if (i <= 0) null else path.substring(0, i)
    }

    /** Nama folder = segmen terakhir dari path. */
    private fun baseName(dir: String): String {
        val i = dir.lastIndexOf('/')
        return if (i >= 0) dir.substring(i + 1) else dir
    }

    private val BY_TITLE = Comparator<Song> { a, b ->
        (a.title ?: "").lowercase().compareTo((b.title ?: "").lowercase())
    }

    private val BY_ARTIST = Comparator<Song> { a, b ->
        val c = (a.artist ?: "").lowercase().compareTo((b.artist ?: "").lowercase())
        if (c != 0) c else BY_TITLE.compare(a, b)
    }

    private val BY_DURATION = Comparator<Song> { a, b -> a.duration.compareTo(b.duration) }
}
