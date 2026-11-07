# Google Play Store Publishing Guide for Seamless (`com.seamless.droid`)

This guide walks you step-by-step through publishing **Seamless for Android** on the Google Play Store.

---

## Step 1: Generate Your Production Release Keystore (One-Time Setup)
Every Play Store app requires an upload signing key. Run this once in PowerShell inside the project root (`c:\Users\DELL\StudioProjects\seamless-android`):

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore seamless-release.jks -alias seamless -keyalg RSA -keysize 4096 -validity 10000
```
> **IMPORTANT:** Back up `seamless-release.jks` and your password in a safe offline location. It is already ignored in `.gitignore` so it will never be committed to GitHub.

---

## Step 2: Build the Signed Android App Bundle (`.aab`)
Google Play requires an **Android App Bundle (`.aab`)** rather than a raw `.apk`. Build it with your keystore variables:

```powershell
$env:SEAMLESS_KEYSTORE_PATH = "C:\Users\DELL\StudioProjects\seamless-android\seamless-release.jks"
$env:SEAMLESS_KEYSTORE_PASSWORD = "your-keystore-password"
$env:SEAMLESS_KEY_ALIAS = "seamless"
$env:SEAMLESS_KEY_PASSWORD = "your-key-password"
.\gradlew bundleRelease
```
Your signed Play Store bundle will be generated at:
`app\build\outputs\bundle\release\app-release.aab`

*(Alternatively, in Android Studio: click **Build > Generate Signed App Bundle / APK > Android App Bundle**, select `seamless-release.jks`, and choose `release`.)*

---

## Step 3: Set Up Your Google Play Console Account
1. Go to [Google Play Console](https://play.google.com/console/signup).
2. Register a Developer Account (one-time \$25 USD registration fee) and complete identity verification.
3. Click **Create app**:
   - **App name**: `Seamless`
   - **Default language**: `English (United States)`
   - **App or game**: `App`
   - **Free or paid**: `Free`

---

## Step 4: Complete Mandatory App Content & Policy Declarations
Inside the Google Play Console **Policy and programs > App content** section, complete these required forms:

1. **Privacy Policy URL**:
   - Host your [`PRIVACY_POLICY.md`](PRIVACY_POLICY.md) on GitHub Pages or provide the raw/public GitHub URL to `PRIVACY_POLICY.md`.
2. **Data Safety Section**:
   - Does your app collect or share any required user data types? **No** (Seamless operates 100% peer-to-peer with zero central servers, zero accounts, and zero telemetry).
3. **Sensitive Permissions Declaration**:
   - **Location (`ACCESS_FINE_LOCATION` on Android 8–11)**: Explain that Android OS requires location permission solely to scan for nearby Bluetooth Low Energy (BLE) mesh peers on Android 8–11, and that GPS coordinates are never collected or transmitted to any server.
   - **Foreground Service (`FOREGROUND_SERVICE_CONNECTED_DEVICE` & `FOREGROUND_SERVICE_DATA_SYNC`)**: Explain that the foreground service keeps the user's decentralized Bluetooth LE mesh node and Tor v3 Onion hidden service reachable so incoming peer-to-peer messages and file transfers are not dropped when the screen turns off.
4. **Government / Export Compliance (Encryption)**:
   - Seamless uses standard open-source cryptography (AES-256-GCM, ChaCha20-Poly1305, X25519, Ed25519, Tor). Select **Yes** for standard encryption eligible for open-source / publicly available mass-market exemption.
5. **App Access**:
   - Select **All functionality is available without special access** (since Seamless requires no login, phone number, or account).

---

## Step 5: Upload the `.aab` & Launch
1. Go to **Test and release > Production** (or **Closed testing** if using a personal developer account created after Nov 2023, which requires 12 testers opted-in for 14 days before unlocking Production).
2. Click **Create new release**.
3. Let Google Play manage your **Play App Signing** key, then upload `app\build\outputs\bundle\release\app-release.aab`.
4. Add release notes (from [`CHANGELOG.md`](CHANGELOG.md)), click **Next**, and click **Save and publish** for Google Play review!
