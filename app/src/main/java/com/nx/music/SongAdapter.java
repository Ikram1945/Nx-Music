package com.nx.music;

import com.ikram.system.TimeUtils;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Adapter untuk menampilkan daftar lagu di ListView.
 * Menyorot lagu yang sedang diputar dengan warna primary.
 * Mendukung pencarian: {@link #filter(String)} membatasi lagu yang tampil
 * sesuai kata kunci pada judul atau artis.
 * Menampilkan tombol favorit (hati) per baris serta filter "hanya favorit".
 */
public class SongAdapter extends BaseAdapter {

    private final Context context;
    private final FavoritesHelper favorites;
    /** Semua lagu (sumber asli, tanpa filter). */
    private final List<Song> originalSongs = new ArrayList<>();
    /** Lagu yang sedang ditampilkan (setelah difilter). */
    private final List<Song> filteredSongs = new ArrayList<>();
    private int currentIndex = -1;
    /** Jika {@code true}, hanya tampilkan lagu favorit. */
    private boolean favoritesOnly = false;
    /** Kata kunci pencarian terakhir (dipakai ulang saat toggle filter favorit). */
    private String lastQuery;
    private FavoriteToggleListener onFavoriteToggleListener;

    /** Handler main thread untuk memasang bitmap artwork hasil decode background. */
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Worker tunggal untuk decode artwork; hindari satu Thread per baris. */
    private ExecutorService artWorker;
    /** Cache bitmap di memori agar gulir tidak mengekstrak ulang file yang sama. */
    private final LruCache<String, Bitmap> artCache;

    public SongAdapter(Context context, List<Song> songs) {
        this.context = context;
        this.favorites = FavoritesHelper.getInstance(context);
        this.artWorker = Executors.newFixedThreadPool(2);
        int cacheCount = 128;
        this.artCache = new LruCache<String, Bitmap>(cacheCount) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return 1;
            }
        };
        setSongs(songs);
    }

    /** Menerima pemberitahuan saat favorit di-toggle oleh pengguna. */
    public void setOnFavoriteToggleListener(FavoriteToggleListener listener) {
        this.onFavoriteToggleListener = listener;
    }

    /** Menandai indeks lagu yang sedang diputar untuk penyorotan. */
    public void setCurrentIndex(int index) {
        this.currentIndex = index;
        notifyDataSetChanged();
    }

    /** Mengganti daftar lagu yang ditampilkan (menyalin isi list). */
    public void setSongs(List<Song> songs) {
        this.originalSongs.clear();
        if (songs != null) {
            this.originalSongs.addAll(songs);
        }
        lastQuery = null;
        applyFilter(null);
        // Wajib: pemanggil tak selalu memanggil filter()/notify sendiri (mis. layar
        // Folder) -> tanpa ini daftar tampil kosong walau getCount() sudah benar.
        notifyDataSetChanged();
    }

    /**
     * Memfilter daftar lagu berdasarkan kata kunci. Kosong/null menampilkan semua.
     * Mengembalikan jumlah hasil.
     */
    public int filter(String query) {
        lastQuery = query;
        applyFilter(query);
        notifyDataSetChanged();
        return getCount();
    }

    /** Mengaktifkan/mematikan mode "hanya favorit" lalu memfilter ulang. */
    public void setFavoritesOnly(boolean enabled) {
        this.favoritesOnly = enabled;
        applyFilter(lastQuery);
        notifyDataSetChanged();
    }

    /** Apakah mode "hanya favorit" sedang aktif. */
    public boolean isFavoritesOnly() {
        return favoritesOnly;
    }

    private void applyFilter(String query) {
        filteredSongs.clear();
        boolean hasQuery = query != null && !query.trim().isEmpty();
        String lower = hasQuery ? query.trim().toLowerCase(Locale.getDefault()) : null;

        for (Song song : originalSongs) {
            if (favoritesOnly && !favorites.isFavorite(song.path)) {
                continue;
            }
            if (hasQuery) {
                String title = song.title == null ? "" : song.title.toLowerCase(Locale.getDefault());
                String artist = song.artist == null ? "" : song.artist.toLowerCase(Locale.getDefault());
                if (!title.contains(lower) && !artist.contains(lower)) {
                    continue;
                }
            }
            filteredSongs.add(song);
        }
    }

    /**
     * Memetakan posisi dalam daftar yang tampil ke indeks asli (di playlist).
     * Digunakan saat memutar lagu hasil pencarian.
     */
    public int getOriginalPosition(int filteredPosition) {
        if (filteredPosition < 0 || filteredPosition >= filteredSongs.size()) {
            return filteredPosition;
        }
        return originalSongs.indexOf(filteredSongs.get(filteredPosition));
    }

    @Override
    public int getCount() {
        return filteredSongs.size();
    }

    @Override
    public Song getItem(int position) {
        return filteredSongs.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        if (row == null || row.findViewById(R.id.item_title) == null) {
            row = LayoutInflater.from(context)
                    .inflate(R.layout.item_song, parent, false);
        }

        Song song = getItem(position);

        TextView itemTitle = row.findViewById(R.id.item_title);
        TextView itemArtist = row.findViewById(R.id.item_artist);
        TextView itemDuration = row.findViewById(R.id.item_duration);
        TextView itemPlaying = row.findViewById(R.id.item_playing);
        ImageButton btnFavorite = row.findViewById(R.id.btn_favorite);

        itemTitle.setText(song.title);

        String artist = (song.artist == null || song.artist.trim().isEmpty())
                ? context.getString(R.string.unknown_artist)
                : song.artist;
        itemArtist.setText(artist);

        itemDuration.setText(TimeUtils.format(song.duration));

        // sorot lagu yang sedang diputar
        boolean isCurrent = position == currentIndex;
        if (isCurrent) {
            itemTitle.setTextColor(ContextCompat.getColor(context, R.color.primary));
        } else {
            itemTitle.setTextColor(ContextCompat.getColor(context, R.color.on_surface));
        }
        itemPlaying.setVisibility(isCurrent ? View.VISIBLE : View.GONE);

        bindArtwork(row.findViewById(R.id.item_art), song.path);

        // tombol favorit: hati terisi (navy) jika favorit, outline (abu) jika tidak
        final Song songForClick = song;
        boolean isFav = favorites.isFavorite(song.path);
        btnFavorite.setImageResource(isFav
                ? R.drawable.ic_favorite
                : R.drawable.ic_favorite_border);
        btnFavorite.setColorFilter(ContextCompat.getColor(context,
                isFav ? R.color.primary : R.color.on_surface_variant));
        btnFavorite.setContentDescription(context.getString(isFav
                ? R.string.remove_favorite : R.string.add_favorite));
        btnFavorite.setOnClickListener(v -> {
            favorites.toggle(songForClick.path);
            if (onFavoriteToggleListener != null) {
                onFavoriteToggleListener.onFavoriteToggled(songForClick);
            }
        });

        return row;
    }

    /**
     * Memuat thumbnail sampul album di background lalu memasangnya bila baris
     * belum didaur ulang untuk lagu lain. Placeholder ikon note dipasang lebih
     * dulu supaya baris daur ulang tidak sempat menampilkan artwork lagu lama.
     *
     * <p>Decode dijalankan pada worker tunggal (bukan satu Thread per baris)
     * dan hasilnya di-cache, supaya {@code getView} tetap ringan saat digulir.</p>
     */
    private void bindArtwork(ImageView art, String path) {
        art.setTag(path);
        Bitmap cached = artCache.get(path);
        if (cached != null) {
            applyArtwork(art, cached);
            return;
        }
        showPlaceholder(art);
        if (artWorker == null || artWorker.isShutdown()) {
            return;
        }
        artWorker.execute(() -> {
            final Bitmap bitmap = ArtworkLoader.loadArtwork(path, ART_THUMB_SIDE);
            if (bitmap == null) {
                return;
            }
            artCache.put(path, bitmap);
            handler.post(() -> {
                if (path.equals(art.getTag())) {
                    applyArtwork(art, bitmap);
                }
            });
        });
    }

    /** Sisi terpanjang thumbnail daftar (px). Cukup untuk baris 56dp. */
    private static final int ART_THUMB_SIDE = 160;

    private void applyArtwork(ImageView art, Bitmap bitmap) {
        art.setImageTintList(null);
        art.setImageBitmap(bitmap);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setPadding(0, 0, 0, 0);
    }

    private void showPlaceholder(ImageView art) {
        art.setImageTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.white)));
        art.setImageResource(R.drawable.ic_music_note);
        art.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = Math.round(16 * context.getResources().getDisplayMetrics().density);
        art.setPadding(pad, pad, pad, pad);
    }

    /** Menghentikan worker artwork. Panggil saat activity pemakai memang dibuang. */
    public void shutdown() {
        if (artWorker != null) {
            artWorker.shutdown();
            artWorker = null;
        }
    }
}
