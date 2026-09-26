package com.nx.music;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.widget.RemoteViews;

/**
 * Widget layar utama untuk Harmony Melody (fitur #10).
 *
 * <p>Menampilkan sampul album, judul &amp; artis lagu yang sedang diputar, serta
 * kontrol prev / play-pause / next. Berkomunikasi dengan {@link MusicService}
 * (foreground service) melalui {@link PendingIntent} dengan aksi yang sama
 * dengan tombol notifikasi, sehingga musik tetap dikelola oleh service.</p>
 *
 * <p>Karena widget hanya bisa membaca state saat {@code onUpdate}, service
 * memanggil {@link #notifyUpdate(Context)} setiap ada perubahan status untuk
 * memaksa widget me-refresh RemoteViews-nya.</p>
 */
public class MusicWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildRemoteViews(context));
        }
    }

    /**
     * Memaksa semua instance widget untuk me-refresh tampilan. Dipanggil oleh
     * {@link MusicService} saat status pemutaran berubah.
     */
    public static void notifyUpdate(Context context) {
        Intent intent = new Intent(context, MusicWidgetProvider.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        int[] ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(new ComponentName(context, MusicWidgetProvider.class));
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        context.sendBroadcast(intent);
    }

    /** Membangun RemoteViews widget berdasarkan state statis di MusicService. */
    private static RemoteViews buildRemoteViews(Context context) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_player);

        Song song = MusicService.getCurrentSong();
        String title = song != null ? song.title : context.getString(R.string.widget_no_playing);
        String artist = (song != null && song.artist != null && !song.artist.trim().isEmpty())
                ? song.artist : context.getString(R.string.widget_no_artist);

        rv.setTextViewText(R.id.w_title, title);
        rv.setTextViewText(R.id.w_artist, artist);

        // Artwork album (jika ada), selain itu biarkan ikon musik default dari layout.
        Bitmap artwork = null;
        if (song != null) {
            artwork = ArtworkLoader.loadArtwork(song.path);
        }
        if (artwork != null) {
            // Skala agar tidak boros memori di widget.
            int maxSide = 96;
            int w = artwork.getWidth();
            int h = artwork.getHeight();
            if (w > maxSide || h > maxSide) {
                float scale = maxSide / (float) Math.max(w, h);
                Bitmap scaled = Bitmap.createScaledBitmap(artwork,
                        Math.max(1, (int) (w * scale)),
                        Math.max(1, (int) (h * scale)), true);
                if (scaled != artwork) {
                    artwork.recycle();
                }
                artwork = scaled;
            }
            rv.setImageViewBitmap(R.id.w_artwork, artwork);
        }

        // Ikon play/pause sesuai status.
        boolean playing = MusicService.isPlaying();
        rv.setImageViewResource(R.id.w_play_pause,
                playing ? R.drawable.ic_pause : R.drawable.ic_play);

        // Ketuk area judul membuka PlayerActivity.
        Intent open = new Intent(context, PlayerActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPI = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.w_title, openPI);
        rv.setOnClickPendingIntent(R.id.w_artist, openPI);
        rv.setOnClickPendingIntent(R.id.w_artwork, openPI);

        // Tombol prev / play-pause / next memanggil MusicService (aksi sama dengan notifikasi).
        rv.setOnClickPendingIntent(R.id.w_prev,
                servicePI(context, 1, MusicService.ACTION_PREV));
        rv.setOnClickPendingIntent(R.id.w_play_pause,
                servicePI(context, 2, MusicService.ACTION_TOGGLE));
        rv.setOnClickPendingIntent(R.id.w_next,
                servicePI(context, 3, MusicService.ACTION_NEXT));

        return rv;
    }

    /** Membuat PendingIntent ke MusicService untuk suatu aksi. */
    private static PendingIntent servicePI(Context context, int requestCode, String action) {
        Intent intent = new Intent(context, MusicService.class).setAction(action);
        return PendingIntent.getService(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
