# Xiaomi / HyperOS fix-batch checklist (2026-10-21)

## Later owner priority: logos, transparent viewer and BiRefNet

- [ ] Open `/game`: Ping Pong bat grip sits centrally; Tic-Tac-Toe O is round. Check game cards/results too.
- [ ] Open a photo and the header avatar: match the WhatsApp reference's dark translucent backdrop, with chat faintly visible. Close/download and loading/error text remain readable. Pinch/double-tap zoom still works. PDF/text viewer remains readable on black.
- [ ] Make a sticker on Wi-Fi: Auto explains the one-time 192 MB download and reports progress, then shows the BiRefNet Lite 512 cut-out. Compare fine hair, fingers, a pet and an object with the original. Confirm the exact downloaded revision/hash from ARCHITECTURE if investigating a failure.
- [ ] Cancel during download; reopen and retry. Interrupted/corrupted files must never be used as a model. With no downloaded model and network disabled, Original remains available after failure.
- [ ] After one successful download, use airplane mode: cut-out still works. Time inference and monitor heat/memory on Xiaomi. Save/reopen/send a cut-out; WebP retains transparent edges.
- [ ] Close during inference and choose another photo: no stale result replaces it and no parallel models start; native termination releases resources. Session creation and stalled reads may still delay worker cleanup.
- [ ] With no model/network, Original remains an untouched preview and Cut-out permits manual editing. Erase clears alpha; Restore recovers original pixels, including details wrongly removed by AI. Save/reopen keeps the edited transparency.
- [ ] Offset brush: touch dot stays under the finger, working circle stays above it. Adjust circle size and distance; test taps, diagonal drags and photo edges. Only the active tool's controls appear.
- [ ] Undo/Redo icons reverse/reapply manual work. Crop and placement combine correctly with manual edits; Reset and tab changes never alter Original. Save/Cancel remain reachable with large font and the keyboard hidden.
- [ ] At 32 strokes, Done applies the session and reopening the brush permits more; Undo/Redo applies within the current session. Crop Cancel/back/X restores the crop and placement from entry.
- [ ] Under low free memory, Auto displays its unavailable message and manual tools still work. Measure the successful AI peak/time on Xiaomi; the emulator's failed lower-memory run is not a completed peak measurement.

Use the reviewed Android feature build with its matching backend PR deployed. These are manual checks, not claims of phone verification. Keep two separately signed-in accounts available. No new database migration is required.

Implementation is merged in Android PR #45 (`727ce59`) and backend PR #123 (`d982a84`). Before testing, confirm the deployed backend includes `d982a84`: at 2026-10-21 10:59 UTC its public health endpoint was healthy but Railway still reported older `3d0c148` during a platform outage. No signed Play release was made by this batch. See the [cross-check handoff](prompts/claude-cross-check-fix-batch-2026-10-21.md) for rollout evidence and limits.

## Dictation and letters (1-8)

- [ ] Grant microphone permission, start /audio after existing draft text, and speak for at least 10 minutes with several long pauses. Partial words appear in the composer; completed segments accumulate without duplication. Stop retains the latest words and all original draft text.
- [ ] Repeat in /letter with existing text. Commas, question marks and paragraph punctuation survive Preview and Send unchanged. Text remains bounded at 4000 characters; there is only a Stop control.
- [ ] Leave the letter editor, background the app and rotate during dictation. The microphone stops when leaving/backgrounding; already displayed text remains on return. No retired recognizer callback overwrites a new conversation or account.
- [ ] Deny microphone permission and then grant it from Settings. Neither flow crashes or clears a draft. Test while a voice call is active; microphone ownership remains safe.
- [ ] On a phone with no speech recognizer, record through the server fallback to its 118-second cap. It transcribes the clip rather than discarding it. Verify the English-language limitation. This fallback requires deployed speech service configuration.
- [ ] Letter body retains its large writing area and 4000-character limit. The mic icon is approximately 16 dp, aligned with Your letter, with at least a 40 dp touch target and a readable TalkBack label.
- [ ] The partner sets your nickname; open /letter. From defaults to that name, then saved signature, then me. To uses your nickname for the partner. Manually edited names remain editable.
- [ ] Preview Listen speaks Dear {to}, the full body, then With love, {from}. Edit, Send, Close and app background all stop speech. Compare first and subsequent starts; device TTS warming cannot prove Railway cold-start speed.
- [ ] Open a sent letter. Read, pause, resume and close; natural narration falls back visibly when unavailable. Switching between preview and sent-letter playback never overlaps voices.

## Photos, calls and commands (9, 12, 13)

- [ ] Re-upload your avatar from the original camera image. Try portrait, landscape, rotated and mirrored photos. Settings, your chat header and the other account all show upright pixels.
- [ ] Send the same photos in chat. Their width/height matches the normalized image; transparent PNGs retain transparency. Previously uploaded files are not reprocessed.
- [ ] Complete audio and video calls and hang up from either phone. Both chats show exactly one matching call card with plausible duration.
- [ ] Repeat a video hangup while Wi-Fi/mobile data reconnects. Kill a participant process during an accepted call; after socket-loss detection plus the 30-second grace, a card should appear and another call can start.
- [ ] Briefly disconnect and reconnect before the grace expires; the ongoing call is not prematurely resolved. Also test two sockets for one account: closing one must not end its call.
- [ ] /checkin is absent from suggestions/help and cannot open a composer. Existing historical check-ins, old pushes and exports remain readable. The web client needs its separate follow-up.

## Chat media and navigation (14-19)

- [ ] Scroll away from and back to portrait/landscape image messages: no Loading photo text, stable payload-based shape, subtle spinner during genuine loading, and loaded items reuse the attachment cache.
- [ ] GIFs are capped near 200 dp on the long side and keep their aspect ratio. Large photos still open in the viewer.
- [ ] Exactly one emoji grapheme displays large without a bubble, with time/read receipt visible. Include family/skin-tone/flag/keycap emoji. Two or more emoji, ordinary text and lone modifiers use normal bubbles.
- [ ] Scroll up about 1.5 screens; the arrow appears with TalkBack label Scroll to latest messages. Tap: it moves smoothly to the end, hides and stays pinned for subsequent messages. Scrolling up does not jump on every incoming row.
- [ ] Tap the contact header photo: full-screen photo opens, closes easily and reuses the same image request/cache identity. TalkBack says View profile photo. Test when no photo exists too.
- [ ] Send a large camera photo: picker closes immediately; a local preview and spinner appear while uploading. On success the actual image replaces it. Failed upload is visible and conversation changes cannot send it to the wrong partner.
- [ ] Confirm uploaded opaque photos are at most 2048 px on their long side with acceptable JPEG quality. Compare elapsed upload time on the same real network; no phone performance measurement has been claimed by this batch.

## Optional owner diagnostics

Re-upload any avatar whose wrong orientation is already baked into its pixels. For server read-aloud latency, prevent Railway speech-service sleeping and/or set SPEECH_MODEL_TTL=0, then redeploy and measure. No changes to speech/ are part of this batch.

TTL 0 specifically disables idle-model unloading after first use. It does not preload a model, disable Railway's separate sleep setting or resolve a Railway platform outage. The owner's saved TTL change has not been verified in a running deployment; confirm it after Railway recovers. Nothing new must be run in Supabase.

To inspect the newest call cards in Supabase:

```sql
select created_at, payload->>'kind', payload->>'outcome'
from messages
where type='call'
order by created_at desc
limit 20;
```

Issues 10 and 11 (games) are deliberately outside this batch and need no new game checks here.
