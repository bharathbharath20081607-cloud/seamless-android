# Third-Party Open-Source Notices & Credits

**Seamless for Android** is built upon and incorporates open-source software, cryptographic libraries, and protocol specifications from the decentralized privacy community. We gratefully acknowledge the original authors, maintainers, and contributors of these projects.

Pursuant to the terms of the GNU General Public License v3.0 (GPL-3.0), the Apache License 2.0, the MIT License, The Unlicense, and the 3-Clause BSD License, the following attributions, copyright notices, modification summaries, and license texts are provided.

---

## 1. Parent Projects & Architectural Lineage

### A. bitchat & bitchat-android
- **Repositories**:
  - [permissionlesstech/bitchat](https://github.com/permissionlesstech/bitchat) (iOS / macOS — Protocol Specification)
  - [permissionlesstech/bitchat-android](https://github.com/permissionlesstech/bitchat-android) (Android Client Foundation)
- **Authors**: PermissionlessTech / jackjackbits and open-source contributors
- **License**: The Unlicense (Public Domain) / GNU General Public License v3.0 (GPL-3.0)
- **Usage & Statement of Modifications (per GPLv3 §5a)**:
  - Seamless retains backward-compatible 13-byte binary packet framing and Bluetooth Low Energy (BLE) GATT mesh interoperability with the original `bitchat` protocol while substantially re-architecting and expanding the application (2025–2026) with:
    1. **Tor v3 Onion P2P Transport (`com.seamless.android.net`)**: Embedded dual-engine Tor integration (Arti + C-Tor via `onionwrapper`), hidden service hosting, circuit management, and `.onion` peer-to-peer streaming.
    2. **Untrusted Offline Store-and-Forward Mailboxes**: Embedded NanoHTTPD Tor mailbox server and client for asynchronous offline message delivery.
    3. **Encrypted Tor Group Messaging**: Multi-party group rooms over Tor v3 with canonical message synchronization, quoted replies, encrypted emoji reactions, live per-recipient delivery ACKs, and automatic retry workers.
    4. **Signal-Style Double Ratchet Engine (`com.seamless.android.crypto`)**: Full X25519 / HKDF-SHA256 / AES-256-GCM Double Ratchet state machine for forward secrecy and post-compromise security.
    5. **HyperDrop High-Speed Wi-Fi Direct File Transfer (`com.seamless.android.ui.fileshare`)**: Wi-Fi Direct P2P + DNS-SD zero-config discovery and high-speed media/file streaming.
    6. **BLE Mesh Reliability Upgrades**: Ed25519 broadcast authentication, Noise XX glare resolution, persistent parked delivery queues, and zero-allocation fragment reassembly.
    7. **Obsidian & Optical Light UI**: Complete Jetpack Compose UI overhaul, voice notes with live waveforms, biometric lock, and SQLCipher database encryption.

---

### B. Briar Project (`onionwrapper-android`, `lyrebird-android`, `tor-android`)
- **Project Website**: [https://briarproject.org](https://briarproject.org)
- **Repositories**:
  - [briarproject/onionwrapper](https://code.briarproject.org/briar/onionwrapper)
  - [briarproject/tor-android](https://code.briarproject.org/briar/tor-android)
  - [briarproject/lyrebird-android](https://code.briarproject.org/briar/lyrebird-android)
  - [briarproject/briar](https://code.briarproject.org/briar/briar)
- **Artifacts Linked**:
  - `org.briarproject:onionwrapper-android:0.1.3` — **GNU General Public License v3.0 or later (GPL-3.0-or-later)**
  - `org.briarproject:lyrebird-android:0.6.2` — **GNU General Public License v3.0 or later (GPL-3.0-or-later)**
  - `org.briarproject:tor-android:0.4.9.14` — **BSD 3-Clause License**
- **Usage**: Used for managing the embedded C-Tor daemon process, publishing v3 Onion Hidden Services on Android, and supporting Pluggable Transports (Lyrebird/obfs4). Architectural concepts from Briar's Bramble transport and rendezvous design also informed Seamless's Tor P2P layer.

---

### C. The Tor Project & Guardian Project (`Arti` & `C-Tor`)
- **Websites**: [https://www.torproject.org](https://www.torproject.org) | [https://guardianproject.info](https://guardianproject.info)
- **Repositories**:
  - [torproject/tor](https://gitlab.torproject.org/tpo/core/tor)
  - [torproject/arti](https://gitlab.torproject.org/tpo/core/arti)
  - [guardianproject/arti-mobile-ex](https://github.com/guardianproject/arti-mobile-ex)
- **Artifact Linked**: `info.guardianproject:arti-mobile-ex:1.2.3`
- **Licenses**:
  - **C-Tor**: 3-Clause BSD License
  - **Arti / arti-mobile-ex**: Dual-licensed under MIT License and Apache License 2.0
- **Tor Copyright & Non-Endorsement Notice**:
  ```text
  Copyright (c) 2001-2004, Roger Dingledine
  Copyright (c) 2004-2006, Roger Dingledine, Nick Mathewson
  Copyright (c) 2007-2025, The Tor Project, Inc.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are
  met:

  * Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.
  * Redistributions in binary form must reproduce the above
    copyright notice, this list of conditions and the following disclaimer
    in the documentation and/or other materials provided with the
    distribution.
  * Neither the names of the copyright owners nor the names of its
    contributors may be used to endorse or promote products derived from
    this software without specific prior written permission.
  ```
  *Trademark Disclaimer: "Tor" and the "Onion Logo" are registered trademarks of The Tor Project, Inc. Seamless is an independent open-source project and is not affiliated with, sponsored by, or endorsed by The Tor Project, Inc.*

---

### D. Southern Storm Software (`noise-java`)
- **Repository**: [rweather/noise-java](https://github.com/rweather/noise-java)
- **Author**: Rhys Weatherley / Southern Storm Software, Pty Ltd.
- **Location in Source**: `app/src/main/java/com/seamless/android/noise/southernstorm/`
- **License**: MIT License
- **Copyright Notice**:
  ```text
  Copyright (C) 2016 Southern Storm Software, Pty Ltd.

  Permission is hereby granted, free of charge, to any person obtaining a
  copy of this software and associated documentation files (the "Software"),
  to deal in the Software without restriction, including without limitation
  the rights to use, copy, modify, merge, publish, distribute, sublicense,
  and/or sell copies of the Software, and to permit persons to whom the
  Software is furnished to do so, subject to the following conditions:

  The above copyright notice and this permission notice shall be included
  in all copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
  OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
  DEALings IN THE SOFTWARE.
  ```

---

### E. Cwtch (Open Privacy), Signal Protocol & Nostr
- **Cwtch (Open Privacy Research Society)**: [https://cwtch.im](https://cwtch.im) (MIT / BSD License) — Architectural inspiration for metadata-resistant, untrusted Tor v3 offline store-and-forward mailboxes.
- **Signal Protocol Specifications**: [https://signal.org/docs/](https://signal.org/docs/) (Public Specifications by Trevor Perrin and Moxie Marlinspike) — Clean-room Kotlin implementation of the Double Ratchet and X3DH-inspired session ratcheting algorithms (`DoubleRatchet.kt`, `SeamlessRatchetManager.kt`). *Signal is a registered trademark of Signal Technology Foundation; Seamless is an independent project and is not affiliated with or endorsed by Signal.*
- **Noise Protocol Framework**: [https://noiseprotocol.org/](https://noiseprotocol.org/) (Public Domain Specification by Trevor Perrin) — Used for `Noise_XX_25519_ChaChaPoly_SHA256` mutual authentication handshakes.
- **Nostr Protocol (NIPs)**: [https://github.com/nostr-protocol/nostr](https://github.com/nostr-protocol/nostr) (Public Domain / CC0) — Clean-room implementation of NIP-01, NIP-04, NIP-13 Proof-of-Work, and Geohash relay channels.

---

## 2. Third-Party Libraries & Mandatory License Notices

### A. SQLCipher for Android (`net.zetetic:sqlcipher-android:4.6.1`)
- **Website**: [https://www.zetetic.net/sqlcipher/](https://www.zetetic.net/sqlcipher/)
- **License**: BSD-Style License (SQLCipher Community Edition)
- **Mandatory Attribution Notice**:
  ```text
  Copyright (c) 2008-2024 Zetetic LLC
  All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are met:
      * Redistributions of source code must retain the above copyright
        notice, this list of conditions and the following disclaimer.
      * Redistributions in binary form must reproduce the above copyright
        notice, this list of conditions and the following disclaimer in the
        documentation and/or other materials provided with the distribution.
      * Neither the name of the ZETETIC LLC nor the
        names of its contributors may be used to endorse or promote products
        derived from this software without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY ZETETIC LLC ''AS IS'' AND ANY
  EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
  DISCLAIMED. IN NO EVENT SHALL ZETETIC LLC BE LIABLE FOR ANY
  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
  ```

### B. NanoHTTPD (`org.nanohttpd:nanohttpd:2.3.1`)
- **Repository**: [NanoHttpd/nanohttpd](https://github.com/NanoHttpd/nanohttpd)
- **License**: Modified BSD 3-Clause License
- **Copyright Notice**:
  ```text
  Copyright (c) 2012-2013 by Paul S. Hawke, 2001,2005-2013 by Jarno Elonen,
  2010 by Konstantinos Togias. All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are met:
  * Redistributions of source code must retain the above copyright notice,
    this list of conditions and the following disclaimer.
  * Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.
  * Neither the name of the NanoHttpd organization nor the names of its
    contributors may be used to endorse or promote products derived from this
    software without specific prior written permission.
  ```

### C. Nordic Semiconductor Android BLE Library (`no.nordicsemi.android:ble:2.6.1`)
- **Repository**: [NordicSemiconductor/Android-BLE-Library](https://github.com/NordicSemiconductor/Android-BLE-Library)
- **License**: BSD 3-Clause License
- **Copyright Notice**:
  ```text
  Copyright (c) 2018, Nordic Semiconductor
  All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are met:
  * Redistributions of source code must retain the above copyright notice, this
    list of conditions and the following disclaimer.
  * Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.
  * Neither the name of Nordic Semiconductor ASA nor the names of its
    contributors may be used to endorse or promote products derived from
    this software without specific prior written permission.
  ```

### D. The Legion of the Bouncy Castle (`org.bouncycastle:bcprov-jdk15on:1.70`)
- **Website**: [https://www.bouncycastle.org](https://www.bouncycastle.org)
- **License**: Bouncy Castle License (MIT-style)
- **Copyright Notice**:
  ```text
  Copyright (c) 2000-2023 The Legion of the Bouncy Castle Inc. (https://www.bouncycastle.org)

  Permission is hereby granted, free of charge, to any person obtaining a copy of this
  software and associated documentation files (the "Software"), to deal in the Software
  without restriction, including without limitation the rights to use, copy, modify, merge,
  publish, distribute, sublicense, and/or sell copies of the Software, and to permit
  persons to whom the Software is furnished to do so, subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all copies or
  substantial portions of the Software.
  ```

### E. Apache License 2.0 Libraries
The following libraries used by Seamless are licensed under the **Apache License, Version 2.0** ([https://www.apache.org/licenses/LICENSE-2.0](https://www.apache.org/licenses/LICENSE-2.0)):
- **AndroidX & Jetpack Compose** (`androidx.core`, `androidx.compose`, `androidx.lifecycle`, `androidx.navigation`, `androidx.room`, `androidx.work`, `androidx.camera`, `androidx.security:security-crypto`, `androidx.biometric`, `androidx.sqlite`, `androidx.exifinterface`) — Copyright (c) The Android Open Source Project
- **Google Tink Cryptographic Library** (`com.google.crypto.tink:tink-android`) — Copyright (c) Google LLC
- **Google Accompanist** (`com.google.accompanist:accompanist-permissions`) — Copyright (c) Google LLC
- **Google Gson** (`com.google.code.gson:gson`) — Copyright (c) Google Inc.
- **ZXing ("Zebra Crossing")** (`com.google.zxing:core`) — Copyright (c) ZXing Authors
- **Square OkHttp** (`com.squareup.okhttp3:okhttp`) — Copyright (c) Square, Inc.
- **Kotlin Standard Library & Coroutines** (`org.jetbrains.kotlin`, `org.jetbrains.kotlinx:kotlinx-coroutines-android`) — Copyright (c) JetBrains s.r.o.
- **Coil Image Loader** (`io.coil-kt:coil-compose`) — Copyright (c) Coil Contributors
