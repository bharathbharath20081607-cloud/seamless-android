# Seamless Tor Messaging Research and Design Record

Audience: Seamless Android engineering and release reviewers

Date: 2026-09-04

Scope: Direct Tor messaging, delayed delivery, media transfer, background behavior, and notifications.

## Evidence-Based Findings

1. Briar uses direct, encrypted device-to-device synchronization over Tor when Internet is available and adds a Mailbox for users who are online at different times. It also offers local Wi-Fi and Bluetooth transports. This supports a hybrid transport design, not a promise that direct Tor works while a recipient device is off. [Briar: How it works](https://briarproject.org/how-it-works/)

2. Cwtch uses Tor v3 onion services for peer-to-peer chat and explicitly requires both people to be online for a direct conversation. Its authenticated session setup supports the decision to show online only after authenticated traffic, not merely after a socket connects. [Cwtch: P2P chat](https://docs.cwtch.im/docs/chat/introduction/)

3. SimpleX separates durable chat delivery from large-file transfer. The current chat protocol sends a file description over the chat connection and uses XFTP for the bytes; this avoids turning a high-latency message path into a serialized attachment transport. [SimpleX Chat Protocol](https://simplex.chat/docs/protocol/simplex-chat.html) [SimpleX XFTP design](https://simplex.chat/blog/20230301-simplex-file-transfer-protocol.html)

4. SimpleX relay delivery persists tasks and jobs rather than treating a successful socket write as delivery. Seamless therefore keeps durable local queue records, idempotent IDs, acknowledgement handling, and retry state separate from visible chat items. [SimpleX Channels Protocol](https://simplex.chat/docs/protocol/channels-protocol.html)

5. Android cannot guarantee immediate background work. Periodic WorkManager work has a 15-minute minimum interval, and ordinary background services are limited. A user-visible foreground service is the appropriate mode for an active Tor connection. [Android background execution limits](https://developer.android.com/about/versions/oreo/background) [Android periodic work](https://developer.android.com/develop/background-work/services/alarms)

6. WhatsApp-style instant notification after an app process is stopped requires a push provider such as FCM or an equivalent always-on service. FCM is an external Google dependency and introduces metadata and policy trade-offs; it must be opt-in rather than being represented as fully decentralized Tor delivery. [Firebase Cloud Messaging on Android](https://firebase.google.com/docs/cloud-messaging/android/receive-messages)

7. Ricochet Refresh is the pure direct-onion baseline: a v3 onion address is the user identity and a Tor circuit is created for a peer conversation. Seamless keeps this identity and direct-connection model, but adds durable queues and mailbox fallback rather than claiming that a direct peer can receive while offline. [Ricochet Refresh](https://www.ricochetrefresh.net/)

8. Session achieves offline delivery through an incentivized node swarm that stores messages until expiry. That model requires a separate distributed storage network, validators, and economic incentives; it cannot be copied into a standalone Android client without operating equivalent infrastructure. [Session Network](https://docs.getsession.org/session-network) [Session routing and storage](https://docs.getsession.org/session-network/session-protocol/onion-requests-and-message-routing)

9. Tor onion services hide the host location and authenticate the onion address, but a client must still retrieve the signed service descriptor and establish a rendezvous circuit. This makes startup and reconnect latency intrinsic rather than an application bug that can honestly be promised as zero. [Tor onion services overview](https://community.torproject.org/onion-services/overview/)

## Adopted Architecture

- Direct path: persistent authenticated onion-service connection, message envelope encryption, replay checks, ACKs, and bounded reconnect attempts.
- Mailbox path: recipient-owned folder receives opaque end-to-end encrypted payloads. The mailbox uses a separate onion service mapped to the actual local mailbox port. The client polls only its own registered mailbox, never every contact's mailbox.
- Media path: a visible photo item is persisted before network transfer. Its transport frames have distinct IDs and only the final completed frame changes the visible delivery state. A bounded in-flight window replaces one-chunk-per-round-trip delivery.
- Background path: Tor foreground service maintains live delivery and local notifications while it is enabled. Periodic sync is recovery only, not a realtime claim.
- Trust path: contact identity and onion address must be authenticated before normal traffic. Key changes remain a blocking trust state.
- Release hardening path: use Tor v3 client authorization for restricted mailbox relays where the embedded wrapper exposes it, and require a mailbox capability or signed retrieval authorization at the application layer. [Tor client authorization](https://community.torproject.org/onion-services/advanced/client-auth/)

## Changes Applied In This Iteration

- Added a dedicated persistent mailbox onion identity and published the local mailbox server on port 18889 through that onion service.
- Stopped registering the user with every peer mailbox. Incoming mailbox exchange now saves only the recipient endpoint for sender fallback.
- Encoded mailbox ciphertext in Base64 JSON to avoid text-parser corruption of opaque bytes.
- Added local incoming notifications after persistence, suppressed when the relevant Tor chat is open.
- Kept photo chunks strictly ordered while allowing a bounded window of eight in-flight chunks after metadata acknowledgement.
- Added startup cleanup/retry and longer authenticated-presence grace windows to reduce false offline states after network changes.
- Kept the mailbox read/delete capability only on the mailbox owner's device. Contacts receive the mailbox endpoint but cannot fetch or delete its contents.
- Changed mailbox upload/delete handling so HTTP success follows a durable Room write; a full mailbox rejects new mail instead of silently evicting older mail.
- Changed text and final-image ACK handling so an acknowledgement is sent only after the visible chat record is durable. A failed local write releases deduplication so the sender can retry.
- Added durable short-lived image completion receipts so a sender retry after a receiver restart is acknowledged without duplicating the photo.
- Reduced active mailbox polling to 15 seconds. This improves foreground responsiveness but is not a stopped-app or zero-latency guarantee.
- Changed mailbox acknowledgement semantics: fetched mail is deleted only after the inbound handler reports a valid, durable acceptance, not merely after decryption is attempted.
- Redacted mailbox endpoint and recipient-folder values in the modified mailbox logs.

## Remaining Release Gates

The following are required before a public production claim:

- Test direct text, typing, read receipts, images, reconnect, and app restart on at least two physical Android devices over independent networks.
- Provision and test a separate always-on mailbox device or service. A mailbox hosted on the recipient's same phone cannot accept mail after that phone is powered off or its Tor service is stopped.
- Add bounded anti-abuse controls for mailbox uploads, such as recipient-issued write capabilities or a separately operated mailbox service with quota and abuse policies. The current endpoint permits deposits to a known recipient folder but protects only read/delete access.
- Add an explicit external-mailbox provisioning UI with access capability controls before presenting offline delivery as broadly available.
- Design a separate resumable encrypted media relay or XFTP-compatible data plane for large/offline images. The current direct attachment path is intentionally bounded to small images.
- Conduct an independent security review of identity binding, mailbox access control, encrypted storage, logging, and metadata behavior.

## Claim Ledger

| Claim | Evidence | Status |
| --- | --- | --- |
| Direct Tor requires both peers reachable | Cwtch documentation | Implemented model |
| Offline delivery requires a reachable mailbox | Briar documentation | Transport fixed; external provisioning remains |
| Large media should use a separate data plane | SimpleX protocol and XFTP design | Direction chosen; not complete |
| Android cannot guarantee instant stopped-app polling | Android documentation | Reflected in UI and release claims |
| Push adds an external dependency and metadata trade-off | Firebase documentation | Not enabled by default |
