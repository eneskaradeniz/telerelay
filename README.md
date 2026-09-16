# TeleRelay

[![CI](https://github.com/eneskaradeniz/telerelay/actions/workflows/ci.yml/badge.svg)](./.github/workflows/ci.yml)
[Türkçe README](README.tr.md)

TeleRelay is a lightweight, open-source Android app that runs quietly in the
background and relays **incoming SMS messages and phone calls** to **your own
Telegram bot chat** — the phone in your pocket becomes visible in the Telegram
window you already keep open all day.

## Why TeleRelay

- **Local-first.** Messages go from your phone to `api.telegram.org` and
  nowhere else. No intermediate server, no account, no analytics, no
  third-party SDKs. You can verify it: all network code is one small Retrofit
  interface.
- **Minimal by design.** One screen, three layers of clean architecture,
  and nothing stored on the device except your settings.
- **Private even against itself.** An optional privacy guard detects OTP/2FA
  codes and bank-transaction SMS and either masks the secrets (`482913` →
  `••••••`) or drops the message entirely, before it ever leaves the phone.
- **Transparent reliability.** SMS forwarding works even when the app process
  is dead (system broadcasts wake it). Call monitoring runs behind a
  persistent, low-priority notification and restarts itself after reboot.

## Features

| Feature | Details |
|---|---|
| SMS forwarding | Multipart (long) messages are reassembled into one Telegram message; SIM slot included on dual-SIM devices |
| Incoming calls | "Who is calling" notification the moment the phone rings |
| Missed calls | Separate `☎️` notification when a call rings out unanswered |
| Privacy guard | Regex filter (editable) for OTP codes and bank messages — mask or exclude |
| Number filter | Never forward messages from chosen numbers |
| Retry queue | Failed deliveries are queued in WorkManager with exponential backoff; Telegram's `retry_after` is honoured |
| Languages | English and Turkish, switchable in-app |
| Battery | One-tap request to exempt the app from battery optimisation |

Message format:

```
📩 Yeni SMS
Kimden: +90 555 000 11 22
Zaman: 14:03:22
SIM: SIM1 (Vodafone)
Mesaj: Hello world
```

## Setup

### 1. Create your Telegram bot

1. Open Telegram and talk to [@BotFather](https://t.me/BotFather).
2. Send `/newbot`, choose a name and a username.
3. BotFather replies with a **token** like `123456789:AAH…` — that is your
   **bot token**.

### 2. Get your chat ID

1. Send any message to your new bot (press **Start**). A bot cannot message
   you until you have written to it first.
2. Open `https://api.telegram.org/bot<TOKEN>/getUpdates` in a browser and find
   `"chat":{"id":123456789,…}` — that number is your **chat ID**.

### 3. Configure TeleRelay

Install the app, enter the **bot token** and **chat ID**, press **Send test
message**. Done — grant the requested permissions and the relay is live.

### Installing

- **Sideload:** download `app-release.apk` from
  [Releases](../../releases) (or build it yourself — see below) and install it.
- **After installing, open the app once.** Android only delivers the
  boot-completed broadcast to apps that have been opened, which is what lets
  TeleRelay restart call monitoring after a reboot.
- On Xiaomi / Huawei / Oppo / Samsung devices also disable battery
  optimisation for TeleRelay (button in the app) and, if the OEM offers an
  "autostart" manager, allow TeleRelay there. OEM task killers — not Android —
  are the usual reason a relay app "stops working".

## Build

```bash
git clone https://github.com/eneskaradeniz/telerelay.git
cd telerelay
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # unit tests
```

Requirements: JDK 17+ and an Android SDK with platform 37.

## Permissions — and exactly why

| Permission | Why it is needed |
|---|---|
| `RECEIVE_SMS`, `READ_SMS` | Receive the incoming-SMS broadcast and read its content to forward it |
| `READ_PHONE_STATE` | Detect ringing/missed call state; read SIM slot for dual-SIM info |
| `READ_CALL_LOG` | On Android 12+ the system hides the caller number from call events. TeleRelay looks up the number of the *current* call only, transiently — no call history is stored or forwarded |
| `READ_PHONE_NUMBERS` | Optional SIM identification (`SIM: SIM1 (Vodafone)`) |
| `POST_NOTIFICATIONS` | The persistent notification that keeps call monitoring alive |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | The foreground service type; `specialUse` is the only type meant for an indefinite background relay (others carry runtime limits or require media/telephony semantics) |
| `RECEIVE_BOOT_COMPLETED` | Restart call monitoring after reboot |

## Privacy

- SMS bodies and caller numbers are sent **only** to `api.telegram.org` using
  **your** bot token — i.e. to a chat **you** own.
- The bot token and chat ID are encrypted at rest with an AES-GCM key held in
  the Android Keystore (non-exportable) and excluded from backups.
- No analytics, no crash reporting, no ads, no tracking, no extra servers.
- WorkManager retry payloads are encrypted too — message content never touches
  disk in plaintext.
- The privacy guard (mask/exclude) runs **before** anything leaves the device.

## Architecture

A slim clean-architecture layout; receivers/service/worker are thin entry
points that delegate to use cases:

```
domain/   models, ports (interfaces), pure-Kotlin logic + use cases  ← unit-tested
data/     Retrofit gateway, Keystore crypto, settings store, regex filter,
          telephony monitors, WorkManager sender
ui/       Compose (Material 3) settings screen + ViewModel
receiver/ service/ worker/  thin Android entry points
```

Design decisions of note: `specialUse` foreground-service type (the `dataSync`
type is killed after 6 h/day since Android 15); multipart-SMS reassembly via a
per-sender quiet window (concatenation headers are not exposed to non-default
SMS apps); caller numbers on API 31+ are resolved from the call log because the
platform stopped delivering them in call-state callbacks.

## Limitations

- SMS content arrives with up to ~5 s extra delay only when a message was
  split into multiple parts (merging window); single-part messages are instant.
- On Android 12+ the caller number can be unknown if the call-log row has not
  been written yet — the message then reads `Arayan: Bilinmiyor`.
- TeleRelay cannot see MMS, and it never reads or forwards *outgoing* messages.

## License

[MIT](LICENSE)
