package com.nx.music

import android.widget.SeekBar
import java.util.function.IntBinaryOperator

/**
 * Listener slider per band pada dialog equalizer di [PlayerActivity].
 *
 * [setBandLevel] (SAM Java) menyalurkan (band, level) tanpa merujuk tipe Java
 * EqualizerHelper yang belum dikompilasi saat compileKotlin.
 */
class EqualizerBandListener(
    private val band: Int,
    private val minLevel: Int,
    private val setBandLevel: IntBinaryOperator,
) : SeekBar.OnSeekBarChangeListener {

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (fromUser) {
            setBandLevel.applyAsInt(band, minLevel + progress)
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
}
