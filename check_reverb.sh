#!/system/bin/sh
# Self-check tabel preset reverb (tanpa Android device).
# Butuh: javac + android.jar. Harness stub ada di $NX_STUB (dibuat sekali).
# Pakai:  sh check_reverb.sh
set -e
ANDROID_JAR="${ANDROID_JAR:-/sdcard/Ikram/android.jar}"
STUB="${NX_STUB:-/root/.cache/nxcheck/iso/src}"
OUT="${NX_OUT:-/root/.cache/nxcheck/isoout}"
SRC=app/src/main/java/com/nx/music
javac -nowarn -d "$OUT" -cp "$ANDROID_JAR" $(find "$STUB" -name '*.java') \
  "$SRC/AudioEffectsHelper.java" "$SRC/EffectsSliderListener.java" \
  "$SRC/ArtworkGestureListener.java" "$SRC/SeekBarListener.java" \
  "$SRC/EqualizerHelper.java" "$SRC/EqualizerBandListener.java" \
  "$SRC/PresetSpinnerListener.java" "$SRC/PlayerActivity.java"
cat > /tmp/RevCheck.java <<'JAVA'
import com.nx.music.AudioEffectsHelper;
public class RevCheck {
    public static void main(String[] a) {
        int n = AudioEffectsHelper.reverbPresetCount();
        assert n == 12 : "preset count " + n;
        assert AudioEffectsHelper.REVERB_CUSTOM_INDEX == n : "custom index";
        int cat = AudioEffectsHelper.reverbPresetDecayMs(7);   // Katedral
        int cave = AudioEffectsHelper.reverbPresetDecayMs(9);  // Gua
        for (int i = 0; i < n; i++)
            if (i != 7 && i != 9)
                assert AudioEffectsHelper.reverbPresetDecayMs(i) <= cat : "hanya Gua/Katedral decay >10s";
        assert cave >= 10000 && cat >= 10000 : "Gua & Katedral gema panjang";
        assert AudioEffectsHelper.reverbPresetDecayMs(7) > AudioEffectsHelper.reverbPresetDecayMs(5)
                : "Katedral > Aula Konser";
        assert AudioEffectsHelper.reverbPresetRoomLevel(0) == -6000 : "Tanpa Gema senyap";
        assert AudioEffectsHelper.reverbPresetDecayMs(99) == 0 : "out of range -> 0";
        System.out.println("RevCheck OK: " + n + " preset, Katedral decay=" + cat + "ms");
    }
}
JAVA
javac -nowarn -cp "$OUT" -d "$OUT" /tmp/RevCheck.java
java -ea -cp "$OUT" RevCheck
