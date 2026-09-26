package com.nx.music;

import android.widget.SeekBar;
import android.widget.CompoundButton;

import java.util.function.IntConsumer;

/**
 * Listener slider kekuatan/gain pada dialog efek audio di {@link PlayerActivity}.
 * Top-level agar tidak menghasilkan nest mate (kompatibel dengan d8 CodeAssist).
 */
public class EffectsSliderListener implements SeekBar.OnSeekBarChangeListener {

    private final IntConsumer onValue;
    private final CompoundButton enableSwitch;

    public EffectsSliderListener(IntConsumer onValue, CompoundButton enableSwitch) {
        this.onValue = onValue;
        this.enableSwitch = enableSwitch;
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            onValue.accept(progress);
            // menggerakkan slider otomatis mengaktifkan efek
            if (!enableSwitch.isChecked()) {
                enableSwitch.setChecked(true);
            }
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }
}
