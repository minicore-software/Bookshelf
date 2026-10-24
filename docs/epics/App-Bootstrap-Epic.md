# Epic (DRAFT): App Bootstrap — Runnable Shell, Icon & Splash Screen

| Field   | Value |
|---------|-------|
| Tracker | GitHub Issues + Projects v2 — [minicore-software/bookshelf](https://github.com/minicore-software/bookshelf), [project board](https://github.com/users/minicore-software/projects/1) — not created yet |
| Team    | minicore-software (solo, TBD) |
| Repo    | minicore-software/bookshelf |
| Owner   | TBD |
| Status  | All 4 parts closed — ready for GitHub-ready doc |
| Updated | 2026-10-24 |

> This draft contains only what is confirmed by the user or found in the repo.
> Anything not confirmed is listed under **Open Questions** rather than assumed.
> Work proceeds part by part. This repo uses GitHub Issues/Projects, not Jira —
> "epic" here means a tracking issue with child task issues, not a Jira epic.

---

## 1. Summary

Bootstrap the Bookshelf Android app so it actually runs: a real Gradle/app module
(per the stack already decided in `CLAUDE.md`), the app icon, a splash screen, and
a single blank landing screen showing the title "Bookshelf". No features yet —
this epic only gets something installable and runnable on a device/emulator.

Parts:
1. Android project scaffold
2. App icon (from `resources/icon.svg`)
3. Splash screen (from `resources/splash-screen.svg`)
4. Blank landing screen (title "Bookshelf")

## 2. Decisions (confirmed by product owner)

| # | Decision |
|---|----------|
| D1 | **Scope is bootstrap-only.** No real features (library, lending, progress, stats) — just a runnable shell. (user) |
| D2 | **Title text is "Bookshelf".** Shown on the blank landing screen. (user) |
| D3 | **Icon source is `resources/icon.svg`.** Needs conversion (PNG or other Android-appropriate format) — exact target format(s) TBD. (user) |
| D4 | **Splash screen source is `resources/splash-screen.svg`.** Needs conversion the same way. (user) |
| D5 | **Ticket typing:** Parts 1–3 (scaffold, icon, splash) are **Task/enabler** work — no user-facing trade-off to negotiate. Part 4 (landing screen) is the one **Story**, and carries the end-to-end boot check (icon visible → splash shows → "Bookshelf" title lands) as its acceptance criteria, instead of a separate 5th smoke-test ticket. (PO) |
| D6 | **App ID `com.minicore.bookshelf` is final**, not a placeholder — taken as given from `CLAUDE.md`, not re-litigated. (PO, confirms Q1.4) |
| D7 | **Fact:** `resources/splash-screen.svg` is a full-bleed, 1080×1920 illustrated scene (wall, shelf, books, plant, sun/hills) with the "Bookshelf" wordmark already rendered into the artwork itself — it is not an icon-only mark. The Android 12+ platform `SplashScreen` API only supports a centered icon + solid/branding-image background, so it **cannot** reproduce this SVG as designed. (resolved by opening the file, confirms PO's Q4) |
| D8 | **Fact:** `resources/icon.svg` already bakes its own rounded-square corners (`rx=108`) and a cream gradient background into the single composition — it is not pre-split into adaptive foreground/background layers. (resolved by opening the file, informs Q2.1/PO's Q3) |
| D9 | **No scope conflict on showing "Bookshelf" twice** (once in the splash artwork, once as the landing screen's title) — the user asked for both explicitly; this is intentional, not a duplication bug. |
| D10 | **Scaffold `app` + the `features/library` package now** (not the other feature packages — library is the one to start with). (Q1.1) |
| D11 | **Wire Hilt, Room, and Navigation Compose now**, even though unused yet — needed imminently for the next real feature. (Q1.2) |
| D12 | **CI (GitHub Actions build/lint) is out of scope** for this epic. (Q1.3) |
| D13 | **Phone-only scaffold for now.** Does not restrict future tablet support — nothing in this bootstrap locks orientation/resizability (no `android:resizeableActivity="false"`, no fixed layouts); adaptive/tablet layout work can be added later without rework. (Q1.5, answered) |
| D14 | **Re-cut `icon.svg` into true adaptive icon layers:** books as the foreground layer, a flat `paper`/`forest` palette token as the background layer (not a single flattened legacy icon). (Q2.1) |
| D15 | **Icon conversion done programmatically now** (not via Android Studio's Image Asset tool) — must still produce correct, validated Android assets (adaptive icon XML + mipmaps/vector drawable at the right densities/safe zone). (Q2.2) |
| D16 | **Keep the icon's exact look** — gradients and drop shadow unchanged, no simplification for small-size legibility. (Q2.3) |
| D17 | **Splash follows Cartio's two-stage pattern** (checked `C:\Workspace\Cartio`): the AndroidX `androidx.core.splashscreen` compat library (`Theme.SplashScreen`, `installSplashScreen()` in `onCreate`) shows the instant system splash (launcher icon on a background color) on cold start, then `setContent` shows a **custom full-bleed Compose screen** rendering the real `splash-screen.svg` artwork for a short delay before the landing screen. This resolves Q3.1 — it's effectively both (A) and (B) layered, matching Cartio's own approach. (Q3.1) |
| D18 | **No separate pre-API-31 fallback needed** — `androidx.core.splashscreen` backports the system splash behavior down to minSdk, so one code path covers 29–30 and 31+ alike (same as Cartio, minSdk 29). (Q3.2) |
| D19 | **Custom splash duration: 600ms** (mirrors Cartio's `SPLASH_DURATION_MS`), well under the user's 2s cap. (Q3.3) |
| D20 | **Landing screen uses the real design system** — `paper`/`ink`/`forest` palette and Fraunces (title) + Inter (body) fonts from `CLAUDE.md`'s Design Reference, not a throwaway plain placeholder. (Q4.1) |

## 3. Background (what exists today)

**Repo state** (checked directly):
- No `app/` module, no Gradle files, no `AndroidManifest.xml` — the repo is currently docs + design only.
- `CLAUDE.md` already commits to a stack: Kotlin, Jetpack Compose, MVVM + Repository, Room, Hilt (KSP), Navigation Compose, CameraX + ML Kit Barcode Scanning; App ID `com.minicore.bookshelf`; compileSdk 37 (minor 2), targetSdk 36, minSdk 29; dependency version table included.
- `project/` holds the Design canvas (8 screens: Library, Book Detail, Discover & Add, Update Progress, Reading Stats, Settings, Lend a Book, My Loans) — none of these are in scope for this epic.
- `resources/icon.svg` — 512×512 rounded-square launcher icon (3 book spines on a shelf), cottage-warm palette, radial gradients + drop shadows.
- `resources/splash-screen.svg` — 1080×1920 full splash illustration (bookshelf wall scene, "Bookshelf" title already rendered into the SVG itself, sun/hills motif).

**Related epics / tickets (context only):** none — this is the first GitHub issue/epic for the repo.

## 4. Part 1 — Android project scaffold

**Goal (from product owner):** Get a Gradle Android project that builds and installs, using the stack `CLAUDE.md` already committed to.

**Confirmed:** D1.

### Working flow (from D10, D11, D13)

```
repo root
 ├── app/                                  (new Gradle module, phone-only — D13)
 │    ├── build.gradle.kts                 (plugins: application, compose-compiler, ksp, hilt — D11; versions from CLAUDE.md)
 │    └── src/main/
 │         ├── AndroidManifest.xml
 │         └── java/com/minicore/bookshelf/
 │              ├── MainActivity.kt        → hosts Nav Compose graph, start dest = Part 4's blank screen
 │              ├── di/                    → Hilt modules (D11, scaffolded empty)
 │              ├── core/database/         → Room database wiring (D11, scaffolded empty)
 │              └── features/library/      (D10)
 │                   ├── ui/
 │                   ├── domain/
 │                   └── data/
 ├── build.gradle.kts                      (root top-level plugins block)
 ├── settings.gradle.kts
 └── gradle/libs.versions.toml             (version catalog, values from CLAUDE.md)
```

### Answered — Part 1

| # | Question | Answer |
|---|----------|--------|
| ~~Q1.4~~ | Is `com.minicore.bookshelf` final? | Yes, final — taken as given from `CLAUDE.md` → D6 |
| ~~Q1.1~~ | `app` module only, or also `features/library`? | Scaffold `app` + `features/library` now → D10 |
| ~~Q1.2~~ | Wire Hilt/Room/Nav Compose now or defer? | Now — needed for the next feature → D11 |
| ~~Q1.3~~ | CI in this epic? | Out of scope → D12 |
| ~~Q1.5~~ | Phone-only, or tablet/foldable from day one? | Phone-only for now; confirmed no future restriction → D13 |

**Part 1: closed.**

## 5. Part 2 — App icon

**Goal:** Turn `resources/icon.svg` into a working Android launcher icon.

### Answered — Part 2

| # | Question | Answer |
|---|----------|--------|
| ~~Q2.1~~ | Flat legacy icon, or re-cut into adaptive layers? | Re-cut into true adaptive foreground/background → D14 |
| ~~Q2.2~~ | Conversion method? | Programmatically now, validated → D15 |
| ~~Q2.3~~ | Keep exact look, or simplify? | Keep exact — not much to muddy at this icon's detail level → D16 |

**Part 2: closed.**

### Notes for ticket-writing (Part 2)

Re-cutting the SVG into adaptive layers means separating what's currently one composition:
- **Background layer:** the rounded-square (`rx=108`) cream gradient (`iconBgGradient`) — becomes the adaptive background, likely simplified to a plain square (Android supplies the mask/rounding itself, so the source SVG's own corner-rounding is dropped for this layer, not duplicated).
- **Foreground layer:** the 3 book shapes + shelf — re-centered/scaled to fit Android's adaptive foreground safe zone (inner ~66% of the 108×108dp canvas), since the original artwork was composed assuming its own flat bounds, not the adaptive safe-zone inset.
- Still need a **legacy (non-adaptive) icon** and a **round icon** fallback for older launchers — generated from the same two layers (flattened) rather than as a third separate asset.

## 6. Part 3 — Splash screen

**Goal:** Show `resources/splash-screen.svg` as the app's splash screen on cold start.

### Answered — Part 3 (first batch)

| # | Question | Answer |
|---|----------|--------|
| ~~Q3.3~~ | Does the splash's baked-in "Bookshelf" text duplicate the landing screen's title? | No conflict — showing it in both is what was asked for → D9 |

### Answered — Part 3 (second batch)

| # | Question | Answer |
|---|----------|--------|
| ~~Q3.1~~ | Custom splash, platform API, or both? | Cartio's two-stage pattern: compat-library system splash + custom full-bleed Compose splash → D17 |
| ~~Q3.2~~ | Pre-API-31 fallback needed? | No — compat library backports it uniformly → D18 |
| ~~Q3.3~~ | Custom splash duration? | 600ms (within the 2s cap given) → D19 |

**Part 3: closed.**

### Notes for ticket-writing (Part 3)

Mirrors `Cartio`'s structure directly (file/package names adapted):
- `res/values/themes.xml`: `Theme.Bookshelf.Splash` extends `Theme.SplashScreen` — `windowSplashScreenAnimatedIcon` = the new adaptive launcher icon (Part 2), `windowSplashScreenBackground`/`windowSplashScreenIconBackgroundColor` = a Bookshelf palette token (`paper` `#F3EEE3`), not Cartio's white. `postSplashScreenTheme` = `Theme.Bookshelf`.
- `MainActivity.onCreate`: call `installSplashScreen()` before `super.onCreate()`, same as Cartio.
- `features/splash/ui/BookshelfSplashScreen.kt`: `Composable` showing `painterResource` of the converted `splash-screen.svg` (→ drawable, Part 3's own SVG→Android conversion, same as Part 2's icon conversion but simpler — single full-bleed image, no density mipmaps needed if using a vector/XML drawable), `fillMaxSize()`, `ContentScale.FillBounds`, `LaunchedEffect` + `delay(600L)` → `onComplete`.
- `setContent` in `MainActivity` gates on a `splashCompleted` state exactly like Cartio, showing the splash composable first, then (once Nav Compose is wired per D11) the nav graph with Part 4's landing screen as start destination.

## 7. Part 4 — Blank landing screen

**Goal:** A single screen with the title "Bookshelf", nothing else.

### Answered — Part 4

| # | Question | Answer |
|---|----------|--------|
| ~~Q4.2~~ | Throwaway, or the real start destination? | Resolved by D11 — Nav Compose wired now, so it's the real start destination, replaced in place later by the Library screen |
| ~~Q4.1~~ | Plain placeholder, or real design system? | Real design system — palette + Fraunces/Inter → D20 |

**Part 4: closed.**

---

**All parts closed.**

## N. Dependencies

| Dependency | Owner | Source |
|------------|-------|--------|
| Android Studio / SDK conversion tooling for the SVGs (if manual) | user | Q2.2 |
| GitHub Projects board field values (Status/Priority/Size options) to map issues into | user, TBD | project board already has these fields configured |
