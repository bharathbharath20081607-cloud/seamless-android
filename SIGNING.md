# How to sign your Seamless release (one-time setup)

Release APKs MUST be signed, otherwise Android refuses to install them (and Google's developer
verification will not accept them). Keep the key file private and BACK IT UP: if you lose it you can
never publish an update that installs over the old version.

## Step 1: Create your key (run once, in PowerShell, inside the project folder)
```powershell
keytool -genkeypair -v -keystore seamless-release.jks -alias seamless -keyalg RSA -keysize 4096 -validity 10000
```
It asks for a password and some details. Write the password down somewhere safe.
`*.jks` is already in `.gitignore`, so the file will NOT be uploaded to GitHub by accident.

## Step 2a: Build a signed APK on your own PC
```powershell
$env:SEAMLESS_KEYSTORE_PATH = "C:\full\path\to\seamless-release.jks"
$env:SEAMLESS_KEYSTORE_PASSWORD = "your-password"
$env:SEAMLESS_KEY_ALIAS = "seamless"
$env:SEAMLESS_KEY_PASSWORD = "your-password"
.\gradlew assembleRelease
```
Result: `app\build\outputs\apk\release\app-release.apk` (signed, installable).

## Step 2b: Let GitHub build it for you (tags like v1.5.2)
In your GitHub repo go to **Settings > Secrets and variables > Actions > New repository secret** and add:

| Secret name | Value |
|---|---|
| `SIGNING_KEY_BASE64` | output of `[Convert]::ToBase64String([IO.File]::ReadAllBytes("seamless-release.jks"))` |
| `KEY_STORE_PASSWORD` | your keystore password |
| `KEY_ALIAS` | `seamless` |
| `KEY_PASSWORD` | your key password |

Then push a tag: `git tag v1.5.2` and `git push origin v1.5.2`. The workflow builds, signs, and publishes
the APK plus a `.sha256` checksum to GitHub Releases.

## Step 3 (Android developer verification)
Google is rolling out developer verification for apps installed outside Google Play (pilot countries from
30 Sep 2026, worldwide in 2027). Register your developer account and package name
`com.seamless.droid` in the Android Developer Console, and sign every release with the SAME key.
Check the official Android developer site for the current steps, since the program is still rolling out.
