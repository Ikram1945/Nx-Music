package com.nx.music;

/**
 * Meneruskan callback {@link ServiceCallback} ke {@link PlaybackCallback} milik
 * pemilik UI (mis. {@link PlayerActivity}). Top-level agar tidak menghasilkan
 * nest mate (kompatibel dengnan d8 CodeAssist).
 */
public class CallbackForwarder implements ServiceCallback {

    private final PlaybackCallback target;

    public CallbackForwarder(PlaybackCallback target) {
        this.target = target;
    }

    @Override
    public void onPlaybackStateChanged() {
        target.onPlaybackStateChanged();
    }

    @Override
    public void onSongChanged(int index) {
        target.onSongChanged(index);
    }

    @Override
    public void onProgress(long position, long duration) {
        target.onProgress(position, duration);
    }

    @Override
    public void onQueueChanged() {
        target.onQueueChanged();
    }
}
