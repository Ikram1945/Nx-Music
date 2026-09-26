package com.nx.music;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.List;

/**
 * Layar statistik & riwayat pemutaran (fitur #5).
 * Menampilkan total putar, total waktu mendengarkan, lagu paling sering diputar,
 * dan riwayat lagu yang baru diputar. Data dibaca dari {@link PlaybackStats}.
 */
public class StatisticsActivity extends AppCompatActivity {

    private TextView txtTotalPlays;
    private TextView txtTotalTime;
    private TextView txtTopHeader;
    private TextView txtHistoryHeader;
    private LinearLayout topContainer;
    private LinearLayout historyContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);
        
        ThemeHelper.applyThemeToActivity(this);

        txtTotalPlays = findViewById(R.id.txt_total_plays);
        txtTotalTime = findViewById(R.id.txt_total_time);
        txtTopHeader = findViewById(R.id.txt_top_header);
        txtHistoryHeader = findViewById(R.id.txt_history_header);
        topContainer = findViewById(R.id.top_container);
        historyContainer = findViewById(R.id.history_container);

        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        populate();
    }

    private void populate() {
        PlaybackStats stats = PlaybackStats.getInstance(this);

        txtTotalPlays.setText(String.valueOf(stats.getTotalPlays()));
        txtTotalTime.setText(formatDuration(stats.getTotalListeningTimeSec()));

        List<PlaybackHistoryEntry> top = stats.getTopPlayed();
        List<PlaybackHistoryEntry> history = stats.getHistory();

        // Batasi tampilan agar layar tak jadi scroll panjang tak berujung.
        int topCount = Math.min(top.size(), 5);
        int historyCount = Math.min(history.size(), 10);

        if (topCount == 0) {
            txtTopHeader.setVisibility(View.GONE);
            addEmptyRow(topContainer);
        } else {
            // Bar chart: 5 lagu teratas sebagai bar horizontal proporsional.
            int maxPlays = 1;
            for (int i = 0; i < topCount; i++) {
                maxPlays = Math.max(maxPlays, top.get(i).plays);
            }
            for (int i = 0; i < topCount; i++) {
                addTopChartRow(topContainer, top.get(i), top.get(i).plays, maxPlays, i);
            }
        }

        if (historyCount == 0) {
            txtHistoryHeader.setVisibility(View.GONE);
            addEmptyRow(historyContainer);
        } else {
            for (int i = 0; i < historyCount; i++) {
                addHistoryRow(historyContainer, i, history.get(i));
            }
        }
    }

    /** Baris bar chart untuk satu lagu: peringkat + judul + bar proporsional. */
    private void addTopChartRow(LinearLayout parent, PlaybackHistoryEntry entry,
                                int plays, int maxPlays, int rank) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(8), dp(16), dp(8));

        // Nomor peringkat dalam lingkaran berwarna tema.
        TextView rankTv = new TextView(this);
        rankTv.setText(String.valueOf(rank + 1));
        rankTv.setTextColor(ContextCompat.getColor(this, R.color.white));
        rankTv.setTextSize(13);
        rankTv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        rankTv.setGravity(Gravity.CENTER);
        rankTv.setBackgroundResource(R.drawable.bg_icon_circle);
        LinearLayout.LayoutParams rankLp =
                new LinearLayout.LayoutParams(dp(26), dp(26));
        rankLp.setMargins(0, 0, dp(12), 0);
        row.addView(rankTv, rankLp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // Baris atas: judul + jumlah putar
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleTv = new TextView(this);
        titleTv.setText(entry.title == null || entry.title.isEmpty()
                ? getString(R.string.unknown_title) : entry.title);
        titleTv.setTextColor(ContextCompat.getColor(this, R.color.on_surface));
        titleTv.setTextSize(14);
        titleTv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleTv.setMaxLines(1);
        titleTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.addView(titleTv, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView countTv = new TextView(this);
        countTv.setText(getString(R.string.stat_plays_count, plays));
        countTv.setTextColor(ContextCompat.getColor(this, R.color.primary));
        countTv.setTextSize(13);
        countTv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        head.addView(countTv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        col.addView(head);

        // Bar: FrameLayout berisi track (latar penuh) + fill (bar kiri proporsional).
        final android.widget.FrameLayout barArea = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(16));
        barLp.setMargins(0, dp(6), 0, 0);
        barArea.setLayoutParams(barLp);
        col.addView(barArea);

        View track = new View(this);
        track.setBackgroundResource(R.drawable.bg_bar_track);
        barArea.addView(track, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));

        final View fill = new View(this);
        fill.setBackgroundResource(R.drawable.bg_bar_fill);
        // Tiga teratas diberi warna lebih tegas agar mudah dibedakan.
        fill.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this,
                        rank < 3 ? R.color.primary : R.color.primary_gradient_end)));
        barArea.addView(fill, new android.widget.FrameLayout.LayoutParams(
                dp(6), android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.START));

        // Lebar bar dihitung setelah area terukur. Sebelumnya dipakai
        // widthPixels dikurangi padding tebakan, dan itu membuat bar meluber
        // keluar kartu karena padding container tidak ikut diperhitungkan.
        barArea.post(() -> {
            int available = barArea.getWidth();
            if (available <= 0) {
                return;
            }
            int target = (int) ((float) plays / (float) maxPlays * available);
            if (target < dp(6)) {
                target = dp(6);
            }
            android.view.ViewGroup.LayoutParams lp = fill.getLayoutParams();
            lp.width = target;
            fill.setLayoutParams(lp);
        });

        row.addView(col);
        parent.addView(row);
        row.setOnClickListener(v -> playSong(entry));
    }

    private void addHistoryRow(LinearLayout parent, int index, PlaybackHistoryEntry entry) {
        View row = makeRow(index, entry.title, entry.artist,
                formatRelativeTime(entry.lastPlayedAt));
        row.setOnClickListener(v -> playSong(entry));
        parent.addView(row);
    }

    private View makeRow(int index, String title, String subtitle, String meta) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(16), dp(10));
        // Ripple bawaan tema (bukan list_selector_background) agar efek ketuk
        // konsisten dengan daftar lain di aplikasi ini.
        android.util.TypedValue tv = new android.util.TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            row.setBackgroundResource(tv.resourceId);
        }

        TextView idx = new TextView(this);
        idx.setText(String.valueOf(index + 1));
        idx.setTextColor(ContextCompat.getColor(this, R.color.white));
        idx.setTextSize(10);
        idx.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        idx.setGravity(Gravity.CENTER);
        idx.setBackgroundResource(R.drawable.bg_icon_circle);
        LinearLayout.LayoutParams idxLp =
                new LinearLayout.LayoutParams(dp(24), dp(24));
        idxLp.setMargins(0, 0, dp(12), 0);
        row.addView(idx, idxLp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleTv = new TextView(this);
        titleTv.setText(title == null || title.isEmpty()
                ? getString(R.string.unknown_title) : title);
        titleTv.setTextColor(ContextCompat.getColor(this, R.color.on_surface));
        titleTv.setTextSize(15);
        titleTv.setMaxLines(1);
        titleTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(titleTv);

        TextView subtitleTv = new TextView(this);
        subtitleTv.setText(subtitle == null || subtitle.isEmpty()
                ? getString(R.string.unknown_artist) : subtitle);
        subtitleTv.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant));
        subtitleTv.setTextSize(12);
        subtitleTv.setMaxLines(1);
        subtitleTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(subtitleTv);

        row.addView(texts);

        TextView metaTv = new TextView(this);
        metaTv.setText(meta);
        metaTv.setTextColor(ContextCompat.getColor(this, R.color.primary));
        metaTv.setTextSize(13);
        row.addView(metaTv);

        return row;
    }

    private void addEmptyRow(LinearLayout parent) {
        TextView empty = new TextView(this);
        empty.setText(R.string.stat_empty);
        empty.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant));
        empty.setTextSize(14);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, dp(24), 0, dp(24));
        parent.addView(empty);
    }

    private void playSong(PlaybackHistoryEntry entry) {
        // Cari lagu di playlist berdasarkan path, lalu putars.
        List<Song> playlist = PlaybackController.getInstance() != null
                ? PlaybackController.getInstance().getPlaylist() : null;
        if (playlist == null) {
            return;
        }
        for (int i = 0; i < playlist.size(); i++) {
            Song s = playlist.get(i);
            if (s.path != null && s.path.equals(entry.path)) {
                PlaybackController.getInstance().playAt(i);
                startActivity(new Intent(this, PlayerActivity.class));
                finish();
                return;
            }
        }
    }

    private String formatDuration(long totalSec) {
        long hours = totalSec / 3600L;
        long minutes = (totalSec % 3600L) / 60L;
        if (hours > 0) {
            return getString(R.string.stat_time_hours, hours, minutes);
        }
        return getString(R.string.stat_time_minutes, minutes);
    }

    private String formatRelativeTime(long epochMs) {
        long diff = System.currentTimeMillis() - epochMs;
        long minutes = diff / 60000L;
        if (minutes < 1) {
            return getString(R.string.stat_just_now);
        } else if (minutes < 60) {
            return getString(R.string.stat_minutes_ago, minutes);
        }
        long hours = minutes / 60L;
        if (hours < 24) {
            return getString(R.string.stat_hours_ago, hours);
        }
        long days = hours / 24L;
        return getString(R.string.stat_days_ago, days);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
