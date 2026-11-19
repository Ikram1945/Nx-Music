package com.nx.music

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.media.audiofx.Visualizer
import android.util.AttributeSet
import android.view.View
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Bar spectrum real-time dari output audio pemutar via [Visualizer].
 *
 * Pemetaan bar memakai **frekuensi nyata (Hz)**, bukan indeks bin
 * ternormalisasi, sehingga bass, vokal, dan treble punya bar sendiri dan
 * spectrum mengikuti nada lagu.
 *
 * - [F_MIN]/[F_MAX] = rentang frekuensi yang dipetakan,
 *   batas bawahnya dinaikkan otomatis bila resolusi bin terlalu kasar.
 * - Tiap bar menerima minimal satu bin unik (tanpa tumpang-tindih).
 * - Bar lebar (treble) memakai sub-band peak, bukan rata-rata, agar tetap
 *   reaktif walau energinya tersebar.
 * - [barWeight] menyetarakan bass/treble — energi musik menurun
 *   mengikuti 1/f sehingga treble perlu dinaikkan.
 *
 * Butuh izin `RECORD_AUDIO`. Jika izin tak ada atau Visualizer gagal
 * dibuat, view menggambar bar diam (idle) dan tidak crash..
 */
class SpectrumView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val BARS = 28

        /** Rentang frekuensi yang dipetakan ke bar. */
        private const val F_MIN = 100f   // bawah: bass
        private const val F_MAX = 8000f  // atas: treble

        private fun clampBin(b: Int, bins: Int): Int {
            if (b < 0) return 0
            return min(b, bins)
        }

        private fun magnitude(fft: ByteArray, k: Int): Float {
            val re = fft[2 * k].toInt()
            val im = fft[2 * k + 1].toInt()
            return hypot(re.toDouble(), im.toDouble()).toFloat()
        }

        /** Bobot per bar: ujung bass & treble dinaikkan, tengah diredam. */
        private fun barWeight(b: Int): Float {
            val t = b.toFloat() / (BARS - 1)     // 0 bass .. 1 treble
            // Data nyata (dump Download/NxSpectrum*.txt), diukur berulang:
            //   boost 1+t^1.8*2.2 -> rasio std bass/treble 2.54x (masih timpang)
            //   boost idaman (target/std) di ujung treble 5.5-6.4x
            // Dipakai 1+t^2*5.0 -> ujung treble ~6x, tengah ~2.3x.
            return 1.0f + t * t * 5.0f
        }

        /**
         * Pilih capture size terbesar yang diterima perangkat. Semakin besar,
         * semakin halus resolusi frekuensi (binHz kecil) sehingga bar bass tidak
         * jatuh di satu bin yang sama.
         */
        private fun pickCaptureSize(v: Visualizer): Int {
            val max = Visualizer.getCaptureSizeRange()[1]
            // coba dari besar ke kecil. PENTING: sebagian perangkat TIDAK melempar
            // saat menolak; ia diam-diam memakai nilai lain. Jadi selalu baca ulang
            // getCaptureSize() untuk memastikan, bukan percaya nilai yang diminta.
            val tries = intArrayOf(4096, 2048, 1024, max)
            for (cs in tries) {
                if (cs <= 0 || cs and 1 != 0) continue
                try {
                    v.setCaptureSize(cs)
                    if (v.captureSize == cs) return cs  // benar-benar dipakai
                } catch (ignored: Exception) {
                }
            }
            try { v.setCaptureSize(max) } catch (ignored: Exception) { }
            try { return v.captureSize } catch (ignored: Exception) { }
            return max
        }
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val levels = FloatArray(BARS)   // level target 0..1 per bar
    private val shown = FloatArray(BARS)    // level tergambar (dianimasikan)
    /** Buffer FFT bersama: ditulis thread Visualizer, dibaca onDraw. */
    private val mags = FloatArray(BARS)

    /** true saat onDraw menjadwalkan frame sendiri (animasi 60 fps). */
    @Volatile
    private var animating = false
    /** Timestamp frame terakhir untuk smoothing berbasis waktu (ns). */
    private var lastFrameNs = 0L

    /** Batas bin per bar, dihitung ulang saat tahu sample rate. */
    private val binLo = IntArray(BARS)
    private val binHi = IntArray(BARS)
    private var binsReady = false

    /** Selubung puncak lintas-frame untuk normalisasi dinamis (lihat normalize). */
    private var peakEnv = 1f

    private var visualizer: Visualizer? = null
    private var barColor = -0xA49067 /* 0xFF5B6F99 sebagai int */
    private var sessionActive = false

    init {
        barPaint.style = Paint.Style.FILL
        barPaint.color = barColor
    }

    fun setBarColor(color: Int) {
        barColor = color
        barPaint.color = color
        invalidate()
    }

    /** Mulai menempel Visualizer ke audio session. Panggil saat session berubah/resume. */
    fun start(sessionId: Int) {
        stop()
        if (sessionId <= 0) return
        try {
            val v = Visualizer(sessionId)
            // Resolusi frekuensi = sampleRate / captureSize. Banyak perangkat
            // melaporkan getCaptureSizeRange()[1] hanya 512 (binHz ~86 Hz) ->
            // bar bass jadi 1 bin tiap bar, saling ikut. Coba ukuran yang lebih
            // besar dulu, mundur bila perangkat menolak.
            val capSize = pickCaptureSize(v)

            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: Visualizer, w: ByteArray, rate: Int) {}

                override fun onFftDataCapture(v: Visualizer, fft: ByteArray, rate: Int) {
                    // Hitung di thread Visualizer (bukan UI), tulis ke field mags
                    // bersama -> nol alokasi & nol lambda per frame.
                    fftMagnitudes(fft)
                    // Hanya tendang animasi saat idle; selagi onDraw ber-momentum,
                    // frame berikutnya dijadwalkan sendiri oleh onDraw.
                    if (!animating) postInvalidateOnAnimation()
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true)

            // PENTING: getSamplingRate() baru valid SETELAH setEnabled(true).
            // Kalau dipanggil sebelum, hasilnya sampah (pernah terlihat 48000000
            // -> binHz raksasa -> semua bar jatuh di bin 0-1 -> semua bar identik).
            v.setEnabled(true)

            var sampleRate = v.samplingRate.toFloat()
            // Validasi: di luar rentang wajar = ambil default, jangan sampai
            // binHz rusak lagi. 44100 aman untuk hampir semua perangkat.
            if (!(sampleRate in 8000f..192000f)) sampleRate = 44100f
            prepareBins(capSize, sampleRate)

            visualizer = v
            sessionActive = true
        } catch (e: Exception) {
            visualizer = null
            sessionActive = false
        }
        invalidate()
    }

    fun isRunning(): Boolean = sessionActive

    /** Lepaskan Visualizer. Panggil di onPause. */
    fun stop() {
        sessionActive = false
        animating = false
        lastFrameNs = 0L
        visualizer?.let { v ->
            try {
                v.setEnabled(false)
                v.release()
            } catch (ignored: Exception) {
            }
            visualizer = null
        }
        levels.fill(0f)
        invalidate()
    }

    /**
     * Hitung batas bin FFT tiap bar dari frekuensi nyata.
     * Bar b mewakili rentang [F_MIN, F_MAX] terdistribusi logaritmis.
     */
    private fun prepareBins(captureSize: Int, sampleRate: Float) {
        val bins = captureSize / 2                 // jumlah bin magnitude
        if (bins <= 1 || sampleRate <= 0) { binsReady = false; return }
        val nyquist = sampleRate / 2f
        val binHz = nyquist / bins                 // resolusi tiap bin dalam Hz

        // Resolusi bin menentukan frekuensi terendah yang MASIH bisa dipisah.
        // Kalau F_MIN lebih rendah dari ~2 bin, beberapa bar pertama jatuh di bin
        // yang sama -> bar duplikat -> "semua bar sama". Jadi naikkan F_MIN dinamis
        // agar bar terendah punya >=2 bin.
        val fMin = max(F_MIN, binHz * 2f)
        val fMax = min(F_MAX, nyquist * 0.9f)
        if (fMax <= fMin * 1.5f) { binsReady = false; return }

        val logMin = ln(fMin.toDouble())
        val logMax = ln(fMax.toDouble())
        var prevHi = -1
        for (b in 0 until BARS) {
            val f0 = exp(logMin + (logMax - logMin) * b / BARS).toFloat()
            val f1 = exp(logMin + (logMax - logMin) * (b + 1) / BARS).toFloat()
            var lo = clampBin((f0 / binHz).toInt(), bins)
            var hi = clampBin((f1 / binHz).toInt(), bins)
            // Jamin monoton & tak tumpang-tindih: tiap bar minimal 1 bin baru.
            if (lo < prevHi) lo = prevHi
            if (hi <= lo) hi = min(bins, lo + 1)
            binLo[b] = lo
            binHi[b] = hi
            prevHi = hi
        }
        binsReady = true
    }

    /**
     * FFT byte -> magnitudo 0..1 per bar, memakai rentang frekuensi nyata.
     * Menulis ke [mags] bersama agar tidak mengalokasi array per frame.
     */
    private fun fftMagnitudes(fft: ByteArray) {
        val mags = this.mags
        val n = fft.size / 2
        if (n <= 1) return

        // Fallback bila sample rate belum diketahui: pemetaan log atas n bin.
        if (!binsReady) {
            for (b in 0 until BARS) {
                var lo = (n * (b.toDouble() / BARS).pow(1.7)).toInt()
                var hi = (n * ((b + 1).toDouble() / BARS).pow(1.7)).toInt()
                if (hi <= lo) hi = lo + 1
                viaAvg(mags, b, lo, hi, fft, n)
            }
            normalize(mags)
            return
        }

        for (b in 0 until BARS) viaAvg(mags, b, binLo[b], binHi[b], fft, n)
        normalize(mags)
    }

    /**
     * Rata-rata magnitudo bin [lo, hi) -> mags[b] DENGAN BOBOT, belum diskala.
     * Nilai mentah (bisa jauh > 1) karena normalisasi dilakukan lintas bar di
     * [fftMagnitudes] setelah puncak diketahui.
     */
    private fun viaAvg(mags: FloatArray, b: Int, lo: Int, hi0: Int, fft: ByteArray, n: Int) {
        var hi = hi0
        if (hi > n) hi = n
        if (hi <= lo) { mags[b] = 0f; return }
        val width = hi - lo
        val value: Float
        if (width <= 8) {
            // bar sempit (bass/mid): rata-rata. Bin sedikit, rata-rata sudah peka.
            var sum = 0f
            for (k in lo until hi) sum += magnitude(fft, k)
            value = sum / width
        } else {
            // bar lebar (treble): bagi jadi ~8 sub-band, ambil RATA-RATA DARI PUNCAK
            // tiap sub-band. Rata-rata seluruh bin membuat treble nyaris diam karena
            // energi tersebar; sub-band max membuat bar treble tetap reaktif.
            val subs = 8
            var acc = 0f
            for (sg in 0 until subs) {
                var slo = lo + (width.toLong() * sg / subs).toInt()
                var shi = lo + (width.toLong() * (sg + 1) / subs).toInt()
                if (shi <= slo) shi = slo + 1
                if (shi > hi) shi = hi
                var peak = 0f
                for (k in slo until shi) {
                    val m = magnitude(fft, k)
                    if (m > peak) peak = m
                }
                acc += peak
            }
            value = acc / subs
        }
        mags[b] = value * barWeight(b)
    }

    /**
     * Normalisasi lintas bar: bagi dengan puncak frame ini, lalu skala ke tinggi
     * pas. Inilah yang membuat tiap bar BEDA: bar yang lebih kuat tampak lebih
     * tinggi, bukan semua mentok langit-langit karena divisor tetap.
     *
     * Puncak dihaluskan ([peakEnv]) supaya tinggi bar tidak berkedip
     * saat puncak berpindah antar bar.
     */
    private fun normalize(mags: FloatArray) {
        var peak = 0f
        var sum = 0f
        for (b in 0 until BARS) {
            if (mags[b] > peak) peak = mags[b]
            sum += mags[b]
        }
        val mean = sum / BARS
        // puncak efektif = gabungan puncak & rata-rata (puncak tunggal terlalu
        // dominan bila satu bass drum memukul; campuran ini lebih stabil).
        var ref = max(peak * 0.85f, mean * 2.2f)
        if (ref < 1e-3f) ref = 1e-3f
        // AGC lambat: ikuti puncak dengan attack cepat, release lambat.
        peakEnv = if (ref > peakEnv) peakEnv * 0.5f + ref * 0.5f
                  else peakEnv * 0.85f + ref * 0.15f
        if (peakEnv < 1e-3f) peakEnv = 1e-3f
        for (b in 0 until BARS) {
            var v = mags[b] / peakEnv
            // kurva pangkat ringan -> bar menengah tetap kelihatan bedanya
            v = min(1f, v).toDouble().pow(0.7).toFloat()
            // noise gate lembut: nilai sangat kecil (sisa treble lemah) dinaikkan
            // sedikit supaya bar tidak "mati" total, tanpa membuat semua bar rata.
            v = max(v, 0.05f + 0.10f * v)
            mags[b] = min(1f, v)
        }
    }

    /** Salin magnitudo terbaru ke level dengan attack instan / release cepat. */
    private fun applyMagnitudes() {
        for (i in 0 until BARS) {
            val target = mags[i]
            levels[i] = if (target > levels[i]) target
                        else levels[i] * 0.55f + target * 0.45f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width
        val h = height
        if (w == 0 || h == 0) return
        if (sessionActive) applyMagnitudes()

        // Smoothing TIME-BASED: kecepatan px/detik, bukan fraksi per frame.
        // Dulu 0.8 per frame -> terlihat "loncat" kalau frame terlewat (FFT cuma
        // ~10-20 Hz). Sekarang interpolasi tetap sama cepat di 60/90/120 Hz.
        val now = System.nanoTime()
        var dt = if (lastFrameNs == 0L) 0.016f else (now - lastFrameNs) / 1e9f
        lastFrameNs = now
        if (dt > 0.05f) dt = 0.05f                  // buang lonjakan (GC / frame drop)
        val follow = min(1f, dt * 18f)              // ~55 ms ke target, konstan
        val decay = min(1f, dt * 12f)               // bar turun lebih lembut dari naik

        val slot = w.toFloat() / BARS
        val gap = max(1f, slot * 0.12f)             // gap tipis -> bar tampak lebih kecil
        val barW = max(2f, slot - gap)

        var stillMoving = false
        for (i in 0 until BARS) {
            val target = levels[i]
            var cur = shown[i]
            val k = if (target > cur) follow else decay  // attack cepat, release lembut
            cur += (target - cur) * k
            if (kotlin.math.abs(target - cur) < 0.002f) cur = target
            else stillMoving = true
            shown[i] = cur

            val v = max(cur, 0.04f)                 // bar minimum agar tetap terlihat
            val bh = v * h
            val left = i * slot + gap / 2f
            val top = h - bh
            canvas.drawRoundRect(left, top, left + barW, h.toFloat(), barW / 2f, barW / 2f, barPaint)
        }

        // Jadwalkan frame berikutnya selama bar masih bergerak (animasi 60 fps),
        // atau tetap menggambar selagi musik jalan agar selalu reaktif.
        animating = sessionActive && stillMoving
        if (animating) postInvalidateOnAnimation()
    }
}
