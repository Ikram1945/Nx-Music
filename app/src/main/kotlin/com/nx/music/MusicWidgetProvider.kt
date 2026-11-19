package com.nx.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews

/**
 * Widget layar utama untuk Harmony Melody (fitur #10).
 *
 * Menampilkan sampul album, judul & artis lagu yang sedang diputar, serta
 * kontrol prev / play-pause / next. Berkomunikasi dengan [MusicService]
 * (foreground service) melalui [PendingIntent] dengan aksi yang sama
 * dengan tombol notifikasi, sehingga musik tetap dikelola oleh service.
 *
 * Karena widget hanya bisa membaca state saat `onUpdate`, service
 * memanggil [notifyUpdate] setiap ada perubahan status untuk
 * memaksa widget me-refresh RemoteViews-nya.
 */
class MusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildRemoteViews(context))
        }
    }

    companion object {
        /**
         * Memaksa semua instance widget untuk me-refresh tampilan. Dipanggil oleh
         * [MusicService] saat status pemutaran berubah.
         */
        @JvmStatic
        fun notifyUpdate(context: Context) {
            val intent = Intent(context, MusicWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, MusicWidgetProvider::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }

        /** Membangun RemoteViews widget berdasarkan state statis di MusicService. */
        private fun buildRemoteViews(context: Context): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_player)

            val song = MusicService.getCurrentSong()
            rv.setTextViewText(R.id.w_title, song?.title ?: context.getString(R.string.widget_no_playing))
            rv.setTextViewText(
                R.id.w_artist,
                if (!song?.artist.isNullOrBlank()) song!!.artist
                else context.getString(R.string.widget_no_artist),
            )

            // Artwork album (jika ada), selain itu biarkan ikon musik default dari layout.
            var artwork = song?.let { ArtworkLoader.loadArtwork(it.path) }
            if (artwork != null) {
                // Skala agar tidak boros memori di widget.
                val maxSide = 96
                val w = artwork.width
                val h = artwork.height
                if (w > maxSide || h > maxSide) {
                    val scale = maxSide / maxOf(w, h).toFloat()
                    val scaled = Bitmap.createScaledBitmap(
                        artwork,
                        maxOf(1, (w * scale).toInt()),
                        maxOf(1, (h * scale).toInt()),
                        true,
                    )
                    if (scaled !== artwork) {
                        artwork.recycle()
                    }
                    artwork = scaled
                }
                rv.setImageViewBitmap(R.id.w_artwork, artwork)
            }

            // Ikon play/pause sesuai status.
            rv.setImageViewResource(
                R.id.w_play_pause,
                if (MusicService.isPlaying()) R.drawable.ic_pause else R.drawable.ic_play,
            )

            // Ketuk area judul membuka PlayerActivity.
            val open = Intent(context, PlayerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openPI = PendingIntent.getActivity(
                context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            rv.setOnClickPendingIntent(R.id.w_title, openPI)
            rv.setOnClickPendingIntent(R.id.w_artist, openPI)
            rv.setOnClickPendingIntent(R.id.w_artwork, openPI)

            // Tombol prev / play-pause / next memanggil MusicService (aksi sama dengan notifikasi).
            rv.setOnClickPendingIntent(R.id.w_prev, servicePI(context, 1, MusicService.ACTION_PREV))
            rv.setOnClickPendingIntent(R.id.w_play_pause, servicePI(context, 2, MusicService.ACTION_TOGGLE))
            rv.setOnClickPendingIntent(R.id.w_next, servicePI(context, 3, MusicService.ACTION_NEXT))

            return rv
        }

        /** Membuat PendingIntent ke MusicService untuk suatu aksi. */
        private fun servicePI(context: Context, requestCode: Int, action: String): PendingIntent =
            PendingIntent.getService(
                context, requestCode,
                Intent(context, MusicService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
