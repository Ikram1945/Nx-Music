package com.nx.music

/**
 * Meneruskan callback [ServiceCallback] ke [PlaybackCallback] milik pemilik UI
 * (mis. [PlayerActivity]).
 */
class CallbackForwarder(private val target: PlaybackCallback) : ServiceCallback {

    override fun onPlaybackStateChanged() {
        target.onPlaybackStateChanged()
    }

    override fun onSongChanged(index: Int) {
        target.onSongChanged(index)
    }

    override fun onProgress(position: Long, duration: Long) {
        target.onProgress(position, duration)
    }

    override fun onQueueChanged() {
        target.onQueueChanged()
    }
}
