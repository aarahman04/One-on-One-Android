# One on One — native Android

Kotlin + Compose client only. Shared backend lives in `aarahman04/One-on-One`.
The sibling web repo is READ-ONLY: never edit, commit, push or change its backend.

## Think before coding

Read the current milestone in `docs/prompts/gpt-sol-6.1-android.md`,
`docs/PROGRESS.md`, `docs/ARCHITECTURE.md` and the web repo's `docs/RELEASING.md`.
Trace the actual flow and callers before changing it. State material uncertainties.
`../One-on-One/docs/API-CONTRACT.md` is the ONLY API authority; this repo's copy
is a dated reference. If required behavior is absent or unclear, stop and report
the gap. Never invent an endpoint, payload or backend rule.

## Simplicity and surgical changes

Build only the current milestone, reuse existing code and installed libraries.
No speculative abstractions, placeholder repositories or adjacent cleanup.
Minimum working diff; remove only code made unused by the change.

## Non-negotiable product and security rules

- One account, one active connection, one conversation. Backend/database enforces
  membership, connection status and message validity on every request. UI state
  and Room cache are never authorization.
- Every message path: UI → ViewModel → MessageService → Transport → InternetTransport.
  Only InternetTransport owns Socket.IO, including signaling. Do not bypass it.
- No groups, stories, public profiles, discovery, AI or Bluetooth. Media, calls,
  reactions, letters and the other shipped features are explicitly in scope in
  later milestones; they override the old V1 non-goals.
- Keep applicationId `app.web.oneonone`, the `oneonone-upload` alias and existing
  signing certificate. Never loosen SHA assertions or generate a replacement key.
  versionCode starts at 5 and strictly increases for every Play upload.
- Never commit local.properties, google-services.json, keystores, passwords,
  service-role keys or tokens. BuildConfig contains only public client configuration.
- Never block the main thread or leak sockets/services; handle configuration
  changes and process death. Keep accessibility and trust-boundary validation.

## Verification and delivery

Run `./gradlew assembleDebug lintDebug testDebugUnitTest` before delivery.
Add meaningful unit checks as business logic arrives; do not claim compiled code
was device-tested. Update PROGRESS and ARCHITECTURE (additive Mermaid diagrams)
for every milestone and include a Xiaomi device checklist.

A0 is the initial scaffold commit on main. Every later milestone uses its own
branch and PR to main. Never merge a PR. Report the link, commands/results,
device vs build verification, unresolved gaps, and exact phone checks; then stop
until the owner tests and authorizes the next milestone.
