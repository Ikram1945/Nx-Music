package com.nx.music;

import com.ikram.system.TimeUtils;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Layar utama pemutar musik. Menampilkan artwork, kontrol pemutaran,
 * dan seek bar. Tombol di header membuka {@link SongListActivity}.
 *
 * <p>Pemutaran dikelola oleh {@link PlaybackController} singleton sehingga
 * tetap berlanjut saat pengguna pindah ke daftar lagu.</p>
 */
public class PlayerActivity extends AppCompatActivity implements PlaybackCallback {

    private static final long PROGRESS_INTERVAL_MS = 500L;
    /** Selang ganti kata penyemangat (ms). */
    private static final long MOTIVATION_INTERVAL_MS = 6000L;
    /** Daftar kata penyemangat yang diputar bergiliran. */
    private static final int[] MOTIVATION_RES = {
            R.string.motivation_1, R.string.motivation_2, R.string.motivation_3,
            R.string.motivation_4, R.string.motivation_5
    };
    private static final int REQ_CODE_PERMISSION = 100;
    private static final int REQ_CODE_NOTIFICATION = 101;
    private static final int REQ_CODE_SPECTRUM = 102;
    /** Jumlah lompatan seek (dalam milidetik) saat gesture swipe pada artwork (fitur #9). */
    static final int GESTURE_SEEK_STEP_MS = 5000;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private PlaybackController playback;

    private TextView titleNowPlaying;
    private TextView artistNowPlaying;
    private TextView txtCurrentTime;
    private TextView txtTotalTime;
    private TextView txtSleepTimer;
    private SeekBar seekBar;
    private ImageView artNowPlaying;
    private ImageButton btnPlayPause;
    private ImageButton btnShuffle;
    private ImageButton btnRepeat;
    private ImageButton btnNext;
    private ImageButton btnPrev;
    private ImageButton btnEqualizer;
    private ImageButton btnSleepTimer;
    private SpectrumView spectrumView;
    private TextView txtGreeting;
    private TextView txtMotivation;
    private View greetingCard;
    private TextView txtInfoValue;
    private final List<ObjectAnimator> barPulseAnimators = new java.util.ArrayList<>();
    /** Id bar denyut (equalizer kecil di samping sapaan). */
    private static final int[] BAR_PULSE_IDS = {
            R.id.bar_pulse_1, R.id.bar_pulse_2, R.id.bar_pulse_3,
            R.id.bar_pulse_4, R.id.bar_pulse_5
    };
    private final Runnable motivationRunnable =
            new ScheduledRunnable(handler, MOTIVATION_INTERVAL_MS, this::rotateMotivation);
    private int motivationIndex = 0;

    private EqualizerHelper eqHelper;
    private AudioEffectsHelper audioEffectsHelper;

    /** Nama tiap preset reverb (urutan sinkron dengan AudioEffectsHelper.REVERB_PRESETS) + "Kustom". */
    private static final int[] REVERB_PRESET_LABELS = {
            R.string.reverb_none,
            R.string.reverb_small_room,
            R.string.reverb_medium_room,
            R.string.reverb_large_room,
            R.string.reverb_studio,
            R.string.reverb_concert_hall,
            R.string.reverb_large_hall,
            R.string.reverb_cathedral,
            R.string.reverb_church,
            R.string.reverb_cave,
            R.string.reverb_stadium,
            R.string.reverb_plate,
            R.string.reverb_custom
    };
    // Path lagu yang artwork-nya sedang diminta; dipakai agar hasil request
    // yang sudah kedaluwarsa (ganti lagu cepat) tidak menimpa artwork baru.
    private volatile String artworkRequestPath;

    // ---- Animasi artwork (fitur #10) ----
    private RotateAnimation artworkRotation;
    private ObjectAnimator glowPulseAnimator;

    private final Runnable progressRunnable =
            new ScheduledRunnable(handler, PROGRESS_INTERVAL_MS, this::updateProgress);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        // Terapkan tema kustom
        ThemeHelper.applyThemeToActivity(this);

        bindViews();

        // instance pertama atau perbarui callback milik layar ini
        playback = PlaybackController.create(this);

