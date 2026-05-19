# NewsIN

NewsIN adalah aplikasi Android market/news bergaya Investing.com dengan tampilan dark, market feed real, halaman detail instrumen interaktif, berita terbaru, watchlist, dan WarrenAI.

## Fitur

- Splash screen dengan animasi masuk.
- Bottom navigation: Pasar, Berita, Ide, Watchlist, Lainnya.
- Halaman Pasar bergaya list padat seperti Investing.com.
- Detail instrumen pasar dengan chart harga real, pilihan rentang waktu, dan tab Ikhtisar, Teknikal, Berita, Analisis, Data.
- Statistik instrumen: Bid/Ask, rentang harian, rentang 52 minggu, close sebelumnya, pembukaan, volume 24 jam, market cap, peringkat, dan supply beredar.
- Tombol tampilkan lebih banyak untuk membuka data market lanjutan.
- Berita terbaru dengan halaman detail, gambar, share, sumber asli, dan konteks untuk ditanyakan ke AI.
- Watchlist aset dengan mode edit dan picker aset.
- WarrenAI chat dengan respons berbasis data market/headline yang berhasil dimuat.
- UI dark theme dengan aksen orange.

## Sumber Data

Aplikasi memakai public market/news feed:

- CoinGecko untuk market crypto, chart history, dan statistik market detail.
- Coinbase public order book untuk Bid/Ask crypto jika pair tersedia.
- Currency feed untuk forex, emas, dan perak.
- Spaceflight News untuk headline berita terbaru.

Jika feed tidak tersedia atau koneksi gagal, aplikasi menampilkan state loading/error/retry dengan fallback data yang sudah berhasil dimuat.

## Stack

- Kotlin
- Android Views programmatic UI
- Material Components
- Clean Architecture ringan: `data`, `domain`, `presentation`
- Custom `SparklineView` untuk chart
- `HttpURLConnection` + `org.json` untuk integrasi feed tanpa dependency networking tambahan

## Struktur Project

```text
app/src/main/java/id/rahmat/newsin/
├── data/
│   ├── api/
│   └── repository/
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
├── presentation/
│   └── components/
├── MainActivity.kt
└── NewsInApplication.kt
```

## Build

Buka project di Android Studio, lalu jalankan:

```bash
./gradlew :app:assembleDebug
```

## AI Chat

AI online bersifat opsional. Salin `.env.example` menjadi `.env`, lalu isi Groq API key dari `https://console.groq.com/keys`:

```env
AI_API_KEY=isi_api_key_groq
AI_BASE_URL=https://api.groq.com/openai/v1/chat/completions
AI_MODEL=llama-3.1-8b-instant
```

Build ulang aplikasi setelah mengubah `.env`. Jika `AI_API_KEY` kosong, halaman AI tetap berjalan dengan analisis lokal berbasis market feed dan berita yang berhasil dimuat.

APK debug akan dibuat di:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Catatan

Project ini masih memakai UI programmatic agar cepat stabil di project Android awal. Integrasi Retrofit, Room, Paging, dan chart library eksternal bisa ditambahkan pada iterasi berikutnya.
