# TeleRelay

[![CI](https://github.com/eneskaradeniz/telerelay/actions/workflows/ci.yml/badge.svg)](./.github/workflows/ci.yml)
[English README](README.md)

TeleRelay, arka planda sessizce çalışıp **gelen SMS'leri ve telefon aramalarını
kendi Telegram botunuza** ileten hafif, açık kaynak bir Android uygulamasıdır —
cebinizdeki telefon, gün boyu açık duran Telegram penceresinde görünür olur.

## Neden TeleRelay

- **Yerel odaklı.** Mesajlar telefonunuzdan `api.telegram.org`'a gider, başka
  hiçbir yere. Ara sunucu yok, hesap yok, analitik yok, üçüncü parti SDK yok.
  Doğrulaması kolay: tüm ağ kodu küçük tek bir Retrofit arayüzüdür.
- **Tasarımı gereği minimal.** Tek ekran, üç katmanlı sade clean architecture
  ve cihazda ayarlar dışında hiçbir şey saklamayan bir bellek modeli.
- **Kendisine bile karşı gizli.** Opsiyonel gizlilik filtresi OTP/2FA kodlarını
  ve banka işlem SMS'lerini tespit eder; telefonu terk etmeden önce ya sırları
  maskseler (`482913` → `••••••`) ya da mesajı tamamen düşürür.
- **Şeffaf güvenilirlik.** SMS iletme, uygulama süreci ölmüş olsa bile çalışır
  (sistem broadcast'i süreci uyandırır). Arama izleme, düşük öncelikli kalıcı
  bir bildirimin arkasında çalışır ve cihaz yeniden başlayınca kendiliğinden
  ayağa kalkar.

## Özellikler

| Özellik | Ayrıntı |
|---|---|
| SMS iletme | Uzun (çok parçalı) mesajlar tek Telegram mesajında birleştirilir; çift SIM'de SIM bilgisi eklenir |
| Gelen aramalar | Telefon çaldığı anda "kim arıyor" bildirimi |
| Cevapsız aramalar | Cevapsız kalan çağrılar için ayrı `☎️` bildirimi |
| Gizlilik filtresi | OTP ve banka mesajları için (düzenlenebilir) regex filtresi — maskele ya da hariç tut |
| Numara filtresi | Seçtiğiniz numaralardan gelenler asla iletilmez |
| Yeniden deneme kuyruğu | Başarısız gönderimler WorkManager'da exponential backoff ile bekletilir; Telegram'ın `retry_after`'ına uyulur |
| Diller | Türkçe ve İngilizce, uygulama içinden değiştirilir |
| Pil | Pil optimizasyonu istisnası için tek dokunuşluk istek |

Mesaj formatı:

```
📩 Yeni SMS
Kimden: +90 555 000 11 22
Zaman: 14:03:22
SIM: SIM1 (Vodafone)
Mesaj: Merhaba dünya
```

## Kurulum

### 1. Telegram botunuzu oluşturun

1. Telegram'da [@BotFather](https://t.me/BotFather) ile konuşun.
2. `/newbot` gönderin; bir ad ve kullanıcı adı seçin.
3. BotFather `123456789:AAH…` biçiminde bir **token** döndürür — bu sizin
   **bot token**'ınızdır.

### 2. Chat ID'nizi alın

1. Yeni botunuza herhangi bir mesaj gönderin (**Start**'a basın). Önce siz
   yazmadan bot size mesaj atamaz.
2. Tarayıcıda `https://api.telegram.org/bot<TOKEN>/getUpdates` adresini açın;
   `"chat":{"id":123456789,…}` değerindeki sayı sizin **chat ID**'nizdir.

### 3. TeleRelay'i yapılandırın

Uygulamayı kurun, **bot token** ve **chat ID** girin, **Test mesajı gönder**'e
basın. Hazır — istenen izinleri verin, röle çalışmaya başlar.

### Kurulum (side-load)

- APK'yı [Releases](../../releases) sayfasından indirip kurun (ya da aşağıda
  anlatıldığı gibi kendiniz derleyin).
- **Kurduktan sonra uygulamayı bir kez açın.** Android, boot-tamamlandı
  sinyalini yalnızca açılmış uygulamalara iletir; TeleRelay'in cihaz
  yeniden başladığında arama izlemeyi geri getirmesi buna dayanır.
- Xiaomi / Huawei / Oppo / Samsung cihazlarda ayrıca pil optimizasyonundan
  muaf tutun (uygulama içindeki buton) ve OEM'in "otomatik başlatma" yöneticisi
  varsa TeleRelay'e izin verin. Röle uygulamalarının "çalışmayı durdurmasının"
  nedeni genellikle Android değil, OEM'lerin görev öldürücüleridir.

## Derleme

```bash
git clone https://github.com/eneskaradeniz/telerelay.git
cd telerelay
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # birim testler
```

Gereksinimler: JDK 17+ ve platform 37 içeren bir Android SDK.

## İzinler — ve tam olarak neden

| İzin | Gerekçe |
|---|---|
| `RECEIVE_SMS`, `READ_SMS` | Gelen SMS broadcast'ini almak ve içeriğini okuyup iletmek için |
| `READ_PHONE_STATE` | Çalma/cevapsız durumunu dinlemek; çift SIM'de SIM bilgisini okumak için |
| `READ_CALL_LOG` | Android 12+ üzerinde sistem arayan numarasını arama olayında gizler. TeleRelay yalnızca *güncel* aramanın numarasına geçici olarak bakar; arama geçmişi saklanmaz, iletilmez |
| `READ_PHONE_NUMBERS` | Opsiyonel SIM kimliği (`SIM: SIM1 (Vodafone)`) |
| `POST_NOTIFICATIONS` | Arama izlemeyi canlı tutan kalıcı bildirim |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Ön plan servis tipi; süresiz bir arka plan rölesi için tasarlanan tek tip `specialUse`'tur (diğerleri çalışma sınırı taşır ya da medya/telelofi semantiği ister) |
| `RECEIVE_BOOT_COMPLETED` | Cihaz yeniden başladığında arama izlemeyi geri getirmek için |

## Gizlilik

- SMS içerikleri ve arayan numaraları, **yalnızca** kendi bot token'ınızla
  `api.telegram.org`'a gider — yani **size ait** bir sohbete.
- Bot token ve chat ID, Android Keystore'daki (dışa aktarılamaz) AES-GCM
  anahtarıyla diskte şifreli tutulur ve yedeklerden hariç tutulur.
- Analitik yok, çökme raporlama yok, reklam yok, izleme yok, ek sunucu yok.
- WorkManager yeniden deneme yükleri de şifrelidir — mesaj içeriği asla düz
  metin olarak diske yazılmaz.
- Gizlilik filtresi (maskele/hariç tut), hiçbir şey cihazdan çıkmadan önce
  çalışır.

## Mimari

İnce clean architecture; receiver/servis/worker, use case'e delege eden ince
giriş noktalarıdır:

```
domain/   modeller, portlar (arayüzler), saf Kotlin mantık + use case'ler  ← birim testli
data/     Retrofit gateway, Keystore kripto, ayar deposu, regex filtresi,
          telephony monitörleri, WorkManager sender
ui/       Compose (Material 3) ayarlar ekranı + ViewModel
receiver/ service/ worker/  ince Android giriş noktaları
```

Dikkat çeken tasarım kararları: `specialUse` ön plan servis tipi (`dataSync`
tipi Android 15'ten itibaren günde 6 saatte öldürülüyor); çok parçalı SMS
birleştirme, gönderen başına sessiz pencere ile yapılıyor (birleştirme
başlıkları varsayılan SMS uygulaması olmayanlara açılmıyor); API 31+ üzerinde
arayan numarası çağrı kaydından çözümleniyor çünkü platform artık bunu arama
durumu geri çağrısında vermiyor.

## Sınırlamalar

- Yalnızca çok parçalı bölünmüş mesajlarda içerik ~5 sn'ye kadar ek gecikmeyle
  gelir (birleştirme penceresi); tek parça mesajlar anlıktır.
- Android 12+ üzerinde çağrı kaydı satırı henüz yazılmamışsa arayan numarası
  `Arayan: Bilinmiyor` görünebilir.
- TeleRelay MMS göremez ve **giden** mesajları asla okumaz/iletmez.

## Lisans

[MIT](LICENSE)
