package com.nx.music;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.Equalizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Pembungkus {@link Equalizer} agar mudah digunakan dan pengaturannya
 * disimpan antar sesi. Setiap band & preset disimpan ke {@link SharedPreferences}.
 *
 * <p>Equalizer terikat ke audio session pemutar; jika {@link MusicService}
 * mengganti lagu (audio session bisa berufbah), panggil
 * {@link #attach(int)} lagi dengan id session terbaru.</p>
 */
public class EqualizerHelper {

    private static final String TAG = "EqualizerHelper";
    private static final String PREFS = "equalizer_prefs";

    private static EqualizerHelper instance;

    private final Context context;
    private Equalizer equalizer;
    private int audioSessionId = -1;

    private boolean enabled = false;
    private int bandCount;
    private int minLevel;
    private int maxLevel;
    private int numberPresets;
    private final List<String> presetNames = new ArrayList<>();
    /** Level tersimpan per band (tanpa melewati preferensi berulang). */
    private final List<Short> storedLevels = new ArrayList<>();
    private int currentPreset = -1;

    private EqualizerHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized EqualizerHelper getInstance(Context context) {
        if (instance == null) {
            instance = new EqualizerHelper(context);
        }
        return instance;
    }

    /** Menghubungkan equalizer ke audio session tertentu. Panggil saat session berubah. */
    public synchronized void attach(int sessionId) {
        if (sessionId <= 0) {
            detach();
            return;
        }
        if (equalizer != null && this.audioSessionId == sessionId) {
            return; // sudah terikat ke session yang sama
        }
        detach();
        this.audioSessionId = sessionId;
        try {
            equalizer = new Equalizer(0, sessionId);
            bandCount = equalizer.getNumberOfBands();
            minLevel = equalizer.getBandLevelRange()[0];
            maxLevel = equalizer.getBandLevelRange()[1];
            numberPresets = equalizer.getNumberOfPresets();
            presetNames.clear();
            for (int i = 0; i < numberPresets; i++) {
                presetNames.add(equalizer.getPresetName((short) i));
            }
            // Terapkan pengaturan tersimpan
            restoreSettings();
        } catch (Exception e) {
            Log.e(TAG, "Gagal membuat Equalizer", e);
            detach();
        }
    }

    /** Melepas equalizer yang sedang terikat. */
    public synchronized void detach() {
        if (equalizer != null) {
            try {
                equalizer.release();
            } catch (Exception ignored) {
            }
            equalizer = null;
        }
        audioSessionId = -1;
    }

    /** Apakah perangkat mendukung equalizer dan sudah terikat ke session aktif. */
    public boolean isAvailable() {
        return equalizer != null && audioSessionId > 0;
    }

    // ---- Kumpulan metadata ----

    public int getBandCount() {
        return equalizer != null ? bandCount : 0;
    }

    public int getMinLevel() {
        return minLevel;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    /** Rentang level dalam milibel untuk dipakai di UI (mis. slider). */
    public int getLevelSpan() {
        return maxLevel - minLevel;
    }

    /** Frekuensi tengah band (Hz), untuk label UI. */
    public int getBandCenterFrequency(int band) {
        try {
            return equalizer.getCenterFreq((short) band);
        } catch (Exception e) {
            return 0;
        }
    }

    public int getNumberOfPresets() {
        return equalizer != null ? numberPresets : 0;
    }

    public String getPresetName(int index) {
        if (index < 0 || index >= presetNames.size()) {
            return "";
        }
        return presetNames.get(index);
    }

    // ---- Status aktif / preset ----

    public boolean isEnabled() {
        return equalizer != null && enabled;
    }

    public void setEnabled(boolean enable) {
        if (equalizer == null) {
            return;
        }
        try {
            equalizer.setEnabled(enable);
            enabled = enable;
            saveEnabled(enable);
            if (enable) {
                // pastikan level band tersimpan ikut aktif
                for (int i = 0; i < storedLevels.size(); i++) {
                    setBandLevel(i, storedLevels.get(i));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "setEnabled gagal", e);
        }
    }

    public int getCurrentPreset() {
        try {
            return equalizer != null ? equalizer.getCurrentPreset() : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /** Menggunakan preset yang sudah ada, lalu menyimpan level band yang dihasilkan. */
    public void usePreset(int index) {
        if (equalizer == null) {
            return;
        }
        try {
            equalizer.usePreset((short) index);
            currentPreset = index;
            savePreset(index);
            // Setelah preset, salin level aktual sebagai "tersimpan".
            storedLevels.clear();
            for (int i = 0; i < bandCount; i++) {
                storedLevels.add(equalizer.getBandLevel((short) i));
            }
            saveLevels();
        } catch (Exception e) {
            Log.e(TAG, "usePreset gagal", e);
        }
    }

    // ---- Band level ----

    public int getBandLevel(int band) {
        try {
            return equalizer != null ? equalizer.getBandLevel((short) band) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Mengatur level band tertentu (dalam milibel, clamp ke rentang perangkat). */
    public void setBandLevel(int band, int levelMillibel) {
        if (equalizer == null) {
            return;
        }
        int clamped = Math.max(minLevel, Math.min(maxLevel, levelMillibel));
        try {
            equalizer.setBandLevel((short) band, (short) clamped);
        } catch (Exception e) {
            Log.e(TAG, "setBandLevel gagal", e);
            return;
        }
        // simpan di list (perluas bila perlu)
        while (storedLevels.size() <= band) {
            storedLevels.add((short) 0);
        }
        storedLevels.set(band, (short) clamped);
        saveLevels();
    }

    // ---- Persistensi ----

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void restoreSettings() {
        SharedPreferences sp = prefs();
        enabled = sp.getBoolean("enabled", false);
        currentPreset = sp.getInt("preset", -1);

        storedLevels.clear();
        for (int i = 0; i < bandCount; i++) {
            storedLevels.add((short) sp.getInt("band_" + i, 0));
        }

        if (equalizer != null) {
            try {
                // Terapkan preset bila masih valid
                if (currentPreset >= 0 && currentPreset < numberPresets) {
                    equalizer.usePreset((short) currentPreset);
                }
                // Setelah preset, tetap timpa dengan level tersimpan agar konsisten
                for (int i = 0; i < bandCount; i++) {
                    equalizer.setBandLevel((short) i, storedLevels.get(i));
                }
                equalizer.setEnabled(enabled);
            } catch (Exception e) {
                Log.e(TAG, "restoreSettings gagal", e);
            }
        }
    }

    private void saveEnabled(boolean value) {
        prefs().edit().putBoolean("enabled", value).apply();
    }

    private void savePreset(int value) {
        prefs().edit().putInt("preset", value).apply();
    }

    private void saveLevels() {
        SharedPreferences.Editor editor = prefs().edit();
        for (int i = 0; i < storedLevels.size(); i++) {
            editor.putInt("band_" + i, storedLevels.get(i));
        }
        editor.apply();
    }

    /** Membuang instance agar tidak bocor (dipanggil saat tidak dipakai). */
    public static void release() {
        if (instance != null) {
            instance.detach();
            instance = null;
        }
    }
}
