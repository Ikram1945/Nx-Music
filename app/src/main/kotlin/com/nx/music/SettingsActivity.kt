package com.nx.music

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat

/**
 * Layar pengaturan aplikasi (fitur settings).
 * Mengatur mode tampilan (gelap/terang/ikuti sistem), urutan daftar lagu,
 * dan menghapus riwayat statistik pemutaran. Semua preferensi disimpan di
 * `settings_prefs`.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        setContentView(R.layout.activity_settings)

        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        btnBack.setOnClickListener { finish() }

        setupTheme()
        setupSort()
        setupCustomTheme()
        setupCrossfade()
        setupClearHistory()

        // Apply custom theme saat activity dimuat
        applyCustomTheme()
    }

    private fun setupTheme() {
        val group = findViewById<RadioGroup>(R.id.group_theme)
        val mode = prefs.getInt(KEY_THEME, 0)
        group.check(
            when (mode) {
                1 -> R.id.theme_light
                2 -> R.id.theme_dark
                else -> R.id.theme_follow_system
            },
        )
        group.setOnCheckedChangeListener { _, checkedId ->
            val newMode: Int
            when (checkedId) {
                R.id.theme_light -> {
                    newMode = 1
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }
                R.id.theme_dark -> {
                    newMode = 2
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                }
                else -> {
                    newMode = 0
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
            }
            prefs.edit().putInt(KEY_THEME, newMode).apply()
        }
    }

    private fun setupSort() {
        val group = findViewById<RadioGroup>(R.id.group_sort)
        val sort = prefs.getInt(KEY_SORT, 0)
        group.check(
            when (sort) {
                1 -> R.id.sort_title
                2 -> R.id.sort_artist
                3 -> R.id.sort_duration
                else -> R.id.sort_default
            },
        )
        group.setOnCheckedChangeListener { _, checkedId ->
            val newSort = when (checkedId) {
                R.id.sort_title -> 1
                R.id.sort_artist -> 2
                R.id.sort_duration -> 3
                else -> 0
            }
            prefs.edit().putInt(KEY_SORT, newSort).apply()
        }
    }

    private fun setupClearHistory() {
        val btn = findViewById<Button>(R.id.btn_clear_history)
        btn.setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(R.string.clear_history_confirm)
                .setPositiveButton(R.string.clear_history) { _, _ ->
                    PlaybackStats.getInstance(this).clearAll()
                    Toast.makeText(this, R.string.clear_history_done, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.back, null)
                .show()
        }
    }

    private fun setupCustomTheme() {
        val group = findViewById<RadioGroup>(R.id.group_custom_theme)
        val theme = ThemeHelper.getCustomTheme(this)

        group.check(
            when (theme) {
                ThemeHelper.THEME_BLUE -> R.id.custom_theme_blue
                ThemeHelper.THEME_GREEN -> R.id.custom_theme_green
                ThemeHelper.THEME_PURPLE -> R.id.custom_theme_purple
                ThemeHelper.THEME_ORANGE -> R.id.custom_theme_orange
                ThemeHelper.THEME_RED -> R.id.custom_theme_red
                else -> R.id.custom_theme_default
            },
        )

        group.setOnCheckedChangeListener { _, checkedId ->
            val newTheme = when (checkedId) {
                R.id.custom_theme_blue -> ThemeHelper.THEME_BLUE
                R.id.custom_theme_green -> ThemeHelper.THEME_GREEN
                R.id.custom_theme_purple -> ThemeHelper.THEME_PURPLE
                R.id.custom_theme_orange -> ThemeHelper.THEME_ORANGE
                R.id.custom_theme_red -> ThemeHelper.THEME_RED
                else -> ThemeHelper.THEME_DEFAULT
            }

            ThemeHelper.setCustomTheme(this, newTheme)
            Toast.makeText(this, R.string.custom_theme_saved, Toast.LENGTH_SHORT).show()

            // Refresh UI untuk menerapkan tema baru
            applyCustomTheme()
        }
    }

    private fun setupCrossfade() {
        val switchCrossfade = findViewById<Switch>(R.id.switch_crossfade)
        switchCrossfade.isChecked = MusicService.isCrossfadeEnabled()
        switchCrossfade.setOnCheckedChangeListener { _, isChecked ->
            MusicService.setCrossfadeEnabled(this, isChecked)
            Toast.makeText(
                this,
                if (isChecked) R.string.crossfade_on else R.string.crossfade_off,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun applyCustomTheme() {
        // Terapkan tema ke seluruh layar (ikon, tombol, seek bar, dsb.).
        // CATATAN: jangan pakai applyToImageButton utk btn_back — itu menint
        // BACKGROUND tombol transparan jadi kotak warna. Ikon ditint oleh
        // applyThemeToActivity lewat penanganan ImageButton.
        ThemeHelper.applyThemeToActivity(this)
        updateRadioButtonTints()
    }

    /**
     * Radio button dan switch dikunci memakai warna TETAP, bukan warna tema.
     *
     * Alasannya: warna tema kustom (dan mode gelap) bisa menghasilkan
     * bulatan radio yang menyatu dengan latarnya, sehingga tombolnya tidak
     * terlihat. Warna tetap memastikan bulatan selalu terbaca di tema apa pun.
     * Latar baris pilihan (bg_setting_option) memakai warna tetap yang sama
     * agar kontrasnya terjaga.
     */
    private fun updateRadioButtonTints() {
        val fixed = ContextCompat.getColor(this, R.color.setting_radio_fixed)
        val tint = ColorStateList.valueOf(fixed)
        intArrayOf(R.id.group_theme, R.id.group_sort, R.id.group_custom_theme).forEach { groupId ->
            val group = findViewById<RadioGroup>(groupId)
            for (i in 0 until group.childCount) {
                (group.getChildAt(i) as? RadioButton)?.setButtonTintList(tint)
            }
        }
        findViewById<Switch>(R.id.switch_crossfade).setThumbTintList(tint)
    }

    companion object {
        private const val PREFS = "settings_prefs"

        /** 0 = ikuti sistem, 1 = terang, 2 = gelap. */
        private const val KEY_THEME = "theme_mode"

        /** 0 = default, 1 = judul, 2 = artis, 3 = durasi. */
        private const val KEY_SORT = "sort_order"

        /** Membaca urutan daftar lagu yang disimpan (dipakai oleh SongListActivity/SongLoader). */
        @JvmStatic
        fun getSortOrder(context: Context): Int =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_SORT, 0)
    }
}
