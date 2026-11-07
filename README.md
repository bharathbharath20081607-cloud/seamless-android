<p align="center">
    <img src="art/logo.png" alt="Seamless Android Logo" width="220">
</p>

<h1 align="center">Seamless for Android</h1>

<p align="center">
    <strong>A secure, decentralized, serverless peer-to-peer messaging system.</strong><br>
    Chat off-grid over Bluetooth Low Energy mesh networks, route globally via Tor v3 Onion Services, discover through Nostr relays, and share files instantly with HyperDrop.
</p>

<p align="center">
    <a href="https://github.com/bharathbharath20081607-cloud/seamless-android/releases"><img src="https://img.shields.io/github/v/release/bharathbharath20081607-cloud/seamless-android?color=6366F1&label=Release" alt="Release"></a>
    <a href="LICENSE.md"><img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="License: GPLv3"></a>
    <a href="THIRD_PARTY_NOTICES.md"><img src="https://img.shields.io/badge/Notices-Third--Party%20Credits-0EA5E9.svg" alt="Third-Party Notices"></a>
    <img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-00DC82.svg" alt="Android 8.0+">
    <img src="https://img.shields.io/badge/Kotlin-2.0%2B-7F52FF.svg" alt="Kotlin">
    <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg" alt="Jetpack Compose">
</p>

> [!NOTE]
> Seamless operates with **zero central servers, zero accounts, zero phone numbers, and zero telemetry**. Everything runs entirely on your device and connects directly peer-to-peer.

---

## Overview

**Seamless** is an advanced peer-to-peer communication client engineered by Aquarius Groups. It provides private, censorship-resistant messaging across multiple complementary transports:

1. **Local Bluetooth Low Energy (BLE) Mesh (100% Offline)**: Communicate with nearby peers without internet, Wi-Fi, or cellular towers. Messages automatically hop across intermediate devices to reach distant recipients.
2. **Tor v3 Onion Services (Global Internet P2P & Group Rooms)**: Private 1:1 and multi-party group communication routed through end-to-end encrypted Tor hidden services and untrusted offline store-and-forward mailboxes. IP addresses are completely masked with zero central servers.
3. **Nostr Relay Integration**: Location-based Geohash channels and decentralized discovery with configurable Proof-of-Work (PoW) anti-spam mining.
4. **HyperDrop (Wi-Fi Direct & DNS-SD)**: Ultra-fast local device-to-device file, photo, and media transfer without internet or local Wi-Fi router access.

Seamless maintains **100% binary protocol compatibility** with the open Bluetooth mesh protocol used by the original `bitchat` app, allowing seamless cross-platform communication between Android and iOS users over local mesh networks.

---

## Install Seamless

