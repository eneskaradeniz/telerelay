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
- **OTP'yi tek dokunuşla kopyala.** Doğrulama kodu algılanan mesajlara Telegram'ın
  yerel "kodu kopyala" butonu eklenir. (iOS/macOS'un sistem çapında SMS otomatik
  doldurması üçüncü parti uygulamalara kapalıdır; en hızlı yol budur.)
- **Şeffaf güvenilirlik.** SMS iletme, uygulama süreci ölmüş olsa bile çalışır
  (sistem broadcast'i süreci uyandırır). Arama izleme, düşük öncelikli kalıcı
  bir bildirimin arkasında çalışır ve cihaz yeniden başlayınca kendiliğinden
  ayağa kalkar.

## Özellikler

| Özellik | Ayrıntı |
|---|---|
| SMS iletme | Uzun (çok parçalı) mesajlar tek Telegram mesajında birleştirilir; kayıtlı kişiler numara yerine adla görünür |
| SIM bilgisi | Yalnızca çok SIM'li cihazlarda ve hangi SIM'in aldığı biliniyorsa (`· SIM2`) |
| Gelen aramalar | Telefon çaldığı anda "kim arıyor" bildirimi; kayıtlıysa kişi adıyla |
| Cevapsız aramalar | Cevapsız kalan çağrılar için ayrı `☎️` bildirimi |
| OTP kopya butonu | Doğrulama kodu algılanınca mesaja Telegram'ın tek dokunuşla kopyalama butonu eklenir |
| Yeniden deneme kuyruğu | Başarısız gönderimler WorkManager'da exponential backoff ile bekletilir; Telegram'ın `retry_after`'ına uyulur |
| Diller | Türkçe ve İngilizce — cihaz dilini izler (varsayılan İngilizce) |
| Pil | Pil optimizasyonu istisnası için tek dokunuşluk istek |

Mesaj formatı — normal bir SMS gibi, zarfsız:

```
📩 E-DEVLET · SIM2
Dogrulama kodunuz : 814067  Bu mesaj e-Devlet Kapisi ...
```

Kayıtlı gönderenler adıyla görünür; kayıtsızlar numarayla. Zaman satırı yok —
Telegram mesajın saatini zaten gösterir. SIM bilgisi tek SIM'li cihazlarda
yer almaz.

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
| `READ_PHONE_STATE` | Çalma/cevapsız durumunu dinlemek; çok SIM'li cihazlarda SIM bilgisini okumak için |
| `READ_CALL_LOG` | Android 12+ üzerinde sistem arayan numarasını arama olayında gizler. TeleRelay yalnızca *güncel* aramanın numarasına geçici olarak bakar; arama geçmişi saklanmaz, iletilmez |
| `READ_CONTACTS` | İletilen mesajlarda numara yerine kişi adını gösterebilmek için. Ad yalnızca cihazda aranır; mesaj dışında hiçbir şey iletilmez |
| `READ_PHONE_NUMBERS` | Opsiyonel SIM kimliği (`· SIM1`) |
| `POST_NOTIFICATIONS` | Arama izlemeyi canlı tutan kalıcı bildirim |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Uygulama içinden pil istisnası diyaloğunu açabilmek için (yalnızca siz butona bastığında kullanılır) |
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
- Kişi adları yalnızca cihazda çözümlenir; kişiler listesi okunur ama hiçbir
  şey saklanmaz ve adın ötesinde hiçbir şey iletilmez.
- Analitik yok, çökme raporlama yok, reklam yok, izleme yok, ek sunucu yok.

## Mimari

İnce clean architecture; receiver/servis/worker, use case'e delege eden ince
giriş noktalarıdır:

```
domain/   modeller, portlar (arayüzler), saf Kotlin mantık + use case'ler  ← birim testli
data/     Retrofit gateway, Keystore kripto, ayar deposu, kişi adı çözümleme,
          telephony monitörleri, WorkManager sender
ui/       Compose (Material 3) ayarlar ekranı + ViewModel
receiver/ service/ worker/  ince Android giriş noktaları
```

Dikkat çeken tasarım kararları: `specialUse` ön plan servis tipi (`dataSync`
tipi Android 15'ten itibaren günde 6 saatte öldürülüyor); çok parçalı SMS
birleştirme, gönderen başına sessiz pencere ile yapılıyor (birleştirme
başlıkları varsayılan SMS uygulaması olmayanlara açılmıyor); API 31+ üzerinde
arayan numarası çağrı kaydından çözümleniyor çünkü platform artık bunu arama
durumu geri çağrısında vermiyor; mesajlar HTML parse mode ile gönderilir ve
algılanan OTP kodları Telegram'ın `copy_text` butonuyla tek dokunuşla
kopyalanabilir.

## Sınırlamalar

- Her SMS ~5 sn'lik birleştirme penceresini bekler: UDH erişimi olmadan
  eksiksiz tek parça mesajı, birleştirilmiş mesajın ilk parçasından ayırt
  etmek mümkün değildir. Yalnızca tek broadcast'te çok PDU ile gelen mesajlar
  anlıktır.
- Android 12+ üzerinde çağrı kaydı satırı henüz yazılmamışsa arayan numarası
  `Bilinmiyor` görünebilir.
- Çok SIM'li cihazlarda arama bildirimleri hangi SIM'den geldiğini göstermez —
  genel API arama olayında SIM vermiyor; SMS'te SIM biliniyorsa gösterilir.
- OTP kopya butonu en olası kodu seçer; nadiren yanlış rakam grubunu yakalayabilir.
- TeleRelay MMS göremez ve **giden** mesajları asla okumaz/iletmez.

## Lisans

[MIT](LICENSE)
