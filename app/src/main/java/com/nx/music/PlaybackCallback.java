package com.nx.music;

/**
 * Antarmuka untuk memberi tahu UI tentang perubahan status pemutaran.
 * Dipakai oleh {@link PlayerActivity} sebagai penerima callback dari
 * {@link PlaybackController}. Interface top-level (bukan nested) agar
 * kompatibel dengan kompilator CodeAssist (menghindari masalah nest-mate d8).
 */
public interface PlaybackCallback {
    void onPlaybackStateChanged();
    void onSongChanged(int index);
    void onProgress(long position, long duration);
    void onQueueChanged();
}
