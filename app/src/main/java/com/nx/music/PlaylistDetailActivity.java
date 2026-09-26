package com.nx.music;

import android.content.Context;
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

import com.ikram.system.TimeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Layar isi sebuah daftar putar: menampilkan lagu-lagu di dalamnya, memutar,
 * menambah lagu dari library, dan menghapus lagu dari daftar.
 */
public class PlaylistDetailActivity extends AppCompatActivity {

    public static final String EXTRA_NAME = "playlist_name";

    private PlaylistHelper playlists;
    private String playlistName;
    private final List<Song> songs = new ArrayList<>();
    private SongAdapterSongs adapter;
    private TextView txtEmpty;
    private TextView txtTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_detail);
        ThemeHelper.applyThemeToActivity(this);

        playlistName = getIntent().getStringExtra(EXTRA_NAME);
        playlists = PlaylistHelper.getInstance(this);

        txtTitle = findViewById(R.id.txt_title);
        txtEmpty = findViewById(R.id.txt_empty);
        ListView list = findViewById(R.id.list_songs);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_add_song).setOnClickListener(v -> showAddSongDialog());

        adapter = new SongAdapterSongs(this);
        list.setAdapter(adapter);

        list.setOnItemClickListener((parent, view, position, id) -> {
            List<Song> p = playlists.getSongs(playlistName);
            if (p.isEmpty()) {
                return;
            }
            PlaybackController.create(this).setPlaylist(p);
            PlaybackController.create(this).playAt(position);
            finish();
        });

        reload();
    }

    private void reload() {
        txtTitle.setText(playlistName);
        songs.clear();
        songs.addAll(playlists.getSongs(playlistName));
        adapter.notifyDataSetChanged();
        txtEmpty.setVisibility(songs.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Dialog memilih lagu dari library untuk ditambahkan ke daftar putar. */
    private void showAddSongDialog() {
        final AlertDialog loading = new AlertDialog.Builder(this)
                .setTitle(R.string.playlist_add_song)
                .setMessage(R.string.loading)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        loading.show();
        new Thread(() -> {
            final List<Song> library = SongLoader.loadSongs(this);
            runOnUiThread(() -> {
                loading.dismiss();
                // hanya tampilkan lagu yang belum ada di daftar
                final List<Song> candidates = new ArrayList<>();
                for (Song s : library) {
                    if (!playlists.hasSong(playlistName, s.path)) {
                        candidates.add(s);
                    }
                }
                if (candidates.isEmpty()) {
                    return;
                }
                String[] items = new String[candidates.size()];
                for (int i = 0; i < candidates.size(); i++) {
                    Song s = candidates.get(i);
                    items[i] = s.title + " — " + (s.artist == null ? "" : s.artist);
                }
                new AlertDialog.Builder(PlaylistDetailActivity.this)
                        .setTitle(R.string.playlist_add_song)
                        .setItems(items, (dialog, which) -> {
                            playlists.addSong(playlistName, candidates.get(which));
                            reload();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        }).start();
    }

    /** Adapter daftar lagu dalam playlist (judul, artis, durasi, tombol hapus). */
    private class SongAdapterSongs extends BaseAdapter {
        private final LayoutInflater inflater;

        SongAdapterSongs(Context context) {
            this.inflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return songs.size();
        }

        @Override
        public Song getItem(int position) {
            return songs.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = inflater.inflate(R.layout.item_playlist_song, parent, false);
            }
            final Song song = getItem(position);
            TextView tvTitle = row.findViewById(R.id.pls_title);
            TextView tvArtist = row.findViewById(R.id.pls_artist);
            TextView tvDuration = row.findViewById(R.id.pls_duration);

            tvTitle.setText(song.title);
            String artist = (song.artist == null || song.artist.trim().isEmpty())
                    ? getString(R.string.unknown_artist)
                    : song.artist;
            tvArtist.setText(artist);
            tvDuration.setText(TimeUtils.format(song.duration));

            ImageButton btnRemove = row.findViewById(R.id.btn_remove_song);
            btnRemove.setOnClickListener(v -> {
                playlists.removeSong(playlistName, song.path);
                reload();
            });
            return row;
        }
    }
}
