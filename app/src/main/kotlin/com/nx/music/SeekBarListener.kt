package com.nx.music

import android.widget.SeekBar
import java.util.function.IntConsumer

/**
 * Listener seek bar utama di [PlayerActivity].
 *
 * Memakai [IntConsumer] (SAM Java) alih-alih tipe Java PlaybackController,
 * karena compileKotlin jalan lebih dulu dari compileJava — tapi Java tetap
 * bisa memanggilnya dengan lambda.
 */
class SeekBarListener(
    private val onSeek: IntConsumer,
) : SeekBar.OnSeekBarChangeListener {

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (fromUser) {
            onSeek.accept(progress)
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
}
