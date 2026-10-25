# Plan: #19 Library screen header (wordmark + search bar)

| Field | Value |
|---|---|
| Ticket | [#19](https://github.com/minicore-software/bookshelf/issues/19), part of epic [#17](https://github.com/minicore-software/bookshelf/issues/17) (Library Screen). Closes [#10](https://github.com/minicore-software/bookshelf/issues/10) (the Bootstrap placeholder). |
| Branch | `feature/BOOKSHELF-0019-library-header-wordmark` |
| Stack | Android: Kotlin, Jetpack Compose, Material 3, Hilt, single `app` module. No backend. |
| Agents | `frontend-developer-android` builds it, `frontend-reviewer-android` reviews it |
| Work split | FRONTEND-ONLY |
| UX spec | Not needed. See section 2, H10. |
| API contract | None. There is no backend, and this ticket adds no events or inter-module interfaces. The only new seam is the `onSearchClick: () -> Unit` composable parameter (H7). |
| ADR | None. See section 10. |
| Dependencies | None. Independent of #18 (data foundation): it touches no Room/repository code. |
| Status | Ready to build |

---

## 1. Context

- **Current screen.** `app/src/main/java/com/minicore/bookshelf/features/library/ui/LibraryScreen.kt` (from #10) is a `Box(fillMaxSize, background, safeDrawingPadding, contentAlignment = Center)` with a single `Text(R.string.app_name)` in `typography.headlineMedium` (Fraunces SemiBold 26sp, ink) and `heading()` semantics. `navigation/BookshelfNavGraph.kt` mounts it on `BookshelfDestinations.Home` (`"home"`), the start destination. This ticket grows **that same file on that same route**, as #10's plan (L7) intended.
- **Design system** (`core/designsystem/`): `Color.kt` builds a light `ColorScheme` from `colorResource` (paper `#F3EEE3`, ink `#231F20`, forest `#2F4538`). `Type.kt` declares `Fraunces` (one static file, `res/font/fraunces_semibold.ttf`, SemiBold only) and overrides only `headlineMedium`. `Theme.kt` exposes `BookshelfTheme`. No Inter font is bundled yet. #10 L6 deferred it to "the first body text (Library epic)", and this ticket is that first body text.
- **Design sources**
  - `project/Main.dc.html`, "Library" board, header block: `padding: 28px 20px 12px`, column gap `14px`. Greeting ("Good evening", Inter 13px `#8A8274`), title ("Marco's Shelf", Fraunces 600 26px ink), and a 40px forest avatar circle with "MT". Search bar: `#FBF8F1` fill, `1px solid #E3DCC9`, radius 14px, padding `11px 14px`, gap 10px, 18px magnifier (stroke `#8A8274`, 1.8), Inter 14px placeholder "Search your library, or add a book" in `#8A8274`.
  - `project/wireframe_images/Landing_Screen_Sample_Title.png` (487×122). I measured it with a pixel sampler. It is the Visual Spec's top-bar brand lockup (Visual Spec §5.1 brand mark plus §2.2 "App title"/"App subtitle"), rendered at about 1.25× on the spec's `--bg`:
    - **Brand mark:** a rounded square, 55×55px in the image (= 44px at 1.25×), radius about 10px, gradient forest `#4F6F52` to sage, with a cream "B" in a slab serif.
    - **Title "Bookshelf":** a slab serif, bold, darkest pixel exactly `#4F6F52` (spec `--forest`). Its cap top is aligned with the tile top, 14px (spec gap) to the right of the tile.
    - **Tagline "Your books · your loans · your memory of them":** a sans, darkest pixel exactly `#6B5E53` (spec `--ink-muted`), sitting under the title. The lockup's vertical centre is roughly the tile's centre.
  - `docs/specs/Bookshelf-Visual-Spec.md`: mobile values for the same lockup are brand mark **38px**, radius 10px, "B" Slab 700 22px (at 44px), title Slab **22px** 700, tracking -.01em, forest, subtitle Inter **12.5px** 400, tracking .02em, ink-muted, gap 14px.
- **Epic decisions in force** (`docs/epics/Library-Screen-Epic.md`): D2 Fraunces + Inter are settled; D5 remove the avatar; D6/D7 replace the greeting with the wordmark, and only the search bar below; D9 search is a stub; **D14 build the wordmark exactly as the PNG shows, "resize/recolor for a compact header"**; D15 no other changes to `Main.dc.html`.
- **Token precedent:** #10 plan decision L1. The user deferred the CLAUDE.md vs Visual Spec reconciliation. Screens build against the CLAUDE.md tokens (Fraunces, paper/ink/forest), and the reconciliation ticket swaps values later.

## 2. Key decisions

| # | Decision | Why / alternatives |
|---|---|---|
| H1 | **Composition, proportions and copy come from the PNG. Typeface and colours come from the CLAUDE.md tokens: Fraunces SemiBold, forest `#2F4538`, paper `#F3EEE3`.** The PNG's Roboto Slab and Visual Spec colours are **not** adopted here. | The AC literally says "using Fraunces/palette tokens from `CLAUDE.md`". D14 says "resize/**recolor** for a compact header". #10 L1 records the user's deferral of the token reconciliation. Adopting the PNG's slab and `#4F6F52`/sage would put Visual Spec tokens next to the CLAUDE.md tokens that the rest of the screen (#20–#22, built "exactly as mocked" per D15) will use, so one screen would mix two palettes. **Rejected:** a pixel-literal PNG (bundle Roboto Slab 700, add sage, the new forest and cream). That contradicts the AC and pre-empts the reconciliation ticket. **Cost of reversing:** a value swap in `colors.xml`/`Type.kt` (the #10 L3/L4 structure holds). The reconciliation ticket gets it for free. |
| H2 | **The wordmark is built in Compose (`Row { BrandMark; Column { Title; Tagline } }`), not shipped as a bitmap.** The PNG is a reference, not an asset. | The PNG is a 1.25× screenshot with the background baked in, no transparency, the old tokens and fixed raster text. As a `res/drawable` it would blur at xxhdpi/xxxhdpi, ignore font scaling, be invisible to TalkBack as text, and fall out of sync with H1's tokens. **Rejected:** a bitmap or vector drawable of the whole lockup. A vector drawable of only the "B" tile was also considered and is unnecessary, because a rounded `Box` plus a Fraunces "B" is about 10 lines and stays token-driven. |
| H3 | **Compact sizes = the Visual Spec mobile values**, which are exactly what the PNG renders at desktop scale: **brand mark 38dp**, `RoundedCornerShape(10.dp)`; "B" Fraunces SemiBold **19sp-equivalent** (22/44 × 38); **gap 14dp**; **title 22sp** Fraunces SemiBold, letter spacing `-0.01.em`; **tagline 12.5sp**, Inter Regular, letter spacing `0.02.em`; title-to-tagline spacing `2.dp` (the mock's greeting/title gap). Lockup `verticalAlignment = CenterVertically`. | This gives the AC's "size/position shown in the PNG for the collapsed header height". It is the same lockup scaled to the spec's mobile size, with no invented numbers. Weight: only SemiBold (600) Fraunces is bundled. Requesting 700 would make Compose **synthesize a fake bold** silently (#10 R2), so the title stays 600. **Rejected:** bundling a Fraunces Bold file for a weight difference you can barely see at 22sp. |
| H4 | **Colours (recoloured per H1):** brand-mark fill **forest** (solid, no gradient: the CLAUDE.md palette has no sage), "B" **paper**; title **forest**; tagline **new token `ink_muted` `#6B5E53`**. | Forest-on-paper matches the PNG's role mapping (the title is the brand colour, not ink) and is about 9.6:1. A solid forest tile with a paper Fraunces letter is the removed avatar's treatment, so it reads as continuity. **Tagline colour:** the mock's muted `#8A8274` is only **about 3.3:1 on paper, which fails WCAG AA** for 12.5sp text. `#6B5E53` is about 5.4:1 and is also the colour the PNG actually shows. **Rejected:** `#8A8274`, which fails AA. |
| H5 | **Include the tagline** "Your books · your loans · your memory of them" as part of the wordmark lockup. | D14 "exactly as the PNG shows", and the tagline is part of that image. The AC's "no greeting text" removes "Good evening"; it does not remove the brand's own subtitle. **Reversible in one line.** If the product owner reads "only the search bar below" as excluding the tagline, delete the tagline `Text` and its string. Flagged in the report, not blocking. |
| H6 | **Search bar = a clickable, field-styled `Row`, not a `TextField` and not an M3 `SearchBar`.** It contains an 18dp magnifier icon (`Icons.Outlined.Search`, or a 24-viewBox stroke icon matching the mock) and the placeholder `Text`. Style: fill `paper_bright` `#FBF8F1`, `1.dp` border `line` `#E3DCC9`, `RoundedCornerShape(14.dp)`, padding `horizontal 14.dp`, gap `10.dp`, `fillMaxWidth()`, **`heightIn(min = 48.dp)`**. Placeholder: Inter 14sp, `ink_muted`. Icon tint: `ink_muted`. | AC: tapping it is a **no-op**. A `TextField` takes focus and opens the IME, which is not a no-op. An M3 `SearchBar` expands into a search view, which is also not a no-op. A clickable surface is the honest stub and is trivially replaced by the real search ticket. **48dp minimum height** is a deliberate deviation from the mock's ~42px, for the touch-target minimum. **Placeholder/icon colour** uses `ink_muted` instead of the mock's `#8A8274` for AA contrast (about 5.9:1 on `#FBF8F1`, vs about 3.6:1). These are the only deviations from `Main.dc.html`. |
| H7 | **Stub wiring:** `LibraryScreen(onSearchClick: () -> Unit = {}, modifier)` passes it to `LibraryHeader(onSearchClick)`, which puts it on the search bar's `Modifier.clickable(role = Role.Button, onClickLabel = …)`. `BookshelfNavGraph` keeps calling `LibraryScreen()` (the default no-op). No ViewModel. | This gives the later search ticket a seam without touching navigation now, and keeps tap behaviour testable (the test passes a counting lambda). Hoisting the click follows standard Compose state hoisting. **Rejected:** an un-clickable decoration, because the epic wants a visible "tap target" stub that later becomes search, and a ripple on tap makes the stub feel intentional. **Rejected:** `enabled = false`/disabled styling, because the mock shows it active. |
| H8 | **Layout:** `LibraryScreen` becomes `Column(fillMaxSize().background(colorScheme.background).safeDrawingPadding())` containing `LibraryHeader(...)`. The header is a `Column(padding(start = 20.dp, top = 28.dp, end = 20.dp, bottom = 12.dp), verticalArrangement = spacedBy(14.dp))`: the wordmark lockup, then the search bar. Nothing else on screen yet. The header is **fixed**, not collapsing. | Values are `Main.dc.html`'s header, unchanged (D15). In the mock the header sits outside the scrollable content, so later tickets (#20–#22) add a scrolling body below it. The AC's "collapsed header height" is read as "compact (non-splash) header". **No collapse-on-scroll behaviour** is in the mock or the ticket. If the product owner wanted one, that is new scope for a later ticket. The centred placeholder `Box` goes away. |
| H9 | **Typography tokens** in `Type.kt`. Add `Inter` = `FontFamily(Font(R.font.inter_regular, FontWeight.Normal))`. Set `fontFamily = Inter` on `bodyLarge/Medium/Small` and `labelLarge/Medium/Small`, so later tickets never leak Roboto through `LocalTextStyle`. Override **`titleLarge`** = Fraunces SemiBold 22sp, `letterSpacing = (-0.01).em` (wordmark title); **`bodySmall`** = Inter 12.5sp, `letterSpacing = 0.02.em`, lineHeight 16sp (tagline); **`bodyMedium`** = Inter 14sp (search placeholder, M3 default size). `headlineMedium` stays as it is. The brand-mark "B" uses `titleLarge.copy(fontSize = …)` with a **non-scaling size** (`with(LocalDensity.current) { 19.dp.toSp() }`) so it never overflows the fixed 38dp tile at large font scales. | The tokens live in `core/designsystem` (#10 L3). There are no `FontFamily(...)` or hex literals in feature code (#10 review rule). Making the tile glyph non-scaling is correct because it is a decorative logo inside a fixed box. The real text (title, tagline, placeholder) scales normally. |
| H10 | **No UX spec needed.** | Every visual value is pinned by an existing source: the PNG (composition), the Visual Spec mobile lockup values (H3), `Main.dc.html` (header padding, search bar) and the CLAUDE.md tokens (H1). There is one static state and one stub interaction. The only judgement calls (H4/H6 contrast, H5 tagline, H6 touch target) are accessibility fixes or reversible one-liners, and they are recorded here. |
| H11 | **Accessibility semantics:** the title `Text` keeps `heading()`. The brand mark uses `clearAndSetSemantics {}` (decorative, so TalkBack must not announce "B"). The tagline is plain text. The search bar is `Role.Button` with merged descendants, so it announces the placeholder text, and `onClickLabel = stringResource(R.string.library_search_click_label)` ("Search"). | One heading landmark per screen. No stray "B". The stub is discoverable. |

## 3. Font asset: Inter Regular

- **File:** `app/src/main/res/font/inter_regular.ttf`. **Must be a static instance**: no `fvar` table, `name` table family "Inter", subfamily "Regular". Inter v4 is variable (`opsz`, `wght`). Use the static TTF that the official release ships (`rsms/inter` GitHub release zip, `extras/ttf/Inter-Regular.ttf`). If that file is absent from the pinned release, instance it from the variable font with `fonttools varLib.instancer` the same way `tools/fraunces-font/generate.py` does, pinning `opsz` to the text size and `wght=400`. Record provenance in the `Type.kt` KDoc, in the same style as Fraunces: release tag, original file name, SHA-256.
- **License:** SIL OFL 1.1 goes in `app/src/main/assets/licenses/Inter-OFL.txt`. **Never put it in `res/font/`**, because AAPT2 rejects it (#10 R5).
- **Do not** use the Google Fonts downloadable-font provider (#10 L2: offline-first, silent fallback).
- Declare only `FontWeight.Normal`. Requesting another weight would synthesize one silently.

## 4. File changes

| File | Change |
|---|---|
| `app/src/main/res/font/inter_regular.ttf` | New (section 3). |
| `app/src/main/assets/licenses/Inter-OFL.txt` | New. |
| `app/src/main/res/values/colors.xml` | Add `ink_muted` `#6B5E53`, `paper_bright` `#FBF8F1`, `line` `#E3DCC9`. Existing entries unchanged. |
| `app/src/main/res/values/strings.xml` | Add `library_tagline` ("Your books · your loans · your memory of them"), `library_search_placeholder` ("Search your library, or add a book"), `library_search_click_label` ("Search"), and `brand_mark_initial` ("B", `translatable="false"`). The title keeps using `app_name`. |
| `core/designsystem/Color.kt` | Map the new tokens to M3 roles: `onSurfaceVariant = ink_muted`, `outlineVariant = line`, `surfaceContainerLowest = paper_bright`. Feature code reads roles, not resources. |
| `core/designsystem/Type.kt` | `Inter` family plus the style overrides in H9, with the Inter provenance KDoc. |
| `features/library/ui/LibraryHeader.kt` | **New.** `@Composable fun LibraryHeader(onSearchClick: () -> Unit, modifier: Modifier = Modifier)`, plus private `BrandMark()`, `Wordmark()` and `LibrarySearchBar(onClick)`. Test-tag constants: `LIBRARY_HEADER_TEST_TAG`, `LIBRARY_SEARCH_BAR_TEST_TAG`, `LIBRARY_BRAND_MARK_TEST_TAG`. Previews: default, font scale 2.0, and a narrow 320dp width. |
| `features/library/ui/LibraryScreen.kt` | Replace the centred `Box`/`Text` with the H8 `Column` holding `LibraryHeader`. Add `onSearchClick: () -> Unit = {}`. Update the KDoc. |
| `navigation/BookshelfNavGraph.kt` | No functional change (`LibraryScreen()` uses the default no-op). Optionally pass `onSearchClick = {}` explicitly with a `// stub until search ships (epic #17 D9)` comment. |
| Tests | See section 8. |
| `README.md` | Asset note: Inter is bundled, its source, and where its license lives (next to the Fraunces note). |
| `CLAUDE.md` | No change. The Design Reference reconciliation stays deferred. |
| `CHANGELOG.md` | One line under `[Unreleased]`, written at integration time. |

Not touched: `themes.xml`, splash code and assets, `ic_launcher_background.xml`, `tools/`, Room/`core/database`.

## 5. Quality attributes and fitness functions

| Attribute | How the design serves it | Fitness function (developer implements) |
|---|---|---|
| **Visual fidelity / no silent font fallback** | Bundled static fonts, theme-owned styles (H9), one declared weight per file. | **FF1** (instrumented): adapt the existing `TextMeasurer` test so that the wordmark title node's width matches `titleLarge` measured with Fraunces within 1px, and differs from `FontFamily.Default`/`Serif` by more than 1px. Add the same check for the tagline against `bodySmall` with Inter vs `FontFamily.Default`. **FF2** (unit): `InterFontFileTest` (no `fvar`, name table says Inter Regular) and an Inter license check, mirroring `FrauncesFontFileTest`/`FrauncesLicensingTest`. **FF2b** (instrumented): `ResourcesCompat.getFont(R.font.inter_regular)` is non-null and differs from `Typeface.DEFAULT`. |
| **Palette correctness** | Tokens single-sourced in `colors.xml` and exposed as M3 roles. | **FF3**: extend `PaletteTest` with `ink_muted == #6B5E53`, `paper_bright == #FBF8F1` and `line == #E3DCC9`. **FF4** (instrumented, update `LibraryScreenPixelTest`): corners and centre-top are still paper. The darkest pixel inside the **title** bounds is within ±8 per channel of **forest** `#2F4538` (was ink). Inside the **brand-mark** bounds, a sample at the tile's centre-left edge region (away from the "B") is forest. |
| **Accessibility** | AA contrast (H4, H6), 48dp target, heading, decorative mark hidden, non-scaling tile glyph (H9). | **FF5** (instrumented): at `FontScale(2f)` on a 320dp-wide `DeviceConfigurationOverride`, the title renders on one line, unclipped (keep the existing strict-width check). The tagline is fully displayed (wrapping is allowed, `maxLines` unset). The search bar is displayed and has height ≥ 48dp. **FF6**: exactly one node has `SemanticsProperties.Heading`, and its text is `app_name`. No node exposes the text "B". The search bar has `Role.Button` and a click action. Optional: enable `ComposeTestRule` accessibility checks (`enableAccessibilityChecks()`, if available on the BOM's ui-test) on the header test. |
| **Evolvability** | `onSearchClick` seam (H7). Header is a separate composable that later tickets put above a scrolling body (H8). Tokens are swappable for the reconciliation. | Review rule (from #10): no `Color(0x…)` and no `FontFamily(...)` outside `core/designsystem/`. |

## 6. Security

No new entry point, input, storage, logging, permission or network access. The search bar accepts no text (H6). Fonts are bundled static resources, with no provider query. Nothing for QA to probe security-wise.

## 7. Work split and tasks

**FRONTEND-ONLY:** a single Compose screen change in the `app` module. No backend, no contract, no dependency on #18.

`frontend-developer-android`, in build order:

1. Add Inter Regular (static) plus its OFL license, record provenance, and add FF2/FF2b (section 3). (AC2, AC3: the tagline and placeholder typography)
2. Add `ink_muted`, `paper_bright` and `line` to `colors.xml`. Map them in `Color.kt`. Add the H9 overrides to `Type.kt`. Extend `PaletteTest` (FF3). (AC2, AC3)
3. Create `LibraryHeader.kt`: `BrandMark`, `Wordmark` (title plus tagline), `LibrarySearchBar` per H2–H6, H9 and H11, with the test tags and previews. (AC1, AC2, AC3)
4. Rework `LibraryScreen.kt` to the H8 layout with the `onSearchClick` parameter. This removes the centred placeholder and closes #10. Keep `BookshelfNavGraph` behaviour unchanged. (AC1, AC2, AC3)
5. Tests (section 8): rewrite `LibraryScreenTest`, add `LibraryHeaderTest`, extend `BookshelfNavGraphTest`, update `LibraryScreenPixelTest`. (AC4, plus FF1/FF4–FF6)
6. Run `./gradlew lint test connectedAndroidTest`. Visually compare the header preview, side by side, with `Landing_Screen_Sample_Title.png` scaled to 38dp tile height (proportions and alignment only; colours and typeface differ by design, H1), and attach the screenshot to the PR. Add the README Inter note.

## 8. Test strategy

| AC | Level | What |
|---|---|---|
| AC1: avatar removed | Compose component (`createComposeRule`, `LibraryScreen` in `BookshelfTheme`) | No node has text "MT", "Good evening" or "Marco's Shelf". Exactly one non-text graphic in the lockup (the tag on the brand mark) and nothing at the top-end corner: the header's only `Row` child at end alignment is gone. In practice, assert the full expected text set: `{app_name, library_tagline, library_search_placeholder}`, and nothing else. |
| AC2: wordmark at PNG size/position, Fraunces/palette | Component plus instrumented pixel | Brand mark node size == 38×38dp (`assertWidthIsEqualTo`/`assertHeightIsEqualTo`). The title is `toEndOf` the mark with a 14dp gap (compare `boundsInRoot`, ±1dp). The title and mark are vertically centred to each other (±2dp). The tagline is below the title. FF1 (Fraunces/Inter really used), FF4 (forest title, forest tile on paper). |
| AC3: search bar below wordmark, no greeting | Component | The search bar's `top` ≥ the lockup's `bottom` + 14dp (±1dp). The placeholder text is displayed. The search bar spans the header width minus 2×20dp padding. The text set assertion from AC1 covers "no greeting". |
| **AC4: tapping search is a no-op (no crash, no navigation)** | (a) Component, (b) navigation, (c) app-level | (a) `LibraryHeader(onSearchClick = { clicks++ })`: `performClick()` on `LIBRARY_SEARCH_BAR_TEST_TAG`, then `clicks == 1`. The header is still displayed, no IME/focus (`assertIsNotFocused`), and the tree is unchanged. (b) Extend `BookshelfNavGraphTest` (it already uses `TestNavHostController`): click the search bar, `waitForIdle()`, then `currentDestination.route == Home.route` and the back-stack size is unchanged. (c) In `MainActivityLaunchTest` after the splash: click the search bar, and the activity is still `RESUMED` with the title displayed (no crash). |
| AC1–AC3 regression | Instrumented | `MainActivityLaunchTest` keeps finding `app_name` after the splash. `LibraryScreenPixelTest` is updated per FF4. FF5 (font scale 2.0, 320dp) and FF6 (semantics). |
| Manual | Device | One API 29–30 and one API 31+ device, with system font size at max and display size at max. The header must not clip, and the tagline wraps cleanly. TalkBack order: "Bookshelf, heading", then the tagline, then "Search your library, or add a book, button, double-tap to Search". Nothing happens on activation. |

## 9. Risks for QA to probe

| # | Risk | Probe |
|---|---|---|
| R1 | **Silent font fallback** for Inter (a variable file, a wrong file, or a missing `fontFamily` on a style), so the tagline or placeholder renders in Roboto. | FF1, FF2, FF2b. Inter vs Roboto is subtle at 12.5sp. Compare "y", "·" and "a". |
| R2 | **Fake-bold title.** Someone sets `FontWeight.Bold` to match the PNG's heavier slab. | Only SemiBold is declared (H3). Code review plus FF1 (the width would shift). |
| R3 | **The tile "B" overflows or clips at large font scales** if the non-scaling size (H9) is skipped. | FF5 at 2.0×, plus a manual check at maximum system font size. |
| R4 | **Tap is not a no-op.** A `TextField` or `SearchBar` gets used, so the keyboard opens or the view expands, or a future edit wires navigation. | AC4 tests (a)–(c), `assertIsNotFocused`. |
| R5 | **Narrow width / large display size.** The title plus 38dp mark plus 40dp of horizontal padding on a 320dp screen at 2× font scale. "Bookshelf" at 44sp is about 230dp, which fits. The tagline must wrap, not clip. | FF5, and a manual check at the largest display size. |
| R6 | **Contrast regression.** Someone "restores" the mock's `#8A8274` muted grey. | FF3 pins `ink_muted`. |
| R7 | **Token drift vs the PNG/Visual Spec** (slab vs Fraunces, `#4F6F52` vs `#2F4538`, gradient vs solid tile). This is intended (H1), not a defect. | The reconciliation ticket owns it. |
| R8 | **Status-bar overlap.** The header must sit below the status-bar inset in edge-to-edge mode. | Manual check, with a notch/cutout emulator in portrait and landscape. `safeDrawingPadding` stays on the root `Column`. |

## 10. ADR

**No ADR needed.** This is a routine UI feature that follows existing patterns. It adds one composable file, three colour tokens, a few typography overrides and a second bundled static font, all in the `core/designsystem` structure that #10 established. It changes no framework, no module structure or layering, no external API or service (the downloadable-font provider is again rejected), and no database.

## 11. Open items (not blocking)

- **H5 tagline.** It is included per D14. If the product owner reads AC3 ("search bar renders below the wordmark with no greeting text") as excluding it, remove one `Text` and one string.
- **"Collapsed header height"** (AC2) is interpreted as "compact, non-splash". No collapse-on-scroll behaviour is built (H8). If the intent was a collapsing app bar, raise it as a separate ticket once the scrolling body (#20/#21) exists.
- **Reconciliation ticket (user-deferred, see #10 §11):** when Visual Spec tokens are adopted, the wordmark becomes Roboto Slab 700, forest `#4F6F52`, a forest-to-sage gradient tile and a cream "B", which matches the PNG exactly. With H1/H9 that is a value swap in `colors.xml`/`Type.kt` plus a `Brush` on the tile.
