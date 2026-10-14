# Codex prompts — stickers (2026-10-14)

## When and how to run

1. **Prerequisite:** `feat/chat-polish-round2` (Android) and `feat/profile-photo-gif` (backend) are committed and
   merged to `main` in their repos. Prompt 1 edits `ChatScreen.kt`, `GifPicker.kt` (the composer sheet) and
   `ChatExtras.kt`, so starting from an older `main` would conflict.
2. In Codex, open the **One-on-One-Android** repo on an up-to-date `main`, and give it the sibling web repo
   (`../One-on-One`) read-only so it can read `docs/API-CONTRACT.md`.
3. Paste **Prompt 1** as-is. Wait for its PR, review it, then test it on the emulator with the dummy account.
4. **Prompt 2** is optional (stickers without a bubble). Run it only after Prompt 1 is merged. It touches both repos,
   so give Codex both, and expect one PR per repo. Prompt 2 includes a backend change. Deploy that backend first, then the app.
5. Run them one after the other, never at the same time (same files).

## Prompt 1 — Android sticker maker + library (no backend change)

```
Role: senior Android engineer (Kotlin, Jetpack Compose, Hilt, Room, Coil 3) on One-on-One-Android, a private 1:1 messenger.

Read first, in this order, and follow them:
- CLAUDE.md (non-negotiable rules: message path UI → ViewModel → MessageService → Transport; no invented endpoints; verification)
- docs/ARCHITECTURE.md, docs/PROGRESS.md (latest sections: "Chat polish round 2" and "GIPHY + profile photo")
- ../One-on-One/docs/API-CONTRACT.md §2 attachments, §4 image payload {path, mime, size, width, height}, §9
- Code you will build on:
  app/src/main/java/app/web/oneonone/ui/chat/GifPicker.kt   (ComposerEmojiSheet: Emoji | GIFs tabs — add a Stickers tab here)
  app/src/main/java/app/web/oneonone/ui/chat/ChatScreen.kt  (where ComposerEmojiSheet is shown; `composerEmoji` state)
  app/src/main/java/app/web/oneonone/ui/chat/FeatureViewModel.kt (upload/sendGif: the send path to copy)
  app/src/main/java/app/web/oneonone/data/media/MediaRepository.kt (upload(): image validation, size limits)
  app/src/main/java/app/web/oneonone/ui/chat/PartnerPhoto.kt (downscaledJpeg: EXIF rotation + downsampling pattern)
  app/src/main/java/app/web/oneonone/data/chat/ChatDatabase.kt (Room setup, if you store the library index in Room)

Objective: a WhatsApp-style sticker maker and sticker library inside the composer's emoji sheet (third tab "Stickers").
1. Create: "+" tile → PickVisualMedia → on-device background removal → toggle "Original / Cut-out" → crop (drag/pinch inside a
   square frame) → preview on a checkerboard → Save. 512×512, transparent background, WebP (lossless or quality ≥80), aim ≤100 KB.
2. Background removal: compare MediaPipe Image Segmenter (Apache-2.0, bundled .tflite) and ML Kit Subject Segmentation
   (Play services, Google APIs ToS) on cut-out quality for people, pets and objects, licence, and APK size; pick one; write the
   decision, licence and attribution into docs/ (new section in ARCHITECTURE.md). Process off the main thread, show progress,
   handle "no subject found" with a friendly message and allow keeping the original.
3. Library, per signed-in account (owner id), persisted across sessions: files under filesDir/stickers/<ownerId>/ plus an index
   (Room table or JSON) with id, createdAt, lastUsedAt, favourite. Sections: Favourites, Recent (last 16 used), My stickers,
   and 1–2 built-in packs of ORIGINAL artwork you create (vector drawables rendered to 512 WebP, or WebP assets). Never copy
   WhatsApp sticker assets. Long-press a sticker: Favourite/Unfavourite, Delete (with confirm). Clear the library on sign-out
   and account deletion (see AccountRepository / where other local data is cleared).
4. Send: tap a sticker → it is uploaded as a normal image attachment (image/webp, width/height 512) through the existing
   upload path (FeatureViewModel/MediaGateway; add a method that takes a File if needed) and sent as type "image".
   No new message type or payload field — the contract has none. Received stickers render as normal images; deleting uses the
   existing delete-for-everyone.
5. github.com/WhatsApp/stickers is for third-party pack apps (content providers for WhatsApp). Reuse only concepts (512×512,
   ≤100 KB, 96×96 tray icon) after checking its licence; do not integrate with WhatsApp.

Constraints: at most one new dependency (the segmenter); segmentation fully on-device (no network); no backend change;
no main-thread I/O; accessibility labels on every tile and control; light/dark themes; works at 360dp width; respect the
chat text-size setting (ProvideChatTextScale) for any text.

Verification (must run and report): ./gradlew assembleDebug lintDebug testDebugUnitTest — all green.
Add unit tests for: crop math, 512 scaling, WebP size cap/retry with lower quality, index favourite/recent ordering.
Update docs/PROGRESS.md (new section + Xiaomi/emulator checklist) and docs/ARCHITECTURE.md (additive Mermaid diagram).
Create branch feat/stickers, open a PR to main, DO NOT merge. Report: PR link, commands + results, the segmenter decision,
APK size delta, and anything not verified on a device.
```

## Prompt 2 — Stickers without a bubble (optional, after Prompt 1 is merged)

```
Role: full-stack engineer on One on One (Android repo One-on-One-Android + backend/web repo One-on-One).

Read first: One-on-One-Android/CLAUDE.md, One-on-One/CLAUDE.md, One-on-One/docs/API-CONTRACT.md §4 and §9,
One-on-One/backend/src/services/messageService.ts (payload validation for type image), One-on-One-Android
app/src/main/java/app/web/oneonone/ui/chat/MessageBubble.kt and FeatureUi.kt ("image" branch), and the web client's image message
renderer (grep client/src for the image message component).

Objective: a sticker shows as a free-floating image (no bubble background, no tail, max 160 dp / 160 px) on Android and web.
1. Backend: accept optional payload.sticker === true on type "image" only when mime is image/webp and size ≤ 512 KB; otherwise
   strip the field. Document it in API-CONTRACT.md (new dated section). Tests in backend/test. No migration needed.
2. Android: Prompt 1's sender sets sticker: true; validateFeaturePayload allows it; MessageBubble renders sticker images without
   background/border/tail with the time + ticks in a small pill under it.
3. Web client: same rendering.
Constraints: no production deploy, do not merge. Deploy order to tell the owner: backend first, then apps.
Verify: backend npm test + tsc; Android ./gradlew assembleDebug lintDebug testDebugUnitTest; web build/tests.
One PR per repo. Report links, results, and checks to run on the emulator.
```
