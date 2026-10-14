# Master plan — chat polish round 2 (2026-10-14)

Scope: Android only (owner decision). Web repo untouched. No commit / PR / merge until the owner says so.
Source: Gather note `gather-2026-10-14-0956.md` (tasks A–O).

## Audit findings (what already exists)

| Task | State in repo | Gap |
|---|---|---|
| A animations | message-enter, reaction pop, bar in/out exist. Ticks swap with no transition (`MetaRow`). Send/mic swap is an `if`. Voice card: static progress, no upload state. | Receipt cross-fade; send↔mic animation; voice play/pause icon crossfade + upload progress; delete animation |
| B notifications | FCM → `PushRouter` → `MessageNotifications`; Workers for registration | Audit by reading; fixes only where provable without a device |
| C alarm | Silence action stops the ring locally only (`handled`), never acks. Chat still shows glow/banner afterwards. | Silence ⇒ send ack; in-chat ⇒ in-app ack UI, quiet notification |
| D delete | Server removes media (contract §8). Client `delete()` exists, text-only UI gating? | Verify media/voice/image delete reaches server; animation |
| E composer | paperclip inside pill, mic/send right | Emoji left, clip + camera right, camera capture |
| F reactions | Quick bar + "+" + `emoji2-emojipicker` exist | Verify picked emoji reacts; picker blur (emoji2 renders vector text) |
| G GIF | contract allows `image/gif` uploads | Picker needs a GIF provider (key/ToS) — **owner decision / Codex** |
| H stickers | none; no sticker message type in contract | **Backend gap**: send as `image` (webp) is possible; library/maker is client work — Codex |
| I location card | `FlowRow` of two buttons, label in heading | Layout fix |
| J reply meta | `TextWithMeta` pins meta bottom-right for plain text; reply quote stretches width | Verify with quote; fix if meta drifts |
| K tails | 6dp tail on first of group, top corner | Typing bubble tail; WhatsApp curvature |
| L smoothness | item enter animation only for live rows | Audit LazyColumn keys / scroll-to-bottom |
| M text size | none | New setting in DevicePreferences + Settings + theme scale |
| N avatars | none (`ChatHeader` shows initial) | Custom partner photo = local-only (no backend). Own photo = **backend gap** (no endpoint/storage) |
| O audit | – | Final pass, previews, a11y, reduced motion |

## Allocation

| Owner | Tasks | Files owned |
|---|---|---|
| Opus (lead) | C, B audit, D verify, N-local, integration, build, docs | alarm/*, push/*, ChatScreen.kt, ChatViewModel.kt, MessageService.kt, ChatHeader.kt |
| Sonnet S1 | A (receipts, voice), I, J, K | MessageBubble.kt, FeatureUi.kt, cards/*, BubbleGrouping.kt |
| Sonnet S2 | E, A (send arrow), camera capture | Composer.kt (+ `ic_*` drawables, new camera/emoji icons) |
| Sonnet S3 | M text size | DevicePreferences.kt, Settings*.kt, ui/theme/Type.kt, Theme.kt |
| Codex (prompts) | G GIF, H sticker maker/library, N-personal-photo (needs backend) | see CODEX EXECUTION PROMPTS section |

No agent runs Gradle (single build tree). Opus builds and runs tests after merge of edits.

## Verification
`./gradlew assembleDebug lintDebug testDebugUnitTest`; no device claims.

## Result (2026-10-14)

Branch `feat/chat-polish-round2`, uncommitted. `./gradlew assembleDebug lintDebug testDebugUnitTest`: BUILD SUCCESSFUL.
Nothing device-tested.

| Req | Status | Where |
|---|---|---|
| A receipts / send arrow / voice / delete anim | Completed, Not Verified on device | MessageBubble.MetaRow, Composer, FeatureUi voice |
| A popup/modal enter/exit | Already animated (reaction bar, banners); dialogs keep platform defaults | – |
| B notifications | Completed (inline delivery in FCM window), Not Verified | OneOnOneMessagingService, PushWorkers.deliverMessagePush |
| B "abrupt loading on open" | Not changed, Not Verified (needs a device trace) | – |
| C alarm "few seconds" bug | Completed (backup notification INSISTENT + promoted on full-screen open), Not Verified | AlarmService, MainActivity |
| C Silence = ack; quiet in chat | Completed, Not Verified | AlarmCoordinator, HandledAlarms, NotificationChannels |
| D media delete | Server already correct; local delete of unsent bubbles added | ChatScreen, MessageService.discardUnsent |
| E composer + camera | Completed, Not Verified | Composer, ChatScreen |
| F picker reaction fix | Completed (sheet hoisted); emoji blur Not Verified (emoji2 view, needs device) | ChatScreen, ChatExtras |
| G GIF display/send/receive/delete | Completed for gallery GIFs (decoder registered) | OneOnOneApplication |
| G GIF search picker | Completed (GIPHY via backend proxy), Not Verified; needs GIPHY_API_KEY on Railway | backend gifService, GifPicker.kt |
| H sticker maker/library | Pending Codex: `docs/prompts/codex-stickers-2026-10-14.md` | – |
| I location card | Completed, Not Verified | FeatureUi |
| J reply meta | Completed (real bug: meta followed text width, not bubble width) | MessageBubble.TextWithMeta |
| K tails + typing tail | Completed, Not Verified | MessageBubble.bubbleTail, ChatExtras.TypingBubble |
| L smoothness audit | Not done (needs device profiling) | – |
| M text size | Completed, Not Verified | DevicePreferences, SettingsScreens, Theme |
| N custom partner photo | Completed (device-only) | PartnerPhoto, ChatHeader |
| N own profile photo | Completed (backend + Android), Not Verified; needs migration 037 | backend avatarService, ProfileViewModel, Settings |
| O audit | Partial: build/lint clean, a11y labels on new controls; no reduced-motion helper exists in the codebase | – |

## CODEX EXECUTION PROMPTS

Only stickers (H) remain for Codex: `docs/prompts/codex-stickers-2026-10-14.md` (Prompt 1 Android maker + library, Prompt 2 optional
bubble-less rendering). GIF (G) and own profile photo (N) were built in-session instead (owner decision 2026-10-14: GIPHY).
