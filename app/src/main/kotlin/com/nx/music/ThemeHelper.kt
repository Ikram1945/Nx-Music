package com.nx.music

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView

/**
 * Helper untuk mengelola tema kustom aplikasi.
 * Menyimpan dan menerapkan warna tema yang dipilih pengguna.
 */
object ThemeHelper {

    private const val PREFS = "settings_prefs"
    private const val KEY_CUSTOM_THEME = "custom_theme"

    /** Warna tema default (primary color dari tema sistem). */
    private val DEFAULT_PRIMARY = Color.parseColor("#6200EE")

    // Daftar tema kustom
    const val THEME_DEFAULT = 0
    const val THEME_BLUE = 1
    const val THEME_GREEN = 2
    const val THEME_PURPLE = 3
    const val THEME_ORANGE = 4
    const val THEME_RED = 5

    /** Mendapatkan warna primary berdasarkan tema yang dipilih. */
    @JvmStatic
    fun getPrimaryColor(context: Context): Int = when (getCustomTheme(context)) {
        THEME_BLUE -> Color.parseColor("#2196F3")     // Blue 500
        THEME_GREEN -> Color.parseColor("#4CAF50")    // Green 500
        THEME_PURPLE -> Color.parseColor("#9C27B0")   // Purple 500
        THEME_ORANGE -> Color.parseColor("#FF9800")   // Orange 500
        THEME_RED -> Color.parseColor("#F44336")      // Red 500
        else -> DEFAULT_PRIMARY                       // Default purple
    }

    /** Mendapatkan warna primary versi gelap (untuk dark theme). */
    @JvmStatic
    fun getPrimaryDarkColor(context: Context): Int = when (getCustomTheme(context)) {
        THEME_BLUE -> Color.parseColor("#1976D2")     // Blue 700
        THEME_GREEN -> Color.parseColor("#388E3C")    // Green 700
        THEME_PURPLE -> Color.parseColor("#7B1FA2")   // Purple 700
        THEME_ORANGE -> Color.parseColor("#F57C00")   // Orange 700
        THEME_RED -> Color.parseColor("#D32F2F")      // Red 700
        else -> Color.parseColor("#3700B3")           // Default purple dark
    }

    /** Mendapatkan warna primary versi ringan (untuk light theme). */
    @JvmStatic
    fun getPrimaryLightColor(context: Context): Int = when (getCustomTheme(context)) {
        THEME_BLUE -> Color.parseColor("#BBDEFB")     // Blue 100
        THEME_GREEN -> Color.parseColor("#C8E6C9")    // Green 100
        THEME_PURPLE -> Color.parseColor("#E1BEE7")   // Purple 100
        THEME_ORANGE -> Color.parseColor("#FFE0B2")   // Orange 100
        THEME_RED -> Color.parseColor("#FFCDD2")      // Red 100
        else -> Color.parseColor("#BB86FC")           // Default purple light
    }

    /** Mendapatkan tema kustom yang disimpan. */
    @JvmStatic
    fun getCustomTheme(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_CUSTOM_THEME, THEME_DEFAULT)

