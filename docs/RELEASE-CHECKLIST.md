# Native Android release checklist

Prepared 2026-10-07 for `app.web.oneonone`. Default versionCode stays **5** and
versionName **2.0.0-dev**. Nothing has been uploaded to Play by this work.

## Release history

Latest signed build: **1.0.5 / versionCode 6** (2026-10-10, workflow run
[37998420889](https://github.com/aarahman04/One-on-One-Android/actions/runs/37998420889)).
Previous: 1.0.4 / 5 (2026-10-09). Version name/code are passed as `android-build`
workflow inputs at dispatch; the repo default stays 5 / 2.0.0-dev. The next Play
upload must use versionCode **7 or higher**. Full table: `docs/PROGRESS.md` → Releases.

A4 (alarm, PR #8), A5 (calls, PR #9) and the S1–S5 restyle (PRs #10–#14) are now
merged; the A7-era notes below that call them pending are historical. Owner Xiaomi
QA and authenticated minified-device QA are still required before a rollout.

## Status and gates

A1/A2/A3/A6 are merged. A7 adds R8/resource shrinking, minified build checks,
native-library geometry/ZIP checks and this runbook. **The app is not yet ready
for a Play rollout:** Claude's A4 alarm and A5 call implementations, owner Xiaomi
QA, production signing/configuration and authenticated minified-device QA remain
required. Placeholder alarm/call seams must not be marketed as working features.

No upload keystore or Actions secrets were available in this Android repo at A7
preparation. Local release artifacts are unsigned. Any explicitly labelled
`release-smoke-debug-signed.apk` is a throwaway copy signed with the existing debug
key for emulator evaluation, never a Play artifact or replacement upload key.

## Reproducible checks

Use Android Studio's JBR locally (`JAVA_HOME`); version/catalog/wrapper are pinned.

```powershell
./gradlew.bat assembleDebug lintDebug testDebugUnitTest
./gradlew.bat assembleRelease bundleRelease lintRelease
python scripts/check_native_libs.py --self-test
python scripts/check_native_libs.py app/build/outputs/apk/release/app-release-unsigned.apk app/build/outputs/bundle/release/app-release.aab
& "$env:LOCALAPPDATA/Android/Sdk/build-tools/36.0.0/zipalign.exe" -c -P 16 -v 4 app/build/outputs/apk/release/app-release-unsigned.apk
```

With a configured upload key, the APK is `app-release.apk` instead; CI requires
and verifies the original certificate. CI PR checks also build an unsigned R8
APK/AAB, check release lint, native LOAD/RELRO geometry, ZIP alignment and upload
`android-release-smoke-unsigned` with mapping. Preserve mapping.txt for retrace.

R8 keeps Socket.IO, Supabase/Ktor protocol/engine boundaries, WebRTC JNI,
serialization DTOs/generated serializers and Hilt entry points; library consumer
rules cover framework/generated DI, Room, WorkManager and Retrofit. Only Ktor's
guarded desktop debugger references get two exact JMX `-dontwarn` exclusions.
No custom baseline profile was recorded without an authenticated final-device
flow; dependency ART profiles still ship. Add a measured app profile after A4/A5.

The static native gate checks both 64-bit ABIs in APK/AAB, including read-only
page rounding against writable LOAD bytes. A RELRO endpoint not on a 16 KB page
can be safe when the entire LOAD region is read-only (the pinned graphics-path
binary has this layout). Static geometry isn't a runtime test: run the final app
on a 16 KB device/emulator with compatibility mode disabled. See [Android page-size
guidance](https://developer.android.com/guide/practices/page-sizes) and the
[linker implementation](https://android.googlesource.com/platform/bionic/+/main/linker/linker_phdr.cpp).

## Data Safety draft answers

These answers reflect the native code, the authoritative API and the existing
web privacy text (`client/src/pages/legalShared.ts`). The owner must confirm live
provider/logging configuration and reconcile **all versions still distributed
under this package** before submitting. This is a reviewable draft, not a Console
submission. Google defines collection/sharing and its exceptions in [Data Safety
help](https://support.google.com/googleplay/android-developer/answer/10787469).

- Collects user data: **Yes**. All transport uses HTTPS/WSS; answer encryption in
  transit **Yes** after verifying production endpoints. The messaging service is
  **not end-to-end encrypted**; server-side encryption at rest isn't E2EE.
- Account creation: **Yes**, Google OAuth/Supabase. Account/data deletion: **Yes**,
  native Settings → Delete account and public
  [delete-account page](https://one-on-one-mu.vercel.app/delete-account). Reports
  retain safety evidence after deletion; explain that exception in the policy.
- Data is used for app functionality/account management and safety/fraud prevention.
  No ads, marketing, sale, personalization or analytics SDK is implemented. Backend
  provider access/request logs still need an owner audit; don't infer their absence
  from the lack of a client analytics SDK.
- Messages/media remain stored until connection termination/account deletion;
  stored categories aren't ephemeral. Completed exports/downloads are user copies.
  Date of birth stays on-device (only an age-confirmed flag is retained); appearance,
  letter signature, loaded-message search and drafts are local. No SMS, contacts,
  device call-log permission, background location or broad photo/storage access.

| Play data type | Collected / optionality | Evidence and purpose | Sharing answer to review |
| --- | --- | --- | --- |
| Personal info: name, email, user IDs | Yes; account sign-in required | Google account metadata reaches Supabase Auth; app user/connection IDs, account management | Google/Supabase service processing; verify provider exceptions |
| Photos | Yes; sending media optional | Uploaded images and Google profile photo URL/metadata; app functionality | Chosen peer; private Supabase storage/provider processing |
| Other in-app messages | Yes; text/letters/card content optional, including notification previews | Database message/payload; FCM receives sender nickname and preview | Peer is a user-initiated recipient; FCM/Supabase/Railway provider processing |
| Voice or sound recordings | Yes; voice notes optional | Recorded AAC and other allowed voice media uploaded to private storage | Chosen peer and storage processing |
| Files and docs | Yes; file uploads optional | User-selected document bytes/name/MIME/size | Chosen peer and storage processing |
| Approximate and precise location | Yes; snapshot optional | Coarse/fine permission, one coordinate snapshot; server/peer; visible card sends an approximate map tile/IP to OSM | Include OSM in review; a viewer needn't be the sender and has no separate map consent. Conservatively declare sharing unless an applicable exception is confirmed |
| Other user-generated content | Yes; reports and nickname optional | Report reason/category/snapshot and nickname; safety and app functionality | Safety review/provider processing; report evidence is retained |
| Other actions / app interactions | Yes; reactions, read/delivered times and leave steps | Server-stored feature interactions, not analytics; functionality | Peer/provider processing |
| Device or other IDs | Yes; notifications optional, SDK can collect installation data | FCM token and Firebase installation ID for delivery; SDK/user-agent/app-version metadata | Firebase/provider processing; confirm final SDK disclosure |
| Contacts / relationship information | Yes; connection needed for chat, nickname optional | Single connection and interaction history form relationship metadata under Play's broad definition; no device address-book access | Peer/provider processing; include the final A5 call logs in the audit |
| Videos and real-time call audio | A5-dependent; not implemented in this lane | Web protocol uses peer media/DTLS-SRTP and TURN, with no recording; call logs contain kind/outcome/duration | Claude must audit final media, TURN/IP and ephemeral processing before release |
| Crash logs / diagnostics / other performance data | No client collection implemented; provider audit required | No Crashlytics, Analytics or remote crash reporting dependency; local logs aren't sent by this app | Amend if infrastructure or final SDKs send diagnostics |

The Firebase Messaging SDK transitively includes Firebase Installations; declare
its installation identifier and account for app/SDK version metadata. Analytics
and BigQuery delivery export aren't enabled by this native code. Confirm against
[Firebase's SDK disclosure](https://firebase.google.com/docs/android/play-data-disclosure).

Actual recipients/providers must be disclosed in the privacy policy even if a
specific transfer qualifies for a Play sharing exception. User-initiated recipient
transfers and processors acting solely on the developer's behalf can qualify;
these exceptions require checking the actual arrangements. Don't simply mark
'no sharing' for every provider. Calls/A4/A5 and old Capacitor/PWA versions require
owner reconciliation before the global package disclosure is submitted.

## Manual owner steps in Play / Firebase / Google Cloud

1. **Finish integration and device gates.** Merge Claude A4/A5 with green CI; repeat
   owned feature checks below on the final main SHA. Fix owner-reported failures
   before preparing a release. Audit final manifest/services and SDK disclosure.
2. **Restore existing Android repo Actions secrets**, not web-repo-only secrets:
   `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_PASSWORD`,
   `GOOGLE_SERVICES_JSON_BASE64`, `VITE_API_URL`, `VITE_SUPABASE_URL`,
   `VITE_SUPABASE_ANON_KEY`, `VITE_GOOGLE_WEB_CLIENT_ID`. Keep package
   `app.web.oneonone`, Firebase project `one-on-one-508202`, alias `oneonone-upload`.
3. **Certificate registrations.** Retain debug, upload and Play App Signing Android
   OAuth registrations in the same Google Cloud/Firebase project. On this machine,
   debug SHA-1 is `ED:02:08:A3:38:18:A4:18:40:AC:EF:D9:BD:C2:B2:0C:ED:37:55:9D`.
   Upload SHA-1 is `36:A9:69:D6:10:20:E0:7E:33:79:9F:0C:04:CE:F5:1A:63:BB:90:C6`;
   SHA-256 is `4B:0E:07:42:D4:9B:3A:30:4F:43:C1:3F:44:1E:62:06:91:CE:98:A8:A9:D6:09:5F:E0:71:0F:30:74:C6:74:57`.
   Get the distinct Play signing certificate from App integrity; it must also be
   registered for Play-installed Google sign-in. Never replace the upload key.
4. **Choose an authorized new version.** Check every track's highest uploaded code
   in App bundle explorer; 5 is only this repo's current default. Every eventual
   upload must exceed previously used codes. The owner supplies the final code/name;
   do not upload a dev-labelled build or reuse a consumed code.
5. **Run manual android-build on final main** with those inputs. Download only
   `android-release`, verify both SHA checks, package/version, R8 mapping and native
   checks. Unsigned smoke artifacts and debug-signed copies are excluded. Back up
   the original key/mapping securely. No Play dispatch/upload is performed here.
6. **App content/store review.** Confirm Privacy Policy and deletion URL work, fill
   Data Safety from the audited table, age/content rating/18+ target audience,
   no-ads declaration, user-generated-content reporting/blocking, support contact
   and child-safety standards. Complete full-screen-intent and foreground-service
   declarations for the **actual final A4/A5 uses**, with required evidence/demo.
   Automatic full-screen access isn't guaranteed for this app; verify eligibility
   under [Google's service/full-screen rules](https://support.google.com/googleplay/android-developer/answer/13392821).
7. **Internal testing first.** After the checks and real signed-device tests pass,
   the owner creates an internal release in the existing listing, uploads the
   verified AAB, adds testers and release notes, reviews warnings/pre-launch report,
   and tests the Play-installed update from Capacitor with the Play signing key.
   Check persisted account/session migration, permissions/channels and media/history;
   an unsupported old local session must lead safely to Google sign-in.
8. **Further rollout stays manual.** If this personal account is subject to the
   newer-account production gate, the current rule is at least 12 testers opted
   into closed testing continuously for 14 days before requesting production
   access. The old web note says 20; use the current [official testing rule](https://support.google.com/googleplay/android-developer/answer/14151465)
   and the account's Console requirements. Review tester feedback before promoting.

## Final Xiaomi / HyperOS checklist

Use two disposable test accounts plus the web peer; test a production-configured,
minified APK and then a Play-installed internal build. Emulator launch alone does
not validate any of these. Detailed A2/A3/A6 checklists are in PROGRESS.md.

- [ ] Google login with registered signer, restoration/refresh, 401 retry/sign-out,
      age/consent, codes/request/cancel/accept/decline; block list/logout/deletion.
- [ ] >50 history, concurrent echo/ack, offline outbox/relaunch, reconnect resync,
      duplicate-risk warning after five minutes, read/delivered, reactions/replies.
- [ ] Killed/swiped normal notifications, foreground suppression, inline reply,
      mark read, reboot/token rotation, account change and stale actions. Don't
      confuse Android Force stop with recents dismissal; reopen after Force stop.
- [ ] HyperOS Background autostart and No restrictions; permission denial/recovery,
      first-login onboarding, settings fallbacks and lockscreen privacy.
- [ ] All structured features and media/native-web interop, EXIF removal, size/MIME
      rejection, restoration/failed-upload retry, signed-URL expiry, audio interruption.
- [ ] Appearance/wallpapers, nickname, copy/search/quote paging, TXT/JSON/HTML export,
      report/block and server-gated leave; large fonts/TalkBack/rotation/process loss.
- [ ] Claude A4: killed-app alarm, lockscreen/FSI denied fallback, ack/cancel uses
      raise server id, silence/tap/expiry stops service AND notification, replay doesn't ring.
- [ ] Claude A5: earpiece default, speaker/video/headsets/Bluetooth, volume/proximity,
      incoming killed-app Answer/Decline/end, audio mode restoration and call logs.
- [ ] Final API 26 and API 34+ permission/service behavior; 16 KB runtime with compat
      disabled; native JNI paths, minified sign-in/socket/background work and media.
- [ ] Signed native update from old Play app retains identity and safely handles
      storage/auth migration; reinstall isn't an upgrade test. Review pre-launch report.

Physical QA, signed CI execution, Console declarations and uploads are owner tasks.
No release-ready claim is made while these gates or Claude A4/A5 remain pending.
