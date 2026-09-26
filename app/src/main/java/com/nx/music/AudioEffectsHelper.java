package com.nx.music;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.BassBoost;
import android.media.audiofx.EnvironmentalReverb;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
import android.os.Build;
import android.util.Log;

/**
 * Pembungkus efek audio tambahan: {@link BassBoost}, {@link Virtualizer}, dan
 * {@link LoudnessEnhancer}. Semua pengaturan disimpan antar sesi melalui
 * {@link SharedPreferences} dan terikat ke audio session pemutar.
 *
 * <p>Sama seperti {@link EqualizerHelper}, setiap efek terikat ke audio session
 * pemutar. Jika {@link MusicService} mengganti lagu (audio session bisa berubah),
 * panggil {@link #attach(int)} lagi dengan id session terbaru.</p>
 */
public class AudioEffectsHelper {

    private static final String TAG = "AudioEffectsHelper";
    private static final String PREFS = "audio_effects_prefs";

    private static AudioEffectsHelper instance;

    private final Context context;
    private int audioSessionId = -1;

    private BassBoost bassBoost;
    private Virtualizer virtualizer;
    private LoudnessEnhancer loudnessEnhancer;

    private EnvironmentalReverb reverb;

    // Status tersimpan (agar tidak menulis ke prefs terlalu sering).
    private boolean bassEnabled = false;
    private short bassStrength = 0;          // 0..1000
    private boolean virtualizerEnabled = false;
    private short virtualizerStrength = 0;   // 0..1000
    private boolean loudnessEnabled = false;
    private int loudnessGain = 0;            // dB * 100 (0 = netral)
    private boolean reverbEnabled = false;
    private int reverbPresetIndex = 0;       // indeks ke REVERB_PRESETS
    private int reverbMix = 70;              // 0..100 intensitas gema

    private AudioEffectsHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized AudioEffectsHelper getInstance(Context context) {
        if (instance == null) {
            instance = new AudioEffectsHelper(context);
        }
        return instance;
    }

    /** Menghubungkan semua efek ke audio session tertentu. Panggil saat session berubah. */
    public synchronized void attach(int sessionId) {
        if (sessionId <= 0) {
            detach();
            return;
        }
        if (audioSessionId == sessionId) {
            return; // sudah terikat ke session yang sama
        }
        detach();
        this.audioSessionId = sessionId;
        restoreSettings();
        applyAll();
    }

    /** Melepas semua efek yang sedang terikat. */
    public synchronized void detach() {
        releaseEffect(bassBoost);
        bassBoost = null;
        releaseEffect(virtualizer);
        virtualizer = null;
        releaseEffect(loudnessEnhancer);
        loudnessEnhancer = null;
        releaseEffect(reverb);
        reverb = null;
        audioSessionId = -1;
    }

    private void releaseEffect(Object effect) {
        if (effect instanceof android.media.audiofx.AudioEffect) {
            try {
                ((android.media.audiofx.AudioEffect) effect).release();
            } catch (Exception ignored) {
            }
        }
    }

    private void ensureBassBoost() {
        if (bassBoost == null && audioSessionId > 0) {
            try {
                bassBoost = new BassBoost(0, audioSessionId);
            } catch (Exception e) {
                Log.e(TAG, "BassBoost tidak tersedia", e);
                bassBoost = null;
            }
        }
    }

    private void ensureVirtualizer() {
        if (virtualizer == null && audioSessionId > 0) {
            try {
                virtualizer = new Virtualizer(0, audioSessionId);
            } catch (Exception e) {
                Log.e(TAG, "Virtualizer tidak tersedia", e);
                virtualizer = null;
            }
        }
    }

    private void ensureLoudnessEnhancer() {
        if (loudnessEnhancer == null && audioSessionId > 0) {
            try {
                loudnessEnhancer = new LoudnessEnhancer(audioSessionId);
            } catch (Exception e) {
                Log.e(TAG, "LoudnessEnhancer tidak tersedia", e);
                loudnessEnhancer = null;
            }
        }
    }

    private void applyAll() {
        applyBassBoost();
        applyVirtualizer();
        applyLoudness();
        applyReverb();
    }

    private void applyBassBoost() {
        ensureBassBoost();
        if (bassBoost == null) {
            return;
        }
        try {
            bassBoost.setStrength(bassStrength);
            bassBoost.setEnabled(bassEnabled);
        } catch (Exception e) {
            Log.e(TAG, "applyBassBoost gagal", e);
        }
    }

