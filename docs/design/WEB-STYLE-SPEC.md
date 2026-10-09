# Web style spec (extracted for the native restyle)

Source of truth: `One-on-One/client/src/styles/global.css` (the only stylesheet; line refs below are `Lxxx` in that file),
`client/public/fonts/fonts.css`, `client/src/features/call/icons.ts`, `client/src/pages/ChatPage.ts`.
Native tokens live in `app/src/main/java/app/web/oneonone/ui/theme/` (`Tokens.kt`, `Type.kt`, `Theme.kt`, `BubbleTokens.kt`)
and shared primitives in `ui/components/`. Screens must use those, never raw `Color(0x...)` literals.

The web app is dark-first. Light values come from `@media (prefers-color-scheme: light)` (L66) and, inside `.chat`, from the manual
appearance toggle `.chat[data-theme]` (L1435-1460). On Android the in-app Appearance toggle (`DevicePreferences`, default dark)
drives everything; `isSystemInDarkTheme()` is not used.

## 1. Colors (L2-L82, L1435-L1460)

| Token | Dark | Light | Kotlin |
|---|---|---|---|
| bg | #0d1117 | #f5f5f7 | `OneColors.bg` |
| bg-raised | #12181f | #ffffff | `bgRaised` |
| border | #1f2630 | #d9d9dd | `border` |
| text | #e6edf3 | #1c1c1e | `text` |
| text-dim | #9aa4af | #5b5b60 | `textDim` |
| muted | #6e7681 | #8a8a8e | `muted` |
| accent-you | #7ee787 | #1a7f37 | `accentYou` |
| accent-other | #79c0ff | #0969da | `accentOther` |
| danger | #f85149 | #cf222e | `danger` |

Fixed (theme independent): primary button text `#04170a` (`onPrimary`, L176-181); composer send/mic `bg #7ee787 / fg #102719`
(`sendBg`/`sendFg`, L2996-3003); modal scrim `rgba(0,0,0,.6)` (`scrim`, L2358); quote scrim `rgba(0,0,0,.14)` (`quoteBg`, L854-857);
call-log disc scrim `rgba(0,0,0,.18)` (`discBg`, L2946); danger call button text `#fff` (L2823).

## 2. Bubble palettes (L1416-L1600)

Mirrored 1:1 by `BubbleTokens` (Dark, Light, Love, Samurai). Backgrounds are 135deg gradients; `tail` is a solid colour for the
first-in-group tail (6dp triangle, left for other, right for mine, L1489-1505); `edge` is a 1dp inset outline.
Wallpapers (`love`, `samurai`) override the palette in both themes. Wallpaper `.chat__log` overlays: love `rgba(10,14,20,.22)`,
samurai `rgba(10,8,8,.3)` (L1542, L1573). Checked against web 2026-10-10: no drift.
Bubble body: padding 6 / 8.5, radius 8, max-width 80%, inset 1dp edge (L1477-1487). Meta (time + ticks) 11sp, right aligned,
gap 4, line-height 1 (L1507-1530). Receipt colour = `ticks`; seen = `read`; pending opacity .7 (L700-707).

## 3. Typography (L12-L25, fonts.css)

Families: display = Fraunces (600), body = Figtree (400/500/600/700), mono = JetBrains Mono (400/500/700).
Scale (sp): meta 11, xs 12, sm 13, base 15, md 16, lg 20, xl 28, display 40. Body line-height 1.5, antialiased (L107-114).

| Native style | Spec | Web ref |
|---|---|---|
| `eyebrow` | 13sp, tracking .08em, muted, uppercase by caller | `.screen__eyebrow` L273 |
| `screenTitle` | Fraunces 600, 28sp (max-width 340) | `.screen__title` L279 |
| `subtitle` | 15sp, text-dim (max-width 320) | `.screen__subtitle` L287 |
| `connectionId` | Mono 700, 32sp, tracking .12em; box bg-raised, 1dp border, radius 10, padding 16/28, elevation-1 | `.connection-id` L506 |
| `bubbleText` | 15sp, pre-wrap | `.chat__message-text` L986 |
| `bubbleMeta` | 11sp, line-height 11 | `.chat--bubbles .chat__bubble-time` L1507 |
| `cardHeading` | 13sp / 600 | `.chat__card-heading` L3017 |
| `cardHint` | 13sp, opacity .8 | L3025 |
| `menuItem` | 13sp | `.menu__item` L1301 |
| `groupLabel` | 11sp, tracking .08em, muted | `.menu__group-label` L1289 |
| `callName` | Fraunces 600, 28sp, line-height 1.1 | `.call-screen__name` L2604 |
| `callStatus` | Mono 13sp, tracking .06em, tabular figures, text-dim | `.call-screen__status` L2617 |
| nav title | 16sp / 600 | `.chat__nav-title` L593 |
| nav status | 13sp text-dim, prefixed by a green dot | `.chat__nav-status` L601 |
| composer text | 16sp, line-height 1.4 | `.chat__input-bar textarea` L1055 |

Static TTFs only; Fraunces 500 is declared on the web but is never used by the stylesheet and has no static upstream instance,
so it is not bundled. Fraunces uses the 72pt optical-size static cut.

## 4. Spacing, radii, sizes (L27-L52)

