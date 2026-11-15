# Seamless Privacy Policy

*Last updated: November 2026*

## Our Commitment

Seamless is designed with privacy and security as its foundation. We believe private communication is a fundamental human right. This policy explains what information Seamless processes on your device, what is shared with peers, and what we never collect.

## Summary

- **Zero personal data collection** — Seamless does not collect names, emails, phone numbers, contacts, or device identifiers.
- **Zero central servers** — Seamless operates without central accounts, authentication servers, or developer-operated infrastructure.
- **Multi-transport peer-to-peer communication**:
  - **Bluetooth Low Energy (BLE) Mesh**: Works 100% offline using local peer-to-peer Bluetooth connections with optional multi-hop relay.
  - **Tor v3 Onion P2P & Mailboxes**: Routes global 1:1 messages, voice calls, group chats, and asynchronous mailbox delivery directly over the Tor anonymity network using `.onion` hidden services.
  - **Nostr Geohash Chat**: Optional internet channels via public decentralized Nostr relays using ephemeral client keys.
  - **HyperDrop**: Direct local Wi-Fi Direct / P2P file transfer with zero cloud middlemen.
- **No analytics, ads, or telemetry** — There is no tracking SDK, crash uploader, or usage telemetry in the app.
- **Fully Open Source (GPL-3.0)** — Every claim in this policy can be audited in the public source code.

## What Seamless Stores Locally on Your Device

1. **Cryptographic Identity & Tor Keys**
   - Ephemeral and long-term cryptographic keypairs (Noise/X25519, Ed25519, Double Ratchet session state, and Tor v3 onion service keys) are generated locally on your device and protected by Android Keystore / encrypted local storage.
2. **Display Nickname**
   - Stored locally on your device and shared only with peers you communicate with.
3. **Messages, Contacts & Group State**
   - Conversations, contacts, favorites, and received media live only inside your device's SQLCipher-encrypted local database (or user-exported Downloads for saved media/files).
4. **Temporary Routing & Offline Queues**
   - Transient peer announcements and store-and-forward retry queues needed to deliver messages reliably across flaky links.

## What You Share When Using Seamless

- **With nearby BLE Mesh peers**: Your display nickname, session public key, approximate signal strength (RSSI), and the messages you choose to send.
- **With paired Tor P2P contacts & group members**: Your `.onion` address, display nickname, and end-to-end encrypted chat/voice payloads. Direct voice calls and direct Tor connections transit Tor circuits by default (unless you explicitly choose Direct P2P LAN fallback when offline).
- **With Nostr relays (if you open Geohash channels)**: Signed Nostr events (`kind`) visible on the public relays you connect to.

## Permissions & Hardware Access

- **Bluetooth (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`) & Location (`ACCESS_FINE_LOCATION` on older Android versions)**: Required by the Android OS solely to discover and connect to nearby Bluetooth LE peers. Seamless **never** reads or transmits your GPS coordinates for tracking.
- **Wi-Fi Direct / Nearby Wi-Fi Devices (`NEARBY_WIFI_DEVICES`)**: Used exclusively for HyperDrop local file sharing.
- **Microphone (`RECORD_AUDIO`)**: Used only when you actively record a voice note or speak on an encrypted voice call.
- **Notifications & Full-Screen Intent (`POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`)**: Used to notify you of incoming messages and ring incoming encrypted calls even while the device is locked.

## Your Control & Emergency Wipe

- **Emergency Panic Wipe**: Triple-tap the Seamless logo in the top bar (or use Panic Mode) to immediately wipe cryptographic keys, message history, and local identity state.
- **No Cloud Footprint**: Uninstalling Seamless or deleting its data removes everything — there are no remote servers holding your account.

## Contact & Source Code

- Repository: https://github.com/bharathbharath20081607-cloud/seamless-android
- Report privacy or security issues via GitHub Issues or Pull Requests.

---

*Licensed under the GNU General Public License v3.0 (GPL-3.0). See [LICENSE.md](LICENSE.md) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).*
