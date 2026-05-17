# NewsIN

NewsIN adalah aplikasi Android market/news bergaya Investing.com dengan tampilan dark, data market real, halaman detail instrumen, berita real, watchlist, dan simulasi WarrenAI.

## Fitur

- Splash screen dengan animasi masuk.
- Bottom navigation: Pasar, Berita, Ide, Watchlist, Lainnya.
- Halaman Pasar bergaya list padat seperti Investing.com.
- Detail instrumen pasar dengan chart besar, Bid/Ask, rentang harian, close sebelumnya, dan pembukaan.
- Berita real dari API publik.
- WarrenAI chat dengan respons berbasis data market/headline yang berhasil dimuat.
- UI dark theme dengan aksen orange.

## Sumber Data

Aplikasi memakai API publik tanpa API key dari daftar `public-apis`:

- CoinGecko API untuk market crypto.
- Currency API untuk forex, emas, dan perak.
- Spaceflight News API untuk headline berita real.

Jika API gagal atau rate limited, aplikasi menampilkan state error/retry.

## Stack

- Kotlin
- Android Views programmatic UI
- Material Components
- Clean Architecture ringan: `data`, `domain`, `presentation`
- Custom `SparklineView` untuk chart

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

APK debug akan dibuat di:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Catatan

Project ini masih memakai UI programmatic agar cepat stabil di project Android awal. Integrasi Retrofit, Room, Paging, dan chart library eksternal bisa ditambahkan pada iterasi berikutnya.