    /** Menyimpan tema kustom yang dipilih. */
    @JvmStatic
    fun setCustomTheme(context: Context, theme: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_CUSTOM_THEME, theme).apply()
    }

    /** Menerapkan warna tema ke tombol. */
    @JvmStatic
    fun applyToButton(button: Button, context: Context) {
        button.backgroundTintList = ColorStateList.valueOf(getPrimaryColor(context))
    }

    /** Menerapkan warna tema ke ImageButton. */
    @JvmStatic
    fun applyToImageButton(button: ImageButton, context: Context) {
        button.backgroundTintList = ColorStateList.valueOf(getPrimaryColor(context))
    }

    /** Mendapatkan nama tema sebagai string (untuk UI). */
    @JvmStatic
    fun getThemeName(context: Context, theme: Int): String = context.getString(
        when (theme) {
            THEME_BLUE -> R.string.custom_theme_blue
            THEME_GREEN -> R.string.custom_theme_green
            THEME_PURPLE -> R.string.custom_theme_purple
            THEME_ORANGE -> R.string.custom_theme_orange
            THEME_RED -> R.string.custom_theme_red
            else -> R.string.custom_theme_default
        }
    )

    /** Menerapkan tema kustom ke seluruh activity. */
    @JvmStatic
    fun applyThemeToActivity(activity: Activity) {
        val primaryColor = getPrimaryColor(activity)

        // Status bar: tetap warna background tema + ikon gelap, sesuai
        // themes.xml (windowLightStatusBar=true). Jangan pakai primaryDark —
        // itu membuat bar ungu gelap yg bertabrakan dgn tema terang.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.window.statusBarColor = getBackgroundColor(activity)
        }

        // Apply theme color to activity background
        applyToRootView(activity.window.decorView, primaryColor)
    }

    /** Mendapatkan warna background berdasarkan tema. */
    @JvmStatic
    fun getBackgroundColor(context: Context): Int = when (getCustomTheme(context)) {
        THEME_BLUE -> Color.parseColor("#E3F2FD")     // Light blue background
        THEME_GREEN -> Color.parseColor("#E8F5E9")    // Light green background
        THEME_PURPLE -> Color.parseColor("#F3E5F5")   // Light purple background
        THEME_ORANGE -> Color.parseColor("#FFF3E0")   // Light orange background
        THEME_RED -> Color.parseColor("#FFEBEE")      // Light red background
        else -> Color.parseColor("#F4F1EC")           // Default cream background
    }

    private fun applyToRootView(view: View?, color: Int) {
        if (view == null) return

        // Apply to children recursively for common interactive elements
        // CATATAN: background ImageButton TIDAK di-tint langsung; yg di-tint adalah
        // IKON-nya. Background oval solid diwarnai tema lewat hasSolidOvalBackground().
        when (view) {
            is ImageButton -> {
                // Ikon putih di atas lingkaran berisi dibiarkan putih; lingkarannya yg diwarnai.
                val iconTint = view.imageTintList?.defaultColor ?: Color.TRANSPARENT
                if (iconTint != Color.WHITE) {
                    view.imageTintList = ColorStateList.valueOf(color)
                }
                if (hasSolidOvalBackground(view)) {
                    view.backgroundTintList = ColorStateList.valueOf(color)
                }
            }
            is Button -> view.backgroundTintList = ColorStateList.valueOf(color)
            is SeekBar -> {
                view.progressTintList = ColorStateList.valueOf(color)
                view.thumbTintList = ColorStateList.valueOf(color)
            }
            is TextView -> {
                // Apply accent color to text that uses primary color
                try {
                    val currentColor = view.textColors?.defaultColor
                    // Only apply to accent-colored text (typically #6200EE or similar)
                    if (currentColor != null && isAccentColor(currentColor)) {
                        view.setTextColor(color)
                    }
                } catch (ignored: RuntimeException) {
                    // Beberapa view bisa melempar saat dibaca sebelum ter-attach.
                    // Lewati saja; ini bukan alasan untuk crash.
                }
            }
        }

        // Apply theme color to backgrounds that aren't handled by themes
        applyBackgroundColor(view, color)

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyToRootView(view.getChildAt(i), color)
            }
        }
    }

    // ponytail: simple heuristic - expand when we need more precise color matching
    /**
     * True jika background view adalah shape oval berwarna solid (bg_icon_circle
     * dkk), bukan ripple transparan. Agar hanya tombol ikon berlingkaran yg
     * diwarnai tema; tombol ikon ber-ripple dibiarkan.
     */
    private fun hasSolidOvalBackground(view: View): Boolean {
        val bg = view.background as? GradientDrawable ?: return false
        return bg.shape == GradientDrawable.OVAL && bg.color != null
    }

    private fun isAccentColor(color: Int): Boolean {
        // Default purple or other theme colors we want to override
        val defaultPurple = 0xFF6200EE.toInt()
        return Math.abs(color - defaultPurple) < 0x00101010 // Allow some variation
    }

    private fun applyBackgroundColor(view: View, color: Int) {
        // Apply to specific drawable backgrounds that need theme color
        val bg: Drawable = view.background ?: return
        val bgColor = (bg as? GradientDrawable)?.color?.defaultColor ?: 0
        // If background is the old accent color, update it
        if (bgColor != 0 && isAccentColor(bgColor)) {
            view.backgroundTintList = ColorStateList.valueOf(color)
        }
    }
}
