package com.nx.music;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Layar utama (launcher). Berannda: shhhambutan, daftar lagu, dan mini-player
 * menempel di atas navigasi baswah (Beranda / Perpustakaan / Daftar Putar).
 *
 * <p>Daftar lagu memakai SATU ListViewj. Section "barus diputar" dipasang sebagai
 * header ListView (hkartu horizontal), bukan ListView bersarang, supaya daur
 * ulang baris tetap jalank dan tidak ada section yang bertumpuk.</p>
 */
public class MainActivity extends AppCompatActivity implements PlaybackCallback {

    private static final int REQ_CODE_PERMISSION = 100;

    /** Jumlah lagu yang ditampilkan pada deretan "baru diputar". */
    private static final int RECENT_LIMIT = 10;
    /** Sisi terpanjang thbshsjumbnail (px) untuk mini-player dan kartu recent. */
    private static final int ART_THUMB_SIDE = 160;

    private PlaybackController playback;
    private SongAdapter songAdapter;
    private final List<Song> songs = new ArrayList<>();
    private final List<Song> recentSongs = new ArrayList<>();

    /** Container deretan kartu "bavbhru diputar" (di dalam header ListView). */
    private LinearLayout recentContainer;
    private View recentSection;
    private TextView txtRecentLabel;

    private TextView txtHomeSubtitle;
    private final List<ObjectAnimator> homeBarAnimators = new ArrayList<>();
    private int homeMotivationIndex = 0;
    private static final long HOME_MOTIVATION_INTERVAL_MS = 7000L;
    private static final int[] HOME_MOTIVATION_RES = {
            R.string.motivation_1, R.string.motivation_2, R.string.motivation_3,
            R.string.motivation_4, R.string.motivation_5
    };
    private static final int[] HOME_BAR_IDS = {
            R.id.home_bar_1, R.id.home_bar_2, R.id.home_bar_3
    };
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** WAJIB setelah [handler]: field diinisialisasi berurutan, jadi merujuk handler
     *  di atas deklarasinya = "Cannot reference a field before it is defined" (ECJ). */
    private final Runnable homeMotivationRunnable =
            new ScheduledRunnable(handler, HOME_MOTIVATION_INTERVAL_MS, this::rotateHomeMotivation);

    /** Workercg tunggal + cache untuk thumbnail (hindari Thread per pembaruan). */
    private final ExecutorService artWorker = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> artCache = new LruCache<String, Bitmap>(64) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return 1;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ThemeHelper.applyThemeToActivity(this);

        playback = PlaybackController.create(this);
        songAdapter = new SongAdapter(this, songs);

        ListView listSongs = findViewById(R.id.list_songs);
        listSongs.addHeaderView(buildHeader());
        listSongs.setAdapter(songAdapter);
        listSongs.setOnItemClickListener((parent, view, position, id) -> {
            // position mencakup header; kurangi dulu sebelum dipetakan ke lagu.
            int adjusted = position - listSongs.getHeaderViewsCount();
            if (adjusted < 0) {
                return; // yang diketuk heagahahahder, bukan lagu
            }
            playback.playAt(songAdapter.getOriginalPosition(adjusted));
            startActivity(new Intent(this, PlayerActivity.class));
        });

        findViewById(R.id.btn_play_now).setOnClickListener(v -> togglePlayback());
        findViewById(R.id.btn_mini_play).setOnClickListener(v -> togglePlayback());
        findViewById(R.id.btn_mini_next).setOnClickListener(v -> playback.skip(true));

        // Klik kartu mini-player (bukan tombol play/next) -> buka layar pemutar.
        // Tombol di dalam kartu punya listener sendiri, jadi klik-nya tidak bocor ke sini.
        findViewById(R.id.mini_player).setOnClickListener(v ->
                startActivity(new Intent(this, PlayerActivity.class)));

