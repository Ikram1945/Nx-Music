 package com.nx.music;

import android.content.Context;

import java.util.List;

/**
 * Pengontrol pemutaran musik. Bertindak sebagai <i>facade</i> yang meneruskan
 * semua perintah ke {@link MusicService} (foreground service yang memiliki
 * {@link android.media.MediaPlayer}) sehingga musik tetap berputar di latar
 * belakang saat aplikasi ditutup.
 *
 * <p>API-nya sengaja dipertahankan agar layar {@link PlayerActivity} dan
 * {@link SongListActivity} tidak perlu banyak berubah.</p>
 */
public class PlaybackController implements ServiceCallback {

    /** Mode repeat tidak aktif. */
    public static final int REPEAT_OFF = MusicService.REPEAT_OFF;
    /** Ulangi seluruh daftar lagu. */
    public static final int REPEAT_ALL = MusicService.REPEAT_ALL;
    /** Ulangi hanya lagu yang sedang diputar. */
    public static final int REPEAT_ONE = MusicService.REPEAT_ONE;

    private static PlaybackController instance;

    /**
     * Membuat (atau mengambil) instance singleton dan memastikan service
     * foreground aktif. Panggil dengan konteks Activity.
     */
    public static PlaybackController create(Context context) {
        if (instance == null) {
            instance = new PlaybackController();
        }
        if (context != null) {
            MusicService.start(context);
        }
        // Jangan menimpa callback UI yang sudah terdaftar (mis. PlayerActivity),
        // agar title & artwork tetap sinkron saat activity lain memanggil create().
        MusicService.setCallbackIfUnset(instance);
        return instance;
    }

    /** Mendapatkan instance yang sudah dibuat. {@code null} jika belum dibuat. */
    public static PlaybackController getInstance() {
        return instance;
    }

    private PlaybackController() {
        // service dipanggil melalui create()
    }

    /** Mengganti callback (pemilik UI yang aktif). */
    public void setCallback(PlaybackCallback callback) {
        if (callback == null) {
            MusicService.setCallback(null);
        } else {
            MusicService.setCallback(new CallbackForwarder(callback));
        }
    }

    // ---- Delegasi ke MusicService ----

    /** Mengatur daftar lagu yang akan diputar. */
    public void setPlaylist(List<Song> songs) {
        MusicService.setPlaylist(songs);
    }

    public List<Song> getPlaylist() {
        return MusicService.getPlaylist();
    }

    // ---- Antrian (Play Next / Up Next) ----

    /** Mendapatkan daftar lagu yang akan diputar berikutnya. */
    public List<Song> getUpNext() {
        return MusicService.getUpNext();
    }

    /** Menambah lagu ke posisi berikutnya (Play Next). */
    public void addNext(Song song) {
        MusicService.addNext(song);
    }

    /** Menambahkan lagu ke akhir antrian (Add to Queue). */
    public void addToQueue(Song song) {
        MusicService.addToQueue(song);
    }

    /** Menghapus satu lagu dari antrian berdasarkan posisinya. */
    public void removeFromUpNext(int index) {
        MusicService.removeFromUpNext(index);
    }

    /** Mengosongkan seluruh antrian. */
    public void clearUpNext() {
        MusicService.clearUpNext();
    }

    public int getCurrentIndex() {
        return MusicService.getCurrentIndex();
    }

    public Song getCurrentSong() {
        return MusicService.getCurrentSong();
    }

    public boolean isPlaying() {
        return MusicService.isPlaying();
    }

    public boolean isShuffle() {
        return MusicService.isShuffle();
    }

    public int getRepeatMode() {
        return MusicService.getRepeatMode();
    }

    public int getDuration() {
        return MusicService.getDuration();
    }

    public int getAudioSessionId() {
        return MusicService.getAudioSessionId();
    }

    public int getPosition() {
        return MusicService.getPosition();
    }

    /** Menyalakan/mematikan mode shuffle. */
    public void setShuffle(boolean enable) {
        MusicService.setShuffle(enable);
    }

    /** Memutar lagu pada indeks tertentu dari playOrder. */
    public void playAt(int index) {
        MusicService.playAt(index);
    }

    /** Memutar/melanjutkan lagu saat ini. Jika tidak ada, putar lagu pertama. */
    public void playOrResume() {
        MusicService.playOrResume();
    }

    public void pause() {
        MusicService.pause();
    }

    /** Memindah posisi pemutaran (seek). */
    public void seekTo(int positionMs) {
        MusicService.seekTo(positionMs);
    }

    /** Pindah ke lagu berikutnya (forward=true) atau sebelumnya. */
    public void skip(boolean forward) {
        MusicService.skip(forward);
    }

    /** Memutar mode repeat berikutnya: off -> all -> one -> off. */
    public void cycleRepeat() {
        MusicService.cycleRepeat();
    }

    // ---- Sleep Timer (fitur #2) ----

    /** Apakah sleep timer sedang aktif. */
    public boolean isSleepTimerActive() {
        return MusicService.isSleepTimerActive();
    }

    /** Total durasi sleep timer yang diset (menit). 0 jika nonaktif. */
    public int getSleepTimerMinutes() {
        return MusicService.getSleepTimerMinutes();
    }

    /** Sisa waktu sleep timer dalam detik (-1 jika nonaktif). */
    public long getSleepTimerRemainingSeconds() {
        return MusicService.getSleepTimerRemainingSeconds();
    }

    /** Mengatur sleep timer selama {@code minutes} menit (<=0 membatalkan). */
    public void setSleepTimer(int minutes) {
        MusicService.setSleepTimer(minutes);
    }

    /** Membatalkan sleep timer yang sedang berjalan. */
    public void cancelSleepTimer() {
        MusicService.cancelSleepTimer();
    }

    // ---- Crossfade ----

    /** Apakah crossfade aktif di pengaturan. */
    public boolean isCrossfadeEnabled() {
        return MusicService.isCrossfadeEnabled();
    }

    /** Mengaktifkan/mematikan crossfade. */
    public void setCrossfadeEnabled(Context context, boolean enabled) {
        MusicService.setCrossfadeEnabled(context, enabled);
    }

    /**
     * Melepaskan pemutaran dan menghentikan service. Panggil hanya saat
     * aplikasi benar-benar keluar (mis. dari tombol STOP di notifikasi).
     */
    public void release(Context context) {
        // Service dikelola sendiri oleh MusicService. Di sini hanya
        // melepas instance controller dari callback service.
        MusicService.setCallback(null);
    }

    // ---- ServiceCallback (placeholder, delegasi via setCallback) ----

    @Override
    public void onPlaybackStateChanged() {
    }

    @Override
    public void onSongChanged(int index) {
    }

    @Override
    public void onProgress(long position, long duration) {
    }

    @Override
    public void onQueueChanged() {
    }
}
