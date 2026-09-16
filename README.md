# TeleRelay

[![CI](https://github.com/eneskaradeniz/telerelay/actions/workflows/ci.yml/badge.svg)](./.github/workflows/ci.yml)
[Türkçe README](README.tr.md)

TeleRelay relays **incoming SMS messages and phone calls** from your Android
phone to **your own Telegram bot chat** — the phone in your pocket becomes
visible in the Telegram window you already keep open.

Local-first: messages go from your phone to `api.telegram.org` with **your**
bot token and nowhere else. No servers, no accounts, no analytics, no
third-party SDKs.

## Setup

1. **Create a bot** — talk to [@BotFather](https://t.me/BotFather) on Telegram,
   send `/newbot`, pick a name. BotFather replies with a **token** like
   `123456789:AAH…`. Copy it.
2. **Get your chat ID** — send any message to your new bot (press **Start**; a
   bot cannot write to you first), then open
   `https://api.telegram.org/bot<TOKEN>/getUpdates` in a browser and copy the
   number in `"chat":{"id":…}`.
3. **Install** — download `app-release.apk` from
   [Releases](../../releases) and install it. **Open the app once** after
   installing: Android delivers the boot-completed broadcast only to apps that
   have been opened, which is what lets TeleRelay restart itself after a
   reboot.
4. **Configure** — enter the token and chat ID, press **Send test message**.
   If it arrives in Telegram, grant the permissions from the permission card.
5. **Android 13+ : the SMS and Call log permissions are blocked by the system
   for apps installed outside Play.** When granting fails:
   **Settings → Apps → TeleRelay → ⋮ menu → Allow restricted permissions**,
   then allow **SMS** and **Call log**. This is Android's anti-scam gate for
   all sideloaded apps — not a TeleRelay problem, and safe here because the
   app is fully open source.

On Xiaomi / Huawei / Oppo / Samsung also disable battery optimisation (button
in the app) and allow autostart if the OEM offers it — OEM task killers, not
Android, are the usual reason a relay app "stops working".

## What you get

```
📩 E-DEVLET
Dogrulama kodunuz : 814067 …      ← one-tap Copy code button
📞 Eyüp arıyor
☎️ Cevapsız arama: Eyüp
```

Saved contacts appear by name, unknown senders by number (short codes such as
`2273` stay as-is). On dual-SIM devices the sender line shows which SIM
received the message (`· SIM2`). The copy button appears only when the message
contains verification-code wording — never on marketing SMS.

---

## Details

### Privacy

- SMS bodies and numbers go **only** to `api.telegram.org` with **your** bot
  token — i.e. to a chat **you** own.
- Bot token + chat ID are encrypted at rest (Android Keystore, AES-GCM,
  non-exportable) and excluded from backups; WorkManager retry payloads are
  encrypted too — message content never touches disk in plaintext.
- Contact names are resolved on the device only; the contacts list is never
  stored or forwarded.
- No analytics, no crash reporting, no ads, no tracking.

### Permissions — and exactly why

| Permission | Why it is needed |
|---|---|
| `RECEIVE_SMS`, `READ_SMS` | Receive the incoming-SMS broadcast and read its content to forward it |
| `READ_PHONE_STATE` | Detect ringing/missed call state; read the SIM slot on multi-SIM devices |
| `READ_CALL_LOG` | On Android 12+ the system hides the caller number from call events; TeleRelay looks up the number of the *current* call, transiently — no call history is stored or forwarded |
| `READ_CONTACTS` | Show the contact name instead of a raw number. Looked up on the device only; optional — without it senders show as numbers |
| `READ_PHONE_NUMBERS` | Optional SIM identification (`· SIM1`) |
| `POST_NOTIFICATIONS` | The persistent notification that keeps call monitoring alive |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Lets the in-app button open the battery-exemption dialog (used only when you tap it) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | The foreground service type; `specialUse` is the only type meant for an indefinite background relay |
| `RECEIVE_BOOT_COMPLETED` | Restart call monitoring after reboot |

### Limitations

- Every SMS waits out a ~5 s merging window: without UDH access a complete
  single-part message is indistinguishable from the first segment of a
  concatenated one. Only messages delivered as one multi-PDU broadcast are
  instant.
- On Android 12+ the caller number can be unknown if the call-log row has not
  been written yet — the message then reads `Bilinmiyor`.
- On multi-SIM devices call notifications do not show which SIM rang — the
  public API carries no subscription on call events; SMS shows the SIM when it
  is known.
- The OTP copy button picks the most likely code near verification wording and
  can rarely latch onto the wrong digit group.
- TeleRelay cannot see MMS, and it never reads or forwards *outgoing* messages.

### Build from source

```bash
git clone https://github.com/eneskaradeniz/telerelay.git
cd telerelay
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # 67 unit tests
```

Requirements: JDK 17+ and an Android SDK with platform 37.

### Architecture

Slim clean architecture; receivers/service/worker are thin entry points that
delegate to use cases:

```
domain/   models, ports (interfaces), pure-Kotlin logic + use cases  ← unit-tested
data/     Retrofit gateway, Keystore crypto, settings store, contact-name
          resolver, telephony monitors, WorkManager sender
ui/       Compose (Material 3) settings screen + ViewModel
receiver/ service/ worker/  thin Android entry points
```

Notable decisions: `specialUse` foreground-service type (`dataSync` is killed
after 6 h/day since Android 15); multipart-SMS reassembly via a per-sender
quiet window; caller numbers on API 31+ come from the RINGING broadcast +
call log; messages are sent with HTML parse mode.

## License

[MIT](LICENSE)
