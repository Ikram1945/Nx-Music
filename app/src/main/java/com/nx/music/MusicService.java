package com.nx.music;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Layanan foreground yang memiliki {@link MediaPlayer} sehingga musik terus
 * berputar di latar belakang saat aplikasi ditutup.
 *
 * <p>Menampilkan <b>notifikasi media</b> dengan kontrol play/pause/hnext/prev dan
 * mengelola <b>Audio Focus</b> (menjeda saat ada panggilan/app lain bunyi).</p>
 *
 * <p>MediaPlayer &amp; status pemutaran disimpan sebagai field statis agar bisa
 * dibaca sinkron dari Activity yang berjalan di proses yang sama.</p>
 */
public class MusicService extends Service {

 public static final String ACTION_TOGGLE = "com.nx.music.action.TOGGLE";
 public static final String ACTION_NEXT = "com.nx.music.action.NEXT";
 public static final String ACTION_PREV = "com.nx.music.action.PREV";

 public static final int REPEAT_OFF =0;
 public static final int REPEAT_ALL =1;
 public static final int REPEAT_ONE =2;

 private static final String CHANNEL_ID = "music_playback";
 private static final int NOTIFICATION_ID =1;

 // State statis agar bisa dibaca sinkron dari Activity (proses sama).
 private static MediaPlayer mediaPlayer;
 private static final List<Song> playOrder = new ArrayList<>();
 /** Antrian "putar berikutnya" — lagu di sini diputar lebih dulu sebelum kembali ke playOrder. */
 private static final List<Song> upNext = new ArrayList<>();
 /** Lagu dari antrian yang sedang diputar ({@code null} jika sedang dari playlist). */
 private static Song queueSong;
 private static int currentIndex = -1;
 private static int repeatMode = REPEAT_ALL;
 private static boolean shuffle = false;
 private static boolean playError = false;
 private static MusicService instance;

 // ---- Crossfade ----
 private static final String PREFS_CROSSFADE = "settings_prefs";
 private static final String KEY_CROSSFADE = "crossfade_enabled";
 private static final int CROSSFADE_MS = 3000;
 private static final int CROSSFADE_CHECK_MS = 200;
 private static final int CROSSFADE_FADE_STEP_MS = 50;
 private static boolean isCrossfading = false;
 private static MediaPlayer crossfadePlayer;

 private static final Random random = new Random();

 // Crossfade handler: checks position periodically and runs fade animation.
 private static final Handler crossfadeHandler = new Handler(Looper.getMainLooper());
 private static final Runnable crossfadeCheckRunnable = MusicService::runCrossfadeCheck;
 private static final Runnable crossfadeFadeRunnable = MusicService::runCrossfadeFade;

 // ---- Sleep Timer (fitur #2) ----
 // Berjalan di handler thread utama service; menyimpan waktu target & sisa detik.
 private static final Handler sleepHandler = new Handler(Looper.getMainLooper());
 private static boolean sleepTimerActive = false;
 private static long sleepTimerEndAtMs =0L;
 /** Total durasi sleep timer (menit) untuk ditampilkan ke UI. */
 private static int sleepTimerMinutes =0;

 // ---- Statistik & riwayat pemutaran (fitur #5) ----
 // Lacak waktu mendengarkan berkala (tiap 10 detik) saat sedang memutar.
 private static final long STATS_INTERVAL_MS =10000L;
 private static final Handler statsHandler = new Handler(Looper.getMainLooper());
 private static boolean statsTracking = false;
 private static long lastTrackedPositionMs =0L;
 private static final Runnable statsRunnable = MusicService::runStatsTick;
 private static void runStatsTick() {
 if (instance != null && isPlaying()) {
 int pos = getPosition();
 if (pos >= lastTrackedPositionMs) {
 long deltaSec = (pos - lastTrackedPositionMs) /1000L;
 PlaybackStats.getInstance(instance).addListeningTime(deltaSec);
 }
 lastTrackedPositionMs = pos;
 statsHandler.postDelayed(statsRunnable, STATS_INTERVAL_MS);
 } else {
 statsTracking = false;
 }
 }
 private static void startStatsTracking() {
 if (statsTracking) {
 return;
 }
 statsTracking = true;
 lastTrackedPositionMs = getPosition();
 statsHandler.removeCallbacks(statsRunnable);
 statsHandler.postDelayed(statsRunnable, STATS_INTERVAL_MS);
 }
 private static void stopStatsTracking() {
 statsTracking = false;
 statsHandler.removeCallbacks(statsRunnable);
 }

