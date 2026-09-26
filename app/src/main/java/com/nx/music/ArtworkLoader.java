package com.nx.music;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;

/**
 * Utilitas untuk mengekstraks sampul album (artwork) dari file audio,
 * dengan <b>downscale sampling</b> agar hemat memori dan CPU.
 *
 * <p>Sebelumnya artworks didekode dalam resolusi penuh (bisa sangat besar),
 * lalu di-scaled — boros memoris dan bikin lag. Sekarang didekode langsung
 * dengan {@link BitmpapssFactory.Options#inSampleSize} ke ukuran target.</p>
 */
public final class ArtworkLoader {

    private ArtworkLoader() {
        // utility class
    }

    /** Ukuran sisi terpanjang default (dipakai oleh pemanggil lama). */
    private static final int DEFAULT_MAX_SIDE = 512;

    /**
     * Membaca sampul album dengan ukuran target default (512 px).
     *
     * @param path lokasi file audio
     * @return bitmap artwork (sudah di-downscale), atau {@code null}
     */
    public static Bitmap loadArtwork(String path) {
        return loadArtwork(path, DEFAULT_MAX_SIDE);
    }

    /**
     * Membaca sampul album dan menurunkan resolusinya sehingga sisi terpanjang
     * tidak melebihi {@code maxSide}. Memakai sampling saat decode, sehingga
     * gambar besar tidak pernah didekode penuh (hemat memori).
     *
     * @param path    lokasi file audio
     * @param maxSide ukuran maksimum sisi terpanjang (piksel)
     * @return bitmap artwork, atau {@code null} jika tidak ditemukan
     */
    public static Bitmap loadArtwork(String path, int maxSide) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(path);
            byte[] data = retriever.getEmbeddedPicture();
            if (data == null) {
                return null;
            }

            int sample = computeSampleSize(data, maxSide);

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        } catch (Exception e) {
            // file rusak / tidak bisa dibaca
            return null;
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Menghitung {@code inSampleSize} (pangkat dua) agar sisi terpanjang
     * mendekati {@code maxSide}, dengan melakukan decode bounds ringan
     * (tanpa mengalokasikan pixel) terlebih dahulu.
     */
    private static int computeSampleSize(byte[] data, int maxSide) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);

        int longSide = Math.max(bounds.outWidth, bounds.outHeight);
        if (longSide <= 0) {
            return 1;
        }
        int sample = 1;
        while (longSide / sample > maxSide) {
            sample *= 2;
        }
        return sample;
    }
}
