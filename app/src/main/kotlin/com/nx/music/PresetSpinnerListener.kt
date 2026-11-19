package com.nx.music

import android.view.View
import android.widget.AdapterView
import android.widget.SeekBar
import java.util.function.IntConsumer
import java.util.function.IntUnaryOperator

/**
 * Listener pemilih preset pada dialog equalizer di [PlayerActivity].
 *
 * Callback [usePreset] dan [bandLevel] (SAM Java) menggantikan tipe Java
 * EqualizerHelper.
 */
class PresetSpinnerListener(
    private val presets: Int,
    private val minLevel: Int,
    private val bandSeekBars: List<SeekBar>,
    private val usePreset: IntConsumer,
    private val bandLevel: IntUnaryOperator,
) : AdapterView.OnItemSelectedListener {

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        if (presets > 0) {
            usePreset.accept(position)
            for (i in bandSeekBars.indices) {
                bandSeekBars[i].progress = bandLevel.applyAsInt(i) - minLevel
            }
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {}
}
