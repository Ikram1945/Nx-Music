package com.nx.music;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

/**
 * Layar pengaturan aplikasi (fitur settings).
 * Mengatur mode tampilan (gelap/terang/ikuti sistem), urutan daftar lagu,
 * dan menghapus riwayat statistik pemutaran. Semua preferensi disimpan di
 * {@code settings_prefs}.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS = "settings_prefs";
    /** 0 = ikuti sistem, 1 = terang, 2 = gelap. */
    private static final String KEY_THEME = "theme_mode";
    /** 0 = default, 1 = judul, 2 = artis, 3 = durasi. */
    private static final String KEY_SORT = "sort_order";

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        setContentView(R.layout.activity_settings);

        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        setupTheme();
        setupSort();
        setupCustomTheme();
        setupCrossfade();
        setupClearHistory();
        
        // Apply custom theme saat activity dimuat
        applyCustomTheme();
    }

    private void setupTheme() {
        RadioGroup group = findViewById(R.id.group_theme);
        int mode = prefs.getInt(KEY_THEME, 0);
        group.check(mode == 1 ? R.id.theme_light
                : mode == 2 ? R.id.theme_dark : R.id.theme_follow_system);
        group.setOnCheckedChangeListener((g, checkedId) -> {
            int newMode;
            if (checkedId == R.id.theme_light) {
                newMode = 1;
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            } else if (checkedId == R.id.theme_dark) {
                newMode = 2;
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                newMode = 0;
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            }
            prefs.edit().putInt(KEY_THEME, newMode).apply();
        });
    }

    private void setupSort() {
        RadioGroup group = findViewById(R.id.group_sort);
        int sort = prefs.getInt(KEY_SORT, 0);
        group.check(sort == 1 ? R.id.sort_title
                : sort == 2 ? R.id.sort_artist
                : sort == 3 ? R.id.sort_duration : R.id.sort_default);
        group.setOnCheckedChangeListener((g, checkedId) -> {
            int newSort;
            if (checkedId == R.id.sort_title) {
                newSort = 1;
            } else if (checkedId == R.id.sort_artist) {
                newSort = 2;
            } else if (checkedId == R.id.sort_duration) {
                newSort = 3;
            } else {
                newSort = 0;
            }
            prefs.edit().putInt(KEY_SORT, newSort).apply();
        });
    }

    private void setupClearHistory() {
        Button btn = findViewById(R.id.btn_clear_history);
        btn.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage(R.string.clear_history_confirm)
                .setPositiveButton(R.string.clear_history, (d, w) -> {
                    PlaybackStats.getInstance(this).clearAll();
                    Toast.makeText(this, R.string.clear_history_done, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.back, null)
                .show());
    }

    private void setupCustomTheme() {
        RadioGroup group = findViewById(R.id.group_custom_theme);
        int theme = ThemeHelper.getCustomTheme(this);

        int radioId;
        switch (theme) {
            case ThemeHelper.THEME_BLUE:
                radioId = R.id.custom_theme_blue;
                break;
            case ThemeHelper.THEME_GREEN:
                radioId = R.id.custom_theme_green;
                break;
            case ThemeHelper.THEME_PURPLE:
                radioId = R.id.custom_theme_purple;
                break;
            case ThemeHelper.THEME_ORANGE:
                radioId = R.id.custom_theme_orange;
                break;
            case ThemeHelper.THEME_RED:
                radioId = R.id.custom_theme_red;
                break;
            case ThemeHelper.THEME_DEFAULT:
            default:
                radioId = R.id.custom_theme_default;
                break;
        }
        group.check(radioId);

        group.setOnCheckedChangeListener((g, checkedId) -> {
            int newTheme;
            if (checkedId == R.id.custom_theme_blue) {
                newTheme = ThemeHelper.THEME_BLUE;
            } else if (checkedId == R.id.custom_theme_green) {
                newTheme = ThemeHelper.THEME_GREEN;
            } else if (checkedId == R.id.custom_theme_purple) {
                newTheme = ThemeHelper.THEME_PURPLE;
            } else if (checkedId == R.id.custom_theme_orange) {
                newTheme = ThemeHelper.THEME_ORANGE;
            } else if (checkedId == R.id.custom_theme_red) {
                newTheme = ThemeHelper.THEME_RED;
            } else {
                newTheme = ThemeHelper.THEME_DEFAULT;
            }

            ThemeHelper.setCustomTheme(this, newTheme);
            Toast.makeText(this, R.string.custom_theme_saved, Toast.LENGTH_SHORT).show();

            // Refresh UI untuk menerapkan tema baru
            applyCustomTheme();
        });
    }

    private void setupCrossfade() {
        android.widget.Switch switchCrossfade = findViewById(R.id.switch_crossfade);
        boolean enabled = MusicService.isCrossfadeEnabled();
        switchCrossfade.setChecked(enabled);
        switchCrossfade.setOnCheckedChangeListener((buttonView, isChecked) -> {
            MusicService.setCrossfadeEnabled(this, isChecked);
            Toast.makeText(this,
                    isChecked ? R.string.crossfade_on : R.string.crossfade_off,
                    Toast.LENGTH_SHORT).show();
        });
    }
    
    private void applyCustomTheme() {
        // Terapkan tema ke seluruh layar (ikon, tombol, seek bar, dsb).
        // CATATAN: jangan pakai applyToImageButton utk btn_back — itu menint
        // BACKGROUND tombol transparan jadi kotak warna. Ikon ditint oleh
        // applyThemeToActivity lewat penanganan ImageButton.
        ThemeHelper.applyThemeToActivity(this);
        updateRadioButtonTints();
    }
    
    /**
     * Radio button dan switch dikunci memakai warna TETAP, bukan warna tema.
     *
     * <p>Alasannya: warna tema kustom (dan mode gelap) bisa menghasilkan
     * bulatan radio yang menyatu dengan latarnya, sehingga tombolnya tidak
     * terlihat. Warna tetap memastikan bulatan selalu terbaca di tema apa pun.
     * Latar baris pilihan (bg_setting_option) memakai warna tetap yang sama
     * agar kontrasnya terjaga.</p>
     */
    private void updateRadioButtonTints() {
        int fixed = ContextCompat.getColor(this, R.color.setting_radio_fixed);
        ColorStateList tint = ColorStateList.valueOf(fixed);
        int[] groups = {R.id.group_theme, R.id.group_sort, R.id.group_custom_theme};
        for (int groupId : groups) {
            RadioGroup group = findViewById(groupId);
            for (int i = 0; i < group.getChildCount(); i++) {
                if (group.getChildAt(i) instanceof android.widget.RadioButton) {
                    ((android.widget.RadioButton) group.getChildAt(i))
                            .setButtonTintList(tint);
                }
            }
        }
        android.widget.Switch sw = findViewById(R.id.switch_crossfade);
        sw.setThumbTintList(tint);
    }

    /** Membaca urutan daftar lagu yang disimpan (dipakai oleh SongListActivity/SongLoader). */
    public static int getSortOrder(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_SORT, 0);
    }
}
