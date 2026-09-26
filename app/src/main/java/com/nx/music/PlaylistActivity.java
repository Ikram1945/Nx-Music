package com.nx.music;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Layar daftar putar. Menampilkan semua daftar putar pengguna, membuat daftar
 * baru, menghapus, mengubah nama, memutar, dan membuka isi daftar.
 */
public class PlaylistActivity extends AppCompatActivity {

    private PlaylistHelper playlists;
    private ListView listPlaylists;
    private TextView txtEmpty;
    private final List<String> names = new ArrayList<>();
    private PlaylistAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist);
        ThemeHelper.applyThemeToActivity(this);

        playlists = PlaylistHelper.getInstance(this);
        listPlaylists = findViewById(R.id.list_playlists);
        txtEmpty = findViewById(R.id.txt_empty);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_add_playlist).setOnClickListener(v -> showCreateDialog());

        adapter = new PlaylistAdapter(this);
        listPlaylists.setAdapter(adapter);

        listPlaylists.setOnItemClickListener((parent, view, position, id) -> {
            String name = names.get(position);
            startActivity(new Intent(this, PlaylistDetailActivity.class)
                    .putExtra(PlaylistDetailActivity.EXTRA_NAME, name));
        });

        listPlaylists.setOnItemLongClickListener((parent, view, position, id) -> {
            showPlaylistOptions(names.get(position));
            return true;
        });

        setupBottomNav();
    }

    /** Navigasi bawah konsisten dengan beranda; tab aktif = Daftar Putar. */
    private void setupBottomNav() {
        com.google.android.material.bottomnavigation.BottomNavigationView nav =
                findViewById(R.id.bottom_nav);
        nav.setSelectedItemId(R.id.nav_playlist);
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
            } else if (id == R.id.nav_folder) {
                startActivity(new Intent(this, FolderActivity.class));
                finish();
                return true;
            }
            return true; // nav_playlist: tetap di sini
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        names.clear();
        names.addAll(playlists.getPlaylistNames());
        adapter.notifyDataSetChanged();
        txtEmpty.setVisibility(names.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showCreateDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(R.string.playlist_name_hint);
        new AlertDialog.Builder(this)
                .setTitle(R.string.playlist_new)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (playlists.createPlaylist(name)) {
                        reload();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showPlaylistOptions(final String name) {
        String[] options = {getString(R.string.playlist_play),
                getString(R.string.playlist_rename), getString(R.string.playlist_delete)};
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        playPlaylist(name);
                    } else if (which == 1) {
                        showRenameDialog(name);
                    } else {
                        confirmDelete(name);
                    }
                })
                .show();
    }

    private void playPlaylist(String name) {
        List<Song> songs = playlists.getSongs(name);
        if (songs.isEmpty()) {
            return;
        }
        PlaybackController.create(this).setPlaylist(songs);
        PlaybackController.create(this).playOrResume();
        finish();
    }

    private void showRenameDialog(final String oldName) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(R.string.playlist_name_hint);
        new AlertDialog.Builder(this)
                .setTitle(R.string.playlist_rename)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty() || newName.equals(oldName)
                            || playlists.getPlaylistNames().contains(newName)) {
                        return;
                    }
                    // salin isi lama ke nama baru lalu hapus nama lama
                    List<Song> songs = playlists.getSongs(oldName);
                    playlists.deletePlaylist(oldName);
                    if (playlists.createPlaylist(newName)) {
                        for (Song s : songs) {
                            playlists.addSong(newName, s);
                        }
                    }
                    reload();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDelete(final String name) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.playlist_delete_confirm)
                .setPositiveButton(R.string.playlist_delete, (d, w) -> {
                    playlists.deletePlaylist(name);
                    reload();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Adapter daftar putar (nama + jumlah lagu + tombol putar). */
    private class PlaylistAdapter extends BaseAdapter {
        private final LayoutInflater inflater;

        PlaylistAdapter(Context context) {
            this.inflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return names.size();
        }

        @Override
        public String getItem(int position) {
            return names.get(position);
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
            String name = getItem(position);
            TextView tvName = row.findViewById(R.id.pl_name);
            TextView tvCount = row.findViewById(R.id.pl_count);
            tvName.setText(name);
            int count = playlists.getSongs(name).size();
            tvCount.setText(getString(R.string.playlist_count_song, count));
            ImageButton btnPlay = row.findViewById(R.id.btn_play_playlist);
            btnPlay.setOnClickListener(v -> playPlaylist(name));
            return row;
        }
    }
}
