package com.nx.music;

/**
 * Antarmuka callback yang diterima {@link MusicService} untuk memberi tahu
 * pemilik UI tentang perubahan status pemutaran. Interface top-level (bukan
 * nested) agar kompatibel dengan kompilator CodeAssist (menghindari masalah
 * nest-mate d8 pada interface dalam yang dipakaii lintas file).
 */
public interface ServiceCallback {
    void onPlaybackStateChanged();
    void onSongChanged(int index);
    void onProgress(long position, long duration);
    void onQueueChanged();
}
