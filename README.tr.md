# TeleRelay

[![CI](https://github.com/eneskaradeniz/telerelay/actions/workflows/ci.yml/badge.svg)](./.github/workflows/ci.yml)
[English README](README.md)

TeleRelay, **gelen SMS'leri ve telefon aramalarını** Android telefonunuzdan
**kendi Telegram botunuza** iletir — cebinizdeki telefon, gün boyu açık duran
Telegram penceresinde görünür olur.

Yerel odaklı: mesajlar telefonunuzdan `api.telegram.org`'a **kendi** bot
token'ınızla gider, başka hiçbir yere. Sunucu yok, hesap yok, analitik yok,
üçüncü parti SDK yok.

## Kurulum

1. **Bot oluşturun** — Telegram'da [@BotFather](https://t.me/BotFather) ile
   konuşun, `/newbot` gönderin, bir ad seçin. BotFather `123456789:AAH…`
   biçiminde bir **token** döndürür. Kopyalayın.
2. **Chat ID'nizi alın** — yeni botunuza herhangi bir mesaj gönderin
   (**Start**'a basın; bot önce siz yazmadan yazamaz), sonra tarayıcıda
   `https://api.telegram.org/bot<TOKEN>/getUpdates` adresini açın ve
   `"chat":{"id":…}` içindeki sayıyı kopyalayın.
3. **Kurun** — `app-release.apk`'yı [Releases](../../releases) sayfasından
   indirip kurun. Kurduktan sonra uygulamayı **bir kez açın**: Android,
   boot-tamamlandı sinyalini yalnızca açılmış uygulamalara verir; TeleRelay'in
   cihaz yeniden başlayınca kendini geri getirmesi buna dayanır.
4. **Yapılandırın** — token ve chat ID'yi girin, **Test mesajı gönder**'e
   basın. Telegram'a ulaştıysa hazırsınız; izin kartından izinleri verin.
5. **Android 13+ : SMS ve Arama kaydı izinleri, Play dışından kurulan
   uygulamalarda sistem tarafından kapatılır.** İzin veremiyorsanız:
   **Ayarlar → Uygulamalar → TeleRelay → ⋮ menüsü → Kısıtlanmış izinlere izin
   ver**, sonra **SMS** ve **Arama kayıtları**'na izin verin. Bu, tüm
   sideload uygulamalara uygulanan Android'in dolandırıcılık önlemedir —
   TeleRelay'e özgü değildir; uygulama tamamen açık kaynak olduğu için burada
   güvenlidir.

Xiaomi / Huawei / Oppo / Samsung'da ayrıca pil optimizasyonunu kapatın
(uygulama içindeki buton) ve OEM sunuyorsa otomatik başlatmaya izin verin —
röle uygulamalarının "çalışmayı durdurmasının" nedeni genellikle Android
değil, OEM görev öldürücüleridir.

## Ne elde edersiniz

```
📩 E-DEVLET
Dogrulama kodunuz : 814067 …      ← tek dokunuşla Kodu kopyala butonu
📞 Eyüp arıyor
☎️ Cevapsız arama: Eyüp
```

Kayıtlı kişiler adıyla, bilinmeyenler numarayla görünür (kısa kodlar — `2273`
gibi — aynen kalır). Çift SIM'li cihazlarda gönderen satırı mesajın hangi
SIM'den geldiğini gösterir (`· SIM2`). Kopyala butonu yalnızca mesajda
doğrulama kodu ifadesi varsa çıkar — pazarlama SMS'lerinde asla.

---

## Ayrıntılar

### Gizlilik

- SMS içerikleri ve numaralar **yalnızca** `api.telegram.org`'a **kendi** bot
  token'ınızla gider — yani **size ait** bir sohbete.
- Bot token + chat ID diskte şifreli (Android Keystore, AES-GCM,
  dışa aktarılamaz) ve yedeklerden hariç; WorkManager yeniden deneme
  yükleri de şifrelidir — mesaj içeriği asla düz metin olarak diske yazılmaz.
- Kişi adları yalnızca cihazda çözülür; rehber hiçbir zaman saklanmaz ya da
  iletilmez.
- Analitik yok, çökme raporlama yok, reklam yok, izleme yok.

### İzinler — ve tam olarak neden

| İzin | Gerekçe |
|---|---|
| `RECEIVE_SMS`, `READ_SMS` | Gelen SMS broadcast'ini almak ve içeriğini okuyup iletmek için |
| `READ_PHONE_STATE` | Çalma/cevapsız durumunu dinlemek; çok SIM'li cihazlarda SIM bilgisini okumak için |
| `READ_CALL_LOG` | Android 12+ üzerinde sistem arayan numarasını arama olayında gizler; TeleRelay yalnızca *güncel* aramanın numarasına geçici olarak bakar — arama geçmişi saklanmaz, iletilmez |
| `READ_CONTACTS` | Numara yerine kişi adını gösterebilmek için. Yalnızca cihazda aranır; opsiyoneldir — verilmezse gönderenler numara görünür |
| `READ_PHONE_NUMBERS` | Opsiyonel SIM kimliği (`· SIM1`) |
| `POST_NOTIFICATIONS` | Arama izlemeyi canlı tutan kalıcı bildirim |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Uygulama içi butonun pil istisnası diyaloğunu açmasını sağlar (yalnız siz dokunduğunuzda kullanılır) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Ön plan servis tipi; süresiz bir arka plan rölesi için tasarlanan tek tip `specialUse`'tur |
| `RECEIVE_BOOT_COMPLETED` | Cihaz yeniden başladığında arama izlemeyi geri getirmek için |

### Sınırlamalar

- Her SMS ~5 sn'lik birleştirme penceresini bekler: UDH erişimi olmadan
  eksiksiz tek parça mesajı, birleştirilmiş mesajın ilk parçasından ayırt
  etmek mümkün değildir. Yalnızca tek broadcast'te çok PDU ile gelen mesajlar
  anlıktır.
- Android 12+ üzerinde çağrı kaydı satırı henüz yazılmamışsa arayan numarası
  `Bilinmiyor` görünebilir.
- Çok SIM'li cihazlarda arama bildirimleri hangi SIM'den geldiğini göstermez —
  genel API arama olayında SIM vermiyor; SMS'te SIM biliniyorsa gösterilir.
- OTP kopyala butonu, doğrulama ifadesine en yakın olası kodu seçer; nadiren
  yanlış rakam grubuna takılabilir.
- TeleRelay MMS göremez ve **giden** mesajları asla okumaz/iletmez.

### Kaynaktan derleme

```bash
git clone https://github.com/eneskaradeniz/telerelay.git
cd telerelay
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # 67 birim test
```

Gereksinimler: JDK 17+ ve platform 37 içeren bir Android SDK.

### Mimari

İnce clean architecture; receiver/servis/worker, use case'e delege eden ince
giriş noktalarıdır:

```
domain/   modeller, portlar (arayüzler), saf Kotlin mantık + use case'ler  ← birim testli
data/     Retrofit gateway, Keystore kripto, ayar deposu, kişi adı çözümleme,
          telephony monitörleri, WorkManager sender
ui/       Compose (Material 3) ayarlar ekranı + ViewModel
receiver/ service/ worker/  ince Android giriş noktaları
```

Dikkat çeken kararlar: `specialUse` ön plan servis tipi (`dataSync` Android
15'ten itibaren günde 6 saatte öldürülüyor); çok parçalı SMS birleştirme,
gönderen başına sessiz pencere ile; API 31+ arayan numarası RINGING
broadcast'i + çağrı kaydından; mesajlar HTML parse mode ile gönderilir.

## Lisans

[MIT](LICENSE)
