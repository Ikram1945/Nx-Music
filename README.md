<div align="center">

# 🎵 Harmony Melody (Nx Music)

**Pemutar musik lokal untuk Android** — Java 21 + Kotlin, `compileSdk` 36, `minSdk` 26.

Memutar file audio di perangkat lewat `MediaStore`, dengan **background playback**,
notifikasi media, equalizer, efek audio, daftar putar, favorit, statistik pemutaran,
widget layar utama, dan **bar spectrum real-time**.

</div>

---

## ✨ Fitur Utama

### Pemutaran
- 🎧 Putar & jeda musik lokal dari perangkat
- ⏭️ Next / Previous, **Shuffle**, **Repeat** (off → all → one)
- 🔀 **Crossfade** — peralihan antar lagu yang mulus
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
- 🗂️ **Folder** — telusuri lagu per folder di perangkat
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
| Folder | `FolderActivity` | Daftar folder yang berisi lagu |
| Detail Folder | `FolderDetailActivity` | Lagu di dalam satu folder |
| Statistik | `StatisticsActivity` | Statistik & riwayat pemutaran |
| Pengaturan | `SettingsActivity` | Tema, urutan lagu, hapus riwayat |

---

## 🛠️ Teknologi

| Aspek | Keterangan |
|-------|------------|
| Bahasa | **Java 21** + **Kotlin** (K2) |
| UI | `Activity` + layout XML, Material 3 (`Material Components`) |
| Pemutaran | `android.media.MediaPlayer` dalam foreground service |
| MediaSession | `androidx.media` (`MediaSessionCompat`, `MediaStyle`) |
| Build | **ApkBuild** — build system di perangkat, tanpa Gradle (`app/module.toml`) |
| compileSdk / minSdk / targetSdk | 36 / 26 / 36 |
| Persistensi | `SharedPreferences` (playlist, favorit, statistik, pengaturan, efek) |

**Dependensi** (dari `app/module.toml`):

```toml
implementation = [
  "androidx.appcompat:appcompat:1.7.0",
  "com.google.android.material:material:1.12.0",
  "androidx.media3:media3-exoplayer:1.7.0",
  "androidx.media:media:1.7.0",
  "androidx.annotation:annotation:1.9.1",
]
```

---

## 🏗️ Arsitektur

```
app/src/main/
├── AndroidManifest.xml
├── java/com/nx/music/           # Kode sumber Java (27 file)
│   ├── MusicService.java        # Foreground service — pemilik MediaPlayer
│   ├── PlaybackController.java  # Facade/singleton untuk mengontrol service
│   ├── MainActivity.java        # Layar beranda (launcher)
│   ├── PlayerActivity.java      # Layar pemutar
│   ├── SongListActivity.java    # Perpustakaan lagu
│   ├── PlaylistActivity.java    # Daftar putar
│   ├── PlaylistDetailActivity.java
│   ├── FolderActivity.java      # Telusuri per folder
│   ├── FolderDetailActivity.java
│   ├── StatisticsActivity.java  # Statistik & riwayat
│   ├── SettingsActivity.java    # Pengaturan
│   ├── MusicWidgetProvider.java # Widget layar utama
│   ├── SongAdapter.java         # Adapter daftar lagu
│   ├── EqualizerHelper.java     # Wrapper Equalizer
│   ├── AudioEffectsHelper.java  # Wrapper Bass/Virtualizer/Loudness
│   ├── ArtworkLoader.java       # Ekstraktor artwork
│   ├── SpectrumView.java        # Bar FFT real-time
│   └── …                        # listener & helper lain
├── kotlin/                      # Kode sumber Kotlin (9 file)
│   ├── com/nx/music/
│   │   ├── Song.kt                  # Model data lagu
│   │   ├── SongLoader.kt            # Query MediaStore + pengelompokan folder
│   │   ├── PlaylistHelper.kt        # Persistensi playlist
│   │   ├── FavoritesHelper.kt       # Persistensi favorit
│   │   ├── PlaybackStats.kt         # Persistensi statistik & riwayat
│   │   ├── PlaybackHistoryEntry.kt  # Entri riwayat pemutaran
│   │   ├── ThemeHelper.kt           # Manajemen tema kustom
│   │   └── ScheduledRunnable.kt     # Runnable yang menjadwalkan ulang diri
│   └── com/ikram/system/TimeUtils.kt # Format durasi (murni java.*)
└── res/
    ├── layout/                 # Layout semua layar & item
    ├── drawable/               # Ikon & background (vektor + shape)
    ├── values/ values-night/   # strings, colors, themes
    ├── color/ menu/ xml/ mipmap/
```

