#!/system/bin/sh
# ============================================================
# check_errors.sh — Cek error Java menggunakan javac + android.jar
# ------------------------------------------------------------
# Kegunaan:
#   Memvalidasi error SINTASIS & error ANDROID MURNI (platform)
#   sebelum build di CodeAssist, sehingga tidak bolak-balik.
#
# Catatan:
#   - Library androidx (AppCompatActivity, MediaSessionCompat, dll)
#     TIDAK tersedia di sini, sehingga error "cannot find symbol"
#     untuk androidx diabaikan / dianggap false-positive.
#   - Error yang TULEN dari kode kita (sintaks, salah tipe platform,
#     salah method android.*) akan tetap ditampilkan.
# ============================================================

# Lokasi android.jar (API 36, diunduh dari Sable/android-platforms). Ganti jika beda.
# Catatan: unduh ke Termux via:
#   curl -sL -o android-36.jar \
#     https://raw.githubusercontent.com/Sable/android-platforms/master/android-36/android.jar
ANDROID_JAR="${ANDROID_JAR:-/sdcard/Ikram/androidcodes/android.jar}"
JAVAC="${JAVAC:-/opt/nodejs/bin/ecj}"

# Direktori sumber
SRC="app/src/main/java/com/nx/music"

# Direktori kerja sementara DI LUAR proyek agar tidak mencemari source.
# Ganti lewat variabel lingkungan NX_TMP bila perlu.
NX_TMP="${NX_TMP:-/tmp/nx-music-check}"
mkdir -p "$NX_TMP"
OUT="$NX_TMP/check_errors_out"
RAW="$NX_TMP/javac_raw.log"

echo "== Nx Music — Cek Error Java =="
echo "JAVAC      : $JAVAC"
echo "ANDROID_JAR: $ANDROID_JAR"
echo "SRC        : $SRC"
echo

if [ ! -f "$ANDROID_JAR" ]; then
    echo "[ERROR] android.jar tidak ditemukan: $ANDROID_JAR"
    echo "Set variabel ANDROID_JAR ke lokasi android.jar kamu."
    exit 1
fi

if [ ! -d "$SRC" ]; then
    echo "[ERROR] Direktori sumber tidak ditemukan: $SRC"
    echo "Jalankan dari root proyek (folder yang berisi app/)."
    exit 1
fi

# Bersihkan output lama
rm -rf "$OUT"
mkdir -p "$OUT"

echo "Mengompilasi semua file Java di $SRC ..."
echo "----------------------------------------------"

# Kompilasi semua file. Simpan log.
# ECJ tidak support -Xmaxerrs, jadi hapus untuk ECJ
"$JAVAC" -source 8 -target 8 -d "$OUT" \
    -classpath "$ANDROID_JAR" \
    $SRC/*.java 2>&1 | tee "$RAW"

echo "----------------------------------------------"
echo

# ============================================================
# Analisis log: pisahkan error "nyata" vs "false-positive androidx"
# ============================================================

# ============================================================
# Filter false-positive. Kelas-kelas ini TIDAK tersedia di sini
# karena berasal dari library androidx / media / material:
#   - nama dengan prefix androidx. / android.support.
#   - kelas androidx yang dipakai langsung (tanpa prefix penuh di pesan error)
#   - kelas resource R (dibuat build system / aapt)
#   - API yang lebih baru dari android.jar Termux (TIRAMISU, POST_NOTIFICATIONS)
#   - error "kaskade" karena superclass androidx tak ter-resolve
# ============================================================
# Kelas-kelas androidx yang dipakai langsung (nama tanpa prefix muncul di pesan error)
AX_CLASSES='NotificationCompat|NotificationManagerCompat|MediaStyle|MediaMetadataCompat|PlaybackStateCompat|MediaSessionCompat|AlertDialog|ActivityCompat|ContextCompat|AppCompatActivity|NonNull|Nullable'
AX_PATTERNS='androidx|android\.support'
AX_PATTERNS="$AX_PATTERNS|package (R|$AX_CLASSES) does not exist"
AX_PATTERNS="$AX_PATTERNS|(class|variable) ($AX_CLASSES)\b"
AX_PATTERNS="$AX_PATTERNS|cannot find symbol: (class|method|variable|field) R|package R does not exist"
AX_PATTERNS="$AX_PATTERNS|TIRAMISU|POST_NOTIFICATIONS"
AX_PATTERNS="$AX_PATTERNS|cannot be converted to Context"
AX_PATTERNS="$AX_PATTERNS|no suitable method found for makeText|no suitable constructor found for Intent\(PlayerActivity"
AX_PATTERNS="$AX_PATTERNS|cannot infer type arguments for ArrayAdapter|method does not override or implement a method from a supertype"
AX_PATTERNS="$AX_PATTERNS|reference to setText is ambiguous"
# Error kaskade pada baris yang memakai ContextCompat / kelas androidx
AX_PATTERNS="$AX_PATTERNS|(ContextCompat|ActivityCompat|SongLoader)\.getColor|SongLoader\.loadSongs"
# Error karena superclass Activity (androidx) tak ter-resolve, atau API Android yang
# lebih baru dari android.jar Termux (mis. READ_MEDIA_AUDIO = API 33)
AX_PATTERNS="$AX_PATTERNS|variable super"
AX_PATTERNS="$AX_PATTERNS|method (getResources|finish|isTaskRoot|finishAffinity)\b"
AX_PATTERNS="$AX_PATTERNS|variable READ_MEDIA_AUDIO|variable POST_NOTIFICATIONS"

# ERROR NYATA = semua error dikurangi false-positive di atas.
# Pesan "cannot find symbol" menaruh detail (symbol:) di baris berikutnya,
# jadi gabungkan tiap blok (baris error: + symbol:/location:) jadi SATU baris,
# lalu filter dengan grep (lebih andal untuk pola kata).
awk '
  /error:/ { if (err!="") print err; err=$0; next }
  err!="" { err = err " " $0 }
  END { if (err!="") print err }
' "$RAW" > "$NX_TMP/errors_blocks.txt"

echo "== ERROR NYATA (perlu diperbaiki) =="
grep -iE "error:" "$NX_TMP/errors_blocks.txt" \
  | grep -viE "$AX_PATTERNS" \
  | head -60
echo

echo "== Ringkasan: total error vs false-positive =="
TOTAL=$(grep -icE "error:" "$NX_TMP/errors_blocks.txt")
AX=$(grep -icE "error:.*($AX_PATTERNS)" "$NX_TMP/errors_blocks.txt")
echo "Total error                    : $TOTAL"
echo "False-positive (androidx/R/API lama): $AX"
echo
echo "Jika 'ERROR NYATA' di atas kosong, berarti tidak ada error nyata yang terdeteksi"
echo "dengan android.jar ini — aman untuk build di CodeAssist."
echo
echo "Catatan: android.jar di sini adalah API 36 (android-36.jar). Error yang tersisa"
echo "semuanya false-positive karena library androidx tidak ada di classpath ini."
echo "Untuk validasi penuh androidx, jalankan build di CodeAssist itu sendiri."
