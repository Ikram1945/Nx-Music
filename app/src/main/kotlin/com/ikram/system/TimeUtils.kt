package com.ikram.system

import java.util.Locale

/**
 * Utilitas sistem untuk memformat waktu.
 * Murni non-Android (hanya java.*), dapat dipakai modul lain tanpa framework Android.
 */
object TimeUtils {
    /** Mengubah durasi milidetik menjadi format menit:detik, misal 3:05. */
    @JvmStatic
    fun format(millis: Long): String {
        val totalSec = millis / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "$min:${String.format(Locale.US, "%02d", sec)}"
    }
}