<div align="center">

# 🎵 Harmony Melody (Nx Music)

**Pemutar musik lokal untuk Android** — dibangun dengan **Java** (Java 21, target API 36).

Memutar file audio yang tersimpan di perangkat lewat `MediaStore`, dengan dukungan **background playback**, notifikasi media, equalizer, efek audio, daftar putar, favorit, statistik pemutaran, widget layar utama, dan **bar spectrum real-time**.

> **Sesi terakhir:** memperbaiki bar spectrum (`SpectrumView`) yang tidak
> mengikuti irama lagu — pemetaan frekuensi salah, bar identik satu sama lain,
> dan treble nyaris diam. Rincian di [📊 Spectrum Bar](#-spectrum-bar-spectrumview).

</div>

---

## ✨ Fitur Utama

### Pemutaran
- 🎧 Putar & jeda musik lokal dari perangkat
- ⏭️ Next / Previous, **Shuffle**, **Repeat** (off → all → one)
- 📊 Seek bar + progress real-time
- 🖼️ Ekstraksi & animasi **artwork album** (rotasi saat diputar + glow berdenyut)
- 👆 **Gesture seek** — swipe kiri/kanan pada artwork untuk maju/mundur 5 detik
- 🔄 **Background playback** via foreground service (musik tetap berputar saat app ditutup)
- 🔔 **Notifikasi media** (MediaStyle) dengan kontrol prev/play-pause/next + artwork
- 🔒 Sinkronisasi lock screen & quick settings via `MediaSessionCompat`
- 🔊 **Audio Focus** — jeda saat panggilan, ducking saat audio transien

### Antrian & Daftar Putar
- 🗂️ **Antrian (Up Next)** — "Putar Berikutnya", "Tambah ke Antrian", hapus & kosongkan
- 📑 **Daftar Putar (Playlist)** — buat, ubah nama, hapus, tambah/hapus lagu, putar seluruh playlist
- 💾 Playlist tersimpan permanen (metadata lagu disimpan, tahan terhadap perubahan library)

### Audio Efek
- 🎛️ **Equalizer** — preset + slider per band + persistensi
- 🔊 **Bass Boost**, **Virtualizer (Surround)**, **Loudness Enhancer** + persistensi

### Pencarian & Navigasi
- 🔍 **Pencarian live** (judul/artis) dengan tombol hapus
- ⭐ **Favorit** — tandai lagu favorit & filter hanya favorit
- 🧭 Navigasi bawah: **Beranda / Perpustakaan / Daftar Putar**
- 🕐 **Sleep Timer** — hentikan pemutaran otomatis setelah 10–120 menit

### Statistik & Personalisasi
- 📈 **Statistik & Riwayat Pemutaran** — total putar, waktu mendengar, lagu teratas, riwayat terbaru (baris dapat diklik untuk memutar)
- 🎨 **Tema kustom** — Default/Biru/Hijau/Ungu/Oranye/Merah
- 🌗 **Mode gelap & terang** — ikuti sistem / paksa terang / paksa gelap
- 🔤 **Urutan daftar lagu** — default / judul (A–Z) / artis / durasi terpendek

### Tambahan
- 🏠 **Widget layar utama** — kontrol prev/play-pause/next + artwork + judul/artis, tersinkron dengan status pemutaran
- 📊 **Spectrum real-time** (`SpectrumView`) — bar FFT dari `Visualizer`, dipetakan ke frekuensi nyata (bass→treble), mengikuti irama lagu

---

## 📱 Layar Aplikasi

| Screen | Activity | Fungsi |
|--------|----------|--------|
| Beranda (launcher) | `MainActivity` | Sambutan, mini-player, daftar lagu & playlist, navigasi bawah |
| Pemutar | `PlayerActivity` | Artwork, kontrol, equalizer, efek audio, sleep timer, antrian |
| Perpustakaan | `SongListActivity` | Daftar lagu + pencarian + filter favorit |
| Daftar Putar | `PlaylistActivity` | Kelola daftar putar |
| Detail Daftar Putar | `PlaylistDetailActivity` | Isi playlist, tambah/hapus lagu, putar |
| Statistik | `StatisticsActivity` | Statistik & riwayat pemutaran |
| Pengaturan | `SettingsActivity` | Tema, urutan lagu, hapus riwayat |

---

## 🛠️ Teknologi

| Aspek | Keterangan |
|-------|------------|
| Bahasa | Java 21 |
| UI | `Activity` + layout XML, Material 3 (`Material Components`) |
| Pemutaran | `android.media.MediaPlayer` dalam foreground service |
| MediaSession | `androidx.media` (`MediaSessionCompat`, `MediaStyle`) |
| Binding | `ViewBinding` |
| Build | Gradle (editor CodeAssist / Tyron) |
| Min / Target SDK | 26 / 34 |
| Persistensi | `SharedPreferences` (playlist, favorit, statistik, pengaturan, efek) |

**Dependensi utama:** `androidx.appcompat`, `androidx.core`, `androidx.media`, `com.google.android.material`.

---

## 🏗️ Arsitektur

```
app/src/main/
├── AndroidManifest.xml
├── java/com/nx/music/          # Kode sumber
│   ├── MusicService.java       # Foreground service — pemilik MediaPlayer
│   ├── PlaybackController.java # Facade/singleton untuk mengontrol service
│   ├── MainActivity.java       # Layar beranda (launcher)
│   ├── PlayerActivity.java     # Layar pemutar
│   ├── SongListActivity.java   # Perpustakaan lagu
│   ├── PlaylistActivity.java   # Daftar putar
│   ├── PlaylistDetailActivity.java
│   ├── StatisticsActivity.java # Statistik & riwayat
│   ├── SettingsActivity.java   # Pengaturan
│   ├── MusicWidgetProvider.java# Widget layar utama
│   ├── Song.java               # Model data lagu
│   ├── SongLoader.java         # Query MediaStore
│   ├── SongAdapter.java        # Adapter daftar lagu
│   ├── PlaylistHelper.java     # Persistensi playlist
│   ├── FavoritesHelper.java    # Persistensi favorit
│   ├── PlaybackStats.java      # Persistensi statistik & riwayat
│   ├── EqualizerHelper.java    # Wrapper Equalizer
│   ├── AudioEffectsHelper.java # Wrapper Bass/Virtualizer/Loudness
│   ├── ThemeHelper.java        # Manajemen tema kustom
│   ├── ArtworkLoader.java      # Ekstraktor artwork
│   ├── ArtworkGestureListener.java # Gesture seek
│   └── … (listener & helper lain)
└── res/
    ├── layout/                 # Layout semua layar & item
    ├── drawable/               # Ikon & background (vektor + shape)
    ├── drawable-night/         # Varian mode gelap
    ├── values/ values-night/   # strings, colors, themes
    ├── color/ menu/ xml/ mipmap/
```

### Catatan arsitektur penting
1. **State statis di `MusicService`** — `MediaPlayer`, `playOrder`, `currentIndex`, `repeatMode`, `shuffle` disimpan sebagai field `static` agar Activity bisa membaca sinkron dari proses yang sama.
2. **Audio session** — Equalizer & efek audio terikat ke audio session `MediaPlayer` via `getAudioSessionId()`; karena session bisa berubah saat ganti lagu, `attach()` dipanggil ulang di `onResume`.
3. **`PlaybackController`** adalah singleton; `create(Context)` memastikan service foreground aktif.
4. **Musik tidak dihentikan** di `onDestroy` Activity — pemutaran sepenuhnya dikelola `MusicService`.

---

## 🔐 Izin

| Izin | Keperluan |
|------|-----------|
| `READ_MEDIA_AUDIO` | Android 13+ — membaca audio perangkat |
| `READ_EXTERNAL_STORAGE` | Pra-Android 13 — membaca audio |
| `FOREGROUND_SERVICE` | Menjalankan service pemutaran di latar |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Tipe foreground service media playback |
| `POST_NOTIFICATIONS` | Android 13+ — notifikasi media |

---

## 🚀 Build

Proyek dikembangkan menggunakan editor **CodeAssist / Tyron** (bukan Android Studio).

```sh
# Build debug APK (via gradle wrapper)
./gradlew assembleDebug
```

APK hasil build berada di `app/build/outputs/apk/`.

### Cek error sebelum build (opsional)

`check_errors.sh` memvalidasi error Java dengan `javac` + `android.jar` sebelum build di CodeAssist:

```sh
sh check_errors.sh
```

> **Catatan:** Error yang tersisa setelah menjalankan script umumnya false-positive karena library androidx tidak ada di classpath (bukan karena kode). Untuk validasi penuh, build langsung di CodeAssist.

---

## 📊 Spectrum Bar (`SpectrumView`)

Bar spectrum di layar Player. Sumber data `android.media.audiofx.Visualizer`
(butuh izin `RECORD_AUDIO`), digambar sebagai 28 bar dari bass ke treble.

### Cara kerja
- **Pemetaan frekuensi nyata** — batas bin tiap bar dihitung dari Hz
  (`F_MIN`..`F_MAX`, logaritmis), bukan indeks bin ternormalisasi.
  `F_MIN` dinaikkan otomatis bila resolusi bin terlalu kasar, agar tiap bar
  menerima bin unik (tanpa tumpang-tindih).
- **Bar lebar (treble)** — dibagi jadi 8 sub-band, diambil puncak tiap
  sub-band lalu dirata-rata. Rata-rata seluruh bin membuat treble nyaris diam.
- **Normalisasi dinamis** — dibagi puncak frame (`peakEnv`, AGC attack cepat /
  release lambat), sehingga bar yang lebih kuat tampak lebih tinggi.
- **`barWeight`** — menyetarakan bass/treble; energi musik menurun ~1/f
  sehingga treble perlu dinaikkan (`1 + t²·5.0`).

### Tiga bug yang ditemukan & diperbaiki (dari dump FFT nyata)
1. **`getSamplingRate()` dipanggil sebelum `setEnabled(true)`** → hasil sampah
   (`48000000`), `binHz` raksasa → semua bar jatuh di bin 0-1 → **16 bar
   identik**. Perbaikan: urutan `setDataCaptureListener` → `setEnabled(true)` →
   `getSamplingRate()`, plus validasi rentang `8000..192000` (fallback `44100`).
2. **Resolusi bin terlalu kasar** (`binHz` ~86 Hz) → beberapa bar pertama
   memakai bin sama → bar duplikat. Perbaikan: `F_MIN = max(100, binHz*2)` +
   paksa batas bin monoton naik.
3. **Treble nyaris diam** — std-dev antar frame bass 27-44 vs treble 3-9.
   Perbaikan: sub-band peak + `barWeight` dinaikkan. Rasio std bass/treble
   membaik dari **5.4× → 1.96×** (setara).

### Tuning cepat
| Gejala | Ubah |
| --- | --- |
| Bar terlalu besar / sedikit | `BARS` (mis. 28 → 36) |
| Gap antar bar | `gap = Math.max(1f, slot * 0.12f)` |
| Treble terlalu lemah | `barWeight`: `1f + t*t*5.0f` (naikkan `5.0f`) |
| Bass terlalu dominan | turunkan `5.0f`, atau naikkan kontribusi bass |

### Catatan
- `Visualizer` memerlukan sesi audio aktif; `start(sessionId)` dipanggil ulang
  di `onResume` karena audio session berubah saat ganti lagu.
- Beberapa perangkat membatasi `captureSize` (dilaporkan hanya 512 → `binHz`
  ~86 Hz). `pickCaptureSize()` mencoba 4096 → 2048 → 1024 dan **memverifikasi**
  hasil lewat `getCaptureSize()` (sebagian perangkat menolak tanpa melempar).

---

## 🗺️ Roadmap

- Navigasi per album / artis
- Tombol Stop / hentikan di player
- Export / import playlist (`.m3u`)


---

## 🧾 Catatan sesi — sapaan, animasi, dan bug pipeline build

### 1. UI baru di PlayerActivity
- Blok **sapaan** (`greeting_card`) + teks penyemangat (`txt_motivation`) + **5 bar equalizer**
  (`bar_pulse_1..5`) di atas artwork.
- **Kartu info** (`info_card`, `txt_info_title`, `txt_info_value`) di atas kontrol utama.
- Sapaan dipilih menurut jam (`greetingForHour`), penyemangat bergiliran tiap 6 detik,
  bar berdenyut `scaleY` dengan durasi/delay berselang-seling.
- Animasi: `playGreetingEntrance()` (fade + slide) dipanggil dari `onCreate` **dan**
  `onResume`; `playTitlePop()` saat lagu berganti.
- **Penting:** `onPause` mematikan bar + rotasi, jadi `onResume` **wajib** menyalakannya
  kembali — kalau tidak, sapaan/bar "mati" setiap kembali dari layar lain.

### 2. UI baru di MainActivity (home)
- `txt_greeting` kini **dinamis** (sebelumnya statis "Selamat Mendengarkan").
- `txt_home_motivation` (bergiliran tiap 7 detik) + 4 bar (`home_bar_1..4`).
- **Jebakan Java:** `homeMotivationRunnable` **harus dideklarasikan setelah** `handler`.
  Field diinisialisasi berurutan; merujuk `handler` di atas deklarasinya menghasilkan
  ECJ: *"Cannot reference a field before it is defined"*.

### 3. Perbaikan pipeline build (`android-code/app/.../build/BuildPipeline.java`)
Semua ini bug nyata yang membuat **build sukses tapi aplikasi tetap crash**:

| # | Bug | Perbaikan |
| --- | --- | --- |
| 1 | `gen/` dihapus di awal stage, lalu `link reuse` early-return tanpa menulis R.java → id baru tak ada di R | `gen/` tidak dihapus di awal; `link reuse` dibatalkan bila `gen/R.java` kosong |
| 2 | `R.class` diekstrak dari `R.jar` **setelah** ECJ → compile inkremental membaca R lama dari `classes/` | ekstraksi dipindah ke **sebelum** ECJ |
| 3 | `rFp` hanya `lastResFp#count` → `R.jar` basi dipertahankan walau `resources.ap_` berubah | `rFp` menyertakan hash `resources.ap_` |
| 4 | Build **gagal** tetap meninggalkan `output.apk` + `.fingerprint` → build berikutnya "sudah terbaru (skip)" memakai APK rusak | saat gagal: buang `output.apk`, `.fingerprint`, `classes/`, `.stage-cache`, `gen/`, `R.jar`, `.rjar-fp` |
| 5 | `aapt2 link -o resources.ap_` menulis **langsung** ke tujuan → gagal di tengah meninggalkan arsc terpotong | link ke file `.new-*` lalu `Files.move` atomic; gagal ⇒ buang `resources.ap_` + `.resap-fp` |
| 6 | `aapt2 compile user` gagal → `user.zip` + `.userfp` lama tertinggal | keduanya dibuang saat gagal |
| 7 | `installApk` pakai `Intent.ACTION_VIEW` dengan `versionCode` tetap 1 → installer sistem menolak senyap, APK lama tetap jalan | coba `pm install -r -d` dulu; installer sistem hanya fallback |

**Yang sengaja TIDAK dibuang saat build gagal:** `flat/user.zip`, `.userfp`,
`resources.ap_`, `.resap-fp` — semuanya hasil stage `resource` yang selesai sebelum
`compileJava`, jadi isinya sah; membuangnya hanya memaksa `aapt2 link` (~9–11 s) jalan lagi.

### 4. Cara memeriksa id/resource dengan benar
Jangan menulis parser ARSC sendiri — mudah salah baca panjang string (UTF-8 vs UTF-16,
byte NUL) dan menghasilkan kesimpulan palsu. Pakai:

```sh
aapt2 dump resources <apk> | grep 'id/list_songs'
aapt2 dump xmltree  <apk> --file res/layout/activity_main.xml
```

Hasil yang benar untuk kasus terakhir:

```
R.id.list_songs  = 0x7f080114   (R.java / R.jar)
id/list_songs    = 0x7f080114   (resources.arsc)
ListView @id     = 0x7f080114   (res/layout/activity_main.xml di APK)
```

Ketiganya cocok ⇒ APK konsisten; kalau aplikasi masih crash, penyebabnya **bukan**
resource/id, melainkan tahap pemasangan (`versionCode` sama + installer sistem).

---

## 📄 Lisensi

Belum ada lisensi ditentukan. Gunakan proyek ini sesuai kebutuhan Anda.