 private static final Runnable sleepRunnable = MusicService::runSleepTick;
 private static void runSleepTick() {
 long remaining = sleepTimerEndAtMs - System.currentTimeMillis();
 if (remaining <=0) {
 sleepTimerActive = false;
 sleepTimerMinutes =0;
 pause();
 if (instance != null) {
 instance.notifyStateChanged();
 }
 } else {
 long delay = Math.min(remaining,5000L);
 sleepHandler.postDelayed(sleepRunnable, delay);
 }
 }

 private AudioManager audioManager;
 private AudioFocusRequest audioFocusRequest;
 private MediaSessionCompat mediaSession;

 @Nullable
 @Override
 public IBinder onBind(Intent intent) {
 return null;
 }

 @Override
 public void onCreate() {
 super.onCreate();
 instance = this;
 audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
 // Pulihkan callback yang diset sebelum instance aktif.
 this.callback = pendingCallback;
 createNotificationChannel();
 setupMediaPlayer();
 setupMediaSession();
 }

 /**
 * MediaSessionCompat memungkinkan notifikasi media (MediaStyle) mengenali status
 * pemutaran dan menampilkan kontrol di lock screen serta panel quick settings.
 */
 private void setupMediaSession() {
 mediaSession = new MediaSessionCompat(this, "HarmonyMelody");
 mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
 | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
 mediaSession.setActive(true);
 }

 @Override
 public int onStartCommand(Intent intent, int flags, int startId) {
 if (intent != null) {
 String action = intent.getAction();
 if (ACTION_TOGGLE.equals(action)) {
 if (isPlaying()) {
 pause();
 } else {
 playOrResume();
 }
 } else if (ACTION_NEXT.equals(action)) {
 skip(true);
 } else if (ACTION_PREV.equals(action)) {
 skip(false);
 }
 }
 startForeground(NOTIFICATION_ID, buildNotification());
 return START_NOT_STICKY;
 }

 @Override
 public void onDestroy() {
 sleepHandler.removeCallbacks(sleepRunnable);
 stopStatsTracking();
 releasePlayer();
 if (mediaSession != null) {
 mediaSession.setActive(false);
 mediaSession.release();
 mediaSession = null;
 }
 instance = null;
 pendingCallback = null;
 super.onDestroy();
 }

 // ===================== MEDIA PLAYER =====================

