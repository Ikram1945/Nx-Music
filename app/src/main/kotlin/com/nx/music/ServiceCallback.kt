package com.nx.music

/**
 * Antarmuka callback yang diterima [MusicService] untuk memberi tahu
 * pemilik UI tentang perubahan status pemutaran.
 */
interface ServiceCallback {
    fun onPlaybackStateChanged()
    fun onSongChanged(index: Int)
    fun onProgress(position: Long, duration: Long)
    fun onQueueChanged()
}
