# Google Play screenshots

Seven English screenshot scenes, framed by goldie at 1080 × 1920. Panorama layouts export two images for one scene. The native app uses its existing Love sunset wallpaper and matching bubble palette. The conversation is between Maya (female) and Arjun (male). All names, messages and feature cards are fictional demo content.

The store activities exist only in debug builds. The normal demo keeps its original fixtures; release builds contain none of the store activities. Each flow restarts its explicit activity because the debug APK has two launcher activities, which argent cannot resolve automatically.

## Recreate on the existing Pixel 9a

Run from the repository root with the Pixel 9a emulator running. Install the updated debug APK with `adb install -r`; keep the existing app data.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:ANDROID_HOME = 'C:/Users/aarah/AppData/Local/Android/Sdk'
$env:PATH = "$env:ANDROID_HOME/platform-tools;$env:ANDROID_HOME/emulator;$env:PATH"
.\gradlew.bat :app:assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk

npx.cmd -y goldie@0.5.0 help
$npmCache = npm.cmd config get cache
$env:GOLDIE_PACKAGE_ROOT = Get-ChildItem -LiteralPath (Join-Path $npmCache '_npx') -Directory |
    ForEach-Object { Join-Path $_.FullName 'node_modules/goldie' } |
    Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
$env:GOLDIE_CONFIG = (Resolve-Path goldie/goldie.config.ts).Path
node goldie/capture-pixel.mjs emulator-5554
npx.cmd -y goldie@0.5.0 frame
npx.cmd -y goldie@0.5.0 manifest
npx.cmd -y goldie@0.5.0 verify
npx.cmd -y goldie@0.5.0 studio --no-open
```

Replace `emulator-5554` with the serial reported by `adb devices` if needed. `capture-pixel.mjs` runs the argent flows and saves full-resolution ADB screenshots without clearing app data. This lets the existing Pixel 9a supply captures while goldie uses its bundled Pixel 10 Pro frame and Google Play output size; the standard goldie capture command requires a Pixel 10 Pro or Pixel 9 Pro hardware profile.

Finished images are in `out/screenshots/pixel-10-pro/en-US/`. The studio runs at http://localhost:4321. `goldie.config.ts` owns the order, headlines, backgrounds and typography; `.argent/flows/store-*.yaml` owns navigation. No preview video is configured because this request is for screenshots.

Screenshot alt text, in upload order:

1. A private conversation between Maya and Arjun over a sunset wallpaper, with gold and blue message bubbles.
2. A pastel letter to Arjun, signed by Maya, with the sunset chat behind it.
3. An emergency alarm from Arjun appears in the foreground with a red banner and an acknowledgement control; a letter remains on the second phone in the duo layout.
4. A shared choice reveals that both people picked a sunset walk over a movie night.
5. A shared countdown tracks the time until the next adventure together.
6. Maya and Arjun exchange heart and cat stickers over the sunset wallpaper.
7. Arjun appears in a video call with Maya's camera inset and the app's microphone, speaker, camera and hang-up controls.

The sticker scene uses the app's original Cats and Little signals artwork. The call scene reuses the production `CallContent` layout with a fixed demo duration and fictional video frames; it starts no microphone, camera or network connection. Portraits were created with the built-in imagegen tool and saved only in debug resources. Exact prompts and asset paths are in [caller-portraits.md](caller-portraits.md).

The studio's developer, rating and age fields are preview copy, not a verified live store listing.