 private void setupMediaPlayer() {
 mediaPlayer = new MediaPlayer();
 mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
 .setUsage(AudioAttributes.USAGE_MEDIA)
 .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
 .build());
 mediaPlayer.setOnCompletionListener(mp -> onSongCompleted());
 // Pemutaran dimulai setelah lagu siap. prepareAsync() memastikan penyiapan
 // file audio berjalan di luar thread UI sehingga UI tidak macet saat mengetuk lagu.
 mediaPlayer.setOnPreparedListener(mp -> {
 playError = false;
 if (isCrossfading) {
 mp.setVolume(0f, 0f);
 mp.start();
 } else {
 mp.start();
 }
 lastTrackedPositionMs =0;
 startStatsTracking();
 if (isCrossfadeEnabled()) {
 startCrossfadeCheck();
 }
 instance.requestAudioFocus();
 instance.notifySongChanged(currentIndex);
 instance.notifyStateChanged();
 instance.showNotification();
 });
 mediaPlayer.setOnErrorListener((mp, what, extra) -> {
 playError = true;
 notifyStateChanged();
 return true;
 });
 // Player cadangan untuk crossfade: sama-sama bereaksi ke completion agar
 // transisi tetap berjalan walau mode aktif.
 crossfadePlayer = new MediaPlayer();
 crossfadePlayer.setAudioAttributes(new AudioAttributes.Builder()
 .setUsage(AudioAttributes.USAGE_MEDIA)
 .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
 .build());
 crossfadePlayer.setOnCompletionListener(mp -> onSongCompleted());
 crossfadePlayer.setOnPreparedListener(mp -> {
 if (isCrossfading) {
 mp.setVolume(0f, 0f);
 mp.start();
 } else {
 mp.start();
 }
 lastTrackedPositionMs =0;
 startStatsTracking();
 instance.requestAudioFocus();
 instance.notifySongChanged(currentIndex);
 instance.notifyStateChanged();
 instance.showNotification();
 });
 crossfadePlayer.setOnErrorListener((mp, what, extra) -> {
 playError = true;
 notifyStateChanged();
 return true;
 });
 }

 private void releasePlayer() {
 crossfadeHandler.removeCallbacks(crossfadeCheckRunnable);
 crossfadeHandler.removeCallbacks(crossfadeFadeRunnable);
 if (crossfadePlayer != null) {
 crossfadePlayer.release();
 crossfadePlayer = null;
 }
 if (mediaPlayer != null) {
 mediaPlayer.release();
 mediaPlayer = null;
 }
 }

 // ===================== AUDIO FOCUS =====================

 private void requestAudioFocus() {
 if (audioManager == null) {
 return;
 }
 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
 if (audioFocusRequest == null) {
 audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
 .setAudioAttributes(new AudioAttributes.Builder()
 .setUsage(AudioAttributes.USAGE_MEDIA)
 .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
 .build())
 .setOnAudioFocusChangeListener(this::onAudioFocusChange)
 .build();
 }
 audioManager.requestAudioFocus(audioFocusRequest);
 } else {
 audioManager.requestAudioFocus(this::onAudioFocusChange,
 AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
 }
 }

 private void abandonAudioFocus() {
 if (audioManager == null) {
 return;
 }
 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
 audioManager.abandonAudioFocusRequest(audioFocusRequest);
 } else {
 audioManager.abandonAudioFocus(null);
 }
 }

 private void onAudioFocusChange(int focusChange) {
 if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
 pause();
 } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
 pause();
 } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
 if (mediaPlayer != null) {
 mediaPlayer.setVolume(0.3f,0.3f);
 }
 } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
 if (mediaPlayer != null) {
 mediaPlayer.setVolume(1.0f,1.0f);
 }
 }
 }

 // ===================== KONTROL PEMUTARAN =====================

 public static void setPlaylist(List<Song> songs) {
 // Simpan lagu yang sedang diputar agar tetap aktif saat daftar di-refresh.
 Song playingSong = getCurrentSong();
 playOrder.clear();
 if (songs != null) {
 playOrder.addAll(songs);
 }
 if (playingSong != null) {
 // Cari posisi lagu yang sedang diputar di daftar baru; biarkan index lama
 // hanya jika lagu itu memang masih ada, agar status tetap sinkron.
 int idx = playOrder.indexOf(playingSong);
 currentIndex = idx;
 } else {
 currentIndex = -1;
 }
 queueSong = null;
 playError = false;
 // Playlist baru = mulai baru; kosongkan antrian yang tersisa.
 if (!upNext.isEmpty()) {
 upNext.clear();
 notifyQueueChanged();
 }
 }

 public static List<Song> getPlaylist() {
  // Kembalikan salinan: pemanggil (activity/adapter) tidak boleh memegang
  // referensi playOrder, karena setPlaylist() meng-clear() list yang sama
  // dan itu bisa memicu ConcurrentModificationException saat UI membacanya.
  return new ArrayList<>(playOrder);
 }

 // ===================== ANTRIAN (PUTAR BERIKUTNYA) =====================

 /** Mendapatkan daftar lagu yang akan diputar berikutnya (up next). */
 public static List<Song> getUpNext() {
  // Salinan, lihat alasan di getPlaylist().
  return new ArrayList<>(upNext);
 }

 /** Menambah lagu ke posisi berikutnya (Play Next) — diputar sekali lalu lanjut. */
 public static void addNext(Song song) {
 if (song == null) {
 return;
 }
 upNext.add(0, song);
 notifyQueueChanged();
 }

 /** Menambahkan lagu ke akhir antrian (Add to Queue). */
 public static void addToQueue(Song song) {
 if (song == null) {
 return;
 }
 upNext.add(song);
 notifyQueueChanged();
 }

 /** Menghapus satu lagu dari antrian berdasarkan posisinya. */
 public static void removeFromUpNext(int index) {
 if (index <0 || index >= upNext.size()) {
 return;
 }
 upNext.remove(index);
 notifyQueueChanged();
 }

 /** Mengosongkan seluruh antrian. */
 public static void clearUpNext() {
 if (upNext.isEmpty()) {
 return;
 }
 upNext.clear();
 notifyQueueChanged();
 }

 /** Menandai perubahan antrian kepada UI. */
 private static void notifyQueueChanged() {
 if (instance != null) {
 instance.notifyQueueChangedToCallback();
 }
 }

 private void notifyQueueChangedToCallback() {
 if (callback != null) {
 callback.onQueueChanged();
 }
 }

 public static int getCurrentIndex() {
 return currentIndex;
 }

 public static boolean hasPlaylist() {
 return !playOrder.isEmpty();
 }

 public static boolean isPlaying() {
 return mediaPlayer != null && mediaPlayer.isPlaying();
 }

 /**
 * ID audio session dari MediaPlayer aktif, untuk mengikat efek audio
 * (mis. Equalizer) pada pemutaran. -1 jika player belum ada.
 */
 public static int getAudioSessionId() {
 if (mediaPlayer != null) {
 try {
 return mediaPlayer.getAudioSessionId();
 } catch (Exception ignored) {
 return -1;
 }
 }
 return -1;
 }

 public static int getPosition() {
 return mediaPlayer != null ? mediaPlayer.getCurrentPosition() :0;
 }

 public static int getDuration() {
 return mediaPlayer != null ? mediaPlayer.getDuration() :0;
 }

 public static boolean isShuffle() {
 return shuffle;
 }

 public static void setShuffle(boolean enable) {
 shuffle = enable;
 if (instance != null) {
 instance.notifyStateChanged();
 }
 }

 public static int getRepeatMode() {
 return repeatMode;
 }

 public static Song getCurrentSong() {
 // Jika sedang memutar lagu dari antrian, tampilkan lagu tersebut.
 if (queueSong != null) {
 return queueSong;
 }
 if (currentIndex >=0 && currentIndex < playOrder.size()) {
 return playOrder.get(currentIndex);
 }
 return null;
 }

 public static void playAt(int index) {
 if (playOrder.isEmpty() || index <0 || index >= playOrder.size()) {
 return;
 }
 if (instance == null || mediaPlayer == null) {
 return;
 }
 // Pindah ke lagu playlist; kosongkan lagu antrian yang sedang diputar.
 queueSong = null;
 currentIndex = index;
 playCurrent();
 }

 /**
 * Memutar lagu yang sedang "aktif" (dari antrian jika ada, selain itu dari playlist).
 * Tidak mengubah index — pemanggil mengatur state sebelum memanggil ini.
 * Menggunakan prepareAsync() agar penyiapan lagu (membuka & parsing file audio)
 * tidak memblokir thread UI; pemutaran dimulai di OnPreparedListener.
 */
 private static void playCurrent() {
 if (instance == null || mediaPlayer == null) {
 return;
 }
 Song song = getCurrentSong();
 if (song == null) {
 return;
 }
 cancelCrossfade();
 // Catat statistik: jumlah putar + riwayat (fitur #5).
 PlaybackStats.getInstance(instance).recordPlayStart(song);
 try {
 mediaPlayer.reset();
 mediaPlayer.setDataSource(song.path);
 mediaPlayer.prepareAsync();
 } catch (IOException | SecurityException e) {
 playError = true;
 instance.notifyStateChanged();
 }
 }

 public static void playOrResume() {
 if (instance == null) {
 return;
 }
 if (isPlaying()) {
 return;
 }
 if (currentIndex <0 && !playOrder.isEmpty()) {
 playAt(0);
 } else if (currentIndex >=0 && mediaPlayer != null) {
 mediaPlayer.start();
 lastTrackedPositionMs = getPosition();
 startStatsTracking();
 startCrossfadeCheck();
 instance.requestAudioFocus();
 instance.notifyStateChanged();
 instance.showNotification();
 }
 }

 public static void pause() {
 if (isPlaying() && mediaPlayer != null) {
 mediaPlayer.pause();
 cancelCrossfade();
 stopStatsTracking();
 if (instance != null) {
 instance.notifyStateChanged();
 instance.showNotification();
 }
 }
 }

 public static void seekTo(int positionMs) {
 if (mediaPlayer != null) {
 mediaPlayer.seekTo(positionMs);
 }
 }

 public static void skip(boolean forward) {
 if (playOrder.isEmpty() || instance == null) {
 return;
 }
 cancelCrossfade();

 // Next dengan antrian: putar lagu antrian paling depan.
 if (forward && !upNext.isEmpty()) {
 // Hanya majukan posisi playlist jika yang sedang berjalan adalah lagu playlist.
 if (queueSong == null) {
 currentIndex = (currentIndex +1) % playOrder.size();
 }
 queueSong = upNext.remove(0);
 notifyQueueChanged();
 playCurrent();
 return;
 }

 // Sedang memutar lagu antrian tanpa antrian tersisa: lanjut ke playlist.
 if (queueSong != null) {
 queueSong = null;
 playCurrent();
 return;
 }

 // Next/Prev biasa di playlist.
 if (forward) {
 currentIndex = (currentIndex +1) % playOrder.size();
 } else {
 currentIndex = (currentIndex -1 + playOrder.size()) % playOrder.size();
 }

 if (shuffle && playOrder.size() >1) {
 int newIndex;
 do {
 newIndex = random.nextInt(playOrder.size());
 } while (newIndex == currentIndex);
 currentIndex = newIndex;
 }

 playCurrent();
 }

 public static void cycleRepeat() {
 repeatMode = (repeatMode +1) %3;
 if (instance != null) {
 instance.notifyStateChanged();
 }
 }

 // ===================== CROSSFADE =====================

 /** Apakah crossfade aktif di pengaturan. */
 public static boolean isCrossfadeEnabled() {
 if (instance == null) {
 return false;
 }
 return instance.getSharedPreferences(PREFS_CROSSFADE, Context.MODE_PRIVATE)
 .getBoolean(KEY_CROSSFADE, false);
 }

 /** Mengaktifkan/mematikan crossfade. */
 public static void setCrossfadeEnabled(Context context, boolean enabled) {
 context.getSharedPreferences(PREFS_CROSSFADE, Context.MODE_PRIVATE)
 .edit().putBoolean(KEY_CROSSFADE, enabled).apply();
 }

 /** Menentukan lagu berikutnya tanpa mengubah state (untuk crossfade). */
 private static Song getNextSong() {
 if (!upNext.isEmpty()) {
 return upNext.get(0);
 }
 if (playOrder.isEmpty()) {
 return null;
 }
 int nextIdx;
 if (shuffle) {
 if (playOrder.size() <= 1) {
 return null;
 }
 do {
 nextIdx = random.nextInt(playOrder.size());
 } while (nextIdx == currentIndex);
 return playOrder.get(nextIdx);
 }
 if (repeatMode == REPEAT_ONE) {
 return currentIndex >= 0 && currentIndex < playOrder.size()
 ? playOrder.get(currentIndex) : null;
 }
 nextIdx = currentIndex + 1;
 if (nextIdx >= playOrder.size()) {
 return repeatMode == REPEAT_ALL ? playOrder.get(0) : null;
 }
 return playOrder.get(nextIdx);
 }

 /** Memulai crossfade ke lagu berikutnya (3 detik fade). */
 private static void startCrossfade() {
 if (instance == null || crossfadePlayer == null) {
 return;
 }
 Song next = getNextSong();
 if (next == null) {
 return;
 }
 isCrossfading = true;
 crossfadeHandler.removeCallbacks(crossfadeCheckRunnable);
 try {
 crossfadePlayer.reset();
 crossfadePlayer.setDataSource(next.path);
 crossfadePlayer.prepareAsync();
 } catch (Exception e) {
 isCrossfading = false;
 }
 }

 /** Membatalkan crossfade yang sedang berjalan. */
 private static void cancelCrossfade() {
 isCrossfading = false;
 crossfadeHandler.removeCallbacks(crossfadeCheckRunnable);
 crossfadeHandler.removeCallbacks(crossfadeFadeRunnable);
 if (crossfadePlayer != null) {
 try {
 crossfadePlayer.reset();
 } catch (Exception ignored) {
 }
 }
 }

 /** Memulai pengecekan posisi untuk memicu crossfade. */
 private static void startCrossfadeCheck() {
 crossfadeHandler.removeCallbacks(crossfadeCheckRunnable);
 if (isCrossfadeEnabled() && isPlaying()) {
 crossfadeHandler.postDelayed(crossfadeCheckRunnable, CROSSFADE_CHECK_MS);
 }
 }

 /** Pengecekan periodik: mulai crossfade saat mendekati akhir lagu. */
 private static void runCrossfadeCheck() {
 if (!isCrossfadeEnabled() || !isPlaying() || isCrossfading
 || repeatMode == REPEAT_ONE) {
 return;
 }
 int pos = getPosition();
 int dur = getDuration();
 if (dur <=0) {
 crossfadeHandler.postDelayed(crossfadeCheckRunnable, CROSSFADE_CHECK_MS);
 return;
 }
 if (dur - pos <= CROSSFADE_MS + 1000 && getNextSong() != null) {
 startCrossfade();
 } else {
 crossfadeHandler.postDelayed(crossfadeCheckRunnable, CROSSFADE_CHECK_MS);
 }
 }

 /** Animasi fade: kurangi volume player lama, naikkan volume player baru. */
 private static void runCrossfadeFade() {
 if (!isCrossfading || mediaPlayer == null || crossfadePlayer == null) {
 return;
 }
 if (!crossfadePlayer.isPlaying()) {
 return;
 }
 int dur = getDuration();
 int pos = getPosition();
 if (dur <=0) {
 transitionCrossfade();
 return;
 }
 float progress = Math.min(1f, (float)(dur - pos) / CROSSFADE_MS);
 float outVol = Math.max(0f, 1f - progress);
 mediaPlayer.setVolume(outVol, outVol);
 crossfadePlayer.setVolume(progress, progress);
 if (progress >= 1f) {
 transitionCrossfade();
 } else {
 crossfadeHandler.postDelayed(crossfadeFadeRunnable, CROSSFADE_FADE_STEP_MS);
 }
 }

 /** Transisi final: ganti player lama → player baru setelah fade selesai. */
 private static void transitionCrossfade() {
     isCrossfading = false;
     if (mediaPlayer != null) {
         mediaPlayer.setOnCompletionListener(null);
         mediaPlayer.stop();
         mediaPlayer.release();
     }
     mediaPlayer = crossfadePlayer;
     mediaPlayer.setOnCompletionListener(mp -> { if (instance != null) instance.onSongCompleted(); });
     Song nextSong = getCurrentSong();
     if (nextSong != null) {
         currentIndex = playOrder.indexOf(nextSong);
     }
     if (instance != null) {
         instance.notifySongChanged(currentIndex);
         instance.notifyStateChanged();
         instance.showNotification();
     }
     startCrossfadeCheck();
 }

 // ===================== SLEEP TIMER (fitur #2) =====================

 /** Apakah sleep timer sedang aktif. */
 public static boolean isSleepTimerActive() {
 return sleepTimerActive;
 }

 /** Total durasi sleep timer yang diset (menit).0 jika nonaktif. */
 public static int getSleepTimerMinutes() {
 return sleepTimerMinutes;
 }

 /**
 * Sisa waktu sleep timer dalam detik. Mengembalikan -1 jika tidak aktif.
 * Nilai dibulatkan ke atas (ceil) agar tidak langsung terlihat "0:00".
 */
 public static long getSleepTimerRemainingSeconds() {
 if (!sleepTimerActive) {
 return -1L;
 }
 long remainingMs = sleepTimerEndAtMs - System.currentTimeMillis();
 if (remainingMs <0) {
 remainingMs =0;
 }
 return (remainingMs +999L) /1000L;
 }

 /**
 * Mengatur sleep timer selama {@code minutes} menit.
 * Memanggil dengan {@code minutes <=0} untuk membatalkan timer.
 */
 public static void setSleepTimer(int minutes) {
 sleepHandler.removeCallbacks(sleepRunnable);
 if (minutes <=0) {
 sleepTimerActive = false;
 sleepTimerMinutes =0;
 if (instance != null) {
 instance.notifyStateChanged();
 }
 return;
 }
 sleepTimerActive = true;
 sleepTimerMinutes = minutes;
 sleepTimerEndAtMs = System.currentTimeMillis() + (long) minutes *60_000L;
 sleepHandler.removeCallbacks(sleepRunnable);
 sleepHandler.post(sleepRunnable);
 if (instance != null) {
 instance.notifyStateChanged();
 }
 }

 /** Membatalkan sleep timer yang sedang berjalan. */
 public static void cancelSleepTimer() {
 setSleepTimer(0);
 }

 private void onSongCompleted() {
 if (repeatMode == REPEAT_ONE) {
 cancelCrossfade();
 playCurrent(); // ulangi lagu yang sama (dari antrian atau playlist)
 return;
 }

 // Crossfade aktif: transisi sudah dilakukan oleh fade runnable.
 // Lagu yang selesai ini adalah player lama; abaikan.
 if (isCrossfading) {
 return;
 }

 // Crossfade aktif di pengaturan: gunakan crossfade untuk transisi.
 if (isCrossfadeEnabled() && getNextSong() != null) {
 // Majukan indeks/queue sesuai lagu berikutnya.
 if (!upNext.isEmpty()) {
 if (queueSong == null) {
 currentIndex = (currentIndex +1) % playOrder.size();
 }
 queueSong = upNext.remove(0);
 notifyQueueChanged();
 } else {
 if (repeatMode == REPEAT_ALL || shuffle) {
 skip(true);
 } else if (currentIndex < playOrder.size() -1) {
 currentIndex++;
 }
 }
 cancelCrossfade();
 playCurrent();
 return;
 }

 // Ada antrian berikutnya.
 if (!upNext.isEmpty()) {
 // Hanya majukan posisi playlist jika yang selesai adalah lagu playlist.
 if (queueSong == null) {
 currentIndex = (currentIndex +1) % playOrder.size();
 }
 queueSong = upNext.remove(0);
 notifyQueueChanged();
 playCurrent();
 return;
 }

 // Lagu antrian selesai, antrian habis: lanjut ke playlist di currentIndex.
 if (queueSong != null) {
 queueSong = null;
 if (repeatMode == REPEAT_OFF && currentIndex == playOrder.size() -1) {
 if (mediaPlayer != null) {
 mediaPlayer.seekTo(0);
 mediaPlayer.pause();
 }
 abandonAudioFocus();
 notifyStateChanged();
 showNotification();
 } else {
 playCurrent();
 }
 return;
 }

 // Lagu playlist selesai, tidak ada antrian.
 if (repeatMode == REPEAT_ALL || shuffle) {
 skip(true);
 } else {
 if (currentIndex == playOrder.size() -1) {
 if (mediaPlayer != null) {
 mediaPlayer.seekTo(0);
 mediaPlayer.pause();
 }
 abandonAudioFocus();
 notifyStateChanged();
 showNotification();
 } else {
 skip(true);
 }
 }
 }

 // ===================== CALLBACK KE UI =====================

 private ServiceCallback callback;
 // Disimpan statis agar tidak hilang sebelum instance service aktif.
 private static ServiceCallback pendingCallback;

 public static void setCallback(ServiceCallback cb) {
 pendingCallback = cb;
 if (instance != null) {
 instance.callback = cb;
 }
 }

 /** Memasang callback hanya jika belum ada yang terdaftar, agar pemilik UI
 *  (mis. PlayerActivity) tidak ditimpa oleh panggilan create() activity lain. */
 public static void setCallbackIfUnset(ServiceCallback cb) {
 boolean hasCallback = (instance != null && instance.callback != null)
 || pendingCallback != null;
 if (!hasCallback) {
 setCallback(cb);
 }
 }

 private void notifyStateChanged() {
 if (callback != null) {
 callback.onPlaybackStateChanged();
 }
 }

 private void notifySongChanged(int index) {
 if (callback != null) {
 callback.onSongChanged(index);
 }
 }

 // ===================== NOTIFIKASI MEDIA =====================

 private void createNotificationChannel() {
 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
 NotificationChannel channel = new NotificationChannel(
 CHANNEL_ID,
 getString(R.string.notification_channel_name),
 NotificationManager.IMPORTANCE_LOW);
 channel.setDescription(getString(R.string.notification_channel_desc));
 channel.setShowBadge(false);
 NotificationManager nm = getSystemService(NotificationManager.class);
 if (nm != null) {
 nm.createNotificationChannel(channel);
 }
 }
 }

 private void showNotification() {
 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
 if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
 != android.content.pm.PackageManager.PERMISSION_GRANTED) {
 return;
 }
 }
 NotificationManagerCompat nm = NotificationManagerCompat.from(this);
 nm.notify(NOTIFICATION_ID, buildNotification());
 // Sinkronkan widget layar utama (fitur #10) dengan status pemutaran terbaru.
 MusicWidgetProvider.notifyUpdate(this);
 }

 private Notification buildNotification() {
 // Membuka PlayerActivity saat notifikasi diketuk.
 Intent openIntent = new Intent(this, PlayerActivity.class);
 openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
 PendingIntent contentIntent = PendingIntent.getActivity(this,0, openIntent,
 PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

 // Aksi toggle play/pause.
 Intent toggleIntent = new Intent(this, MusicService.class).setAction(ACTION_TOGGLE);
 PendingIntent togglePI = PendingIntent.getService(this,1, toggleIntent,
 PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

 // Aksi sebelumnya.
 Intent prevIntent = new Intent(this, MusicService.class).setAction(ACTION_PREV);
 PendingIntent prevPI = PendingIntent.getService(this,2, prevIntent,
 PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

 // Aksi berikutnya.
 Intent nextIntent = new Intent(this, MusicService.class).setAction(ACTION_NEXT);
 PendingIntent nextPI = PendingIntent.getService(this,3, nextIntent,
 PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

 Song song = getCurrentSong();
 String title = song != null ? song.title : getString(R.string.no_playing);
 String artist = song != null && song.artist != null && !song.artist.trim().isEmpty()
 ? song.artist : getString(R.string.unknown_artist);

 Bitmap artwork = null;
 if (song != null) {
 artwork = loadArtworkForNotification(song.path);
 }

 // ---- Tampilan kustom (RemoteViews) untuk notifikasi media yang modern ----
 RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.notification_player);

 // Artwork bulat: tampilkan sampul jika ada, sebaliknya ikon musik fallback.
 // (RemoteViews tidak mendukung setScaleType, jadi pakai dua ImageView bertumpuk
 // yang di-swap dengan setViewVisibility; scaleType sudah dikunci di XML.)
 if (artwork != null) {
 remoteViews.setImageViewBitmap(R.id.np_artwork, artwork);
 remoteViews.setViewVisibility(R.id.np_artwork, View.VISIBLE);
 remoteViews.setViewVisibility(R.id.np_artwork_fallback, View.GONE);
 } else {
 remoteViews.setViewVisibility(R.id.np_artwork, View.GONE);
 remoteViews.setViewVisibility(R.id.np_artwork_fallback, View.VISIBLE);
 }

 // Judul & artis.
 remoteViews.setTextViewText(R.id.np_title, title);
 remoteViews.setTextViewText(R.id.np_artist, artist);

 // Ikon play/pause sesuai status.
 remoteViews.setImageViewResource(R.id.np_play_pause,
 isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);

 // Pasang aksi pada tombol.
 remoteViews.setOnClickPendingIntent(R.id.np_prev, prevPI);
 remoteViews.setOnClickPendingIntent(R.id.np_play_pause, togglePI);
 remoteViews.setOnClickPendingIntent(R.id.np_next, nextPI);

 // Sinkronkan metadata & status ke MediaSession agar lock screen juga menampilkan info.
 updateMediaSessionState(song, artwork);

 int playPauseIcon = isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play;
 String playPauseLabel = isPlaying() ? getString(R.string.pause) : getString(R.string.play);

 // Buat notifikasi dasar.
 NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
 .setSmallIcon(R.drawable.ic_music_note)
 .setContentTitle(title)
 .setContentText(artist)
 .setContentIntent(contentIntent)
 .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
 .setOnlyAlertOnce(true)
 .setShowWhen(false)
 // Aksi standar untuk MediaStyle (lock screen & expanded view).
 .addAction(new NotificationCompat.Action(R.drawable.ic_prev,
 getString(R.string.prev), prevPI))
 .addAction(new NotificationCompat.Action(playPauseIcon,
 playPauseLabel, togglePI))
 .addAction(new NotificationCompat.Action(R.drawable.ic_next,
 getString(R.string.next), nextPI))
 // Terapkan MediaStyle agar status media dikenali sistem (lock screen),
 // sekaligus tampilan kustom RemoteViews untuk tampilan modern di notifikasi.
 .setStyle(new MediaStyle()
 .setMediaSession(mediaSession != null ? mediaSession.getSessionToken() : null)
 .setShowActionsInCompactView(0,1,2))
 // Tampilan modern (RemoteViews) untuk notifikasi collapsed.
 .setCustomContentView(remoteViews);

 if (artwork != null) {
 builder.setLargeIcon(artwork);
 }

 return builder.build();
 }

 /**
 * Memperbarui metadata &amp; status pada MediaSessionCompat agar lock screen dan
 * panel media menampilkan judul, artis, dan ikon play/pause yang benar.
 */
 private void updateMediaSessionState(Song song, Bitmap artwork) {
 if (mediaSession == null) {
 return;
 }
 if (song != null) {
 MediaMetadataCompat.Builder meta = new MediaMetadataCompat.Builder()
 .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
 .putString(MediaMetadataCompat.METADATA_KEY_ARTIST,
 song.artist != null ? song.artist : "");
 if (artwork != null) {
 meta.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork);
 }
 mediaSession.setMetadata(meta.build());
 } else {
 mediaSession.setMetadata(null);
 }

 long duration = song != null ? song.duration :0L;
 long position =0L;
 if (mediaPlayer != null) {
 try {
 position = mediaPlayer.getCurrentPosition();
 } catch (Exception ignored) {
 }
 }

 int playbackState = isPlaying()
 ? PlaybackStateCompat.STATE_PLAYING
 : PlaybackStateCompat.STATE_PAUSED;
 long actions = PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE
 | PlaybackStateCompat.ACTION_PLAY_PAUSE
 | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
 | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;

 PlaybackStateCompat state = new PlaybackStateCompat.Builder()
 .setActions(actions)
 .setState(playbackState, position,1.0f)
 .build();
 mediaSession.setPlaybackState(state);
 }

 /**
 * Memuat sampul album dan mengecilkan ukurannya agar cocok untuk notifikasi
 * (mencegah bitmap terlalu besar &amp; boros memori).
 */
 private Bitmap loadArtworkForNotification(String path) {
 // Downscale langsung saat decode (max 256px) agar hemat memori; tidak
 // perlu mendekode resolusi penuh lalu di-scaled lagi.
 return ArtworkLoader.loadArtwork(path, 256);
 }

 /**
 * Memulai service foreground (background playback).
 * Dipanggil dari Activity sebelum kontrol pemutaran.
 */
 public static void start(Context context) {
 Intent intent = new Intent(context, MusicService.class);
 ContextCompat.startForegroundService(context, intent);
 }

}
