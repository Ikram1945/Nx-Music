package com.nx.music;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Layar daftar lagu. Memuat daftar lagu dari penyimpanan, menampilkannya,
 * dan memutar lagu yang dipilih melalui {@link PlaybackController} singleton,
 * lalu kembali ke {@link PlayerActivity}.
 */
public class SongListActivity extends AppCompatActivity {

    private static final int REQ_CODE_PERMISSION = 100;

    private PlaybackController playback;
    private ListView listSongs;
    private SongAdapter adapter;
    private EditText searchInput;
    private ImageView btnClearSearch;
    private ImageButton btnFavoriteFilter;
    private ImageButton btnStatistics;
    private ImageButton btnSettings;
    private ImageButton btnPlaylists;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_song_list);
        
        ThemeHelper.applyThemeToActivity(this);

        listSongs = findViewById(R.id.list_songs);
        searchInput = findViewById(R.id.search_input);
        btnClearSearch = findViewById(R.id.btn_clear_search);
        btnFavoriteFilter = findViewById(R.id.btn_favorite_filter);
        btnStatistics = findViewById(R.id.btn_statistics);
        btnSettings = findViewById(R.id.btn_settings);
        btnPlaylists = findViewById(R.id.btn_playlists);
        // create() selalu memastikan service foreground aktif & mediaPlayer tersedia,
        // sehingga lagu dapat diputar walau service pernah dihentikan sistem.
        playback = PlaybackController.create(this);

        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        btnStatistics.setOnClickListener(v ->
                startActivity(new Intent(this, StatisticsActivity.class)));

        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        btnPlaylists.setOnClickListener(v ->
                startActivity(new Intent(this, PlaylistActivity.class)));

        setupBottomNav();
        setupList();
        setupSearch();
        setupFavoriteFilter();
        checkAndRequestPermission();
    }

    /** Navigasi bawah konsisten dengan beranda; tab aktif = Perpustakaan. */
    private void setupBottomNav() {
        com.google.android.material.bottomnavigation.BottomNavigationView nav =
                findViewById(R.id.bottom_nav);
        nav.setSelectedItemId(R.id.nav_library);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
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
            return true; // nav_library: tetap di sini
        });
    }

    /** Tombol filter "hanya favorit" di header: toggle ikon & filter daftar. */
    private void setupFavoriteFilter() {
        btnFavoriteFilter.setOnClickListener(v -> {
            if (adapter == null) {
                return;
            }
            boolean enabled = !adapter.isFavoritesOnly();
            adapter.setFavoritesOnly(enabled);
            updateFavoriteFilterButton(enabled);
            Toast.makeText(this, enabled
                    ? R.string.favorites_only : R.string.favorites_all, Toast.LENGTH_SHORT).show();
        });
        // status awal: nonaktif
        updateFavoriteFilterButton(false);
    }

    /** Menampilkan keadaan tombol filter favorit (konsisten dgn pola sleep timer). */
    private void updateFavoriteFilterButton(boolean enabled) {
        // aktif: latar biru lembut + hati terisi putih; nonaktif: latar navy + hati outline putih
        btnFavoriteFilter.setBackgroundResource(enabled
                ? R.drawable.bg_icon_circle_active
                : R.drawable.bg_icon_circle);
        btnFavoriteFilter.setImageResource(enabled
                ? R.drawable.ic_favorite
                : R.drawable.ic_favorite_border);
        btnFavoriteFilter.setColorFilter(ContextCompat.getColor(this, R.color.white));
    }

    /** Mencari lagu secara live sesuai teks, plus tombol hapus teks. */
    private void setupSearch() {
        searchInput.addTextChangedListener(new SearchTextWatcher(adapter, btnClearSearch));

        btnClearSearch.setOnClickListener(v -> {
            searchInput.setText("");
            btnClearSearch.setVisibility(View.GONE);
        });
    }

    private void setupList() {
        List<Song> songs = (playback != null && !playback.getPlaylist().isEmpty())
                ? playback.getPlaylist()
                : new ArrayList<>();

        adapter = new SongAdapter(this, songs);
        if (playback != null) {
            adapter.setCurrentIndex(playback.getCurrentIndex());
        }
        // saat favorit berubah: jika mode "hanya favorit" aktif, filter ulang
        // (lagu yang baru dihapus harus hilang dari list); jika tidak, hanya refresh item.
        adapter.setOnFavoriteToggleListener(song -> {
            if (adapter.isFavoritesOnly()) {
                adapter.setFavoritesOnly(true);
            } else {
                adapter.notifyDataSetChanged();
            }
        });
        listSongs.setAdapter(adapter);

        listSongs.setOnItemClickListener((parent, view, position, id) -> {
            if (playback != null) {
                // position adalah posisi dalam hasil pencarian; petakan ke indeks asli
                int originalIndex = adapter.getOriginalPosition(position);
                playback.playAt(originalIndex);
                adapter.setCurrentIndex(originalIndex);
            }
            // Buka layar pemutar, lalu tutup daftar.
            startActivity(new Intent(this, PlayerActivity.class));
            finish(); // kembali ke layar pemutar
        });

        // Tekan lama item untuk memunculkan opsi "Putar Berikutnya" / "Tambah ke Antrian".
        listSongs.setOnItemLongClickListener((parent, view, position, id) -> {
            if (playback != null) {
                // Gunakan item hasil filter langsung (objek Song yang sama dengan playlist).
                Song song = adapter.getItem(position);
                if (song != null) {
                    showSongOptions(song);
                }
            }
            return true;
        });
    }

    /** Dialog aksi untuk satu lagu: Putar Berikutnya atau Tambah ke Antrian. */
    private void showSongOptions(final Song song) {
        String[] options = {getString(R.string.play_next), getString(R.string.add_to_queue)};
        new AlertDialog.Builder(this)
                .setTitle(song.title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        playback.addNext(song);
                        Toast.makeText(this, R.string.added_to_play_next, Toast.LENGTH_SHORT).show();
                    } else {
                        playback.addToQueue(song);
                        Toast.makeText(this, R.string.added_to_queue, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
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
        if (requestCode == REQ_CODE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadSongs();
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadSongs() {
        // Query MediaStore + sorting bisa lambat pada daftar besar; jalankan
        // di background agar UI tidak membeku, lalu perbarui list di main thread.
        new Thread(() -> {
            final List<Song> songs = SongLoader.loadSongs(SongListActivity.this);
            runOnUiThread(() -> {
                if (songs.isEmpty()) {
                    Toast.makeText(SongListActivity.this, R.string.no_songs,
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                playback.setPlaylist(songs);
                adapter.setSongs(songs);
                // terapkan ulang teks pencarian bila ada, agar hasil tetap terfilter
                String query = searchInput.getText().toString();
                if (!query.isEmpty()) {
                    adapter.filter(query);
                }
                adapter.notifyDataSetChanged();
            });
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (adapter != null) {
            adapter.shutdown();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null && playback != null) {
            adapter.setCurrentIndex(playback.getCurrentIndex());
        }
    }
}
