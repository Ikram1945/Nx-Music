package com.nx.music

import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import com.ikram.system.TimeUtils

/**
 * Listener gesture seek pada artwork di [PlayerActivity] (fitur #9):
 * swipe kiri maju 5 detiks, swipe kanan mundur 5 detik. Top-level agar tidak
 * menghasilkan nest mate (kompatibel dengan d8 CodeAssist).
 */
class ArtworkGestureListener(
    private val activity: PlayerActivity,
    private val playback: PlaybackController,
) : GestureDetector.SimpleOnGestureListener() {

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        val dx = e2.x - (e1?.x ?: 0f)
        val dy = e2.y - (e1?.y ?: 0f)
        // Pastikan dominan horizontal agar swipe vertikal tidak memicu seek.
        if (kotlin.math.abs(dx) < kotlin.math.abs(dy)) {
            return false
        }
        if (kotlin.math.abs(dx) < SWIPE_THRESHOLD) {
            return false
        }
        val duration = playback.duration
        if (duration <= 0) {
            return true
        }
        var target = playback.position
        target = if (dx < 0) {
            // Swipe kiri -> maju
            (target + PlayerActivity.GESTURE_SEEK_STEP_MS).coerceAtMost(duration)
        } else {
            // Swipe kanan -> mundur
            (target - PlayerActivity.GESTURE_SEEK_STEP_MS).coerceAtLeast(0)
        }
        // getPosition()/getDuration() = Java int -> target Int; TimeUtils.format butuh Long.
        playback.seekTo(target)
        activity.onGestureSeekDone(target)
        Toast.makeText(activity, TimeUtils.format(target.toLong()), Toast.LENGTH_SHORT).show()
        return true
    }

    private companion object {
        private const val SWIPE_THRESHOLD = 80f
    }
}