### Catatan arsitektur penting

1. **State statis di `MusicService`** — `MediaPlayer`, `playOrder`, `currentIndex`,
   `repeatMode`, `shuffle` disimpan sebagai field `static` agar Activity bisa membaca
   sinkron dari proses yang sama.
2. **Audio session** — Equalizer & efek audio terikat ke audio session `MediaPlayer`
   via `getAudioSessionId()`; karena session bisa berubah saat ganti lagu, `attach()`
   dipanggil ulang di `onResume`.
3. **`PlaybackController`** adalah singleton; `create(Context)` memastikan service
   foreground aktif.
4. **Musik tidak dihentikan** di `onDestroy` Activity — pemutaran sepenuhnya dikelola
   `MusicService`.
5. **Kotlin → Java searah saja.** Build system mengompilasi Kotlin **lebih dulu**
   daripada Java, dan classpath Kotlin hanya berisi `R.jar` + library — bukan output
   Java. Karena itu **Kotlin tidak boleh merujuk kelas Java** (mis. `SettingsActivity`).
   Bila butuh nilai dari kelas Java, duplikasi logikanya di Kotlin atau lewatkan
   sebagai parameter.

---

## 🔐 Izin

| Izin | Keperluan |
|------|-----------|
| `READ_MEDIA_AUDIO` | Android 13+ — membaca audio perangkat |
| `READ_EXTERNAL_STORAGE` | Pra-Android 13 — membaca audio |
| `RECORD_AUDIO` | `Visualizer` (bar spectrum) |
| `FOREGROUND_SERVICE` | Menjalankan service pemutaran di latar |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Tipe foreground service media playback |
| `POST_NOTIFICATIONS` | Android 13+ — notifikasi media |

---

## 🚀 Build

Proyek dibangun dengan **ApkBuild** — build system di perangkat, **tanpa Gradle**.
Konfigurasi ada di `app/module.toml` (bukan `build.gradle`).

**Di UI ApkBuild:** buka folder proyek → tekan **Build**.

Hasil build:

```
app/build/output.apk
```

### Cek error sebelum build (opsional)

`check_errors.sh` memvalidasi error Java memakai `javac` + `android.jar` sebelum build:

```sh
sh check_errors.sh
```

> **Catatan:** script di atas hanya memeriksa kode Java. Error Kotlin dan error
> androidx baru muncul saat build di ApkBuild.

### Catatan build

- **Compiler Kotlin:** `kotlinExec = "forkworker"` di `module.toml` menjalankan
  compiler di **VM persisten** (heap terpisah, environment K2 hangat). Build berulang
  tak membayar biaya cold-start.
- **Metadata Kotlin:** compiler K2 2.4 menulis metadata lebih baru daripada yang
  dipahami D8/R8 bawaan. Build system membuang anotasi `@kotlin.Metadata` sebelum
  dexing (lihat `KotlinMetadataStrip`), sebab D8 dapat **menjatuhkan seluruh output
  dex** bila gagal me-rewrite metadata — gejalanya "build gagal tanpa pesan error".
- **Kotlin stdlib tunggal:** proyek ini menyertakan `kotlin-stdlib` sebagai
  dependensi, jadi build system **tidak** menambahkan stdlib bawaan toolchain.
  Dua stdlib beda versi di classpath membuat D8 berhenti dengan
  `Classpath type already present`.

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

## 📄 Lisensi

Belum ada lisensi ditentukan. Gunakan proyek ini sesuai kebutuhan Anda.
