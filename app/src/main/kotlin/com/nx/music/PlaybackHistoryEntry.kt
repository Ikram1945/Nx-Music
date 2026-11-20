package com.nx.music

import java.io.File

/**
 * Satu entri statistik pemutaran: metadata lagu + jumlah putar + waktu terakhir diputar.
 * Dipakai untuk daftar "paling sering diputar" dan "riwayat pemutaran".
 * [listenSec] = total detik lagu ini didengarkan (untuk "paling lama didengar");
 * 0 untuk data lama sebelum pencatatan per lagu berjalan.
 */
class PlaybackHistoryEntry(
    @JvmField var title: String,
    @JvmField var artist: String,
    @JvmField var path: String,
    @JvmField var plays: Int,
    @JvmField var lastPlayedAt: Long,
    @JvmField var listenSec: Long = 0L,
) {
    /**
     * Mengubah entri ini menjadi [Song] agar bisa diputar langsung.
     * Mengembalikan null jika path kosong atau filenya sudah tidak ada
     * (lagu dihapus / media dilepas), supaya daftar tidak menampilkan lagu mati.
     *
     * Durasi diisi 0; pemanggil dapat mengisinya dari file di luar thread UI.
     */
    fun toSong(): Song? {
        if (path.isEmpty()) return null
        if (!File(path).exists()) return null
        return Song(title, artist, path, 0L)
    }
}