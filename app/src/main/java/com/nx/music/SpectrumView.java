package com.nx.music;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.audiofx.Visualizer;
import android.util.AttributeSet;
import android.view.View;

/**
 * Bar spectrum real-time dari output audio pemutar via {@link Visualizer}.
 *
 * <p>Pemetaan bar memakai <b>frekuensi nyata (Hz)</b>, bukan indeks bin
 * ternormalisasi, sehingga bass, vokal, dan treble punya bar sendiri dan
 * spectrum mengikuti nada lagu.</p>
 *
 * <ul>
 *   <li>{@link #F_MIN}/{@link #F_MAX} = rentang frekuensi yang dipetakan,
 *       batas bawahnya dinaikkan otomatis bila resolusi bin terlalu kasar.</li>
 *   <li>Tiap bar menerima minimal satu bin unik (tanpa tumpang-tindih).</li>
 *   <li>Bar lebar (treble) memakai sub-band peak, bukan rata-rata, agar tetap
 *       reaktif walau energinya tersebar.</li>
 *   <li>{@link #barWeight} menyetarakan bass/treble — energi musik menurun
 *       mengikuti 1/f sehingga treble perlu dinaikkan.</li>
 * </ul>
 *
 * <p>Butuh izin {@code RECORD_AUDIO}. Jika izin tak ada atau Visualizer gagal
 * dibuat, view menggambar bar diam (idle) dan tidak crash.</p>
 */
public class SpectrumView extends View {

    private static final int BARS = 28;

