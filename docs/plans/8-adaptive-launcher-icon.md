# Plan: #8 Convert icon.svg into an adaptive Android launcher icon

| Field | Value |
|---|---|
| Ticket | [#8](https://github.com/minicore-software/bookshelf/issues/8), part of epic #6 (App Bootstrap) |
| Branch | `feature/BOOKSHELF-0008-adaptive-launcher-icon` |
| Stack | Android: Kotlin, Jetpack Compose, single `app` module. Dev-only Node script for asset generation. No backend. |
| Agents | `frontend-developer-android` builds it, `frontend-reviewer-android` reviews it |
| Work split | FRONTEND-ONLY |
| UX spec | Not needed. This converts an approved design asset. It adds no flow, screen or state. |
| ADR | None. See section 11. |
| Status | Ready to build |

---

## 1. Context

- `resources/icon.svg` is a 512x512 flat icon. It has a rounded-square background (`rx=108`, gradient `#FBF7EE` to `#ECE3D0`), then a group scaled 1.25x around (256,256). The group holds a terracotta shelf (filter `shelfShadow`) and three book spines (filter `iconShadow` on the book group). Every fill is a linear gradient or a flat color with opacity.
- The app has **no launcher icon yet**. #7 left out `android:icon`/`android:roundIcon` on purpose, and lint's `MissingApplicationIcon` is an accepted warning until this ticket lands.
- Epic decisions: D14 (true adaptive layers: books = foreground, flat `paper`/`forest` token = background), D15 (generate the assets in code, no Android Studio Image Asset Studio), D16 (keep the exact look: gradients and drop shadows unchanged).
- `minSdk` is 29, so every device that runs the app supports adaptive icons (API 26+). This affects folder qualifiers and legacy icons (sections 5 and 7).
- #9 (splash) will reuse this adaptive icon as `windowSplashScreenAnimatedIcon`. Nothing in this plan blocks that.

## 2. Key decisions

| # | Decision | Why |
|---|---|---|
| K1 | **Foreground layer = raster PNG per density, not a VectorDrawable.** | `VectorDrawable` supports gradients (`aapt:attr` + `<gradient>`, API 24+) but has **no filter or blur primitive**. Both drop shadows (`feDropShadow`, stdDeviation 10 and 8) cannot be expressed. The only vector option is faking the blur with stacked translucent paths or radial gradients. That is the "simplification" D16 rules out. Image Asset Studio rasterizes in this same situation. |
| K2 | **Background layer = flat color `paper` `#F3EEE3` as a color resource.** | The ticket's scope says "a flat `paper`/`forest` palette token". `paper` is almost exactly the midpoint of the original gradient: the midpoint of `#FBF7EE`..`#ECE3D0` is about `#F3EDDF`, which differs from `#F3EEE3` by 0/1/4 levels per channel. So the AC "visually matches" holds. `forest` `#2F4538` is rejected: the left book (`#193529`..`#244738`) would almost disappear against it, and the whole look would change. The source's rounded corners (`rx=108`) are **dropped**, because the launcher applies its own mask. See section 3 for the gradient option. |
| K3 | **Render with a pinned Node script using `@resvg/resvg-js`**, committed under `tools/launcher-icon/`. The generated PNGs/XML are committed. Gradle never runs Node. | resvg renders SVG 1.1 + `feDropShadow` accurately, including filter regions. It ships prebuilt binaries, so no native toolchain is needed. It returns raw RGBA pixels, which the automated checks use. **Spike result (done while planning, v2.6.2):** it rendered both shadows correctly and measured the foreground extent below. CairoSVG was rejected (weak filter support, needs native Cairo on Windows). Inkscape/ImageMagick are not installed and are heavy CLI dependencies. Image Asset Studio is a manual step (D15). |
| K4 | **The source markup is used verbatim.** The script reads `icon.svg`, removes only the background `<rect>`, and wraps the rest in one affine transform. | This guarantees the look is preserved: same gradients, same filters, same filter regions. Filter `dx/dy/stdDeviation` are in user space, so the shadows scale with the content. No hand-copied path data can drift from the source. |
| K5 | **Legacy and round fallbacks are flattened from the same two layers.** No third asset. | Epic Part 2 says so. One source of truth. |
| K6 | **Adaptive XML goes in `mipmap-anydpi/`, not `mipmap-anydpi-v26/`.** | With `minSdk` 29, the `-v26` qualifier is redundant and lint `ObsoleteSdkInt` flags it. Current Android Studio templates also use `mipmap-anydpi` when minSdk is 26 or higher. |

## 3. Background: flat vs subtle gradient (decided: flat)

The ticket text says flat, and flat `paper` matches the original gradient's midpoint to within 4 levels per channel. So this plan uses flat. **Nothing blocks this, and it is cheap to change.** If the user later wants the gradient, the reversible alternative is:

- Swap `@color/ic_launcher_background` for a `drawable/ic_launcher_background.xml` VectorDrawable. Use a 108x108 viewport, one full-bleed square path (no corner radius, no mask of its own), and an `aapt:attr` linear gradient from `#FBF7EE` (0,0) to `#ECE3D0` (108,108). The legacy composites in the script would use the same gradient.
- Cost: one file plus one script constant. Note that every launcher mask crops the 108dp canvas differently, so the visible gradient range changes per shape. The flat color does not have this problem.

## 4. Foreground geometry (the "to settle during implementation" item, settled)

Adaptive icon rules: 108x108dp canvas. The launcher shows the inner 72x72dp through its mask. Content that must never clip has to stay inside the **66dp-diameter circle centered at (54,54)**. Every mask shape (circle, squircle, rounded square, teardrop) contains this circle. The outer 18dp on each side is for parallax/pulse effects.

**Transform (exact).** The foreground SVG is generated as:

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="108" height="108">
  <g transform="translate(54 54) scale(0.12) translate(-256 -254.75)">
    <!-- icon.svg inner content verbatim (defs + the existing 1.25x group), background rect removed -->
  </g>
</svg>
```

How the numbers are derived:
- Content in raw (pre-1.25) source units: shapes span x 100..412, y 140..370, so the visual center is (256, 255). With the shadows and their filter regions, the visible extent reaches x ~68..444 and y ~119..390.
- The existing 1.25x group stays in place. So in the source's outer coordinates, the center is (256, 256 + 1.25 x (255 - 256)) = **(256, 254.75)**.
- Target: the effective scale is **0.15dp per raw source unit**, which is `0.12` after the existing 1.25x. Farthest visible pixel, estimated by hand: about 211 raw units from center (the shelf-shadow corner), so 211 x 0.15 = 31.7dp.
- **Spike measurement** (resvg 2.6.2, rendered at xxxhdpi 432px, every pixel with alpha > 0): max radius **31.39dp**, bounding box x 28.0..80.0dp, y 35.3..73.0dp. That leaves **1.6dp of margin** inside the 33dp safe radius. For comparison, scale 0.155 gives 32.64dp (too tight) and 0.16 gives 33.54dp (fails).
- Keep `0.12`/`0.15` as **one named constant** in the script. If the safe-zone check (section 8) ever fails, for example after a renderer upgrade, lower it. Never raise it above 0.155.
- Side effect to expect: the shelf takes about 65% of the visible 72dp width, compared with 76% of the 512px canvas in the original. Every adaptive icon is smaller than its flat version because of the safe zone. This is not a fidelity loss.

## 5. Output files

All generated by the script. Do not hand-edit them.

```
app/src/main/res/
 ├── mipmap-anydpi/ic_launcher.xml            adaptive-icon: background @color/ic_launcher_background, foreground @mipmap/ic_launcher_foreground, NO <monochrome>
 ├── mipmap-anydpi/ic_launcher_round.xml      identical content
 ├── values/ic_launcher_background.xml        <color name="ic_launcher_background">#F3EEE3</color>
 ├── mipmap-mdpi/    ic_launcher_foreground.png 108px | ic_launcher.png 48px  | ic_launcher_round.png 48px
 ├── mipmap-hdpi/    ic_launcher_foreground.png 162px | ic_launcher.png 72px  | ic_launcher_round.png 72px
 ├── mipmap-xhdpi/   ic_launcher_foreground.png 216px | ic_launcher.png 96px  | ic_launcher_round.png 96px
 ├── mipmap-xxhdpi/  ic_launcher_foreground.png 324px | ic_launcher.png 144px | ic_launcher_round.png 144px
 └── mipmap-xxxhdpi/ ic_launcher_foreground.png 432px | ic_launcher.png 192px | ic_launcher_round.png 192px
```

Adaptive icon XML (both files):

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
```

- Format: **PNG**, RGBA, written by resvg's `asPng()`. AAPT2 crunches PNGs at build time. WebP would need a second dependency (an encoder) for a few KB of savings.
- `ic_launcher_foreground` stays a separate mipmap resource so #9 can reference it if needed.

## 6. Legacy and round fallbacks

Each fallback is a composite SVG. It uses the same foreground group and the same background color, and its viewBox is the visible viewport `18 18 72 72`:

- `ic_launcher` (legacy square): clip to a rounded rect `x=18 y=18 w=72 h=72 rx=15.19`. That is the original's corner ratio, 108/512 = 0.2109, times 72. Fill the clip with `#F3EEE3`, then draw the foreground group.
- `ic_launcher_round`: clip to `circle cx=54 cy=54 r=36`. Same fill, same foreground group.
- Rendered at 48dp per density (48/72/96/144/192px). Outside the clip is transparent. No extra legacy drop shadow (D16: no change to the look).
- Spike: the round composite rendered correctly at 192px with both shadows intact.

**Honest note:** with `minSdk` 29, the `mipmap-anydpi` adaptive XML always wins resource resolution (`anydpi` beats every density qualifier), so these legacy PNGs are **never chosen at runtime** on a supported device. They are still generated because the AC requires them, and they cost about 10 small files. They also give a ready-made flat PNG for store listings or docs later. The Play Store 512px icon is out of scope.

## 7. Manifest wiring

In `app/src/main/AndroidManifest.xml`, on `<application>`, add:

```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

No other app code, Gradle or theme changes. This resolves lint's `MissingApplicationIcon`.

## 8. The script: `tools/launcher-icon/`

```
tools/launcher-icon/
 ├── package.json         "private": true, "type": "module", devDependency @resvg/resvg-js pinned EXACT (spike used 2.6.2), scripts: generate, check
 ├── package-lock.json    committed (integrity hashes)
 └── generate.mjs         single script, two modes
```

Add to the root `.gitignore`: `tools/launcher-icon/node_modules/` and `tools/launcher-icon/out/`.

How to run (document this in README, section 10): `cd tools/launcher-icon && npm ci && npm run generate`, then `npm run check`. Prerequisite: Node LTS. This is dev-only. Node is not needed to build the app.

**`generate` mode:**
1. Read `resources/icon.svg`. Find the background `<rect width="512" height="512" rx="108" fill="url(#iconBgGradient)" />` and **assert exactly one match**. Fail loudly otherwise, so a redesign of `icon.svg` cannot silently produce a wrong layer.
2. Remove that rect. Strip the root `<svg ...>`/`</svg>` tags (assert both are found). Keep everything else byte-for-byte, including the now-unused `iconBgGradient` def, which is harmless.
3. Build the foreground SVG (section 4) and the two composite SVGs (section 6). All three come from shared constants: `SCALE`, `CENTER`, `BACKGROUND = "#F3EEE3"`, `LEGACY_RX_RATIO = 108/512`.
4. Render with `new Resvg(svg, { fitTo: { mode: 'width', value: px } })` at every size in section 5. Write the PNGs into `app/src/main/res/mipmap-*/`.
5. Write the two adaptive XMLs and `values/ic_launcher_background.xml`, with LF endings and a final newline.
6. Write review aids to `out/` (gitignored): `foreground.svg`; a **mask preview sheet** with the adaptive icon under a circle (r=36dp), a squircle (superellipse n≈4 inscribed in 72dp), a rounded square (rx≈0.25x72) and a teardrop, each with the 66dp safe circle outlined; and a side-by-side of `icon.svg` rendered as is vs `ic_launcher.png`.

**`check` mode** (the fitness function, exit code ≠ 0 on failure):
1. **Safe zone.** Load each `ic_launcher_foreground.png` (or render it again). Every pixel with alpha > 0 must have its center within `33dp x density` of the canvas center. Report the max radius in dp (expected about 31.4).
2. **Dimensions.** Each PNG has exactly the pixel size in section 5.
3. **Background coverage.** Assert the adaptive XML's background points at a color resource, and that the color is fully opaque `#F3EEE3` (a 6-digit hex, no alpha byte). An opaque color drawable fills the whole 108dp canvas, so it fills every mask.
4. **No monochrome.** Assert the adaptive XMLs have no `<monochrome>` (out of scope per the ticket).
5. **Drift.** Render everything again in memory and compare it pixel by pixel with the committed PNGs (allow at most 1 level per channel). This catches hand edits and a changed `icon.svg` without a regeneration.

Determinism: the exact version pin plus `npm ci` gives identical renders. A resvg upgrade is a deliberate change: run `generate`, then `check`, and commit the new PNGs.

## 9. Quality attributes and fitness functions

| Attribute | How the design serves it | Fitness function |
|---|---|---|
| Visual fidelity (D16) | Verbatim source markup, affine transform only, a renderer with full filter support | `check` drift test; the side-by-side preview for reviewer sign-off |
| Mask compatibility (AC3) | Content scaled into the 66dp safe circle with 1.6dp margin; opaque full-bleed background | `check` safe-zone + background-coverage assertions; mask preview sheet |
| Reproducibility (D15) | One script, pinned renderer, lockfile, outputs committed | `npm ci && npm run check` passes on a clean clone |
| Build hygiene | Correct qualifiers, manifest wired | `./gradlew lint` shows no `MissingApplicationIcon`, `ObsoleteSdkInt`, `IconDensities`, `IconMissingDensityFolder`, `IconDipSize` or `IconLocation` warnings for these files |

Optional cheap instrumented test (`connectedAndroidTest`): `LauncherIconTest` checks that `context.applicationInfo.icon != 0` and that `ContextCompat.getDrawable(context, R.mipmap.ic_launcher)` is an `AdaptiveIconDrawable`, with a non-null `foreground` and `background`. It uses only test dependencies that already exist.

## 10. Work split and tasks (FRONTEND-ONLY, `frontend-developer-android`)

In build order:

1. **Tooling skeleton**: `tools/launcher-icon/package.json` (private, ESM, `@resvg/resvg-js` pinned exact) and `package-lock.json` via `npm install --save-exact`. Add the `.gitignore` entries. (AC1)
2. **`generate` mode** per section 8, steps 1-5. Run it and commit the generated `res/` files. (AC1, AC2)
3. **`check` mode** per section 8 (safe zone, dimensions, background, no monochrome, drift), plus the `out/` preview sheets. It must pass. (AC2, AC3)
4. **Manifest**: add `android:icon` and `android:roundIcon` (section 7). (AC1)
5. **Verify**: `./gradlew assembleDebug lint test`, then `installDebug` on an emulator. Check the home screen and app drawer icons. Switch the launcher icon shape where the device offers it. If not, the mask preview sheet is the evidence for circle, squircle and rounded square. Optionally add `LauncherIconTest` (section 9). (AC1-AC3)
6. **Docs**: a short README section "Regenerating the launcher icon" (Node prerequisite, dev-only; the two commands; "never edit the mipmaps by hand"). The CHANGELOG entry happens at integration, per delivery conventions.

Suggested commits: `chore(BOOKSHELF-0008): add launcher icon generation script`, `feat(BOOKSHELF-0008): add adaptive launcher icon`, `docs(BOOKSHELF-0008): document launcher icon regeneration`.

## 11. ADR

**No ADR needed.** This is not a framework or structural change: Node is a dev-only asset tool, outside the Gradle build and outside the APK. It integrates no external API or service and changes no database. It is a routine asset addition that follows the standard Android adaptive icon pattern.

## 12. Test strategy per AC

| AC | Level | How |
|---|---|---|
| AC1: adaptive icon with separate layers, plus legacy and round fallbacks | Script check + build + optional instrumented | `check` (files and dimensions present); `assembleDebug` + `lint` clean; `LauncherIconTest` asserts `AdaptiveIconDrawable` |
| AC2: visually matches `icon.svg` | Script check + manual | Drift check; the code guarantees verbatim markup; reviewer and QA compare the `out/` side-by-side and the on-device icon (gradients on all three spines, gold diamonds, shelf gradient, soft shadow under the books and shelf) |
| AC3: no clipping, background fills the mask in circle, squircle and rounded square | Script check + manual | Safe-zone pixel assertion (max radius < 33dp); background-opacity assertion; mask preview sheet; on-device check where the shape setting exists |

## 13. Security notes

- Supply chain: `@resvg/resvg-js` is dev-only and pulls a platform-specific native binary. Pin the exact version, commit the lockfile, use `npm ci`, and keep `node_modules` out of git. It never reaches the APK.
- No PII, secrets, network access or new permissions. The script only reads `resources/icon.svg` and writes under `app/src/main/res/` and `tools/launcher-icon/out/`.

## 14. Risks for QA to probe

| Risk | What to check |
|---|---|
| Shadow tail clipped by the circle mask | Zoom into the round icon at xxxhdpi: the bottom shelf shadow fades out before the mask edge |
| Shelf shadow has a hard bottom edge | This is in the **original**: the `shelfShadow` filter region ends at 140% of the shelf height, so resvg clips it the same way browsers do. Expected, not a regression. Compare with the `out/` render of `icon.svg` |
| Dark green book loses contrast at small sizes | Check mdpi 48px legacy and the on-device icon at the smallest launcher grid |
| Launcher auto-themed icons (Android 13+ themed icons, OEM auto-theming) | No monochrome layer exists, so the full-color icon shows. Any OEM auto-generated tint is accepted per the ticket's out-of-scope note, not a defect |
| Stale assets after someone edits `icon.svg` | `npm run check` fails on drift; the rect assertion fails if the background element changes shape |
| `mipmap-anydpi` vs `-v26` | Confirm lint shows no `ObsoleteSdkInt` and the icon resolves on API 29 (minimum) and on the latest API image |

## 15. Open decisions

None blocking. One option the user may want to confirm later (cheap to reverse, see section 3): flat `paper` background, which is the ticket's literal scope, vs. the original's subtle full-bleed gradient.