        setupSeekBar();
        setupButtons();
        setupGestureSeek();
        refreshUI();
        setupGreeting();
        checkAndRequestPermission();
        requestSpectrumPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (playback != null) {
            playback.setCallback(this);
            refreshUI();
            attachEqualizer();
            attachAudioEffects();
            startSpectrum();
            syncArtworkAnimation();
            updateSleepTimerButton();
            updateSleepTimerText();
            // onPause mematikan bar + rotasi; keduanya harus dinyalakan lagi di sini,
            // kalau tidak bar/sapaan "mati" setiap kali kembali dari layar lain.
            startBarPulse();
            startMotivationRotation();
            playGreetingEntrance();
            if (playback.isPlaying()) {
                handler.removeCallbacks(progressRunnable);
                handler.post(progressRunnable);
            }
        }
    }

    /** Nyalakan bar spectrum bila izin RECORD_AUDIO ada. */
    private void startSpectrum() {
        if (spectrumView == null) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            return; // belum diizinkan; bar tampil idle
        }
        int session = playback != null ? playback.getAudioSessionId() : -1;
        spectrumView.start(session);
    }

    /** Matikan bar spectrum & lepaskan Visualizer. */
    private void stopSpectrum() {
        if (spectrumView != null) spectrumView.stop();
    }

    /** Minta izin RECORD_AUDIO untuk Visualizer (bar spectrum). */
    private void requestSpectrumPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_CODE_SPECTRUM);
        }
    }

    /** Menghubungkan equalizer ke audio session pemutar (untuk mengikat efek). */
    private void attachEqualizer() {
        try {
            if (eqHelper == null) {
                eqHelper = EqualizerHelper.getInstance(this);
            }
            eqHelper.attach(playback != null ? playback.getAudioSessionId() : -1);
        } catch (Exception ignored) {
            // Equalizer tidak tersedia; abaikan.
        }
    }

    /** Menghubungkan efek audio tambahan (bass/virtualizer/loudness) ke session. */
    private void attachAudioEffects() {
        try {
            if (audioEffectsHelper == null) {
                audioEffectsHelper = AudioEffectsHelper.getInstance(this);
            }
            audioEffectsHelper.attach(playback != null ? playback.getAudioSessionId() : -1);
        } catch (Exception ignored) {
            // Efek audio tidak tersedia; abaikan.
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // WAJIB lepas callback: PlaybackController singleton masih hidup dan terus memanggil
        // onPlaybackStateChanged() ke Activity ini walau sudah di-pause. Tanpa ini, callback
        // mengakses view (txtTotalTime/seekBar) yang sudah dibuang -> NullPointerException.
        if (playback != null) {
            playback.setCallback(null);
        }
        handler.removeCallbacks(progressRunnable);
        handler.removeCallbacks(motivationRunnable);
        stopBarPulse();
        stopSpectrum();
        // Hentikan animasi agar tidak terus berjalan saat layar tak terlihat.
        stopArtworkRotation();
        stopGlowPulse();
    }

    /** Sapaan berdasar jam + kata penyemangat bergiliran + bar berdenyut. */
    private void setupGreeting() {
        txtGreeting.setText(greetingForHour(java.util.Calendar.getInstance()
                .get(java.util.Calendar.HOUR_OF_DAY)));
        startBarPulse();
        startMotivationRotation();
        playGreetingEntrance();
    }

    /**
     * Animasi masuk blok sapaan saat layar dibuka: fade + naik sedikit dari bawah,
     * lalu teks sapaan muncul berurutan (alpha). Dipanggil ulang di onResume sehingga
     * sapaan selalu "hidup" setiap kembali ke layar ini.
     */
    private void playGreetingEntrance() {
        if (greetingCard == null) return;
        greetingCard.animate().cancel();
        greetingCard.setAlpha(0f);
        greetingCard.setTranslationY(dp(16));
        greetingCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(420L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
        if (txtGreeting != null) {
            txtGreeting.animate().cancel();
            txtGreeting.setAlpha(0f);
            txtGreeting.animate().alpha(1f).setStartDelay(180L).setDuration(320L).start();
        }
    }

    /** Animasi "pop" singkat pada judul lagu saat berganti lagu. */
    private void playTitlePop() {
        if (titleNowPlaying == null) return;
        titleNowPlaying.animate().cancel();
        titleNowPlaying.setScaleX(0.92f);
        titleNowPlaying.setScaleY(0.92f);
        titleNowPlaying.animate()
                .scaleX(1f).scaleY(1f)
                .setDuration(260L)
                .setInterpolator(new android.view.animation.OvershootInterpolator())
                .start();
    }

    /** Pilih sapaan sesuai jam (0-23). */
    private int greetingForHour(int hour) {
        if (hour >= 4 && hour < 11) return R.string.greeting_morning;
        if (hour >= 11 && hour < 15) return R.string.greeting_noon;
        if (hour >= 15 && hour < 18) return R.string.greeting_evening;
        return R.string.greeting_night;
    }

    /** Ganti kata penyemangat dengan animasi fade singkat. */
    private void rotateMotivation() {
        if (txtMotivation == null) return;
        motivationIndex = (motivationIndex + 1) % MOTIVATION_RES.length;
        txtMotivation.animate().cancel();
        txtMotivation.animate().alpha(0f).setDuration(220L).withEndAction(() -> {
            txtMotivation.setText(MOTIVATION_RES[motivationIndex]);
            txtMotivation.animate().alpha(1f).setDuration(320L).start();
        }).start();
    }

    private void startMotivationRotation() {
        handler.removeCallbacks(motivationRunnable);
        handler.post(motivationRunnable);
    }

    /**
     * Bar equalizer kecil di samping sapaan: tiap bar berdenyut tinggi (scaleY)
     * dengan durasi & fase berbeda supaya terlihat ramai/berirama, bukan seragam.
     */
    private void startBarPulse() {
        stopBarPulse();
        int n = BAR_PULSE_IDS.length;
        for (int i = 0; i < n; i++) {
            final View bar = findViewById(BAR_PULSE_IDS[i]);
            if (bar == null) continue;
            // Pivot di tengah baru benar setelah layout dihitung.
            bar.post(() -> bar.setPivotY(bar.getHeight() / 2f));
            // Durasi berselang-seling supaya bar tidak kompak naik-turun bersama.
            long dur = 520L + (i % 3) * 160L;
            ObjectAnimator a = ObjectAnimator.ofFloat(bar, "scaleY", 0.35f, 1f);
            a.setDuration(dur);
            a.setStartDelay(i * 90L);
            a.setRepeatMode(ValueAnimator.REVERSE);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.start();
            barPulseAnimators.add(a);
        }
    }

    private void stopBarPulse() {
        for (ObjectAnimator a : barPulseAnimators) a.cancel();
        barPulseAnimators.clear();
    }

    private void bindViews() {
        titleNowPlaying = findViewById(R.id.title_now_playing);
        artistNowPlaying = findViewById(R.id.artist_now_playing);
        artNowPlaying = findViewById(R.id.art_now_playing);
        txtCurrentTime = findViewById(R.id.txt_current_time);
        txtTotalTime = findViewById(R.id.txt_total_time);
        txtSleepTimer = findViewById(R.id.txt_sleep_timer);
        seekBar = findViewById(R.id.seek_bar);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnShuffle = findViewById(R.id.btn_shuffle);
        btnRepeat = findViewById(R.id.btn_repeat);
        btnNext = findViewById(R.id.btn_next);
        btnPrev = findViewById(R.id.btn_prev);
        btnEqualizer = findViewById(R.id.btn_equalizer);
        btnSleepTimer = findViewById(R.id.btn_sleep_timer);
        spectrumView = findViewById(R.id.spectrum_view);
        txtGreeting = findViewById(R.id.txt_greeting);
        txtMotivation = findViewById(R.id.txt_motivation);
        greetingCard = findViewById(R.id.greeting_card);
        txtInfoValue = findViewById(R.id.txt_info_value);
    }

    private void setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(new SeekBarListener(playback));
    }

    private void setupButtons() {
        btnPlayPause.setOnClickListener(v -> {
            if (playback.isPlaying()) {
                playback.pause();
            } else {
                playback.playOrResume();
            }
        });

        btnShuffle.setOnClickListener(v -> {
            playback.setShuffle(!playback.isShuffle());
            updateModeButtons();
            Toast.makeText(this,
                    playback.isShuffle() ? R.string.shuffle : R.string.shuffle_off,
                    Toast.LENGTH_SHORT).show();
        });

        btnRepeat.setOnClickListener(v -> {
            playback.cycleRepeat();
            updateModeButtons();
        });

        btnNext.setOnClickListener(v -> playback.skip(true));
        btnPrev.setOnClickListener(v -> playback.skip(false));

        ImageButton btnOpenList = findViewById(R.id.btn_open_list);
        btnOpenList.setOnClickListener(v ->
                startActivity(new Intent(this, SongListActivity.class)));
        // Tekan lama ikon antrian untuk membuka dialog daftar "Putar Berikutnya".
        btnOpenList.setOnLongClickListener(v -> {
            showQueueDialog();
            return true;
        });

        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> onBackPressed());

        btnEqualizer.setOnClickListener(v -> showEqualizerDialog());

        ImageButton btnAudioEffects = findViewById(R.id.btn_audio_effects);
        btnAudioEffects.setOnClickListener(v -> showAudioEffectsDialog());

        btnSleepTimer.setOnClickListener(v -> showSleepTimerDialog());
    }

    /**
     * Gesture seek pada artwork (fitur #9): swipe kiri untuk maju 5 detik,
     * swipe kanan untuk mundur 5 detik. Menggunakan {@link GestureDetector}
     * dengan listener FLING agar tetap responsif dan tidak bertabrakan dengan
     * animasi rotasi artwork.
     */
    private void setupGestureSeek() {
        final GestureDetector detector = new GestureDetector(this,
                new ArtworkGestureListener(this, playback));

        artNowPlaying.setOnTouchListener((v, event) -> detector.onTouchEvent(event));
    }

    /** Dipanggil {@link ArtworkGestureListener} setelah gesture seek selesai. */
    void onGestureSeekDone(int target) {
        updateProgress();
    }

    @Override
    public void onBackPressed() {
        // PlayerActivity adalah launcher. Jika ada layar di belakang, kembali;
        // jika tidak, keluar dari aplikasi.
        if (isTaskRoot()) {
            finishAffinity();
        } else {
            super.onBackPressed();
        }
    }

    private void checkAndRequestPermission() {
        if (playback != null && !playback.getPlaylist().isEmpty()) {
            // playlist sudah siap, tidak perlu minta izin baca audio lagi.
            // Namun pastikan izin notifikasi tetap diminta agar notifikasi media muncul.
            requestNotificationPermission();
            return;
        }

        String readPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        java.util.ArrayList<String> needed = new java.util.ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, readPermission)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(readPermission);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]), REQ_CODE_PERMISSION);
        } else {
            loadSongs();
        }
    }

    /** Minta izin menampilkan notifikasi (Android 13+) untuk kontrol media. */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_CODE_NOTIFICATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CODE_PERMISSION) {
            // Muat lagu jika izin baca audio diberikan (abaikan hasil izin notifikasi).
            String readPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? Manifest.permission.READ_MEDIA_AUDIO
                    : Manifest.permission.READ_EXTERNAL_STORAGE;
            if (ContextCompat.checkSelfPermission(this, readPermission)
                    == PackageManager.PERMISSION_GRANTED) {
                loadSongs();
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQ_CODE_NOTIFICATION) {
            // izin notifikasi tidak wajib untuk memutar musik
        } else if (requestCode == REQ_CODE_SPECTRUM) {
            // izin spectrum diberikan/ditolak; nyalakan bila diizinkan
            startSpectrum();
        }
    }

    private void loadSongs() {
        // Query MediaStore di background agar UI tidak membeku pada daftar besar.
        new Thread(() -> {
            final List<Song> songs = SongLoader.loadSongs(PlayerActivity.this);
            runOnUiThread(() -> {
                if (songs.isEmpty()) {
                    Toast.makeText(PlayerActivity.this, R.string.no_songs,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                playback.setPlaylist(songs);
                updateNowPlayingUI();
            });
        }).start();
    }

    // ---- PlaybackCallback ----

    @Override
    public void onPlaybackStateChanged() {
        updateNowPlayingUI();
        syncArtworkAnimation();
        if (playback.isPlaying()) {
            handler.removeCallbacks(progressRunnable);
            handler.post(progressRunnable);
        } else {
            handler.removeCallbacks(progressRunnable);
            updateProgress();
        }
    }

    @Override
    public void onSongChanged(int index) {
        updateNowPlayingUI();
        playTitlePop();
        // Saat lagu mulai diputar, audio session menjadi aktif. Re-attach
        // equalizer & efek audio agar tersedia (beberapa perangkat butuh
        // session aktif agar Equalizer bisa dibuat).
        attachEqualizer();
        attachAudioEffects();
    }

    @Override
    public void onProgress(long position, long duration) {
        // posisi diperbarui melalui Runnable updateProgress()
    }

    @Override
    public void onQueueChanged() {
        // Antrian berubah (mis. lagu baru dimasukkan / habis). Tidak ada
        // indikator khusus di player utama; dialog antrian membaca data saat dibuka.
    }

    // ---- Pembaruan UI ----

    private void refreshUI() {
        updateNowPlayingUI();
        updateModeButtons();
        updateSleepTimerButton();
        updateSongInfoCard();
    }

    /** Kartu info: jumlah lagu di antrian (uji: UI + ID + string baru). */
    private void updateSongInfoCard() {
        if (txtInfoValue == null || playback == null) return;
        txtInfoValue.setText(String.valueOf(playback.getPlaylist().size()));
    }

    private void updateProgress() {
        // Guard null: runnable bisa tiba setelah Activity di-pause/destroy (view sudah dibuang).
        if (seekBar == null || txtCurrentTime == null || txtTotalTime == null) return;
        if (playback != null && playback.isPlaying()) {
            int current = playback.getPosition();
            int total = playback.getDuration();
            if (total > 0) {
                seekBar.setMax(total);
                seekBar.setProgress(current);
                txtCurrentTime.setText(TimeUtils.format(current));
                txtTotalTime.setText(TimeUtils.format(total));
            }
        }
    }

    private void updateNowPlayingUI() {
        Song song = playback.getCurrentSong();
        if (song != null) {
            titleNowPlaying.setText(song.title);
            String artist = (song.artist == null || song.artist.trim().isEmpty())
                    ? getString(R.string.unknown_artist)
                    : song.artist;
            artistNowPlaying.setText(artist);
            loadArtworkAsync(song.path);
        } else {
            titleNowPlaying.setText(R.string.no_playing);
            artistNowPlaying.setText(R.string.no_playing_artist);
            showDefaultArtwork();
        }

        if (playback.isPlaying()) {
            btnPlayPause.setImageResource(R.drawable.ic_pause);
            btnPlayPause.setContentDescription(getString(R.string.pause));
        } else {
            btnPlayPause.setImageResource(R.drawable.ic_play);
            btnPlayPause.setContentDescription(getString(R.string.play));
        }
    }

    private void loadArtworkAsync(String path) {
        artworkRequestPath = path;
        new Thread(() -> {
            // Downscale saat decode agar hemat memori (artwork tampil ~280dp).
            Bitmap bitmap = ArtworkLoader.loadArtwork(path, 560);
            handler.post(() -> {
                // Abaikan hasil jika lagu sudah berganti sejak request dimulai.
                if (!path.equals(artworkRequestPath)) {
                    return;
                }
                if (bitmap != null) {
                    artNowPlaying.setImageTintList(null);
                    artNowPlaying.setImageBitmap(bitmap);
                    artNowPlaying.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    artNowPlaying.setPadding(0, 0, 0, 0);
                    artNowPlaying.setBackgroundResource(R.drawable.bg_artwork_frame);
                    artNowPlaying.setClipToOutline(true);
                } else {
                    showDefaultArtwork();
                }
            });
        }).start();
    }

    private void showDefaultArtwork() {
        artNowPlaying.setImageResource(R.drawable.ic_music_note);
        artNowPlaying.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        artNowPlaying.setPadding(44, 44, 44, 44);
        artNowPlaying.setBackgroundResource(R.drawable.bg_artwork_frame);
        artNowPlaying.setClipToOutline(false);
        artNowPlaying.setImageTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.on_surface_variant)));
    }

    // ---- Animasi artwork & glow (fitur #10) ----

    /**
     * Menjalankan rotasi lambat pada artwork selama lagu diputar.
     * Menggunakan RotateAnimation dari 0 hingga 360 derajat dengan interpolator
     * linier dan repeat tak terbatas sehingga terlihat berputar terus-menerus.
     */
    private void startArtworkRotation() {
        stopArtworkRotation();
        artworkRotation = new RotateAnimation(0f, 360f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        artworkRotation.setDuration(24000L);          // satu putaran penuh ~24 detik
        artworkRotation.setRepeatCount(Animation.INFINITE);
        artworkRotation.setInterpolator(new LinearInterpolator());
        artNowPlaying.startAnimation(artworkRotation);
    }

    /** Menghentikan rotasi artwork dan mengunci posisinya saat ini. */
    private void stopArtworkRotation() {
        if (artworkRotation != null) {
            artworkRotation.cancel();
            artworkRotation = null;
        }
        artNowPlaying.clearAnimation();
    }

    /** Menghidupkan efek glow berdenyut (pulsing) di belakang artwork saat diputar. */
    private void startGlowPulse() {
        stopGlowPulse();
        View glow = findViewById(R.id.art_glow);
        if (glow == null) {
            return;
        }
        // Hanya denyut alpha (lebih ringan & hemat CPU) daripada mengubah scale
        // yang memicu re-layout tiap frame. Efek "bernafas" tetap terlihat.
        glowPulseAnimator = ObjectAnimator.ofFloat(glow, "alpha", 0.6f, 1f);
        glowPulseAnimator.setDuration(1400L);
        glowPulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        glowPulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        glowPulseAnimator.start();
    }

    /** Menghentikan denyut glow dan mereset ke keadaan awal. */
    private void stopGlowPulse() {
        if (glowPulseAnimator != null) {
            glowPulseAnimator.cancel();
            glowPulseAnimator = null;
        }
        View glow = findViewById(R.id.art_glow);
        if (glow != null) {
            glow.setScaleX(1f);
            glow.setScaleY(1f);
            glow.setAlpha(1f);
        }
    }

    /** Menjaga animasi artwork tetap sinkron dengan status pemutaran. */
    private void syncArtworkAnimation() {
        boolean playing = playback != null && playback.isPlaying();
        if (playing) {
            startArtworkRotation();
            startGlowPulse();
        } else {
            stopArtworkRotation();
            stopGlowPulse();
        }
    }

    /** Menampilkan dialog equalizer dengan preset dan slider per band. */
    private void showEqualizerDialog() {
        attachEqualizer(); // pastikan terikat ke session terbaru
        if (eqHelper == null || !eqHelper.isAvailable()) {
            Toast.makeText(this, R.string.equalizer_not_available, Toast.LENGTH_SHORT).show();
            return;
        }

        int bandCount = eqHelper.getBandCount();
        int minLevel = eqHelper.getMinLevel();
        int levelSpan = eqHelper.getLevelSpan();

        // Kontainer gulir untuk isi dialog
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 16, 24, 8);

        // 1) Saklar aktif/nonaktif
        final MaterialSwitch enableSwitch = new MaterialSwitch(this);
        enableSwitch.setText(R.string.eq_enable);
        enableSwitch.setChecked(eqHelper.isEnabled());
        root.addView(enableSwitch);

        // 2) Pemilih preset
        TextView presetLabel = new TextView(this);
        presetLabel.setText(R.string.eq_preset);
        presetLabel.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        presetLabel.setTextColor(ContextCompat.getColor(this, R.color.primary));
        LinearLayout.LayoutParams lblLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lblLp.topMargin = dp(16);
        root.addView(presetLabel, lblLp);

        final android.widget.Spinner presetSpinner = new android.widget.Spinner(this);
        java.util.List<String> presetNames = new java.util.ArrayList<>();
        int presets = eqHelper.getNumberOfPresets();
        for (int i = 0; i < presets; i++) {
            presetNames.add(eqHelper.getPresetName(i));
        }
        if (presets == 0) {
            presetNames.add(getString(R.string.eq_normal));
        }
        android.widget.ArrayAdapter<String> presetAdapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, presetNames);
        presetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        presetSpinner.setAdapter(presetAdapter);
        int curPreset = eqHelper.getCurrentPreset();
        if (curPreset >= 0 && curPreset < presets) {
            presetSpinner.setSelection(curPreset);
        }
        root.addView(presetSpinner);

        // 3) Slider per band
        final java.util.List<SeekBar> bandSeekBars = new java.util.ArrayList<>();
        for (int b = 0; b < bandCount; b++) {
            int freq = eqHelper.getBandCenterFrequency(b);
            String label = formatFrequency(freq);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView freqTv = new TextView(this);
            freqTv.setText(label);
            freqTv.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium);
            freqTv.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant));
            freqTv.setWidth(dp(56));
            row.addView(freqTv);

            final SeekBar sb = new SeekBar(this);
            sb.setMax(levelSpan);
            sb.setProgress(eqHelper.getBandLevel(b) - minLevel);
            int accent = ContextCompat.getColor(this, R.color.primary);
            sb.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
            sb.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));
            LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(sb, sbLp);

            final int band = b;
            sb.setOnSeekBarChangeListener(new EqualizerBandListener(eqHelper, band, minLevel));
            bandSeekBars.add(sb);

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = 6;
            root.addView(row, rowLp);
        }

        // Saat preset dipilih, perbarui slider agar sesuai level preset.
        presetSpinner.setOnItemSelectedListener(
                new PresetSpinnerListener(eqHelper, presets, minLevel, bandSeekBars));

        // Saat saklar diubah, aktifkan/nonaktifkan equalizer.
        enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (eqHelper != null) {
                eqHelper.setEnabled(isChecked);
            }
        });

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.equalizer)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /** Menampilkan dialog untuk efek audio tambahan (bass/virtualizer/loudness). */
    private void showAudioEffectsDialog() {
        attachAudioEffects(); // pastikan terikat ke session terbaru
        if (audioEffectsHelper == null) {
            Toast.makeText(this, R.string.audio_effects_not_available, Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(4), dp(4), dp(4), dp(4));

        addEffectsSliderRow(root, R.string.fx_bass_boost,
                audioEffectsHelper.isBassAvailable(),
                audioEffectsHelper.isBassEnabled(),
                audioEffectsHelper.getBassStrength(),
                audioEffectsHelper.getBassMax(),
                enable -> audioEffectsHelper.setBassEnabled(enable),
                value -> audioEffectsHelper.setBassStrength(value),
                false);

        addEffectsSliderRow(root, R.string.fx_virtualizer,
                audioEffectsHelper.isVirtualizerAvailable(),
                audioEffectsHelper.isVirtualizerEnabled(),
                audioEffectsHelper.getVirtualizerStrength(),
                audioEffectsHelper.getVirtualizerMax(),
                enable -> audioEffectsHelper.setVirtualizerEnabled(enable),
                value -> audioEffectsHelper.setVirtualizerStrength(value),
                false);

        addEffectsSliderRow(root, R.string.fx_loudness,
                audioEffectsHelper.isLoudnessAvailable(),
                audioEffectsHelper.isLoudnessEnabled(),
                audioEffectsHelper.getLoudnessGain(),
                audioEffectsHelper.getLoudnessMax(),
                enable -> audioEffectsHelper.setLoudnessEnabled(enable),
                value -> audioEffectsHelper.setLoudnessGain(value),
                true);

        addReverbSection(root);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.audio_effects)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /** Menampilkan dialog daftar "Putar Berikutnya" (antrian up-next). */
    /**
     * Menampilkan dialog Sleep Timer (fitur #2) untuk mengatur waktu berhenti
     * otomatis pemutaran musik.
     */
    private void showSleepTimerDialog() {
        final String[] items = {
                getString(R.string.sleep_timer_off),
                getString(R.string.sleep_timer_10),
                getString(R.string.sleep_timer_15),
                getString(R.string.sleep_timer_20),
                getString(R.string.sleep_timer_30),
                getString(R.string.sleep_timer_45),
                getString(R.string.sleep_timer_60),
                getString(R.string.sleep_timer_90),
                getString(R.string.sleep_timer_120)
        };
        final int[] minutes = {0, 10, 15, 20, 30, 45, 60, 90, 120};

        int checked = 0;
        if (playback != null && playback.isSleepTimerActive()) {
            int current = playback.getSleepTimerMinutes();
            for (int i = 0; i < minutes.length; i++) {
                if (minutes[i] == current) {
                    checked = i;
                    break;
                }
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sleep_timer_title)
                .setMessage(R.string.sleep_timer_hint)
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    if (which == 0) {
                        if (playback != null) {
                            playback.cancelSleepTimer();
                        }
                        Toast.makeText(this, R.string.sleep_timer_cancelled, Toast.LENGTH_SHORT).show();
                    } else {
                        if (playback != null) {
                            playback.setSleepTimer(minutes[which]);
                        }
                        Toast.makeText(this,
                                getString(R.string.sleep_timer_set, minutes[which]),
                                Toast.LENGTH_SHORT).show();
                    }
                    dialog.dismiss();
                })
                .show();
    }

    /** Memperbarui tampilan tombol sleep timer sesuai status aktif/nonaktif. */
    private void updateSleepTimerButton() {
        boolean active = playback != null && playback.isSleepTimerActive();
        btnSleepTimer.setBackgroundResource(active
                ? R.drawable.bg_icon_circle_active : R.drawable.bg_icon_circle);
        btnSleepTimer.setImageResource(active
                ? R.drawable.ic_sleep_timer_active : R.drawable.ic_sleep_timer);
        btnSleepTimer.setColorFilter(ContextCompat.getColor(this, R.color.white));
        updateSleepTimerText();
    }

    private void updateSleepTimerText() {
        if (txtSleepTimer == null) {
            return;
        }
        if (playback == null || !playback.isSleepTimerActive()) {
            txtSleepTimer.setText("");
            txtSleepTimer.setVisibility(View.GONE);
            return;
        }
        long remaining = MusicService.getSleepTimerRemainingSeconds();
        if (remaining < 0) {
            txtSleepTimer.setText("");
            txtSleepTimer.setVisibility(View.GONE);
            return;
        }
        long minutes = remaining / 60L;
        long seconds = remaining % 60L;
        txtSleepTimer.setText(getString(R.string.sleep_timer_remaining, minutes, seconds));
        txtSleepTimer.setVisibility(View.VISIBLE);
        handler.removeCallbacks(timerTextRunnable);
        handler.postDelayed(timerTextRunnable, 1000L);
    }

    private final Runnable timerTextRunnable = () -> updateSleepTimerText();

    /** Menampilkan dialog daftar "Putar Berikutnya" (antrian up-next). */
    private void showQueueDialog() {
        List<Song> upNext = playback != null ? playback.getUpNext() : null;
        if (upNext == null || upNext.isEmpty()) {
            Toast.makeText(this, R.string.queue_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        final String[] items = new String[upNext.size()];
        for (int i = 0; i < upNext.size(); i++) {
            Song s = upNext.get(i);
            String artist = (s.artist == null || s.artist.trim().isEmpty())
                    ? getString(R.string.unknown_artist) : s.artist;
            items[i] = (i + 1) + ".  " + s.title + " — " + artist;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.queue)
                .setItems(items, (dialog, which) -> {
                    // Klik satu item untuk menghapusnya dari antrian.
                    playback.removeFromUpNext(which);
                    Toast.makeText(this, R.string.queue_removed, Toast.LENGTH_SHORT).show();
                    showQueueDialog(); // muat ulang dialog dengan sisa antrian
                })
                .setNeutralButton(R.string.queue_clear, (dialog, which) -> {
                    playback.clearUpNext();
                    Toast.makeText(this, R.string.queue_cleared, Toast.LENGTH_SHORT).show();
                })
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /**
     * Menambahkan bagian Reverb ke dialog efek audio: saklar aktif, pemilih
     * preset ruangan (Katedral / Aula / Gua / dst), dan slider intensitas gema.
     */
    private void addReverbSection(LinearLayout root) {
        if (!audioEffectsHelper.isReverbAvailable()) {
            return; // efek tidak ada: sembunyikan bagian ini saja
        }

        MaterialCardView card = dialogCard(R.string.reverb);
        LinearLayout body = cardBody(card);
        root.addView(card);

        final MaterialSwitch enableSwitch = new MaterialSwitch(this);
        enableSwitch.setText(getString(R.string.reverb_enable));
        enableSwitch.setChecked(audioEffectsHelper.isReverbEnabled());
        enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                audioEffectsHelper.setReverbEnabled(isChecked));
        body.addView(enableSwitch);

        TextView presetHint = new TextView(this);
        presetHint.setText(R.string.reverb_preset);
        presetHint.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        presetHint.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant));
        body.addView(presetHint);

        final int[] labels = REVERB_PRESET_LABELS;
        final String[] items = new String[labels.length];
        for (int i = 0; i < labels.length; i++) {
            items[i] = getString(labels[i]);
        }
        final int current = Math.max(0, Math.min(items.length - 1,
                audioEffectsHelper.getReverbPresetIndex()));
        final Button presetButton = new Button(this);
        presetButton.setAllCaps(false);
        presetButton.setText(items[current]);
        presetButton.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reverb_preset)
                .setSingleChoiceItems(items, audioEffectsHelper.getReverbPresetIndex(),
                        (dialog, which) -> {
                            audioEffectsHelper.setReverbPresetIndex(which);
                            presetButton.setText(items[which]);
                            // memilih preset ruangan otomatis menyalakan reverb
                            if (!audioEffectsHelper.isReverbEnabled()) {
                                audioEffectsHelper.setReverbEnabled(true);
                                enableSwitch.setChecked(true);
                            }
                            dialog.dismiss();
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show());
        body.addView(presetButton);

        TextView mixHint = new TextView(this);
        mixHint.setText(R.string.reverb_level);
        mixHint.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        mixHint.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant));
        body.addView(mixHint);

        final Slider mixSlider = new Slider(this);
        mixSlider.setValueFrom(0f);
        mixSlider.setValueTo(100f);
        mixSlider.setStepSize(1f);
        mixSlider.setValue(audioEffectsHelper.getReverbMix());
        mixSlider.addOnChangeListener((sl, value, fromUser) -> {
            if (fromUser) {
                audioEffectsHelper.setReverbMix(Math.round(value));
                if (!enableSwitch.isChecked()) {
                    enableSwitch.setChecked(true);
                }
            }
        });
        body.addView(mixSlider);
    }

    /** Kartu M3 pembungkus satu grup efek di dalam dialog. */
    private MaterialCardView dialogCard(int titleRes) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(20));
        card.setCardElevation(0f);
        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_container));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        card.setLayoutParams(lp);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        inner.setPadding(pad, pad, pad, pad);
        card.addView(inner);
        card.setTag(inner);

        TextView heading = new TextView(this);
        heading.setText(titleRes);
        heading.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        heading.setTextColor(ContextCompat.getColor(this, R.color.primary));
        inner.addView(heading);
        return card;
    }

    /** Isi kartu (LinearLayout vertikal) hasil {@link #dialogCard}. */
    private LinearLayout cardBody(MaterialCardView card) {
        return (LinearLayout) card.getTag();
    }

    /**
     * Membangun satu kartu kontrol efek: judul, saklar aktif, lalu slider.
     * Slider M3 ({@link Slider}) dipakai agar tampil sesuai tema; nilainya
     * diubah ke skala {@code maxValue} milik helper.
     */
    private void addEffectsSliderRow(LinearLayout root, int labelRes,
                                     boolean available, boolean enabled,
                                     int currentValue, int maxValue,
                                     final Consumer<Boolean> onEnabled,
                                     final IntConsumer onValue,
                                     boolean gainMode) {
        MaterialCardView card = dialogCard(labelRes);
        LinearLayout body = cardBody(card);
        root.addView(card);

        if (!available) {
            TextView unavailable = new TextView(this);
            unavailable.setText(R.string.audio_effects_not_available);
            unavailable.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
            unavailable.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant));
            body.addView(unavailable);
            return;
        }

        final MaterialSwitch enableSwitch = new MaterialSwitch(this);
        enableSwitch.setText(getString(R.string.eq_enable));
        enableSwitch.setChecked(enabled);
        enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> onEnabled.accept(isChecked));
        body.addView(enableSwitch);

        // Slider M3: nilai 0..100, dipetakan ke rentang helper.
        final int steps = 100;
        final float scale = maxValue / (float) steps;
        final Slider slider = new Slider(this);
        slider.setValueFrom(0f);
        slider.setValueTo(steps);
        slider.setStepSize(1f);
        slider.setValue(Math.max(0f, Math.min(steps, Math.round(currentValue / scale))));
        slider.addOnChangeListener((sl, value, fromUser) -> {
            if (fromUser) {
                onValue.accept(Math.round(value * scale));
                if (!enableSwitch.isChecked()) {
                    enableSwitch.setChecked(true);
                }
            }
        });
        body.addView(slider);

        TextView valueHint = new TextView(this);
        valueHint.setText(gainMode ? R.string.fx_gain : R.string.fx_strength);
        valueHint.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        valueHint.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant));
        body.addView(valueHint);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private String formatFrequency(int freqHz) {
        if (freqHz >= 1000) {
            return String.format(java.util.Locale.getDefault(), "%.1f kHz", freqHz / 1000f);
        }
        return freqHz + " Hz";
    }

    private void updateModeButtons() {
        // shuffle/repeat di kontrol bhshshar berlatar lingkaran navy: selalu ikon
        // putih agar konsisten dengan saudaranya; aktif menyala, nonaktif redup.
        boolean shuffle = playback.isShuffle();
        btnShuffle.setSelected(shuffle);
        btnShuffle.setColorFilter(ContextCompat.getColor(this, R.color.white));
        btnShuffle.setImageAlpha(shuffle ? 255 : 96);

        int repeatMode = playback.getRepeatMode();
        boolean repeatActive = repeatMode != PlaybackController.REPEAT_OFF;
        btnRepeat.setSelected(repeatActive);
        btnRepeat.setImageResource(repeatMode == PlaybackController.REPEAT_ONE
                ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);
        btnRepeat.setColorFilter(ContextCompat.getColor(this, R.color.white));
        btnRepeat.setImageAlpha(repeatActive ? 255 : 96);
        btnRepeat.setContentDescription(getString(repeatMode == PlaybackController.REPEAT_OFF
                ? R.string.repeat_off
                : repeatMode == PlaybackController.REPEAT_ALL
                        ? R.string.repeat : R.string.repeat_one));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(progressRunnable);
        // Musik TIDAK dihentinnnkan di sini: pejajajmutaran berlanjut di MusicServhyhice
        // (foreground seruuuvice) sehingga terus berputar saat app ditutup.
    }

}
