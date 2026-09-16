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
| i18n | `values/` (EN, varsayılan) + `values-tr/`; cihaz dilini izler. Uygulama içi dil seçeneği KALDIRILDI (2026-09-16, kullanıcı kararı) — `localeConfig` kalır, sistem ayarından per-app dil yine mümkün. **İletilen mesaj formatları sabit Türkçe** (localized değil, bilinçli spec kararı) |
| READ_CALL_LOG | Eklendi (kullanıcı onayladı): API 31+ callback numara vermez, CallLog'tan çözülür |
| READ_CONTACTS | Eklendi (kullanıcı onayladı, 2026-09-16 revizyon turu): kayıtlı kişiler iletilen mesajda adla görünür; arama yalnızca cihazda yapılır |
| Mesaj formatı | Compact: `📩 <b>gönderen</b>[ · SIMn]\nbody` (HTML parse mode). Zaman satırı yok (Telegram kendi saatini gösterir); SIM satırı yalnızca çok-SIM'de ve biliniyorsa. Algılanan OTP koduna Telegram `copy_text` butonu eklenir |
| Gizlilik guard + numara filtresi | KALDIRILDI (kullanıcı kararı, 2026-09-16) — regex filtre/maskeleme ve hariç tutulan numaralar özelliği yok; OTP algılama yalnızca copy butonu için, maskeleme yok |

## Komutlar

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # 67 test — sayı düşerse sebebi açıkla (65→36 gizlilik guard silinmesi, sonra 67 yeni pipeline testleriyle)
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
- **2026-09-16** `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` manifest'te
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` bildirilmeden atılırsa SecurityException
  fırlatır; `runCatching` bunu yuttuğu için pil butonu sessiz no-op oldu
  (gerçek hata). Sessiz yutma yerine state'e yaz.
- **2026-09-16** kotlinx.serialization JSON'da `explicitNulls = false` şart —
  default'ta nullable alanlar wire'a `"alan":null` olarak gidiyor; opsiyonel
  Telegram alanları (reply_markup vb.) temiz kalsın.
- **2026-09-16** Per-app dil: API 33+ activity'de `configChanges="locale|layoutDirection"`
  ile `setApplicationLocales` recreate'siz config update verir; <33 AppCompat
  backport yine recreate eder (kabul edildi, eski cihazlarda standart davranış).
- **2026-09-16** SMS broadcast'inin `"subscription"` extra'sı undocumented —
  her platformda var olmayabilir; SIM marker yalnızca eşleşme bulunursa basılır,
  bulunamazsa atlanır (yanlış SIM basmaktan iyisi).
- **2026-09-16** (gerçek cihaz) `batteryExempt` combine içinde yeniden
  hesaplanıyordu; ON_RESUME'da permissions değeri değişmemişse MutableStateFlow
  eşit değer yayınlamadığı için pil durumu bayat kaldı → kendi flow'una alındı,
  `refreshPermissions()` ikisini de günceller. StateFlow dedupe'sine dikkat:
  "yeniden hesaplamak" ≠ "yayınlamak".
- **2026-09-16** (gerçek cihaz) İlk per-app dil değişimi `configChanges`
  bildirilmesine rağmen bir kez recreate edebiliyor (API 33+); sonraki
  değişimler recreate'siz. API 34+ `overrideActivityTransition(OPEN/CLOSE, 0, 0)`
  ile maskele (pozisyonel çağrı) — flaş görünmez.

## Güvenlik kuralları

- Token/chat ID **asla** Log'a yazılmaz; `HttpLoggingInterceptor` eklenmez
  (token URL path'inde). Yeni log ihtiyacı → önce bu kuralı hatırla.
- Kullanıcıdan token'ı sohbete yapıştırmasını isteme; sorun teşhisi için
  X'lenmiş URL (token yerine aynı uzunlukta `X`) yeterli.
- Mesaj içeriği plaintext diske yazılmaz: WorkManager payload şifreli,
  `allowBackup=false` + `dataExtractionRules` prefs'i hariç tutar.

## Tasarım sabitleri (performans bütçeleri)

- Multipart SMS: gönderen başına **5 sn** sessiz pencere — UDH kapalı olduğu
  için tek-PDU broadcast'ler de (tam mesaj olsalar bile) pencereyi bekler;
  çok-PDU broadcast anında iletilir.
- Arama bildirimi çalma anında gider; numara API 31+'da PHONE_STATE broadcast
  extra'sından (bilinmiyorsa ≤0.75 sn bekleme), olmazsa CallLog'tan best-effort
  (satır arama bitince yazılıyor, çalırken boş); kişi adı PhoneLookup'tan
  best-effort.
- Cevapsız = RINGING→IDLE + çalma süresi ≥3 sn; 5 sn cooldown flicker'ı yutar.
- RateLimiter: gönderimler arası ≥1.1 sn; 429'da `retry_after`'a uyulur.
- UI: "Calm Groups" — 4 düz kart (servis durumu, izinler, iletim, Telegram —
  Telegram kartı en altta, kullanıcı kararı), `surfaceContainerLow` + 0
  elevation + 16dp; satır ikonu yok; tek SectionCard/ToggleRow bileşeni
  `ui/components/`'ta.

## Yeni iş kuralları

- Yeni bağımlılık önerisi → önce mevcut port'larla çözülebilir mi tartış.
  "Analitik/üçüncü parti SDK yok" proje vaadi; Robolectric ve MockK bilinçli yok.
- Domain mantığı saf Kotlin kalır (Android import yok) — test edilebilirlik şartı.
- Port→impl değişimi yalnızca `di/AppModule.kt`'de bir satır.

## Durum (2026-09-16 itibarıyla)

- ✅ Public repo yayında: `github.com/eneskaradeniz/telerelay`, CI yeşil.
- ✅ Revizyon turu 1 uygulandı (issue #1): UI redesign "Calm Groups", compact
  mesaj formatı + kişi adı + koşullu SIM + OTP copy_text, gizlilik guard ve
  numara filtresi kaldırıldı, pil butonu manifest izniyle düzeltildi, dil
  değişimi kaldırıldı (cihaz dili izlenir). 67 test yeşil; reviewer + tester
  ajan denetimi yapıldı, bulgular (cross-call numara sızıntısı, machine
  thread-safety, CONTACTS'un servisi bloklaması, missed-call numara kaybı)
  kapatıldı.
- ⏳ Bekleyen işler:
  1. Revizyon commit + push (kullanıcı onayı gerekli)
  2. Gerçek cihazda tam tur: SMS iletme (compact format + kişi adı + SIM),
     gelen arama, cevapsız arama, reboot sonrası servis, dil değişimi, pil butonu
  3. Release imzalama yapılandırması yok (bilinçli; `*.keystore` gitignore'da)
  4. İlk release tag'i + APK yayını

## Dosya haritası (hızlı)

- `domain/logic/` — saf Kotlin test hedefleri (assembler, state machine, formatter, OTP detector)
- `domain/usecase/` — ForwardSms / ForwardCall
- `data/telegram/` — gateway + rate limiter + Retrofit arayüzü + DTO'lar (HTML parse mode + copy_text)
- `data/crypto/` — Keystore AES-GCM
- `data/contact/` — PhoneLookup kişi adı çözümleme (READ_CONTACTS)
- `service/MonitorService.kt` — specialUse FGS
- `ui/SettingsScreen.kt` — tek ekran, 4 kart (servis durumu, izinler, iletim, Telegram)
- `ui/components/` — SectionCard / ToggleRow / StatusDot / InlineStatus
