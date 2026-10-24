# App Bootstrap — Runnable Shell, Icon & Splash Screen

**Type:** Epic (tracking issue) · **Repo:** [minicore-software/bookshelf](https://github.com/minicore-software/bookshelf) · **Board:** [Bookshelf project](https://github.com/users/minicore-software/projects/1) · **Team:** minicore-software
**Source:** brainstorming doc `App-Bootstrap-Epic.md` (decisions D1–D20)

---

## Summary

Scaffold the Bookshelf Android project so it builds, installs, shows the real icon and splash screen, and boots to a blank screen titled "Bookshelf" — no product features yet.

This epic:
1. **Android project scaffold** — Gradle `app` module + `features/library` package, Hilt/Room/Navigation Compose wired in now, phone-only.
2. **App icon** — `icon.svg` re-cut into true adaptive icon layers (foreground/background), generated programmatically, exact look preserved.
3. **Splash screen** — a two-stage splash (system + custom), reusing the pattern already proven in the Cartio project: compat-library system splash, then a custom full-bleed Compose screen built from `splash-screen.svg`, shown for 600ms.
4. **Blank landing screen** — Compose screen titled "Bookshelf", using the real palette/type tokens, wired as the actual Navigation Compose start destination.

## Background

- **Current state:** the repo has no Android project yet — only docs (`CLAUDE.md`, `README.md`, `CHANGELOG.md`) and two design assets.
- **`CLAUDE.md`** already commits the stack: Kotlin, Jetpack Compose, MVVM + Repository, Room, Hilt (KSP), Navigation Compose; App ID `com.minicore.bookshelf`; compileSdk 37 (minor 2), targetSdk 36, minSdk 29; a full dependency version table.
- **Existing building blocks reused:** the splash-screen architecture (AndroidX `androidx.core.splashscreen` compat library for the instant system splash + a custom full-bleed Compose screen for the illustrated artwork) is the same pattern already shipped in the Cartio project — reused as the implementation model, not copied code.
- `resources/icon.svg` and `resources/splash-screen.svg` are the two source assets this epic converts into Android resources.

## Flow

```
Cold start
  └─ System splash (androidx.core.splashscreen, instant, all API levels ≥29)
       icon: new adaptive launcher icon · background: "paper" #F3EEE3
  └─ Custom BookshelfSplashScreen (Compose, 600ms)
       full-bleed render of splash-screen.svg artwork (title "Bookshelf" baked into the art)
  └─ Navigation Compose start destination: blank Landing screen
       "Bookshelf" title · Fraunces display font · paper/ink/forest palette
```

## Scope

### 1. Android project scaffold
- New `app` Gradle module, versions from `CLAUDE.md`'s table (AGP 9.4.1, Compose BOM 2026.09.00, Hilt 2.60.1, Room 2.8.5, Navigation Compose 2.10.2, etc.).
- Wire Hilt, Room, and Navigation Compose now, even though no feature uses them yet.
- Scaffold `features/library/{ui,domain,data}` (empty) alongside `core/` and `di/`.
- Phone-only target for now — nothing locks out tablet/foldable support later.
- No CI/lint pipeline in this epic.

### 2. App icon
- Re-cut `resources/icon.svg` into true Android adaptive icon layers: foreground (the three books) + background (a flat `paper`/`forest` palette token).
- Generate the adaptive icon XML, mipmaps/vector drawables, and legacy/round fallbacks programmatically.
- Preserve the exact look — gradients and drop shadow unchanged, no simplification.

### 3. Splash screen
- `Theme.Bookshelf.Splash` (extends `Theme.SplashScreen`): instant system splash shows the new adaptive launcher icon on a `paper` (`#F3EEE3`) background.
- `installSplashScreen()` called in `MainActivity.onCreate()`, before `super.onCreate()`.
- A custom `BookshelfSplashScreen` Composable renders the converted `splash-screen.svg` artwork full-bleed (`ContentScale.FillBounds`) for 600ms, then hands off to the nav graph.
- No separate pre-API-31 fallback — the compat library covers minSdk 29 up to current uniformly.

### 4. Blank landing screen
- Single Compose screen: "Bookshelf" title in Fraunces, over the `paper`/`ink`/`forest` palette from `CLAUDE.md`'s Design Reference — not a throwaway placeholder.
- Wired as the real Navigation Compose start destination, later replaced in place by the Library screen from `project/`.
- Acceptance includes the end-to-end boot check: fresh install → correct launcher icon → splash sequence (system then custom) → lands on the "Bookshelf" title, no crash.

## Out of scope

- Any real app feature (library, lending, progress, stats) — those are later epics; already designed in `project/`.
- CI/CD or linting pipeline.
- Tablet/foldable-specific layouts (phone-only for now; not precluded later).
- A custom pre-API-31 splash fallback (not needed — the compat library covers it).

## Dependencies

| Dependency | Owner |
|------------|-------|
| None — all open points were resolved during brainstorming | — |

## To settle at refinement

- Exact adaptive-icon safe-zone crop/scale for the re-cut foreground layer (books).
- Confirm the system-splash background token and the custom Compose splash's own background both resolve to the same `paper` value.

## Candidate tickets

**Order: Scaffold → Icon → Splash → Landing.** Each ticket builds on the previous one's output (Splash needs Icon's adaptive icon resource; Landing's end-to-end AC needs all three done) — not independently pickable despite the epic having no *external* dependencies.

| Area | Ticket | Acceptance criteria |
|------|--------|---------------------|
| Scaffold | Task: Create Android project scaffold | `./gradlew assembleDebug` succeeds; app installs and launches on a device/emulator; package layout matches `CLAUDE.md` (`app`, `features/library`, `core`, `di`); `@HiltAndroidApp` application class present and the Hilt component generates without error; an empty `@Database` Room class compiles; `MainActivity` hosts a `NavHost` with at least one registered route. |
| Icon | Task: Convert `icon.svg` into an adaptive Android launcher icon | Adaptive icon (separate foreground/background layers) plus legacy/round fallbacks generated; visually matches `icon.svg` (gradients/shadow preserved); foreground content stays fully inside the adaptive safe zone (no clipping) and the background fully fills the mask under circle, squircle, and rounded-square launcher shapes. No themed/monochrome (Android 13+ Material You) icon variant in this pass — falls back to the full-color icon, consistent with D16. |
| Splash | Task: Implement the two-stage splash screen from `splash-screen.svg` | System splash shows the new icon (from the Icon ticket) on a `paper`-colored background instantly on cold start, verified on at least one emulator/device at API 29–30 and one at API 31+ — no fallback code path needed on either; custom full-bleed Compose splash shows the converted `splash-screen.svg` artwork for 600ms; transitions to the landing screen with no flicker or crash. |
| Landing | Story: Blank landing screen titled "Bookshelf" | Navigation Compose start destination renders "Bookshelf" in Fraunces over the paper/ink/forest palette; end-to-end check passes: fresh install → correct icon → splash sequence → "Bookshelf" title visible, no crash. |

## References

- Design canvas: [`project/`](project) (not used by this epic — reused by later feature epics)
- Reused pattern: Cartio's splash-screen implementation (`androidx.core.splashscreen` + custom Compose splash)
- Brainstorm doc: `App-Bootstrap-Epic.md` (D1–D20)