Spacing (dp): 2, 4, 8, 12, 16, 24, 32. Radii: buttons/inputs 4, sm 6, bubble 8, md 10, lg 16, composer pill 22 (`--radius-input`),
full 999 (`--radius-pill`). Sizes: touch 40, send 48, header 56, chat column max 720 (centred, 0.5dp side borders, L520).
Screen padding 24, gap 20, content centred (L239-254). Chat log padding 8/6 (L628).

## 5. Elevation (L45-L48, L72-L75)

`elevation-1 0 1 3 rgba(0,0,0,.3)`, `-2 0 4 16 .4`, `-3 0 8 32 .5`; light alphas .1/.12/.16. Toast uses elevation-2; connection-id uses elevation-1.

## 6. Motion (L54-L60)

standard `cubic-bezier(.2,0,0,1)`, emphasized `cubic-bezier(.3,0,.1,1)`; fast 120, enter 160, base 200, slow 320 ms.
Keyframes: `screen-enter` 280ms standard (fade + rise 8, L256); `message-enter` 160ms (fade + rise 8, L783, live rows only);
`menu-pop` 200ms emphasized (scale .95 + y -4 to 1, origin top-right, L1235) and `menu-pop-out` 150ms standard (L1255);
`call-pulse` (avatar ring, L2669); `recording-pulse` 1.2s opacity 1 to .3 (L1157); spinner 0.8s linear (L309);
call screen enter = `screen-enter` slow/emphasized (L591). `prefers-reduced-motion` disables all of these.

## 7. Components

- **Button** (L143-L190): bg-raised, 1dp border, text, radius 4, padding 10/18, press `scale(.97)` 120ms standard. `.primary`: accent-you fill and border, `#04170a`, weight 600, hover brightness 1.08. `.danger`: border + text danger. Focus ring: 2dp accent-other, offset 2 (L171).
- **Input / textarea** (L201-L226): bg-raised, 1dp border, radius 4, padding 10/12, focus border accent-other. `.screen__input` max-width 240, centred text; `--code` uppercase, tracking .08em.
- **Text link** `.screen__alt` (L329): borderless, text-dim, 13sp, tracking .01em, padding 4/8, underline on hover.
- **Icon button** (L2981-L2994): 40x40 pill, transparent, text-dim, hover/press tint = text at 8%. Send/mic: 48 pill, `#7ee787` / `#102719` (L2996).
- **Nav bar** (L546-L626): min-height 56 + inset, padding 4/8, bg-raised; avatar 40 pill, bg `accent-you 18% over bg`, accent-you glyph 20sp; actions gap 2.
- **Composer** (L1031-L1120): bar bg `bg`, padding 8, gap 8; pill min-height 48, radius 22, bg-raised, 4dp inline padding, focus outline 0.5dp accent-you; textarea padding 12/8.
- **Menu** (L1219-L1330): bg-raised, 0.5dp border, radius 6 (nav menu in chat: radius 8), min-width 200, padding 6 0; items padding 8/15, 13sp, danger variant, disabled = muted; divider 0.5dp border; group label see typography.
- **Modal** (L2357-L2402): scrim .6; panel bg-raised, 1dp border, radius 10, padding 46/22/22/22, max-width 620, max-height 90dvh; close X absolute top 12 right 12.
- **Toast** (L2519): bg-raised, 1dp border, radius 10, elevation-2, padding 12/16, 13sp, slides from y -12, 200ms standard.
- **Date separator** (L642): centred pill, bg-raised, text-dim, 13sp, padding 4/12. **Load older** (L654): outlined, text-dim, 12sp.
- **Reply bar / quote** (L830-L925): quote left border 3dp accent-other (bubble mode: currentColor + `quoteBg`), radius 4, name 11sp/700, snippet 12sp.
- **Call screen** (L2580-L2860): full-screen bg, padding 32/24, gap 24; name and status as typography; control button 62dp pill, 1dp border, bg `bg`; `--active` text-on-bg inverted; `--danger` danger fill white glyph; `--accept` accent-you fill `#04170a` glyph; label 12sp text-dim.
- **Call-log card** (L2863-L2956): row gap 9, disc 36 pill (bubble mode `discBg`, no border), title 13sp/600, sub 12sp (opacity .7 in bubbles); missed disc = danger.
- **Keepsake cards** (letter/countdown/checkin/location/ask/thisorthat/alarm, L1598-L2075): transparent grid, gap 10, icon 20sp, heading `cardHeading`, hints `cardHint`; alarm bubble has a 4dp danger left border (L3058). Detailed rules are in S4's brief.

## 8. Icons

Vector drawables `res/drawable/ic_*.xml`, 24 viewport, 2dp round stroke (white, tint at use site). Names and web source:
phone, video, video_off, mic, mic_off, volume, volume_off, camera_flip, phone_off (hang-up, filled, rotated 135deg) from `icons.ts`;
paperclip, send (filled), more_vertical (filled dots), lock, reply, tick_single / tick_double (filled, 16x15 viewport, receipts) from `ChatPage.ts`
(L3033, L3042, L3006, L3019, L2653, L1613-1616). `arrow_left`, `x`, `search` are NOT SVGs on the web (text glyphs / emoji); they are
drawn in the same feather style. Call-log disc glyphs (L`icons.ts` log icons) are composite and left to S4.
