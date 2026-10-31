# OpticalVLC

Tamamen çevrimdışı çalışan optik haberleşme (Li-Fi/VLC) Android uygulaması.
Verici telefonun LED flaşı veriyi ışıkla gönderir, alıcı telefonun arka kamerası
bu ışığı çözerek mesajı alır. İnternet izni yoktur; konuşma tanıma (Vosk) ve
metin okuma (Android TTS) cihaz üzerinde çalışır.

## Özellikler

- Flaş ile Manchester kodlamalı optik verici (2-10 Hz bit hızı).
- Kamera ile batch (burst bazlı) çözücü; CRC-8 doğrulaması.
- Türkçe/İngilizce çevrimdışı konuşma tanıma (paketlenmiş Vosk modelleri).
- Çevrimdışı metin okuma (cihazda yüklü TTS motoru varsa).
- Alıcıda canlı parlak nokta takibi, ROI ve telephoto kamera tercihi.
- Çevrimdışı çalışma garantisi: `INTERNET` izni yok.

## Gereksinimler

- JDK 17+ (geliştirme ortamında JBR 21 kullanıldı).
- Android SDK (compileSdk 34, minSdk 24).
- Gradle wrapper depoya dahildir (Gradle 8.7).

## Derleme

PowerShell:

```powershell
$env:JAVA_HOME="C:\Users\<kullanici>\.jdks\jbr-21.0.11"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleDebug --no-daemon
```

APK: `app/build/outputs/apk/debug/app-debug.apk` (~170 MB, Vosk modelleri dahil).

Birim testleri:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

## Kullanım

1. İki telefona uygulamayı kurun.
2. **Verici** sekmesinde metni girin, kaynak dili seçin, bit hızını ayarlayın
   ve gönderin. Flaş yanıp sönerek veriyi yayınlar.
3. **Alıcı** sekmesinde kamerayı vericinin flaşına doğrultun. Parlak nokta
   ROI içinde kaldığında paket otomatik çözülür.
4. Çözülen mesaj istenirse hedef dilde sesli okunur.

> **Not:** 30 fps kamera ile güvenilir aralık **2-5 Hz**'dir. 5 Hz üzerinde
> kare atlamaları nedeniyle hata olasılığı artar; arayüz de bunu uyarır.

## Protokol

```
[0xAA][LANG 1B][LENGTH 1B][PAYLOAD <=128B UTF-8][CRC-8]
```

- Bitler MSB-first gönderilir.
- Manchester: `0` = yüksek→düşük, `1` = düşük→yüksek.
- CRC-8: polinom `0x07`, başlangıç `0x00`.
- Her bitin ortasında mutlaka bir kenar vardır; alıcı bu kenarları takip
  ederek saat kaymasını sıfırlar.

## Modeller

Vosk modelleri `app/src/main/assets/models/{tr,en}` altında paketlenmiştir
(~124 MB). Çalışma zamanında `StorageService.sync` ile harici dosya alanına
açılır. Modellerin `uuid` dosyası dahildir.

## Sınırlamalar

- Optik hizalama kritiktir: flaş ve kamera aynı hatta olmalı, ortam ışığı
  kontrastı düşürmemelidir.
- Kamera pozlama/odak kilitleri destekleyen cihazlarda daha kararlı çalışır.
- TTS sesinin çevrimdışı olması cihazdaki motorun gömülü ses paketine bağlıdır.
- Donanım üzerinde saha testi yapılmamıştır; testler sentetik kamera izleriyle
  doğrulanmıştır.
