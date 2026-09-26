package com.nx.music;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Isi satu folder perangkat sebagai daftar putar (read-only). Lagu dimuat
 * langsung dari MediaStore lewat {@link SongLoader#loadSongsInFolder}, bukan
 * dari {@link PlaylistHelper} — jadi tak ada tombol tambah/ubah/hapus.
 */
public class FolderDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PATH = "folder_path";
    public static final String EXTRA_NAME = "folder_name";

    private String folderPath;
    private final List<Song> songs = new ArrayList<>();
    private SongAdapter adapter;
    private TextView txtEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder_detail);
        ThemeHelper.applyThemeToActivity(this);

        folderPath = getIntent().getStringExtra(EXTRA_PATH);
        String folderName = getIntent().getStringExtra(EXTRA_NAME);

        TextView txtTitle = findViewById(R.id.txt_title);
        TextView txtSubtitle = findViewById(R.id.txt_subtitle);
        txtEmpty = findViewById(R.id.txt_empty);
        ListView list = findViewById(R.id.list_songs);

        txtTitle.setText(folderName);
        txtSubtitle.setText(folderPath);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Shuffle di sini hanya memengaruhi pemutaran folder ini: playlist aktif
        // diganti daftar folder saat lagu dipilih, jadi "lagu berikutnya" tetap
        // di dalam folder, bukan library global.
        ImageButton btnShuffle = findViewById(R.id.btn_shuffle_folder);
        updateShuffleIcon(btnShuffle);
        btnShuffle.setOnClickListener(v -> {
            PlaybackController pc = PlaybackController.create(this);
            pc.setShuffle(!pc.isShuffle());
            updateShuffleIcon(btnShuffle);
        });

        adapter = new SongAdapter(this, songs);
        list.setAdapter(adapter);

        list.setOnItemClickListener((parent, view, position, id) -> {
            if (songs.isEmpty()) {
                return;
            }
            // Posisi adapter == posisi songs (tanpa filter), jadi index aman.
            PlaybackController pc = PlaybackController.create(this);
            pc.setPlaylist(songs);   // antrian = isi folder ini saja
            pc.playAt(position);
            startActivity(new Intent(this, PlayerActivity.class));
            finish();
        });

        load();
    }

    /** Keadaan ikon shuffle — pola sama dgn PlayerActivity: alpha penuh/meredup. */
    private void updateShuffleIcon(ImageButton btn) {
        boolean on = PlaybackController.create(this).isShuffle();
        btn.setImageAlpha(on ? 255 : 96);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ImageButton btn = findViewById(R.id.btn_shuffle_folder);
        if (btn != null) {
            updateShuffleIcon(btn);
        }
    }

    @Override
    protected void onDestroy() {
        if (adapter != null) {
            adapter.shutdown();
        }
        super.onDestroy();
    }

    private void load() {
        new Thread(() -> {
            final List<Song> result = SongLoader.loadSongsInFolder(this, folderPath);
            runOnUiThread(() -> {
                // SongAdapter menyalin list di ctor, jadi harus lewat setSongs
                // (bukan notifyDataSetChanged) agar isi adapter ikut berubah.
                songs.clear();
                songs.addAll(result);
                adapter.setSongs(songs);
                txtEmpty.setVisibility(songs.isEmpty() ? View.VISIBLE : View.GONE);
            });
        }).start();
    }
}
