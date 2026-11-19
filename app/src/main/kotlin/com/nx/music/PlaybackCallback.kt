package com.nx.music

/**
 * Antarmuka untuk memberi tahu UI tentang perubahan status pemutaran.
 * Dipakai oleh [PlayerActivity] sebagai penerima callback dari [PlaybackController].
 */
interface PlaybackCallback {
    fun onPlaybackStateChanged()
    fun onSongChanged(index: Int)
    fun onProgress(position: Long, duration: Long)
    fun onQueueChanged()
}