    private void applyVirtualizer() {
        ensureVirtualizer();
        if (virtualizer == null) {
            return;
        }
        try {
            virtualizer.setStrength(virtualizerStrength);
            virtualizer.setEnabled(virtualizerEnabled);
        } catch (Exception e) {
            Log.e(TAG, "applyVirtualizer gagal", e);
        }
    }

    private void applyLoudness() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        ensureLoudnessEnhancer();
        if (loudnessEnhancer == null) {
            return;
        }
        try {
            loudnessEnhancer.setTargetGain(loudnessGain);
            loudnessEnhancer.setEnabled(loudnessEnabled);
        } catch (Exception e) {
            Log.e(TAG, "applyLoudness gagal", e);
        }
    }

    // ---- Bass Boost ----

    public boolean isBassAvailable() {
        return bassBoost != null;
    }

    public boolean isBassEnabled() {
        return bassBoost != null && bassEnabled;
    }

    public int getBassStrength() {
        return bassStrength;
    }

    public int getBassMax() {
        return 1000;
    }

    public void setBassEnabled(boolean enable) {
        bassEnabled = enable;
        if (bassBoost != null) {
            try {
                bassBoost.setEnabled(enable);
            } catch (Exception ignored) {
            }
        }
        saveEnabled("bass_enabled", enable);
    }

    public void setBassStrength(int strength) {
        bassStrength = (short) Math.max(0, Math.min(1000, strength));
        if (bassBoost != null) {
            try {
                bassBoost.setStrength(bassStrength);
            } catch (Exception ignored) {
            }
        }
        saveBassStrength(bassStrength);
    }

    // ---- Virtualizer ----

    public boolean isVirtualizerAvailable() {
        return virtualizer != null;
    }

    public boolean isVirtualizerEnabled() {
        return virtualizer != null && virtualizerEnabled;
    }

    public int getVirtualizerStrength() {
        return virtualizerStrength;
    }

    public int getVirtualizerMax() {
        return 1000;
    }

    public void setVirtualizerEnabled(boolean enable) {
        virtualizerEnabled = enable;
        if (virtualizer != null) {
            try {
                virtualizer.setEnabled(enable);
            } catch (Exception ignored) {
            }
        }
        saveEnabled("virtualizer_enabled", enable);
    }

    public void setVirtualizerStrength(int strength) {
        virtualizerStrength = (short) Math.max(0, Math.min(1000, strength));
        if (virtualizer != null) {
            try {
                virtualizer.setStrength(virtualizerStrength);
            } catch (Exception ignored) {
            }
        }
        saveVirtualizerStrength(virtualizerStrength);
    }

    // ---- Loudness Enhancer ----

    /** Apakah perangkat mendukung LoudnessEnhancer (API 19+ dan tersedia). */
    public boolean isLoudnessAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                && loudnessEnhancer != null;
    }

    public boolean isLoudnessEnabled() {
        return loudnessEnhancer != null && loudnessEnabled;
    }

    /** Nilai gain saat ini dalam dB*100 (mis. 5000 = 50 dB). */
    public int getLoudnessGain() {
        return loudnessGain;
    }

    /** Rentang gain dari 0 hingga maksimum (dB*100). */
    public int getLoudnessMax() {
        return 10000;
    }

    public void setLoudnessEnabled(boolean enable) {
        loudnessEnabled = enable;
        if (loudnessEnhancer != null) {
            try {
                loudnessEnhancer.setEnabled(enable);
            } catch (Exception ignored) {
            }
        }
        saveEnabled("loudness_enabled", enable);
    }

    public void setLoudnessGain(int gainDbX100) {
        loudnessGain = Math.max(0, Math.min(getLoudnessMax(), gainDbX100));
        if (loudnessEnhancer != null) {
            try {
                loudnessEnhancer.setTargetGain(loudnessGain);
            } catch (Exception ignored) {
            }
        }
        saveLoudnessGain(loudnessGain);
    }


    // ---- Reverb (EnvironmentalReverb) ----
    //
    // EnvironmentalReverb menyetel gema ruangan secara manual (bukan preset OS),
    // sehingga kita bisa menawarkan preset seperti Katedral / Aula / Gua.
    // Rumus: roomLevel/decayTime/density/diffusion menentukan "ukuran ruangan",
    // reflectionsLevel/level + reverbLevel mengatur seberapa basah (wet) gema.

    /** Satu preset ruangan: parameter inti EnvironmentalReverb. */
    private static final class ReverbPreset {
        final int roomLevel;        // -6000..0 (mB)
        final int decayTime;        // 100..20000 ms
        final int reflectionsLevel; // -6000..1000 (mB)
        final short density;        // 0..1000
        final short diffusion;      // 0..1000
        final int reverbLevel;      // -6000..2000 (mB) tingkat gema basah
        ReverbPreset(int roomLevel, int decayTime, int reflectionsLevel,
                     int density, int diffusion, int reverbLevel) {
            this.roomLevel = roomLevel;
            this.decayTime = decayTime;
            this.reflectionsLevel = reflectionsLevel;
            this.density = (short) density;
            this.diffusion = (short) diffusion;
            this.reverbLevel = reverbLevel;
        }
    }

    /** ReverbPreset yang diekspos ke UI. Urutan sinkron dengan REVERB_PRESET_NAMES. */
    public static final ReverbPreset[] REVERB_PRESETS = {
            /* 0  Tanpa Gema    */ new ReverbPreset(-6000,   300, -6000,  100,  100, -6000),
            /* 1  Kamar Kecil   */ new ReverbPreset(    0,  1000,   500,  700,  750,  1200),
            /* 2  Kamar Sedang  */ new ReverbPreset(    0,  2000,   800,  800,  850,  1800),
            /* 3  Kamar Besar   */ new ReverbPreset(    0,  3500,  1000,  900,  900,  2000),
            /* 4  Studio        */ new ReverbPreset(    0,  1500,   700,  900,  950,  1600),
            /* 5  Aula Konser   */ new ReverbPreset(    0,  5000,  1000,  900,  950,  2000),
            /* 6  Aula Besar    */ new ReverbPreset(    0,  7000,  1000,  950,  950,  2000),
            /* 7  Katedral      */ new ReverbPreset(    0, 12000,  1000, 1000, 1000,  2000),
            /* 8  Gereja        */ new ReverbPreset(    0,  9000,  1000,  950, 1000,  2000),
            /* 9  Gua           */ new ReverbPreset(    0, 14000,   800,  900,  900,  2000),
            /* 10 Stadion       */ new ReverbPreset(    0, 10000,   600,  850,  900,  2000),
            /* 11 Plate         */ new ReverbPreset(    0,  3000,  1000, 1000, 1000,  2000)
    };

    /** Indeks preset "Kustom" (di luar tabel, memakai nilai terakhir yang disetel). */
    public static final int REVERB_CUSTOM_INDEX = REVERB_PRESETS.length;

    /** Jumlah preset ruangan yang tersedia (di luar "Kustom"). */
    public static int reverbPresetCount() {
        return REVERB_PRESETS.length;
    }

    /** Durasi gema (ms) preset ke-{@code index}; 0 bila di luar tabel. */
    public static int reverbPresetDecayMs(int index) {
        return (index >= 0 && index < REVERB_PRESETS.length)
                ? REVERB_PRESETS[index].decayTime : 0;
    }

    /** Tingkat ruangan (mB) preset ke-{@code index}; 0 bila di luar tabel. */
    public static int reverbPresetRoomLevel(int index) {
        return (index >= 0 && index < REVERB_PRESETS.length)
                ? REVERB_PRESETS[index].roomLevel : 0;
    }

    private void ensureReverb() {
        if (reverb == null && audioSessionId > 0) {
            try {
                reverb = new EnvironmentalReverb(0, audioSessionId);
            } catch (Exception e) {
                Log.e(TAG, "Reverb tidak tersedia", e);
                reverb = null;
            }
        }
    }

    private void applyReverb() {
        ensureReverb();
        if (reverb == null) {
            return;
        }
        try {
            if (reverbEnabled) {
                applyReverbPreset(reverbPresetIndex);
            }
            reverb.setEnabled(reverbEnabled);
        } catch (Exception e) {
            Log.e(TAG, "applyReverb gagal", e);
        }
    }

    /** Menuliskan parameter satu preset ke efek reverb (tanpa mengubah enabled). */
    private void applyReverbPreset(int index) {
        if (reverb == null) {
            return;
        }
        if (index < 0 || index >= REVERB_PRESETS.length) {
            return; // Kustom: biarkan nilai terakhir
        }
        ReverbPreset p = REVERB_PRESETS[index];
        try {
            reverb.setRoomLevel((short) p.roomLevel);
            reverb.setRoomHFLevel((short) -500);
            reverb.setDecayTime(p.decayTime);
            reverb.setDecayHFRatio((short) 1000);
            reverb.setReflectionsLevel((short) p.reflectionsLevel);
            reverb.setReflectionsDelay(40);
            reverb.setReverbLevel(reverbLevelFor(reverbMix, p));
            reverb.setReverbDelay(60);
            reverb.setDensity(p.density);
            reverb.setDiffusion(p.diffusion);
        } catch (Exception e) {
            Log.e(TAG, "applyReverbPreset gagal", e);
        }
    }

    /**
     * Konversi intensitas 0..100 ke {@code reverbLevel} (-6000..2000 mB).
     * Kurva kuadratik agar perubahan di separuh bawah rentang tetap terasa,
     * saturasi di ujung atas. Preset "Tanpa Gema" dipegang tetap senyap.
     */
    private static short reverbLevelFor(int mix, ReverbPreset p) {
        if (p.reverbLevel <= -6000) {
            return -6000; // Tanpa Gema
        }
        int clamped = Math.max(0, Math.min(100, mix));
        // Wet linear -1500..2000 mB (-15..+20 dB): terasa sejak mix rendah,
        // karena gema di bawah ~-15 dB praktis tidak terdengar di IEM.
        int v = (int) Math.round(-1500 + (clamped / 100.0) * 3500);
        return (short) Math.max(-6000, Math.min(2000, v));
    }

    /** Apakah perangkat menyediakan efek reverb. */
    public boolean isReverbAvailable() {
        ensureReverb();
        return reverb != null;
    }

    public boolean isReverbEnabled() {
        return reverb != null && reverbEnabled;
    }

    /** Indeks preset aktif (lihat REVERB_PRESETS) atau REVERB_CUSTOM_INDEX. */
    public int getReverbPresetIndex() {
        return reverbPresetIndex;
    }

    /** Intensitas gema 0..100. */
    public int getReverbMix() {
        return reverbMix;
    }

    public void setReverbEnabled(boolean enable) {
        reverbEnabled = enable;
        if (reverb != null) {
            try {
                if (enable) {
                    applyReverbPreset(reverbPresetIndex);
                }
                reverb.setEnabled(enable);
            } catch (Exception ignored) {
            }
        }
        saveEnabled("reverb_enabled", enable);
    }

    /** Menyetel preset; indeks REVERB_CUSTOM_INDEX berarti pakai nilai terakhir. */
    public void setReverbPresetIndex(int index) {
        reverbPresetIndex = Math.max(0, Math.min(REVERB_CUSTOM_INDEX, index));
        applyReverbPreset(reverbPresetIndex);
        prefs().edit().putInt("reverb_preset", reverbPresetIndex).apply();
    }

    public void setReverbMix(int mix) {
        reverbMix = Math.max(0, Math.min(100, mix));
        if (reverb != null) {
            try {
                if (reverbPresetIndex >= 0 && reverbPresetIndex < REVERB_PRESETS.length) {
                    reverb.setReverbLevel(reverbLevelFor(reverbMix, REVERB_PRESETS[reverbPresetIndex]));
                } else {
                    reverb.setReverbLevel(reverbLevelFor(reverbMix,
                            new ReverbPreset(0, 0, 0, 0, 0, 2000)));
                }
            } catch (Exception ignored) {
            }
        }
        prefs().edit().putInt("reverb_mix", reverbMix).apply();
    }

    // ---- Persistensi ----

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void restoreSettings() {
        SharedPreferences sp = prefs();
        bassEnabled = sp.getBoolean("bass_enabled", false);
        bassStrength = (short) sp.getInt("bass_strength", 400);
        virtualizerEnabled = sp.getBoolean("virtualizer_enabled", false);
        virtualizerStrength = (short) sp.getInt("virtualizer_strength", 500);
        loudnessEnabled = sp.getBoolean("loudness_enabled", false);
        loudnessGain = sp.getInt("loudness_gain", 0);
        reverbEnabled = sp.getBoolean("reverb_enabled", false);
        reverbPresetIndex = sp.getInt("reverb_preset", 0);
        reverbMix = sp.getInt("reverb_mix", 70);
    }

    private void saveEnabled(String key, boolean value) {
        prefs().edit().putBoolean(key, value).apply();
    }

    private void saveBassStrength(short value) {
        prefs().edit().putInt("bass_strength", value).apply();
    }

    private void saveVirtualizerStrength(short value) {
        prefs().edit().putInt("virtualizer_strength", value).apply();
    }

    private void saveLoudnessGain(int value) {
        prefs().edit().putInt("loudness_gain", value).apply();
    }

    /** Membuang instance agar tidak bocor (dipanggil saat tidak dipakai). */
    public static void release() {
        if (instance != null) {
            instance.detach();
            instance = null;
        }
    }
}
