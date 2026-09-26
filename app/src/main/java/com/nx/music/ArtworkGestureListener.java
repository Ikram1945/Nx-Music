package com.nx.music;

import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.Toast;

/**
 * Listener gesture seek pada artwork di {@link PlayerActivity} (fitur #9):
 * swipe kiri maju 5 detiks, swipe kanan mundur 5 detik. Top-level agar tidak
 * menghasilkan nest mate (kompatibel dengasn d8 CodeAssist).
 */
public class ArtworkGestureListener extends GestureDetector.SimpleOnGestureListener {

    private final PlaybackController playback;
    private final PlayerActivity activity;

    public ArtworkGestureListener(PlayerActivity activity, PlaybackController playback) {
        this.activity = activity;
        this.playback = playback;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2,
                           float velocityX, float velocityY) {
        if (e1 == null || e2 == null || playback == null) {
            return false;
        }
        float dx = e2.getX() - e1.getX();
        float dy = e2.getY() - e1.getY();
        // Pastikan dominan horizontal agar swipe vertikal tidak memicu seek.
        if (Math.abs(dx) < Math.abs(dy)) {
            return false;
        }
        if (Math.abs(dx) < 80f) {
            return false;
        }
        int duration = playback.getDuration();
        if (duration <= 0) {
            return true;
        }
        int target = playback.getPosition();
        if (dx < 0) {
            // Swipe kiri -> maju
            target += PlayerActivity.GESTURE_SEEK_STEP_MS;
            if (target > duration) {
                target = duration;
            }
        } else {
            // Swipe kanan -> mundur
            target -= PlayerActivity.GESTURE_SEEK_STEP_MS;
            if (target < 0) {
                target = 0;
            }
        }
        playback.seekTo(target);
        activity.onGestureSeekDone(target);
        Toast.makeText(activity,
                com.ikram.system.TimeUtils.format(target),
                Toast.LENGTH_SHORT).show();
        return true;
    }
}
