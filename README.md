# One on One — native Android

Private 1:1 messaging, Kotlin + Jetpack Compose. Same Play identity:
`app.web.oneonone`, minSdk 26, compile/target SDK 37 (Android 17), versionCode 5.
**A0 scaffold only:** native welcome screen, brand theme/icon and build pipeline.
Authentication, chat, notifications, alarms and calls arrive in A1–A7.

## Local setup

1. Open this repo in Android Studio with AGP 9.1.1 support or newer.
   Use JDK 21 (the Android Studio `jbr` works). Install Android SDK platform 37,
   build-tools 36.0.0 and platform-tools. Gradle 9.6.0 is supplied by the wrapper.
2. Copy `local.properties.example` to `local.properties`, set `sdk.dir`, and fill
   the four `VITE_*` values from `../One-on-One/client/.env`. They become
   `BuildConfig.API_URL`, `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `GOOGLE_WEB_CLIENT_ID`.
   Gradle properties override environment variables, which override local.properties.
   Never use a Supabase service-role key.
3. Copy `../One-on-One/android/app/google-services.json` to
   `app/google-services.json`. Reuse Firebase project `one-on-one-508202` and its
   existing Android registration. Both local files are gitignored.

The reference `.env` currently uses localhost for the API and omits the Google
web client ID. A0 does not call the network. Before A1 phone testing, set the
deployed HTTPS API origin and the web OAuth audience documented in the API
contract (or use a deliberately configured development server). `localhost` on
a phone is the phone itself. Release CI requires all four nonempty settings and
HTTPS URLs. Debug CI works without secrets and skips Google Services when the
file is absent; this is deliberately not an FCM-ready artifact.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
.\gradlew.bat assembleDebug lintDebug testDebugUnitTest
.\gradlew.bat installDebug  # only on the owner's chosen test device
```

Linux/macOS: `./gradlew assembleDebug lintDebug testDebugUnitTest`.
Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Lint/test reports are in
`app/build/reports/`. Firebase token auto-init is enabled by the authenticated A3
registration lifecycle; API-contract changes remain Claude-owned.

## CI and releases

`android-build` runs debug assembly, strict lint and JVM tests on PRs and main
pushes, then uploads the debug APK/reports. Manual dispatch runs those checks
first, then produces **signed AAB + APK** using `versionCode` and `versionName`
inputs. PRs also smoke-build unsigned minified APK/AAB, run release lint and native
16 KB static checks, and upload `android-release-smoke-unsigned` with R8 mapping.
Unsigned smoke outputs are for inspection only. See [release checklist](docs/RELEASE-CHECKLIST.md)
for the Data Safety draft and required integration/device/signing gates.

Set these Actions secrets **in this Android repo** (web repo secrets do not carry
over). Use the existing values; never create another upload keystore:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 existing upload keystore, alias `oneonone-upload` |
| `ANDROID_KEYSTORE_PASSWORD` | Existing store password |
| `ANDROID_KEY_PASSWORD` | Existing alias password |
| `GOOGLE_SERVICES_JSON_BASE64` | Base64 existing Firebase Android JSON |
| `VITE_API_URL` | Deployed Railway HTTPS origin |
| `VITE_SUPABASE_URL` | Supabase project HTTPS URL |
| `VITE_SUPABASE_ANON_KEY` | Public anon key |
| `VITE_GOOGLE_WEB_CLIENT_ID` | Google Web OAuth client ID from the contract |

CI checks the restored key and both output signers against **both** fingerprints:

- SHA-1: `36:A9:69:D6:10:20:E0:7E:33:79:9F:0C:04:CE:F5:1A:63:BB:90:C6`
- SHA-256: `4B:0E:07:42:D4:9B:3A:30:4F:43:C1:3F:44:1E:62:06:91:CE:98:A8:A9:D6:09:5F:E0:71:0F:30:74:C6:74:57`

Download the `android-release` artifact after a successful dispatch. Every Play
upload needs a versionCode greater than every previous upload on every track.
The Capacitor candidate is 4 / 1.0.3; the native default starts at 5 / 2.0.0-dev.
Local signing uses the optional `ANDROID_*` entries in local.properties;
`bundleRelease assembleRelease` without a configured key produces unsigned
outputs. Local outputs are not certificate-verified; use CI for release artifacts.

Google sign-in needs Android OAuth registrations for the **debug**, **upload**,
and **Play App Signing** certificates (same package). Run `./gradlew signingReport`
to find this machine's debug SHA-1/SHA-256; register both in the existing Firebase
Android app, and register the debug SHA-1 in Google Cloud Android OAuth.
Register the upload fingerprints above and the Play App Signing SHA-1/SHA-256
from Play Console → App integrity. Never replace the existing Firebase app.
Auth is implemented; live sign-in and production signing registrations still need
owner verification on the final signed build.

This machine's debug certificate (checked with keytool during A0):

- SHA-1: `ED:02:08:A3:38:18:A4:18:40:AC:EF:D9:BD:C2:B2:0C:ED:37:55:9D`
- SHA-256: `A9:37:BF:74:3D:1D:DA:AD:96:94:36:43:C1:CF:AC:F4:25:61:11:6B:C5:89:7B:94:AA:B8:DD:57:9A:34:F8:2C`

Other developers and CI use different debug certificates; register their own
fingerprints before testing Google sign-in.

## Architecture and milestone gate

The full requested stack is pinned in `gradle/libs.versions.toml`: Compose /
Material 3, Navigation, Hilt + KSP, Retrofit/OkHttp/serialization, Socket.IO 2.x
(v4-server compatible), supabase-kt Auth + Credential Manager, Room, DataStore,
WorkManager, Firebase Messaging, Stream WebRTC and Coil. Dependencies are wired;
the owned auth/chat/normal-push/feature milestones are merged. Claude owns alarm
and calls; their seams remain placeholders until A4/A5 integration.

The backend remains in the read-only sibling web repo. Its
`docs/API-CONTRACT.md` is the only API authority. The copy here is a reference
snapshot, not a second independently editable specification.

See [architecture](docs/ARCHITECTURE.md), [progress and Xiaomi checklist](docs/PROGRESS.md)
and [full instructions](docs/prompts/gpt-sol-6.1-android.md). A0 is the initial
commit on main; A1–A7 each get a branch + PR. Follow the current task prompt for
merge authority. The [phase-2 prompt](docs/prompts/gpt-sol-6.1-phase2.md) authorizes
agent merges only after green CI and continues through owned milestones while
the owner performs Xiaomi checkpoints. Play uploads remain manual.

Platform references checked for A0: [Android 17](https://developer.android.com/about/versions/17),
[AGP 9.1.1 compatibility](https://developer.android.com/build/releases/agp-9-1-0-release-notes),
[Compose BOM](https://developer.android.com/develop/ui/compose/bom),
[Stream WebRTC](https://github.com/GetStream/webrtc-android).
