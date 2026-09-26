package com.nx.music;

import android.widget.SeekBar;

/**
 * Listener seek bar utama di {@link PlayerActivity}. Top-level agar tidak
 * menghasilkan nest mate (kompatibel dengan d8 CodeAssist yang tidak
 * mendukung nest mates Java 11+).
 */
public class SeekBarListener implements SeekBar.OnSeekBarChangeListener {

    private final PlaybackController playback;

    public SeekBarListener(PlaybackController playback) {
        this.playback = playback;
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser && playback != null) {
            playback.seekTo(progress);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }
}
