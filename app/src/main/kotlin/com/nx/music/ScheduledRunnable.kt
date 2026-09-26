package com.nx.music

import android.os.Handler

/**
 * Runnable yang menjadwalkan ulang dirinya sendiri setiap [intervalMs].
 * Top-level agar tidak menghasilkan halo mate (kompatibel dengan d8 CodeAssist).
 */
class ScheduledRunnable(
    private val handler: Handler,
    private val intervalMs: Long,
    private val task: Runnable,
) : Runnable {
    override fun run() {
        task.run()
        handler.postDelayed(this, intervalMs)
    }
}