        setupBottomNav();
        setupHomeGreeting();
        checkAndRequestPermission();
    }

    /** Sapaan dinamis (jam) + penyemangat bergiliran + bar equalizer kecil di header home. */
    private void setupHomeGreeting() {
        txtHomeSubtitle = findViewById(R.id.txt_home_subtitle);
        txtHomeSubtitle.setText(composeSubtitle(greetingForHour(java.util.Calendar.getInstance()
                .get(java.util.Calendar.HOUR_OF_DAY)), HOME_MOTIVATION_RES[0]));
        startHomeBarPulse();
    }

    /** Pilih sapaan sesuai jam (0-23). */
    private int greetingForHour(int hour) {
        if (hour >= 4 && hour < 11) return R.string.greeting_morning;
        if (hour >= 11 && hour < 15) return R.string.greeting_noon;
        if (hour >= 15 && hour < 18) return R.string.greeting_evening;
        return R.string.greeting_night;
    }

    /** Subtitle header: "sapaan · penyemangat" dalam satu baris. */
    private CharSequence composeSubtitle(int greetingRes, int motivationRes) {
        return getString(greetingRes) + "  ·  " + getString(motivationRes);
    }

    /** Ganti kata penyemangat di home dengan fade singkat. */
    private void rotateHomeMotivation() {
        if (txtHomeSubtitle == null) return;
        homeMotivationIndex = (homeMotivationIndex + 1) % HOME_MOTIVATION_RES.length;
        txtHomeSubtitle.animate().cancel();
        txtHomeSubtitle.animate().alpha(0f).setDuration(220L).withEndAction(() -> {
            txtHomeSubtitle.setText(composeSubtitle(greetingForHour(java.util.Calendar.getInstance()
                    .get(java.util.Calendar.HOUR_OF_DAY)), HOME_MOTIVATION_RES[homeMotivationIndex]));
            txtHomeSubtitle.animate().alpha(1f).setDuration(320L).start();
        }).start();
    }

    private void startHomeMotivationRotation() {
        handler.removeCallbacks(homeMotivationRunnable);
        handler.post(homeMotivationRunnable);
    }

    /** Bar equalizer kecil di header: denyut scaleY berselang-seling. */
    private void startHomeBarPulse() {
        stopHomeBarPulse();
        for (int i = 0; i < HOME_BAR_IDS.length; i++) {
            final View bar = findViewById(HOME_BAR_IDS[i]);
            if (bar == null) continue;
            bar.post(() -> bar.setPivotY(bar.getHeight() / 2f));
            long dur = 520L + (i % 3) * 160L;
            ObjectAnimator a = ObjectAnimator.ofFloat(bar, "scaleY", 0.35f, 1f);
            a.setDuration(dur);
            a.setStartDelay(i * 90L);
            a.setRepeatMode(ValueAnimator.REVERSE);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.start();
            homeBarAnimators.add(a);
        }
    }

    private void stopHomeBarPulse() {
        for (ObjectAnimator a : homeBarAnimators) a.cancel();
        homeBarAnimators.clear();
    }

    /**
     * Header ListView: judul "baru diputar" + deretan kartu horizontal + judul
     * "daftar lagu". Semua digabung ke ajjajanahahahsatu ListView agar beranda hanya punya
     * satu daftar yang bisa menggulir.
     */
    private View buildHeader() {
        View header = LayoutInflater.from(this)
                .inflate(R.layout.item_home_recent_header, null, false);
        recentSection = header.findViewById(R.id.recent_section);
        txtRecentLabel = header.findViewById(R.id.txt_recent_label);
        recentContainer = header.findViewById(R.id.recent_container);
        return header;
    }

    private void togglePlayback() {
        if (playback.getPlaylist().isEmpty()) {
            startActivity(new Intent(this, SongListActivity.class));
            return;
        }
        if (playback.isPlaying()) {
            playback.pause();
        } else {
            playback.playOrResume();
        }
        startActivity(new Intent(this, PlayerActivity.class));
    }

    /** Navigasi bawah: Perpustakaan & Daftar Putar punya layarnya sendiri. */
    private void setupBottomNav() {
        com.google.android.material.bottomnavigation.BottomNavigationView nav =
                findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_library) {
                startActivity(new Intent(this, SongListActivity.class));
                finish();
                return true;
            } else if (id == R.id.nav_playlist) {
                startActivity(new Intent(this, PlaylistActivity.class));
                finish();
                return true;
            } else if (id == R.id.nav_folder) {
                startActivity(new Intent(this, FolderActivity.class));
                finish();
                return true;
            }
            return true; // nav_home: tetap di beranda
        });
        nav.setSelectedItemId(R.id.nav_home);
    }

    private void checkAndRequestPermission() {
        String permission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_AUDIO;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }

        if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, REQ_CODE_PERMISSION);
        } else {
            loadSongs();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CODE_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadSongs();
        }
    }

    private void loadSongs() {
        new Thread(() -> {
            final List<Song> result = SongLoader.loadSongs(MainActivity.this);
            runOnUiThread(() -> {
                if (result.isEmpty()) {
                    return;
                }
                songs.clear();
                songs.addAll(result);
                // Sinkronkan playlist service hanya jika belum ada daftar, agar
                // tidak mereset currentIndex saat ada lagu yang sedang diputar sialan.
                if (playback != null && playback.getPlaylist().isEmpty()) {
                    playback.setPlaylist(result);
                }
                songAdapter.setSongs(result);
                songAdapter.notifyDataSetChanged();
            });
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (playback != null) {
            playback.setCallback(this);
        }
        updateMiniPlayer();
        reloadRecent();
        // Bar & penyemangat dimatikan di onPause; nyalakan lagi tiap balik ke home.
        startHomeBarPulse();
        startHomeMotivationRotation();
        if (songAdapter != null) {
            songAdapter.setCurrentIndex(playback != null ? playback.getCurrentIndex() : -1);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (playback != null) {
            playback.setCallback(null);
        }
        handler.removeCallbacks(homeMotivationRunnable);
        stopHomeBarPulse();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (songAdapter != null) {
            songAdapter.shutdown();
        }
        artWorker.shutdown();
        handler.removeCallbacksAndMessages(null);
    }

    /** Memperbarui mini-player: judul, artis, ikon play/pause, dan artwork. */
    private void updateMiniPlayer() {
        Song song = playback != null ? playback.getCurrentSong() : null;
        TextView miniTitle = findViewById(R.id.mini_title);
        TextView miniArtist = findViewById(R.id.mini_artist);
        ImageButton btnMiniPlay = findViewById(R.id.btn_mini_play);
        ImageButton btnPlayNow = findViewById(R.id.btn_play_now);

        if (song == null) {
            miniTitle.setText(R.string.no_playing);
            miniArtist.setText(R.string.no_playing_artist);
            btnMiniPlay.setImageResource(R.drawable.ic_play);
            btnPlayNow.setImageResource(R.drawable.ic_play);
            showMiniPlaceholder();
            return;
        }
        miniTitle.setText(song.title);
        miniArtist.setText((song.artist == null || song.artist.trim().isEmpty())
                ? getString(R.string.unknown_artist)
                : song.artist);
        int icon = playback.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play;
        btnMiniPlay.setImageResource(icon);
        btnPlayNow.setImageResource(icon);
        loadMiniArtwork(song.path);
    }

    /** Memuat artwork mini-player dengan placeholder reset (anti gambar basi). */
    private void loadMiniArtwork(final String path) {
        final ImageView art = findViewById(R.id.mini_art);
        art.setTag(path);
        Bitmap cached = artCache.get(path);
        if (cached != null) {
            applyArtwork(art, cached, R.color.on_primary_container);
            return;
        }
        showMiniPlaceholder();
        artWorker.execute(() -> {
            final Bitmap bitmap = ArtworkLoader.loadArtwork(path, ART_THUMB_SIDE);
            if (bitmap == null) {
                return;
            }
            artCache.put(path, bitmap);
            handler.post(() -> {
                if (path.equals(art.getTag())) {
                    applyArtwork(art, bitmap, R.color.on_primary_container);
                }
            });
        });
    }

    private void showMiniPlaceholder() {
        ImageView art = findViewById(R.id.mini_art);
        art.setImageTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.on_primary_container)));
        art.setImageResource(R.drawable.ic_music_note);
        art.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = Math.round(12 * getResources().getDisplayMetrics().density);
        art.setPadding(pad, pad, pad, pad);
    }

    /** Memasang bitmap artwork menggantikan placeholder ikon. */
    private void applyArtwork(ImageView art, Bitmap bitmap, int tintColorRes) {
        art.setImageTintList(null);
        art.setImageBitmap(bitmap);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setPadding(0, 0, 0, 0);
    }

    /**
     * Memuat deretan "baru diputar" dari riwayat. Pemeriksaan keberadaan file
     * (disk I/O) dilakukan di background; UI diperbarui di main thread.
     * Section disembunyikan bila belum ada riwayat.
     */
    private void reloadRecent() {
        new Thread(() -> {
            final List<Song> recent =
                    PlaybackStats.getInstance(this).getRecentSongs(RECENT_LIMIT);
            runOnUiThread(() -> {
                recentSongs.clear();
                recentSongs.addAll(recent);
                if (recentContainer != null) {
                    renderRecentCards(recent);
                }
                if (recentSection != null) {
                    // Section tetap tampil walau kosong: judul "daftar lagu"
                    // ada di header yang sama, jadi menyembunyikannya akan
                    // ikut menghilangkan judul tersebut.
                    recentSection.setVisibility(View.VISIBLE);
                }
                if (txtRecentLabel != null) {
                    txtRecentLabel.setVisibility(recent.isEmpty() ? View.GONE : View.VISIBLE);
                }
            });
        }).start();
    }

    /** Mengisi container dengan kartu-kartu lagu recent (dibangun manual). */
    private void renderRecentCards(List<Song> recent) {
        recentContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (final Song song : recent) {
            View card = inflater.inflate(R.layout.item_recent_card, recentContainer, false);
            TextView title = card.findViewById(R.id.recent_title);
            TextView artist = card.findViewById(R.id.recent_artist);
            ImageView art = card.findViewById(R.id.recent_art);

            title.setText(song.title);
            artist.setText((song.artist == null || song.artist.trim().isEmpty())
                    ? getString(R.string.unknown_artist)
                    : song.artist);
            loadCardArtwork(art, song.path);

            card.setOnClickListener(v -> {
                // Jadikan daftar recent sebagai sumber pemutaran, agar indeks
                // tetap konsisten walau lagu tidak ada di daftar utama.
                playback.setPlaylist(new ArrayList<>(recentSongs));
                playback.playAt(recentSongs.indexOf(song));
                startActivity(new Intent(this, PlayerActivity.class));
            });
            recentContainer.addView(card);
        }
    }

    /** Memuat artwork untuk kartu recent (placeholder lalu ganti bitmap). */
    private void loadCardArtwork(final ImageView art, final String path) {
        art.setTag(path);
        Bitmap cached = artCache.get(path);
        if (cached != null) {
            applyArtwork(art, cached, R.color.white);
            return;
        }
        art.setImageTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.white)));
        art.setImageResource(R.drawable.ic_music_note);
        art.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = Math.round(30 * getResources().getDisplayMetrics().density);
        art.setPadding(pad, pad, pad, pad);

        artWorker.execute(() -> {
            final Bitmap bitmap = ArtworkLoader.loadArtwork(path, ART_THUMB_SIDE);
            if (bitmap == null) {
                return;
            }
            artCache.put(path, bitmap);
            handler.post(() -> {
                if (path.equals(art.getTag())) {
                    applyArtwork(art, bitmap, R.color.white);
                }
            });
        });
    }

    // ---- PlaybackCallback ----

    @Override
    public void onPlaybackStateChanged() {
        updateMiniPlayer();
    }

    @Override
    public void onSongChanged(int index) {
        updateMiniPlayer();
        if (songAdapter != null) {
            songAdapter.setCurrentIndex(index);
        }
    }

    @Override
    public void onProgress(long position, long duration) {
        // Progress ditampilkan di PlayerActivity; beranda tidak perlu.
    }

    @Override
    public void onQueueChanged() {
    }
}
