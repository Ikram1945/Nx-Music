package com.nx.music;

import android.view.View;
import android.widget.AdapterView;
import android.widget.SeekBar;

import java.util.List;

/**
 * Listener pemilih preset pada dialog equalizer di {@link PlayerActivity}.
 * Top-level agar tidak menghasilkan nest mate (kompatibel dengan d8 CodeAssist).
 */
public class PresetSpinnerListener implements AdapterView.OnItemSelectedListener {

    private final EqualizerHelper eqHelper;
    private final int presets;
    private final int minLevel;
    private final List<SeekBar> bandSeekBars;

    public PresetSpinnerListener(EqualizerHelper eqHelper, int presets, int minLevel,
                                 List<SeekBar> bandSeekBars) {
        this.eqHelper = eqHelper;
        this.presets = presets;
        this.minLevel = minLevel;
        this.bandSeekBars = bandSeekBars;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (presets > 0 && eqHelper != null) {
            eqHelper.usePreset(position);
            for (int i = 0; i < bandSeekBars.size(); i++) {
                bandSeekBars.get(i).setProgress(eqHelper.getBandLevel(i) - minLevel);
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }
}