Download the latest release APK directly from the [GitHub Releases page](https://github.com/bharathbharath20081607-cloud/seamless-android/releases).

### Sideloading Instructions:
1. **Download the APK**: Download the latest `seamless-v1.5.1.apk` from the Releases tab on your Android device.
2. **Allow Unknown Sources**: If prompted, grant permission to your browser or file manager to install unknown apps (found under **Settings > Security** or **Settings > Apps & notifications > Special app access > Install unknown apps**).
3. **Install**: Tap the downloaded `.apk` file and confirm installation.

---

## Features

### 📡 Multi-Transport Networking
- **Decentralized BLE Mesh**: Autonomous peer discovery, advertisement, and multi-hop routing over Bluetooth Low Energy with parked offline delivery queues.
- **Tor Onion Services (v3)**: Built-in dual-engine Tor integration (Rust Arti + C-Tor hidden services) for private internet communication without server intermediaries or metadata leakage.
- **Encrypted Tor Group Messaging**: Multi-party group rooms over Tor v3 with canonical message synchronization, quoted replies, encrypted emoji reactions, live per-recipient delivery ACKs (`1/3` -> `✓✓`), and one-tap delivery retry.
- **Nostr Relay Channels**: Connect to open Nostr relays for Geohash area channels and wide-area broadcast.
- **HyperDrop High-Speed File Transfer**: Direct Wi-Fi P2P file, photo, and voice memo streaming with DNS-SD service discovery.
- **Store & Forward Offline Delivery**: Messages destined for offline peers are cached in parked queues or encrypted Tor mailboxes and automatically delivered when recipients reconnect.

### 🔒 End-to-End Cryptography
- **Double Ratchet Forward Secrecy**: Ephemeral key derivation using the Signal-style Double Ratchet algorithm and Noise Protocol framework (`Noise_XX_25519_ChaChaPoly_SHA256`).
- **Authenticated Encryption**: X25519 key agreements, Ed25519 identity and broadcast signatures, AES-256-GCM, and ChaCha20-Poly1305.
- **Encrypted Local Storage**: SQLCipher-encrypted Room database and Android Keystore-backed encrypted preferences.
- **Channel Security**: Argon2id / SHA-256 password-derived key material for private group channels.
- **Proof-of-Work (PoW)**: Client-side cryptographic difficulty challenges on Nostr broadcasts to defeat spam without central moderators.

### 🛡️ Privacy & OpSec First
- **Zero Identity Footprint**: No accounts, phone numbers, emails, or central registries. Cryptographic identities are generated locally on-device.
- **Emergency Panic Wipe**: Triple-tap the brand logo in the top bar to instantly erase all active cryptographic keypairs, message histories, cached peers, and reset identity state.
- **Cover Traffic & Noise Padding**: Configurable message padding and packet pacing to mitigate passive traffic analysis.

### 🎨 Modern Android Experience
- **Jetpack Compose UI**: Built with Material Design 3 and a bespoke **Obsidian & Optical Light** design system.
- **Interactive Emoji Reactions & Replies**: Real-time reactions and threaded replies synced across mesh, Tor 1:1, and Tor group chats.
- **Voice Notes**: Built-in voice recording with live dynamic audio waveforms and low-latency compressed delivery.
- **Fast Media Transfers**: Automatic image downscaling, progressive chunking, full-screen zoomable lightbox viewer, and selective acknowledgment windowing.
- **Battery Optimization**: Adaptive power profiles adjust BLE scanning duty cycles, Tor circuit heartbeats, and worker frequencies based on battery percentage.

---

## Usage & Commands

Seamless features an IRC-inspired command line interface directly in the chat bar:

| Command | Action |
|:---|:---|
| `/j #channel` | Join or create a topic-based channel |
| `/m @nickname message` | Send an end-to-end encrypted direct message to a peer |
| `/w` | List active peers discovered on the mesh or Tor network |
| `/channels` | Discover active broadcast and group channels |
| `/block @nickname` | Block an offending peer from delivering messages to you |
| `/block` | List all currently blocked peers |
| `/unblock @nickname` | Unblock a previously blocked peer |
| `/clear` | Clear the current conversation message feed |
| `/pass [password]` | Set or update the encryption password for a channel (Owner only) |
| `/transfer @nickname` | Transfer channel administrative ownership to another peer |
| `/save` | Toggle persistent message retention for the channel (Owner only) |

### Quick Start:
1. Launch **Seamless** on your Android device (requires Android 8.0+).
2. Grant Bluetooth and Location permissions when prompted (required by Android OS for BLE hardware access).
3. The app automatically initializes local mesh networking and provisions a private Tor v3 onion service in the background.
4. Set your display nickname or use the auto-generated cryptographic handle.
5. Nearby peers will be discovered automatically. Join `#general`, pair over Tor via QR code, or share files via HyperDrop!

---

## Android Technical Architecture

```
┌────────────────────────────────────────────────────────┐
│                   Jetpack Compose UI                   │
│      Obsidian & Optical Light Design / ChatScreen      │
├────────────────────────────────────────────────────────┤
│                     ChatViewModel                      │
│        Unified State Machine & Event Dispatcher        │
├───────────────────┬────────────────────────────────────┤
│   Cryptographic   │  Double Ratchet & Noise Protocol   │
│      Engine       │  X25519 / Ed25519 / AES-256-GCM    │
├───────────────────┴────────────────────────────────────┤
│             Multi-Transport Routing Layer              │
├─────────────┬─────────────┬──────────────┬─────────────┤
│   BLE Mesh  │  Tor Onion  │ Nostr Relays │  HyperDrop  │
│  (Central / │   Services  │ (NIP-01/04)  │   (Wi-Fi    │
│ Peripheral) │  (P2P v3)   │   with PoW   │   Direct)   │
└─────────────┴─────────────┴──────────────┴─────────────┘
```

### Core Components:

- **[`SeamlessApplication.kt`](app/src/main/java/com/seamless/android/SeamlessApplication.kt)**: Application-level dependency injection, cryptographic identity bootstrap, and lifecycle management.
- **[`MainActivity.kt`](app/src/main/java/com/seamless/android/MainActivity.kt)**: Permission handling, hardware state observers, and Jetpack Compose navigation scaffold.
- **[`ChatViewModel.kt`](app/src/main/java/com/seamless/android/ui/ChatViewModel.kt)**: MVVM presentation logic managing active peers, mesh packets, Tor conversations, and UI state.
- **[`BluetoothMeshService.kt`](app/src/main/java/com/seamless/android/mesh/BluetoothMeshService.kt)**: Core BLE mesh engine operating simultaneous Central (scanner/client) and Peripheral (advertiser/server) roles.
- **[`TorManager.kt`](app/src/main/java/com/seamless/android/net/TorManager.kt)**, **[`TorDaemonManager.kt`](app/src/main/java/com/seamless/android/net/TorDaemonManager.kt)**, **[`TorMessagingService.kt`](app/src/main/java/com/seamless/android/net/TorMessagingService.kt)** & **[`TorGroupMessaging.kt`](app/src/main/java/com/seamless/android/net/TorGroupMessaging.kt)**: Dual-engine Tor orchestration, v3 hidden service publishing, persistent circuit keep-alives, offline mailboxes, and 1:1 / group P2P framing.
- **[`SeamlessRatchetManager.kt`](app/src/main/java/com/seamless/android/noise/SeamlessRatchetManager.kt)** & **[`DoubleRatchet.kt`](app/src/main/java/com/seamless/android/crypto/DoubleRatchet.kt)**: Signal-compatible Double Ratchet session state machine delivering post-compromise security and forward secrecy.
- **[`NostrDirectMessageHandler.kt`](app/src/main/java/com/seamless/android/nostr/NostrDirectMessageHandler.kt)**: Nostr protocol client with Proof-of-Work spam rejection.
- **[`HyperDropUtils.kt`](app/src/main/java/com/seamless/android/ui/fileshare/HyperDropUtils.kt)**: Wi-Fi Direct peer-to-peer file transfer engine.
- **[`BinaryProtocol.kt`](app/src/main/java/com/seamless/android/protocol/BinaryProtocol.kt)**: Binary packet serialization, 13-byte header encoding, chunk fragmentation, and bloom filter deduplication.

---

## Android Setup & Development

### Prerequisites
- **Android Studio**: Ladybug, Hedgehog, or newer
- **Android SDK**: API level 26 (Android 8.0) minimum, API 34+ target
- **Java Development Kit (JDK)**: JDK 17 or JDK 21
- **Kotlin**: 2.0+
- **Gradle**: 8.0+

### Build Instructions

1. **Clone the repository:**
   ```bash
   git clone https://github.com/bharathbharath20081607-cloud/seamless-android.git
   cd seamless-android
   ```

2. **Open in Android Studio:**
   - Launch Android Studio, select **Open**, and browse to the cloned `seamless-android` directory.
   - Wait for Gradle sync to index dependencies.

3. **Compile and run debug build:**
   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

4. **Production Release Build:**
   ```bash
   ./gradlew assembleRelease
   ```

---

## Hardware & Permission Requirements

| Permission | Purpose |
|:---|:---|
| `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` / `BLUETOOTH_ADVERTISE` | Mandatory on Android 12+ (API 31+) for BLE mesh discovery and connections. |
| `ACCESS_FINE_LOCATION` | Required by Android OS (API 26–30) to perform Bluetooth Low Energy hardware scans. Seamless does **not** track, log, or transmit GPS data. |
| `NEARBY_WIFI_DEVICES` | Required for HyperDrop Wi-Fi Direct peer-to-peer file transfer. |
| `RECORD_AUDIO` | Required exclusively when recording outgoing voice notes. |
| `POST_NOTIFICATIONS` | Foreground service status and incoming message alerts. |

---

## Open-Source Lineage, Credits & Acknowledgments

**Seamless for Android** stands on the shoulders of pioneering open-source projects in the decentralized privacy and mesh networking ecosystem. We gratefully credit the original authors and maintainers of our parent projects and core dependencies (see **[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)** for full copyright notices and license texts):

| Project / Library | Role in Seamless | Upstream License |
|:---|:---|:---|
| **[bitchat](https://github.com/permissionlesstech/bitchat)** & **[bitchat-android](https://github.com/permissionlesstech/bitchat-android)** | Original BLE mesh foundation, 13-byte binary packet protocol, and cross-platform iOS/Android mesh interoperability. | The Unlicense / GPL-3.0 |
| **[Briar Project](https://briarproject.org/)** (`onionwrapper-android`, `lyrebird-android`, `tor-android`) | Embedded C-Tor daemon lifecycle, Tor v3 Onion Hidden Service publishing, Pluggable Transports, and Bramble-inspired P2P rendezvous concepts. | GPL-3.0-or-later / BSD-3-Clause |
| **[The Tor Project](https://www.torproject.org/)** & **[Guardian Project](https://guardianproject.info/)** (`arti-mobile-ex`, `tor`) | Core Tor anonymity network engine (C-Tor & Rust Arti SOCKS5 bridge) and pluggable anti-censorship transports. | BSD-3-Clause / MIT / Apache-2.0 |
| **[Southern Storm `noise-java`](https://github.com/rweather/noise-java)** | Embedded Java implementation of the Noise Protocol Framework (`Noise_XX_25519_ChaChaPoly_SHA256`), Curve25519, ChaCha20-Poly1305, and BLAKE2. | MIT License |
| **[Cwtch (Open Privacy)](https://cwtch.im/)** | Architectural inspiration for metadata-resistant, untrusted Tor v3 store-and-forward offline mailboxes. | MIT / BSD License |
| **[Signal Protocol Specifications](https://signal.org/docs/)** | Public cryptographic specification for our clean-room Kotlin `DoubleRatchet` forward-secrecy session engine. | Public Specification |
| **[Nostr Protocol](https://github.com/nostr-protocol/nostr)** | Decentralized relay gossip protocol (NIP-01, NIP-04, NIP-13 Proof-of-Work, and Geohash channels). | Public Domain (CC0) |
| **[SQLCipher for Android (Zetetic LLC)](https://www.zetetic.net/sqlcipher/)** | Full-database AES-256 encryption for local message, contact, and group storage. | BSD-Style Community License |
| **[Nordic Semiconductor BLE](https://github.com/NordicSemiconductor/Android-BLE-Library)** & **[NanoHTTPD](https://github.com/NanoHttpd/nanohttpd)** | Reliable Android BLE GATT operations and embedded Tor mailbox HTTP server. | BSD-3-Clause License |

### Summary of Major Modifications & Upgrades (per GPLv3 §5a)
While preserving wire compatibility with the `bitchat` BLE mesh protocol, **Seamless** has been extensively modified and expanded (2025–2026) by Aquarius Groups with new subsystems:
- **Global Tor v3 Onion P2P Engine**: Dual-engine Arti + C-Tor hidden service hosting, IPv6-aware guard selection, persistent circuit pooling, and `.onion` peer-to-peer messaging.
- **Encrypted Tor Group Messaging**: Multi-party Tor group chat with canonical `groupMessageId` synchronization, quoted replies, encrypted emoji reactions, live per-recipient delivery counters, and one-tap retry.
- **Untrusted Offline Mailboxes**: Embedded NanoHTTPD Tor mailbox server and client for asynchronous store-and-forward delivery when peers are offline.
- **HyperDrop Wi-Fi Direct Engine**: High-speed local P2P file and media transfer with DNS-SD (`_hyperdrop._tcp`) discovery and public `Downloads` export.
- **Signal-Style Double Ratchet & SQLCipher Storage**: Post-compromise session ratcheting (`DoubleRatchet.kt`) and encrypted Room persistence.
- **Obsidian & Optical Light UI**: Complete Jetpack Compose redesign, voice notes with dynamic waveforms, full-screen photo lightbox, and biometric app lock.

### Trademark & Non-Endorsement Disclaimer
*Seamless is an independent open-source project developed by Aquarius Groups. It is **not** affiliated with, sponsored by, or officially endorsed by PermissionlessTech (`bitchat`), The Briar Project, The Tor Project, Inc., The Guardian Project, Open Privacy (`Cwtch`), Signal Technology Foundation, or Zetetic LLC. "Tor" and the "Onion Logo" are registered trademarks of The Tor Project, Inc.*

---

## Contributing

Contributions from security researchers, protocol engineers, and open-source developers are welcome!

1. Fork the repository.
2. Create your feature branch (`git checkout -b feature/cool-enhancement`).
3. Commit your changes with descriptive messages (`git commit -m 'feat: Add adaptive BLE pacing'`).
4. Push to your branch (`git push origin feature/cool-enhancement`).
5. Open a Pull Request.

---

## License

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**.
- See **[`LICENSE.md`](LICENSE.md)** for the full GPL-3.0 license text.
- See **[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)** for upstream copyright notices, permissive component licenses (MIT, BSD-3-Clause, Apache 2.0, The Unlicense), and mandatory third-party attributions (including SQLCipher, Tor, Briar, Bitchat, and Southern Storm Software).

---

<p align="center">
    <strong>Engineered with privacy by Aquarius Groups.</strong><br>
    <em>Zero central servers. Zero phone numbers. Pure encrypted communication.</em>
</p>
