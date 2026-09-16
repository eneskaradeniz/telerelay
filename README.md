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
- **One-tap OTP copy.** Messages containing a verification code get Telegram's
  native "copy code" button. (iOS/macOS system-wide SMS autofill is closed to
  third-party apps — this is the fastest legal path.)
- **Transparent reliability.** SMS forwarding works even when the app process
  is dead (system broadcasts wake it). Call monitoring runs behind a
  persistent, low-priority notification and restarts itself after reboot.

## Features

| Feature | Details |
|---|---|
| SMS forwarding | Multipart (long) messages are reassembled into one Telegram message; saved contacts appear by name instead of number |
| SIM marker | Only on multi-SIM devices, and only when the delivering SIM is known (`· SIM2`) |
| Incoming calls | "Who is calling" notification the moment the phone rings, with the contact name when saved |
| Missed calls | Separate `☎️` notification when a call rings out unanswered |
| OTP copy button | When a verification code is detected, Telegram's one-tap copy button is attached to the message |
| Retry queue | Failed deliveries are queued in WorkManager with exponential backoff; Telegram's `retry_after` is honoured |
| Languages | English and Turkish — follows the device language (English by default) |
| Battery | One-tap request to exempt the app from battery optimisation |

Message format — envelope-free, like the SMS itself:

```
📩 E-DEVLET · SIM2
Dogrulama kodunuz : 814067  Bu mesaj e-Devlet Kapisi ...
```

Saved senders appear by name, unknown ones by number. There is no time line —
Telegram already renders the message time. The SIM marker is omitted on
single-SIM devices.

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
| `READ_PHONE_STATE` | Detect ringing/missed call state; read the SIM slot on multi-SIM devices |
| `READ_CALL_LOG` | On Android 12+ the system hides the caller number from call events. TeleRelay looks up the number of the *current* call only, transiently — no call history is stored or forwarded |
| `READ_CONTACTS` | Show the contact name instead of a raw number in forwarded messages. Names are looked up on the device only; nothing is stored or forwarded beyond the message itself |
| `READ_PHONE_NUMBERS` | Optional SIM identification (`· SIM1`) |
| `POST_NOTIFICATIONS` | The persistent notification that keeps call monitoring alive |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Lets the in-app button open the battery-exemption dialog (used only when you tap it) |
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
- Contact names are resolved on the device only; the contacts list is read but
  never stored, and nothing beyond the message itself is forwarded.

## Architecture

A slim clean-architecture layout; receivers/service/worker are thin entry
points that delegate to use cases:

```
domain/   models, ports (interfaces), pure-Kotlin logic + use cases  ← unit-tested
data/     Retrofit gateway, Keystore crypto, settings store, contact-name
          resolver, telephony monitors, WorkManager sender
ui/       Compose (Material 3) settings screen + ViewModel
receiver/ service/ worker/  thin Android entry points
```

Design decisions of note: `specialUse` foreground-service type (the `dataSync`
type is killed after 6 h/day since Android 15); multipart-SMS reassembly via a
per-sender quiet window (concatenation headers are not exposed to non-default
SMS apps); caller numbers on API 31+ are resolved from the call log because the
platform stopped delivering them in call-state callbacks; messages are sent
with HTML parse mode, and detected OTP codes ride Telegram's `copy_text`
button for one-tap copying.

## Limitations

- Every SMS waits out a ~5 s merging window: without UDH access a complete
  single-part message is indistinguishable from the first segment of a
  concatenated one. Only messages delivered as one multi-PDU broadcast are
  instant.
- On Android 12+ the caller number can be unknown if the call-log row has not
  been written yet — the message then reads `Bilinmiyor`.
- On multi-SIM devices call notifications do not show which SIM rang — the
  public API carries no subscription on call events; SMS shows the SIM when it
  is known.
- The OTP copy button picks the most likely code and can rarely latch onto the
  wrong digit group.
- TeleRelay cannot see MMS, and it never reads or forwards *outgoing* messages.

## License

[MIT](LICENSE)
