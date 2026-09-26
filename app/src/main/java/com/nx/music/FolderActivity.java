package com.nx.music;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Layar Folder: daftar putar otomatis dari folder musik di perangkat.
 * Tiap folder = satu daftar putar (read-only), sumbernya dari path lagu
 * MediaStore. Terpisah dari {@link PlaylistActivity} (daftar putar pengguna).
 */
public class FolderActivity extends AppCompatActivity {

    private ListView listFolders;
    private TextView txtEmpty;
    private final List<SongLoader.Folder> folders = new ArrayList<>();
    private FolderAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder);
        ThemeHelper.applyThemeToActivity(this);

        listFolders = findViewById(R.id.list_playlists);
        txtEmpty = findViewById(R.id.txt_empty);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        adapter = new FolderAdapter(this);
        listFolders.setAdapter(adapter);
        listFolders.setOnItemClickListener((parent, view, position, id) -> {
            SongLoader.Folder f = folders.get(position);
            startActivity(new Intent(this, FolderDetailActivity.class)
                    .putExtra(FolderDetailActivity.EXTRA_PATH, f.path)
                    .putExtra(FolderDetailActivity.EXTRA_NAME, f.name));
        });

        setupBottomNav();
        load();
    }

    /** Navigasi bawah konsisten; tab aktif = Folder. */
    private void setupBottomNav() {
        com.google.android.material.bottomnavigation.BottomNavigationView nav =
                findViewById(R.id.bottom_nav);
        nav.setSelectedItemId(R.id.nav_folder);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return true;
            } else if (id == R.id.nav_library) {
                startActivity(new Intent(this, SongListActivity.class));
                finish();
                return true;
            } else if (id == R.id.nav_playlist) {
                startActivity(new Intent(this, PlaylistActivity.class));
                finish();
                return true;
            }
            return true; // nav_folder: tetap di sini
        });
    }

    private void load() {
        // Query MediaStore di background; library besar tidak membekukan UI.
        new Thread(() -> {
            final List<SongLoader.Folder> result = SongLoader.loadFolders(this);
            runOnUiThread(() -> {
                folders.clear();
                folders.addAll(result);
                adapter.notifyDataSetChanged();
                txtEmpty.setVisibility(folders.isEmpty() ? View.VISIBLE : View.GONE);
            });
        }).start();
    }

    /** Adapter daftar folder (ikon folder, nama, jumlah lagu, tombol putar). */
    private class FolderAdapter extends BaseAdapter {
        private final LayoutInflater inflater;

        FolderAdapter(Context context) {
            this.inflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return folders.size();
        }

        @Override
        public SongLoader.Folder getItem(int position) {
            return folders.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = inflater.inflate(R.layout.item_playlist, parent, false);
            }
            final SongLoader.Folder f = getItem(position);
            ImageView icon = row.findViewById(R.id.pl_icon);
            icon.setImageResource(R.drawable.ic_folder);
            TextView tvName = row.findViewById(R.id.pl_name);
            TextView tvCount = row.findViewById(R.id.pl_count);
            tvName.setText(f.name);
            tvCount.setText(getString(R.string.folder_count_song, f.count)
                    + " · " + f.path);
            ImageButton btnPlay = row.findViewById(R.id.btn_play_playlist);
            btnPlay.setOnClickListener(v -> playFolder(f.path));
            return row;
        }
    }

    /** Putar semua lagu dalam folder ini, mulai dari lagu pertama. */
    private void playFolder(final String dir) {
        new Thread(() -> {
            final List<Song> songs = SongLoader.loadSongsInFolder(this, dir);
            runOnUiThread(() -> {
                if (songs.isEmpty()) {
                    return;
                }
                PlaybackController pc = PlaybackController.create(this);
                pc.setPlaylist(songs);
                pc.playAt(0);
                startActivity(new Intent(this, PlayerActivity.class));
                finish();
            });
        }).start();
    }
}