    /** Rentang frekuensi yang dipetakan ke bar. */
    private static final float F_MIN = 100f;    // bawah: bass
    private static final float F_MAX = 8000f;   // atas: treble

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] levels = new float[BARS];   // level target 0..1 per bar
    private final float[] shown = new float[BARS];    // level tergambar (dianimasikan)
    /** Buffer FFT bersama: ditulis thread Visualizer, dibaca onDraw. */
    private final float[] mags = new float[BARS];
    /** true saat onDraw menjadwalkan frame sendiri (animasi 60 fps). */
    private volatile boolean animating = false;
    /** Timestamp frame terakhir untuk smoothing berbasis waktu (ns). */
    private long lastFrameNs = 0L;

    /** Batas bin per bar, dihitung ulang saat tahu sample rate. */
    private final int[] binLo = new int[BARS];
    private final int[] binHi = new int[BARS];
    private boolean binsReady = false;

    /** Selubung puncak lintas-frame untuk normalisasi dinamis (lihat normalize). */
    private float peakEnv = 1f;

    private Visualizer visualizer;
    private float barColor = 0xFF5B6F99;
    private boolean running = false;

    public SpectrumView(Context c) { super(c); init(); }
    public SpectrumView(Context c, AttributeSet a) { super(c, a); init(); }
    public SpectrumView(Context c, AttributeSet a, int d) { super(c, a, d); init(); }

    private void init() {
        barPaint.setStyle(Paint.Style.FILL);
        barPaint.setColor((int) barColor);
    }

    public void setBarColor(int color) {
        this.barColor = color;
        barPaint.setColor(color);
        invalidate();
    }

    /** Mulai menempel Visualizer ke audio session. Panggil saat session berubah/resume. */
    public void start(int sessionId) {
        stop();
        if (sessionId <= 0) return;
        try {
            visualizer = new Visualizer(sessionId);
            // Resolusi frekuensi = sampleRate / captureSize. Banyak perangkat
            // melaporkan getCaptureSizeRange()[1] hanya 512 (binHz ~86 Hz) ->
            // bar bass jadi 1 bin tiap bar, saling ikut. Coba ukuran yang lebih
            // besar dulu, mundur bila perangkat menolak.
            final int capSize = pickCaptureSize(visualizer);

            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer v, byte[] w, int rate) { }

                @Override
                public void onFftDataCapture(Visualizer v, byte[] fft, int rate) {
                    // Hitung di thread Visualizer (bukan UI), tulis ke field mags
                    // bersama -> nol alokasi & nol lambda per frame.
                    fftMagnitudes(fft);
                    // Hanya tendang animasi saat idle; selagi onDraw ber-momentum,
                    // frame berikutnya dijadwalkan sendiri oleh onDraw.
                    if (!animating) postInvalidateOnAnimation();
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true);

            // PENTING: getSamplingRate() baru valid SETELAH setEnabled(true).
            // Kalau dipanggil sebelum, hasilnya sampah (pernah terlihat 48000000
            // -> binHz raksasa -> semua bar jatuh di bin 0-1 -> semua bar identik).
            visualizer.setEnabled(true);

            float sampleRate = visualizer.getSamplingRate();
            // Validasi: di luar rentang wajar = ambil default, jangan sampai
            // binHz rusak lagi. 44100 aman untuk hampir semua perangkat.
            if (!(sampleRate >= 8000f && sampleRate <= 192000f)) sampleRate = 44100f;
            prepareBins(capSize, sampleRate);

            running = true;
        } catch (Exception e) {
            visualizer = null;
            running = false;
        }
        invalidate();
    }

    /**
     * Pilih capture size terbesar yang diterima perangkat. Semakin besar,
     * semakin halus resolusi frekuensi (binHz kecil) sehingga bar bass tidak
     * jatuh di satu bin yang sama.
     */
    private static int pickCaptureSize(Visualizer v) {
        int max = Visualizer.getCaptureSizeRange()[1];
        // coba dari besar ke kecil. PENTING: sebagian perangkat TIDAK melempar
        // saat menolak; ia diam-diam memakai nilai lain. Jadi selalu baca ulang
        // getCaptureSize() untuk memastikan, bukan percaya nilai yang diminta.
        int[] tries = {4096, 2048, 1024, max};
        for (int cs : tries) {
            if (cs <= 0 || (cs & 1) != 0) continue;
            try {
                v.setCaptureSize(cs);
                int got = v.getCaptureSize();
                if (got == cs) return cs;        // benar-benar dipakai
            } catch (Exception ignored) { }
        }
        try { v.setCaptureSize(max); } catch (Exception ignored) { }
        try { return v.getCaptureSize(); } catch (Exception ignored) { }
        return max;
    }

    public boolean isRunning() { return running; }

    /** Lepaskan Visualizer. Panggil di onPause. */
    public void stop() {
        running = false;
        animating = false;
        lastFrameNs = 0L;
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                visualizer.release();
            } catch (Exception ignored) { }
            visualizer = null;
        }
        for (int i = 0; i < BARS; i++) levels[i] = 0f;
        invalidate();
    }

    /**
     * Hitung batas bin FFT tiap bar dari frekuensi nyata.
     * Bar b mewakili rentang [F_MIN, F_MAX] terdistribusi logaritmis.
     */
    private void prepareBins(int captureSize, float sampleRate) {
        int bins = captureSize / 2;              // jumlah bin magnitude
        if (bins <= 1 || sampleRate <= 0) { binsReady = false; return; }
        float nyquist = sampleRate / 2f;
        float binHz = nyquist / bins;            // resolusi tiap bin dalam Hz

        // Resolusi bin menentukan frekuensi terendah yang MASIH bisa dipisah.
        // Kalau F_MIN lebih rendah dari ~2 bin, beberapa bar pertama jatuh di bin
        // yang sama -> bar duplikat -> "semua bar sama". Jadi naikkan F_MIN dinamis
        // agar bar terendah punya >=2 bin.
        float fMin = Math.max(F_MIN, binHz * 2f);
        float fMax = Math.min(F_MAX, nyquist * 0.9f);
        if (fMax <= fMin * 1.5f) { binsReady = false; return; }

        double logMin = Math.log(fMin), logMax = Math.log(fMax);
        int prevHi = -1;
        for (int b = 0; b < BARS; b++) {
            float f0 = (float) Math.exp(logMin + (logMax - logMin) * b / BARS);
            float f1 = (float) Math.exp(logMin + (logMax - logMin) * (b + 1) / BARS);
            int lo = clampBin((int) (f0 / binHz), bins);
            int hi = clampBin((int) (f1 / binHz), bins);
            // Jamin monoton & tak tumpang-tindih: tiap bar minimal 1 bin baru.
            if (lo < prevHi) lo = prevHi;
            if (hi <= lo) hi = Math.min(bins, lo + 1);
            binLo[b] = lo;
            binHi[b] = hi;
            prevHi = hi;
        }
        binsReady = true;
    }

    private static int clampBin(int b, int bins) {
        if (b < 0) return 0;
        return Math.min(b, bins);
    }

    /** FFT byte -> magnitudo 0..1 per bar, memakai rentang frekuensi nyata.
     *  Menulis ke {@link #mags} bersama agar tidak mengalokasi array per frame. */
    private void fftMagnitudes(byte[] fft) {
        final float[] mags = this.mags;
        int n = fft.length / 2;
        if (n <= 1) return;

        // Fallback bila sample rate belum diketahui: pemetaan log atas n bin.
        if (!binsReady) {
            for (int b = 0; b < BARS; b++) {
                int lo = (int) (n * Math.pow((double) b / BARS, 1.7));
                int hi = (int) (n * Math.pow((double) (b + 1) / BARS, 1.7));
                if (hi <= lo) hi = lo + 1;
                viaAvg(mags, b, lo, hi, fft, n);
            }
            normalize(mags);
            return;
        }

        for (int b = 0; b < BARS; b++) viaAvg(mags, b, binLo[b], binHi[b], fft, n);
        normalize(mags);
    }

    /**
     * Rata-rata magnitudo bin [lo, hi) -> mags[b] DENGAN BOBOT, belum diskala.
     * Nilai mentah (bisa jauh > 1) karena normalisasi dilakukan lintas bar di
     * {@link #fftMagnitudes} setelah puncak diketahui.
     */
    private void viaAvg(float[] mags, int b, int lo, int hi, byte[] fft, int n) {
        if (hi > n) hi = n;
        if (hi <= lo) { mags[b] = 0f; return; }
        int width = hi - lo;
        float val;
        if (width <= 8) {
            // bar sempit (bass/mid): rata-rata. Bin sedikit, rata-rata sudah peka.
            float sum = 0;
            for (int k = lo; k < hi; k++) sum += magnitude(fft, k);
            val = sum / width;
        } else {
            // bar lebar (treble): bagi jadi ~8 sub-band, ambil RATA-RATA DARI PUNCAK
            // tiap sub-band. Rata-rata seluruh bin membuat treble nyaris diam karena
            // energi tersebar; sub-band max membuat bar treble tetap reaktif.
            int subs = 8;
            float acc = 0;
            for (int sg = 0; sg < subs; sg++) {
                int slo = lo + (int) ((long) width * sg / subs);
                int shi = lo + (int) ((long) width * (sg + 1) / subs);
                if (shi <= slo) shi = slo + 1;
                if (shi > hi) shi = hi;
                float peak = 0;
                for (int k = slo; k < shi; k++) {
                    float m = magnitude(fft, k);
                    if (m > peak) peak = m;
                }
                acc += peak;
            }
            val = acc / subs;
        }
        mags[b] = val * barWeight(b);
    }

    private static float magnitude(byte[] fft, int k) {
        int re = fft[2 * k];
        int im = fft[2 * k + 1];
        return (float) Math.hypot(re, im);
    }

    /** Bobot per bar: ujung bass & treble dinaikkan, tengah diredam. */
    private static float barWeight(int b) {
        float t = (float) b / (BARS - 1);        // 0 bass .. 1 treble
        // Data nyata (dump Download/NxSpectrum*.txt), diukur berulang:
        //   boost 1+t^1.8*2.2 -> rasio std bass/treble 2.54x (masih timpang)
        //   boost idaman (target/std) di ujung treble 5.5-6.4x
        // Dipakai 1+t^2*5.0 -> ujung treble ~6x, tengah ~2.3x.
        float boost = 1.0f + t * t * 5.0f;
        return boost;
    }

    /**
     * Normalisasi lintas bar: bagi dengan puncak frame ini, lalu skala ke tinggi
     * pas. Inilah yang membuat tiap bar BEDA: bar yang lebih kuat tampak lebih
     * tinggi, bukan semua mentok langit-langit karena divisor tetap.
     *
     * <p>Puncak dihaluskan ({@link #peakEnv}) supaya tinggi bar tidak berkedip
     * saat puncak berpindah antar bar.</p>
     */
    private void normalize(float[] mags) {
        float peak = 0f, sum = 0f;
        for (int b = 0; b < BARS; b++) {
            if (mags[b] > peak) peak = mags[b];
            sum += mags[b];
        }
        float mean = sum / BARS;
        // puncak efektif = gabungan puncak & rata-rata (puncak tunggal terlalu
        // dominan bila satu bass drum memukul; campuran ini lebih stabil).
        float ref = Math.max(peak * 0.85f, mean * 2.2f);
        if (ref < 1e-3f) ref = 1e-3f;
        // AGC lambat: ikuti puncak dengan attack cepat, release lambat.
        peakEnv = ref > peakEnv ? peakEnv * 0.5f + ref * 0.5f
                                : peakEnv * 0.85f + ref * 0.15f;
        if (peakEnv < 1e-3f) peakEnv = 1e-3f;
        for (int b = 0; b < BARS; b++) {
            float v = mags[b] / peakEnv;
            // kurva pangkat ringan -> bar menengah tetap kelihatan bedanya
            v = (float) Math.pow(Math.min(1f, v), 0.7);
            // noise gate lembut: nilai sangat kecil (sisa treble lemah) dinaikkan
            // sedikit supaya bar tidak "mati" total, tanpa membuat semua bar rata.
            v = Math.max(v, 0.05f + 0.10f * v);
            mags[b] = Math.min(1f, v);
        }
    }

    /** Salin magnitudo terbaru ke level dengan attack instan / release cepat. */
    private void applyMagnitudes() {
        for (int i = 0; i < BARS; i++) {
            float target = mags[i];
            levels[i] = target > levels[i]
                    ? target
                    : levels[i] * 0.55f + target * 0.45f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (running) applyMagnitudes();

        // Smoothing TIME-BASED: kecepatan px/detik, bukan fraksi per frame.
        // Dulu 0.8 per frame -> terlihat "loncat" kalau frame terlewat (FFT cuma
        // ~10-20 Hz). Sekarang interpolasi tetap sama cepat di 60/90/120 Hz.
        long now = System.nanoTime();
        float dt = lastFrameNs == 0L ? 0.016f : (now - lastFrameNs) / 1e9f;
        lastFrameNs = now;
        if (dt > 0.05f) dt = 0.05f;              // buang lonjakan (GC / frame drop)
        float follow = Math.min(1f, dt * 18f);    // ~55 ms ke target, konstan
        float decay  = Math.min(1f, dt * 12f);    // bar turun lebih lembut dari naik

        float slot = (float) w / BARS;
        float gap = Math.max(1f, slot * 0.12f); // gap tipis -> bar tampak lebih kecil
        float barW = Math.max(2f, slot - gap);

        boolean stillMoving = false;
        for (int i = 0; i < BARS; i++) {
            float target = levels[i];
            float cur = shown[i];
            float k = target > cur ? follow : decay;   // attack cepat, release lembut
            cur += (target - cur) * k;
            if (Math.abs(target - cur) < 0.002f) cur = target;
            else stillMoving = true;
            shown[i] = cur;

            float v = Math.max(cur, 0.04f); // bar minimum agar tetap terlihat
            float bh = v * h;
            float left = i * slot + gap / 2f;
            float top = h - bh;
            canvas.drawRoundRect(left, top, left + barW, h, barW / 2f, barW / 2f, barPaint);
        }

        // Jadwalkan frame berikutnya selama bar masih bergerak (animasi 60 fps),
        // atau tetap menggambar selagi musik jalan agar selalu reaktif.
        animating = running && stillMoving;
        if (animating) postInvalidateOnAnimation();
    }
}
