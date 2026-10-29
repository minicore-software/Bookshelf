# Spec: App crashes on Add Book — OutlinedTextField label lerp mixes Sp and Em units

Ticket: [#44](https://github.com/minicore-software/Bookshelf/issues/44)

## Problem

`FATAL EXCEPTION: main` — `java.lang.IllegalArgumentException: Cannot perform operation
for Sp and Em`, thrown from `androidx.compose.ui.unit.TextUnitKt.lerp`, inside
Material3's `OutlinedTextField` label-animation code path
(`TextFieldImplKt$CommonDecorationBox`). First reachable by tapping the Library "+" FAB
(Add Book's title field, `BookForm.kt:68`), but not specific to that screen — any
labelled `OutlinedTextField` built on `BookshelfTypography` hits it as soon as its label
animates between the unfocused ("large") and focused ("small") states.

## Root cause

`app/src/main/java/com/minicore/bookshelf/core/designsystem/Type.kt`:

- `bodyLarge = default.bodyLarge.copy(fontFamily = Inter)` — `letterSpacing` is left at
  Material3's own default, which is a `Sp` value.
- `bodySmall = default.bodySmall.copy(..., letterSpacing = 0.02.em, ...)` — explicitly
  overridden to an `Em` value.

`OutlinedTextField`'s decoration box computes its floating label's style by
`lerp(typography.bodyLarge, typography.bodySmall, animationProgress)` as focus changes.
`TextUnit.lerp` requires both operands to share the same unit type and throws
immediately otherwise (`TextUnit.kt:368`, `checkArithmetic`, `InlineClassHelper.kt:30`).
This is a hard crash of the whole process, not a caught/recoverable exception — it is
not sandboxed per-composable.

Crash log: `docs/logs/Pixel-5-Android-17_2026-10-29_093514.logcat` (search `FATAL
EXCEPTION`).

## Fix

In `Type.kt`, change `bodySmall.letterSpacing` from `0.02.em` to the equivalent `Sp`
value at its own `fontSize` (`12.5.sp`): `0.02em × 12.5sp = 0.25.sp`. This preserves the
intended visual letter-spacing (proportional spacing becomes an equivalent fixed value
at this one fixed font size, which is what the style is always rendered at — `bodySmall`
has no other `fontSize` override elsewhere) while making the unit match `bodyLarge`, so
`lerp` no longer throws.

Add a short comment at the fix site explaining *why* the unit matters here, since the
pitfall is easy to reintroduce when either `bodyLarge` or `bodySmall` is next edited
without this spec in view.

## Also flagged, not required for this ticket

`titleLarge.letterSpacing` (`-0.01.em`) and `titleMedium.letterSpacing` (unmodified,
`Sp`) have the identical mismatch. Nothing currently `lerp`s between those two styles,
so it has not crashed yet — but it is the same trap, waiting for the next feature that
animates a title (e.g. a collapsing app bar). Out of scope for this ticket's fix, but
the regression test below should be written broadly enough to catch this one too, not
just the specific `bodyLarge`/`bodySmall` pair that crashed.

## Test requirement (AC3)

A single happy-path UI smoke test ("open Add Book, don't crash") would be too narrow —
it proves this one path but not the underlying invariant, and a future edit could
reintroduce a same-unit violation on a *different* style pair without the test noticing.

Write a **fitness-function-style unit test** (plain JVM, no Compose test rule needed)
that asserts, across every pair of `BookshelfTypography` slots that Material3 or this
app's own code is known to interpolate (`bodyLarge`↔`bodySmall` for
`OutlinedTextField`/`TextField` labels at minimum; `titleLarge`↔`titleMedium` if cheap
to add given the flagged risk above), that `fontSize.type` and `letterSpacing.type`
match between each pair. `TextUnit.type` is public API (`TextUnitType.Sp` /
`TextUnitType.Em` / `TextUnitType.Unspecified`) so this needs no reflection.

Also keep a targeted Compose test (or extend an existing `BookForm`/`AddBookRoute`
instrumented test) that actually focuses and unfocuses the title field, so a real
`lerp` call fires end-to-end — the unit-level fitness function proves the invariant
broadly, this proves the specific user-visible path from the ticket doesn't crash.

## Acceptance criteria (from the ticket)

- Tapping the Library "+" FAB opens Add Book without crashing, on both an empty and a
  populated library.
- Focusing/unfocusing any `OutlinedTextField` built on `BookshelfTypography` never
  throws.
- A regression test exists that would have caught this — not just a single happy-path
  smoke test.
- No visual regression to the intended letter-spacing (equivalent spacing preserved, in
  a compatible unit).
