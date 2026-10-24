# Plan: #10 Blank landing screen titled "Bookshelf"

| Field | Value |
|---|---|
| Ticket | [#10](https://github.com/minicore-software/bookshelf/issues/10), part of epic #6 (App Bootstrap), the last piece after #7, #8 and #9 |
| Branch | `feature/BOOKSHELF-0010-blank-landing-screen` |
| Stack | Android: Kotlin, Jetpack Compose, single `app` module. No backend. |
| Agents | `frontend-developer-android` builds it, `frontend-reviewer-android` reviews it |
| Work split | FRONTEND-ONLY |
| UX spec | Not needed. One static title, no interaction and no states. Layout and type come from the ticket, epic D20 and the `project/Main.dc.html` header. |
| API contract | None. The app has no backend, and this ticket adds no events or inter-module interfaces. |
| ADR | None. See section 10. |
| Status | Ready to build |

---

## 1. Context

- **Current state on `origin/main` (bf547d1).** `MainActivity` calls `installSplashScreen()` and `enableEdgeToEdge()`. Inside `setContent { MaterialTheme { ... } }` it shows `BookshelfSplashScreen` until `splashCompleted`, then `Surface { BookshelfNavGraph(rememberNavController()) }`. `BookshelfNavGraph` has one route, `BookshelfDestinations.Home` (`"home"`), which renders a private `PlaceholderHomeScreen`: a default-font `Text(app_name)`, centered. `features/library/ui/` contains only a `.gitkeep`.
- **Resources.** `values/colors.xml` (hand-owned since #9) defines only `paper` `#F3EEE3`. `Theme.Bookshelf` is `Theme.Material3.Light.NoActionBar` with `android:windowBackground = @color/paper`. `ic_launcher_background` (`#F3EEE3`) is generator-owned by `tools/launcher-icon` and must not be touched. `SplashThemeTest` asserts `paper == ic_launcher_background == #F3EEE3`.
- **No live app font exists yet.** #9 used Playfair Display only as a build-time input to rasterize the splash PNG. The font lives in `tools/splash-screen/fonts/` and is not in the APK.
- **Design sources.**
  - Epic D20: a real design system, palette plus Fraunces/Inter. Not a throwaway.
  - `project/Main.dc.html` (the Library screen this route grows into): the app title is `font-family:'Fraunces'; font-weight:600; font-size:26px; color:#231F20` (ink) on paper.
  - CLAUDE.md Design Reference (flagged stale): paper `#F3EEE3`, ink `#231F20`, forest `#2F4538`, Fraunces + Inter.
  - `docs/specs/Bookshelf-Visual-Spec.md` (flagged as authoritative going forward): it uses **different values and a different serif**. `--bg #F5F1E8`, `--ink #3A3A3A`, `--forest #4F6F52`, and **Roboto Slab** instead of Fraunces. See decision L1.

## 2. Key decisions

| # | Decision | Why / alternatives |
|---|---|---|
| L1 | **Build against the ticket's literal tokens: Fraunces, paper `#F3EEE3`, ink `#231F20`, forest `#2F4538`. Do not reconcile with the Visual Spec in this ticket.** | The AC names these tokens explicitly. Epic D20 and the `project/` canvas use them too, and the user has deferred the CLAUDE.md/spec reconciliation. Paper also has to stay `#F3EEE3` because the system splash, the window background and the launcher icon background (byte-checked by #8's generator, asserted by `SplashThemeTest`) all use it. Switching only the landing screen to the spec's `#F5F1E8` would put a visible seam at the splash-to-landing handover, or pull #8/#9 rework into this ticket. **Rejected:** adopting the spec tokens (Roboto Slab, `#F5F1E8`/`#3A3A3A`/`#4F6F52`) now. That changes the ticket's AC and reaches into #8/#9 assets. **Cost of reversing later:** small by construction (L3). Swap three colour values and one font file plus one `FontFamily` declaration. The reconciliation ticket must also decide whether paper/`ic_launcher_background` move together. |
| L2 | **Bundle one static Fraunces TTF in `res/font/`.** Do not use the Google Fonts downloadable-font provider. | Bookshelf is local-first and offline (CLAUDE.md). The downloadable provider (`ui-text-google-fonts`) needs Google Play services and a network fetch on first launch, and **it falls back silently to the system font** while loading or when unavailable (emulators without Play, offline first run). That is the same class of silent failure as #9's resvg lesson. A bundled resource font is deterministic. A missing resource fails at compile time (`R.font.*`). An unreadable file throws when it loads (Compose's default `FontLoadingStrategy.Blocking`), so it fails loudly rather than falling back. **Rejected:** the variable font `Fraunces[SOFT,WONK,opsz,wght].ttf` with `FontVariation.Settings`. Compose supports it (API 26+, minSdk is 29), but it is several hundred KB for one weight, and it adds axis decisions (opsz, SOFT, WONK) this ticket does not need. Revisit when the Library screen needs three or more Fraunces weights or italics (the canvas uses 600, 700 and italic). At that point one variable file beats several static files. |
| L3 | **One theme entry point, `core/designsystem/`: `Color.kt`, `Type.kt`, `Theme.kt` exposing `BookshelfTheme { }`.** `MainActivity` wraps everything in `BookshelfTheme` instead of a bare `MaterialTheme`. | This puts the palette and typography in one place so L1's later reconciliation is a value swap, and so every later screen inherits paper/ink without per-screen colours. `core/` is the existing home for cross-feature infrastructure (CLAUDE.md tree). **Rejected:** styling the title inline in the screen (`fontFamily = ...`, `color = Color(0xFF231F20)`). That is quicker, but it is exactly the "throwaway placeholder" the ticket rules out, and it would scatter tokens. |
| L4 | **Colours stay single-source in `values/colors.xml`.** Add `ink` `#231F20` and `forest` `#2F4538` next to `paper`. `BookshelfTheme` builds its `ColorScheme` from `colorResource(R.color.*)`, with no Kotlin hex literals. | `paper` must exist in XML (window background, splash theme), so XML is the single source of truth. This honours #9's S5, which rejected a parallel Kotlin constant. `colorResource` inside a `@Composable` theme function costs nothing. **Rejected:** Kotlin `Color` constants with a test guarding parity against XML. That keeps two sources for no benefit. |
| L5 | **Colour roles (light scheme only):** `background = surface = paper`, `onBackground = onSurface = ink`, `primary = forest`, `onPrimary = paper`. Leave every other role at the M3 light default. | Paper with ink text is the canvas's base. Forest is the brand primary (CLAUDE.md) and is wired now so the palette is complete for the next screen. This screen itself has no primary-coloured element. **The title is ink**, as in `project/Main.dc.html`. Dark theme is out of scope: the app theme is already `Material3.Light`, and the design has no dark palette. `BookshelfTheme` ignores `isSystemInDarkTheme()` and does not use dynamic colour. |
| L6 | **Typography:** `BookshelfTypography` overrides **`headlineMedium` = Fraunces SemiBold (600), 26.sp**, from the canvas app-title style. All other styles stay at the M3 defaults. Inter is **not** added. | Add tokens when a screen first needs them, not speculatively (same rule as #9's S5). Inter comes with the first body text (Library epic). Use the **24pt optical-size static SemiBold instance**. That is the closest static cut to the canvas, where the browser auto-applies opsz of about 26 at 26px. |
| L7 | **The screen is `features/library/ui/LibraryScreen.kt` (`@Composable fun LibraryScreen(modifier: Modifier = Modifier)`), mounted on the existing `BookshelfDestinations.Home` route. `PlaceholderHomeScreen` is deleted.** | "Replaced in place by the Library screen" then holds literally. The Library epic grows this same file on this same route, with no rename, no new route and no deletion. CLAUDE.md already defines Library as the home route. The route name `Home`/`"home"` stays as it is, to avoid churn and keep the existing tests stable. No ViewModel: the screen has no state. Add one when the Library epic brings data. |
| L8 | **Layout:** `Box(fillMaxSize().background(colorScheme.background).safeDrawingPadding(), contentAlignment = Center)` holding `Text(stringResource(R.string.app_name), style = typography.headlineMedium, color = colorScheme.onBackground, modifier = Modifier.semantics { heading() })`. | The ticket says "nothing else". A lone title in the top-left corner reads as a broken screen, so it is centered, matching today's placeholder. The explicit `background` keeps the screen correct even if it is rendered outside the Activity's `Surface` (previews, tests). `heading()` gives TalkBack users the page landmark. Reusing `app_name` keeps the brand string single-sourced and keeps `MainActivityLaunchTest` working unchanged. |
| L9 | **Status-bar icons are forced dark:** `enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(TRANSPARENT, TRANSPARENT), navigationBarStyle = SystemBarStyle.light(...))`. | The default `SystemBarStyle.auto` follows the system dark mode. With the system in dark mode it draws **light icons on the light paper background**, which makes them unreadable. The app is light-only (L5), so its bar icons must be too. |

## 3. Font asset

- **File:** `app/src/main/res/font/fraunces_semibold.ttf`. Resource names must be lowercase `[a-z0-9_]`, so rename the upstream file.
- **Source:** the Fraunces family (Undercase Type, SIL OFL 1.1), static TTF, **24pt optical size, SemiBold**. Get it from the upstream `undercasetype/Fraunces` release or the Google Fonts family download (`static/` folder). Record provenance in this plan's "Implementation notes" or in the README asset section, the same way #9 did for Playfair Display: source URL, release/commit, original file name and SHA-256.
- **Must be a static instance.** Before committing, check that its `name` table says Fraunces SemiBold and that it has no `fvar` table. If you commit a variable file by mistake, Android renders its default instance, which is the wrong weight. That is the same trap #9 hit with resvg.
- **License:** the OFL requires the license to travel with redistributed copies. **Do not put `OFL.txt` in `res/font/`.** AAPT2 rejects non-font files in that folder and the build fails. Ship it as `app/src/main/assets/licenses/Fraunces-OFL.txt`. The first open-source-licenses screen (Settings, later) can list it.
- **`Type.kt`:** `val Fraunces = FontFamily(Font(R.font.fraunces_semibold, FontWeight.SemiBold))`. Declare only the weight that exists. Asking for an undeclared weight makes Compose synthesize a fake bold, which is silent.

## 4. File changes

| File | Change |
|---|---|
| `app/src/main/res/font/fraunces_semibold.ttf` | New (section 3). |
| `app/src/main/assets/licenses/Fraunces-OFL.txt` | New. |
| `app/src/main/res/values/colors.xml` | Add `ink` `#231F20` and `forest` `#2F4538`. Leave `paper` unchanged. |
| `core/designsystem/Color.kt` | Internal helper building the light `ColorScheme` from `colorResource` (L4, L5). |
| `core/designsystem/Type.kt` | `Fraunces` family plus `BookshelfTypography` (L6). |
| `core/designsystem/Theme.kt` | `@Composable fun BookshelfTheme(content: @Composable () -> Unit)` = `MaterialTheme(colorScheme, typography, content)`. |
| `features/library/ui/LibraryScreen.kt` | New (L7, L8), plus a `@Preview` wrapped in `BookshelfTheme`. Remove the `.gitkeep` from `features/library/ui/`. |
| `navigation/BookshelfNavGraph.kt` | The `Home` destination renders `LibraryScreen()`. Delete `PlaceholderHomeScreen` and its imports. |
| `MainActivity.kt` | `MaterialTheme` becomes `BookshelfTheme`. Set `enableEdgeToEdge` bar styles (L9). Splash gating unchanged. |
| `CLAUDE.md` | Architecture tree: add `core/designsystem/  # BookshelfTheme, palette, typography`. **Do not** rewrite the Design Reference section (deferred by the user). |
| `README.md` | Short note in the asset section: Fraunces is bundled, where it comes from, and where its license lives. |
| `CHANGELOG.md` | One `Added` line under `[Unreleased]`, written at integration time per the delivery conventions. |

`themes.xml`, the splash code and assets, `ic_launcher_background.xml` and both `tools/` packages are **not** touched.

## 5. Quality attributes and fitness functions

| Attribute | How the design serves it | Fitness function (developer implements) |
|---|---|---|
| **Visual correctness / no silent font fallback** | Bundled resource font (L2), one declared weight, theme-owned style (L3, L6). | **FF1** (instrumented): a `TextMeasurer` measures "Bookshelf" with `BookshelfTypography.headlineMedium` and with the same style using `FontFamily.Default` and `FontFamily.Serif`. Assert that the Fraunces width differs from both, and that the rendered title node's width matches the Fraunces measurement within 1px. This fails if the screen stops using the theme style or the font falls back. **FF2** (instrumented): `ResourcesCompat.getFont(ctx, R.font.fraunces_semibold)` is non-null and differs from `Typeface.DEFAULT` and `Typeface.SERIF`. |
| **Palette consistency / no flash at the splash-to-landing handover** | Paper everywhere: window background, `Surface` (via `colorScheme.surface`) and the screen background. | **FF3** (instrumented): after the splash completes, `onRoot().captureToImage()` sampled at the four corners and the centre-top equals `#F3EEE3`, and the darkest pixel inside the title node's bounds is within ±8 per channel of ink `#231F20`. **FF4**: extend `SplashThemeTest` (or add `PaletteTest`) to assert `R.color.ink == #231F20` and `R.color.forest == #2F4538`. This pins the AC tokens until the reconciliation ticket changes them on purpose. |
| **Accessibility** | Ink on paper is about 14:1 (well above 4.5:1). The title is a `heading()`. Text is in sp. Centered, single word, no `maxLines`. | **FF5** (instrumented): the title node has the heading semantics. Running the screen at font scale 2.0 (`DeviceConfigurationOverride.FontScale(2f)`) shows the full text with no ellipsis or clipping. |
| **Evolvability** | All tokens live in `colors.xml` plus `core/designsystem` (L3, L4). The screen file and route are reused by the Library epic (L7). | Review rule, not automated: no hex `Color(0x...)` literal and no `FontFamily(...)` outside `core/designsystem/`. Promote it to a lint/Konsist check only if it starts drifting. |

## 6. Security

No new entry point, input, storage, logging, permission or network access. The font is a static, bundled asset. The downloadable-fonts option would have added a Play-services provider query. Rejecting it (L2) also keeps the app free of network-dependent behaviour. Nothing for QA to probe security-wise.

## 7. Work split and tasks

**FRONTEND-ONLY:** a single Android screen. No backend exists, and no contract is needed.

`frontend-developer-android`, in build order:

1. Add the Fraunces SemiBold static TTF and its OFL license, record provenance, and check that it is static (section 3). (AC1)
2. Add `ink` and `forest` to `colors.xml`, and create `core/designsystem/` (`Color.kt`, `Type.kt`, `Theme.kt`) per L3–L6. Add FF2 and FF4. (AC1)
3. Switch `MainActivity` to `BookshelfTheme` and set the light system-bar styles (L9). (AC1, AC2)
4. Create `LibraryScreen` (L7, L8) with a preview, wire it as the `Home` destination, and delete `PlaceholderHomeScreen`. Add FF1, FF3 and FF5. (AC1)
5. Run `./gradlew lint test connectedAndroidTest`, then the manual end-to-end check (section 8, AC2). Update the CLAUDE.md architecture tree and the README font note. (AC2)

## 8. Test strategy

| AC | Level | What |
|---|---|---|
| AC1: start destination renders "Bookshelf" in Fraunces over paper/ink/forest | Instrumented Compose (`createAndroidComposeRule<MainActivity>`) | The existing `MainActivityLaunchTest.splashCompletes_andHomePlaceholderIsDisplayed` keeps passing; rename it to `..._andLibraryTitleIsDisplayed`. FF1 (font really used), FF3 (paper background, ink title), FF4 (token values), FF5 (heading, font scale). Also assert that the NavController's current route at start is `BookshelfDestinations.Home.route` (start destination, not a pushed screen). |
| AC1 | Compose component test (`createComposeRule`) | `LibraryScreen` inside `BookshelfTheme` in isolation: exactly one text node, "Bookshelf", and nothing else on screen ("nothing else" AC). |
| AC2: fresh install, then icon, then splash (system then custom), then title, no crash | Automated parts | Already covered: `LauncherIconTest` (#8), `SplashThemeTest` and `BookshelfSplashScreenTest` (#9), and `MainActivityLaunchTest` splash-to-title plus no replay on recreate (#9). They must all stay green. |
| AC2 | **Manual device check** (record the result in the PR) | `adb uninstall com.minicore.bookshelf`, then install the debug APK. Launch **from the launcher icon** (not `adb am start`, which skips the launcher). Do this on one API 29–30 device or emulator (compat splash path) and one API 31+ device (platform splash path), with the **system in light and in dark mode**. Check: correct adaptive icon, paper system splash with the icon, the 600ms artwork splash, then "Bookshelf" in Fraunces, ink on paper, with no white/near-white flash between stages and readable status-bar icons. Rotate once: the title stays and the splash does not replay. |

## 9. Risks for QA to probe

| # | Risk | Probe |
|---|---|---|
| R1 | **Silent font fallback.** The title renders in Roboto or the system serif, and a visual check at a glance misses it. | FF1 and FF2. A side-by-side screenshot against the canvas's "Bookshelf" header. Fraunces has a distinctive "k" and "f". |
| R2 | **Variable or wrong-weight file committed.** The weight looks Regular, or Compose fake-bolds it. | Section 3 static check. Only `FontWeight.SemiBold` is declared. |
| R3 | **Colour flash at the handover.** `Surface` defaults to the M3 near-white `surface` if `BookshelfTheme` is not applied above it. | FF3, and the manual check on a slow emulator. |
| R4 | **Status-bar icons invisible with the system in dark mode** (light icons on paper). | Manual check in dark mode (L9). |
| R5 | **Build failure from `OFL.txt` in `res/font/`.** | Covered by the file placement in section 3. The reviewer verifies it. |
| R6 | **Token drift vs the Visual Spec.** The screen ships with the CLAUDE.md tokens, which the spec marks stale (L1). This is an intended, documented choice, not a defect. | The reconciliation ticket owns it. FF4 makes the change deliberate and visible. |
| R7 | **Large font scale / small screens.** Clipping at 200% font scale on a 320dp-wide device. | FF5, plus one manual run at the largest system font size. |

## 10. ADR

**No ADR needed.** This is a routine feature addition that follows existing patterns: one screen, a theme wrapper, two colour resources and a bundled font file. It does not change the framework or module structure (Compose and M3 are already in place, and `core/designsystem` is a package inside the existing `app` module). It adds no external API or service (the network-based Google Fonts provider was rejected, L2), and it changes no database. If the reconciliation ticket later swaps Fraunces for Roboto Slab, that is a design-token change, not an ADR either.

## 11. Open items (not blocking)

- **Follow-up for the reconciliation ticket (user-deferred):** reconcile the CLAUDE.md tokens with the Visual Spec. That covers Fraunces vs Roboto Slab, paper `#F3EEE3` vs `--bg #F5F1E8` (which also drags in `ic_launcher_background`, the splash theme and `SplashThemeTest`), ink `#231F20` vs `#3A3A3A`, and forest `#2F4538` vs `#4F6F52`. With L3/L4 in place, the code side is a value swap in `colors.xml` and `Type.kt`.
