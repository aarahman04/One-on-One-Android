# S3 — GPT Sol 6.1 (pre-chat screens) — after S1 merges, parallel to S2

You are restyling the native Android app at C:\Users\aarah\One-on-One-Android (Kotlin, Jetpack Compose, Material3 via BOM 2026.09.00, minSdk 26, lint warningsAsErrors) to match the web client at C:\Users\aarah\One-on-One\client EXACTLY. The web repo is READ-ONLY reference. Source of truth: client/src/styles/global.css (only stylesheet), client/public/fonts/fonts.css, client/src/features/call/icons.ts, client/src/pages/*.ts, client/src/features/*.ts. After S1 merges, docs/design/WEB-STYLE-SPEC.md in the Android repo holds the extracted spec — read it first.

Hard rules:
- Presentation only. Do not change ViewModels, repositories, navigation routes, callbacks, network, alarm/call logic, or any behavior. Every existing button/action must still exist and call the same thing.
- No new Gradle dependencies (fonts are res/font files, icons are vector drawables). No material-icons-extended.
- Use theme tokens (OneTheme.colors/spacing/radii/sizes/motion, MaterialTheme.typography) and shared primitives in ui/components/. No new Color(0x...) literals outside ui/theme/.
- The in-app Appearance toggle (DevicePreferences "light"/"dark", default dark) drives the whole app. Do not switch to isSystemInDarkTheme().
- Wallpapers (off/love/samurai) override bubble palettes, as today.
- Read CLAUDE.md in the Android repo and follow it. Minimum diff in files you own; do not touch files outside your ownership list.
- Must pass: ./gradlew assembleDebug lintDebug testDebugUnitTest
- Append a section to docs/PROGRESS.md (what shipped, verification, device checklist) and add to docs/ARCHITECTURE.md only if structure changed (additive mermaid, no rewrites).
- PR description: summary, files changed, screenshots for dark, light, love, samurai of every screen you touched, next to the matching web screenshot if available.
Branch: style/s3-screens off main (S1 merged). You own ONLY: app/src/main/java/app/web/oneonone/ui/AppNavigation.kt and ui/NotificationOnboarding.kt. Do not touch ui/chat/**, ui/call/**, ui/theme/** (if a token/primitive is missing, add a TODO in the PR description instead of creating one).

Match web .screen pages (global.css L239-520, L1345-1360; pages LoginPage.ts, ConnectionIdPage.ts, ConnectPage.ts, WaitingPage.ts, ConnectionRequestPage.ts, LeavePage.ts, features/ageGate.ts, features/permissionRationale.ts):
1. ScreenFrame: scrollable centered column, vertical gap 20dp, padding 24dp (16dp when width < 480dp) + safe area, bg token. Screen-enter: fade + 8dp rise, 280ms OneMotion.standard. Keep LinearProgressIndicator (thin, accentYou) and error line (13sp danger).
2. Title -> ScreenTitle (Fraunces 600 28, max width 340dp, centered). Subtitles textDim max width 320dp. Eyebrow where web has one (sign-in: "ONE").
3. Sign-in: logo, eyebrow, title, tagline, PrimaryButton "Continue with Google", legal links 12sp TextLink.
4. Age: date button as SecondaryButton; Continue PrimaryButton. Under-age and Consent: checkbox tinted accentYou, text 15sp.
5. Connect: connection ID in OneTextStyles.connectionId (24sp under 480dp width) inside a bgRaised box radius 10 padding 12/18; Copy and New ID as SecondaryButton; code OneTextField centered, uppercase, letterSpacing .08em, max width 240dp; Connect PrimaryButton; Settings as TextLink.
6. Waiting, Request: same vocabulary; decline/leave as DangerButton.
7. Settings + Blocks (no web equivalent): build them from the same .screen vocabulary — section group labels (11sp .08em uppercase muted), rows 40dp min with 13-15sp text, danger items DangerButton. Delete-account AlertDialog -> OneModal with OneTextField and DangerButton.
8. NotificationOnboarding: web rationale modal look (14sp body, max width 320, title per report-dialog style L2464-2501), buttons PrimaryButton/SecondaryButton.
All existing buttons, callbacks and text content stay. Screenshots: every screen dark + light, web screenshot beside each where one exists.
