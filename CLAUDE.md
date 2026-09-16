# TeleRelay — Proje Talimatları

Gelen SMS'leri ve aramaları kullanıcının **kendi** Telegram botuna ileten, açık
kaynak Android uygulaması. Dağıtım: GitHub / F-Droid / sideload — **Google Play
hedef değil**. Tüm sohbet ve kod Türkçe/İngilizce karışık olabilir; kod
yorumları İngilizce.

## Kilitli kararlar (kullanıcı onaylı — tartışma kapalı)

Değiştirmek istersen önce kullanıcıya sor, gerekçeni açıkla.

| Konu | Karar |
|---|---|
| SDK | minSdk 26, compile/target **37** (Android 17) |
| Mimari | Slim clean architecture; receiver/service/worker ince giriş noktası, iş mantığı domain'de |
| DI | Hilt (`@AndroidEntryPoint` her yerde, `@HiltWorker` dahil) |
| Hassas depolama | Custom Keystore AES-GCM (`data/crypto/`); `security-crypto` ve Tink **kullanılmaz** (deprecated) |
| FGS tipi | `specialUse` — `dataSync` Android 15+'ta 6 saat/24s limiti yüzünden yasak |
| FGS'in görevi | **Yalnızca arama dinleyicisini canlı tutmak.** SMS manifest broadcast'iyle FGS'siz de çalışır — UI metni bunu yansıtır |
| Retry | WorkManager, payload **şifreli** (mesaj içeriği plaintext diske yazılmaz) |
| i18n | `values/` (EN) + `values-tr/`; AppCompat locales backport. **İletilen mesaj formatları sabit Türkçe** (localized değil, bilinçli spec kararı) |
| READ_CALL_LOG | Eklendi (kullanıcı onayladı): API 31+ callback numara vermez, CallLog'tan çözülür |

## Komutlar

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # 65 test — sayı düşerse sebebi açıkla
./gradlew :app:assembleRelease      # minify'lı (unsigned)
```

AGP **9.2.1**: kullanıcının Android Studio'su daha yenisini desteklemiyor.
AGP yükseltmeden önce Studio'nun AGP uyum tablosunu kontrol et; gerekirse
önce Studio güncellemesini iste.

## Tarihli tuzak kuralları (her biri gerçek hata — silme, üstüne ekleme)

- **2026-09-16** AGP 9 built-in Kotlin: `org.jetbrains.kotlin.android` plugin'i
  UYGULANMAZ. Compose/serialization plugin sürümü AGP'nin gömülü KGP'siyle
  eşleşmeli (2.2.10).
- **2026-09-16** KSP: Kotlin-önekli sürümler (2.2.10-2.0.2) built-in Kotlin ile
  kırılır; bağımsız 2.3.x hattı kullan (2.3.12).
- **2026-09-16** API 37'de `CallLog.Calls.OUTGOING` (String) yok → `OUTGOING_TYPE`.
- **2026-09-16** `android.jar` parametre isimleri taşımaz → framework
  metodlarına named argument **yasak** (`ContentResolver.query` gibi; pozisyonel çağır).
- **2026-09-16** Token'lı path `@Path(encoded=true)` ile RELATIVE verilemez —
  token'daki `:` URI scheme sanılır (404/Malformed URL). `TelegramApi` absolute
  `@Url` kullanır; base URL `@TelegramBaseUrl` ile enjekte edilir (testler mock'a çevirir).
- **2026-09-16** Regex maskeleme: `appendReplacement` tüm eşleşmeyi değiştirir;
  isimli grubun span'i elle maskele (`RegexPrivacyFilter.mask`).
- **2026-09-16** Dagger default parametreyi enjeksiyon sayar → `@Inject
  constructor(x: Long = 5)` yasak; sabit gövdede.
- **2026-09-16** Testlerde paylaşılan fake'ler (örn. `FakeSettingsRepository`)
  testler arasında sıfırlanmalı; `takeRequest()` timeout'suz çağrılmaz
  (`takeRequest(10, SECONDS)`).
- **2026-09-16** `runTest` + gerçek `Dispatchers.IO` + MockWebServer =
  deadlock; gateway testleri `runBlocking` kullanır.

## Güvenlik kuralları

- Token/chat ID **asla** Log'a yazılmaz; `HttpLoggingInterceptor` eklenmez
  (token URL path'inde). Yeni log ihtiyacı → önce bu kuralı hatırla.
- Kullanıcıdan token'ı sohbete yapıştırmasını isteme; sorun teşhisi için
  X'lenmiş URL (token yerine aynı uzunlukta `X`) yeterli.
- Mesaj içeriği plaintext diske yazılmaz: WorkManager payload şifreli,
  `allowBackup=false` + `dataExtractionRules` prefs'i hariç tutar.

## Tasarım sabitleri (performans bütçeleri)

- Multipart SMS: gönderen başına **5 sn** sessiz pencere; tek parça mesaj
  anlık iletilir; çok-PDU broadcast anında iletilir.
- Arama bildirimi çalma anında gider; numara API 31+'da CallLog'tan
  best-effort (≤2 sn, bulunamazsa "Bilinmiyor").
- Cevapsız = RINGING→IDLE + çalma süresi ≥3 sn; 5 sn cooldown flicker'ı yutar.
- RateLimiter: gönderimler arası ≥1.1 sn; 429'da `retry_after`'a uyulur.

## Yeni iş kuralları

- Yeni bağımlılık önerisi → önce mevcut port'larla çözülebilir mi tartış.
  "Analitik/üçüncü parti SDK yok" proje vaadi; Robolectric ve MockK bilinçli yok.
- Domain mantığı saf Kotlin kalır (Android import yok) — test edilebilirlik şartı.
- Port→impl değişimi yalnızca `di/AppModule.kt`'de bir satır.

## Durum (2026-09-16 itibarıyla)

- ✅ Uçtan uca çalışıyor: kullanıcı token+chat ID girip test mesajını Telegram'da aldı.
- ⏳ Bekleyen işler:
  1. `git commit` + GitHub'a push (kullanıcı onayı gerekli — henüz yapılmadı)
  2. Gerçek cihazda tam tur: SMS iletme, gelen arama, cevapsız arama, reboot sonrası servis
  3. README badge'lerindeki `eneskaradeniz/telerelay` repo yolu — GitHub kullanıcı adı doğrulanacak
  4. Release imzalama yapılandırması yok (bilinçli; `*.keystore` gitignore'da)

## Dosya haritası (hızlı)

- `domain/logic/` — saf Kotlin test hedefleri (assembler, state machine, formatter)
- `domain/usecase/` — ForwardSms / ForwardCall / SendTestMessage
- `data/telegram/` — gateway + rate limiter + Retrofit arayüzü
- `data/crypto/` — Keystore AES-GCM
- `data/privacy/` — regex filtre + `DefaultFilterRules` (desenler `(?<code>…)` grubu taşır)
- `service/MonitorService.kt` — specialUse FGS
- `ui/SettingsScreen.kt` — tek ekran (bölümler: izin, kimlik, toggle, gizlilik, servis, dil)
