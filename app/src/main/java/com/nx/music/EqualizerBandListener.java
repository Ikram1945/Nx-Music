package com.nx.music;

import android.widget.SeekBar;

/**
 * Listener slider per band pada dialog equalizer di {@link PlayerActivity}.
 * Top-level agar tidak menghasilkan nest mate (kompatibel dengan d8 CodeAssist).
 */
public class EqualizerBandListener implements SeekBar.OnSeekBarChangeListener {

    private final EqualizerHelper eqHelper;
    private final int band;
    private final int minLevel;

    public EqualizerBandListener(EqualizerHelper eqHelper, int band, int minLevel) {
        this.eqHelper = eqHelper;
        this.band = band;
        this.minLevel = minLevel;
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser && eqHelper != null) {
            eqHelper.setBandLevel(band, minLevel + progress);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }
}
