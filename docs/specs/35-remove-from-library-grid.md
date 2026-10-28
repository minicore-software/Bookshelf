# Delete a book from the Library grid — "⋮" and popover visuals

Status: Draft   Owner: UX   Date: 2026-10-28

## Context

Problem: the only way to delete a book today is Detail → Edit → the pencil → "Remove book" (#34). #35 adds two faster entry points directly on the Library grid card: long-press, and a tap on an overflow "⋮". Both must open the exact same confirm dialog as Edit (#34's `RemoveBookDialog`), with no divergent copy, warning or cascade (AC3).

Goal of this spec: give visual form to the one thing the architect's plan (`docs/plans/35-remove-from-library-grid.md`, decision L9) left open — the "⋮" affordance's placement/size/colour and the popover's styling — on a ~84–97dp-wide grid card, over arbitrary cover art (preset colours and user images, including near-white and near-black images), without colliding with the existing status dot or the FAB.

Users: the single local app user, on their own phone. Success: AC1–AC4 in the ticket.

This spec binds to the architect's plan sections 3 (L1–L9) and 4.1 (seams). It does not reopen the flow, state ownership, gesture wiring or a11y seams already decided there — those are binding regardless of this spec. It resolves exactly: L3's "placement and visuals", L9 in full, and the copy check.

No primary research is warranted at this risk level — this is a small, well-precedented affordance (Android's own "uninstall" badge pattern, cited in the AC) added to an existing card, not a novel task. Findings below are sourced from the existing `LibraryGridCard`/`LibraryStatusDot` implementation, `docs/specs/Bookshelf-Visual-Spec.md` §3.1/§3.2/§7.3, `docs/ux/decisions.md` (2026-10-27 terracotta-on-paper AA failure), and WCAG 2.2 (§2.5.8 target size, §1.4.11 non-text contrast).

## Assumptions and open questions

| # | Item | Type | Owner | Status |
|---|---|---|---|---|
| 1 | "⋮" sits at the cover's **top-start** corner (mirroring the status dot's top-end), as a dark translucent disc with a light glyph — not a light disc. | Decision (this spec, §"Screens/components" below) | UX | Resolved — proceeding |
| 2 | The popover anchors to the card's `Box` bounds (the architect's own structural default in plan 4.1), not to the long-press touch point. | Decision (this spec resolves plan L9's open point) | UX | Resolved — proceeding |
| 3 | The destructive menu item's text/icon colour uses the `overdue`/`error` token, not literal `terracotta`, reusing the exact fix already logged in `docs/ux/decisions.md` (2026-10-27) rather than reintroducing the same AA failure in a new place. | Decision, direct application of existing precedent | UX | Resolved — proceeding; logged below |
| 4 | Copy: "Remove book" (architect's proposal) is confirmed — it is the **same string already shown on Edit's button** (`R.string.edit_book_remove`), so the menu item can reuse it verbatim with no new string needed beyond what the plan's §4.1 table lists (`library_card_remove`, "Remove book"). | Assumption, verified against `strings.xml` | UX | Resolved — proceeding |
| 5 | The menu button's 48dp touch target is allowed to extend slightly past the cover's top/start edge into the card's own padding gutter (`gridCellPadding`), which is dead space today (no sibling consumes it). | Flag for FE | FE | Open — non-blocking, see §"Screens/components" |

## User flow

This spec does not change the flow in the architect's plan (§2, §4.1–4.3). For reference, the two new paths converge into the one existing surface:

```mermaid
flowchart TD
    A[Library grid card] -->|Long-press| B[Card actions popover opens]
    A -->|Tap the card| Z[Book Detail]
    A -->|Tap "More options for &lt;title&gt;"| B
    B -->|Tap Remove book| C[RemoveBookDialog: same copy/warning/cascade as Edit]
    B -->|Outside tap / back| D[Popover closes, nothing happens]
    C -->|Cancel| D
    C -->|Confirm| E[Book removed: card leaves the grid, counts/rows update]
```

Failure paths (owned by the architect's plan, unchanged by this spec): a remove that throws shows a Snackbar (`RemoveFailed`); `NotFound` is a silent no-op; both leave the popover already closed.

## Screens / components

### 1. "⋮" — the card's overflow affordance

**Purpose and when it appears:** a permanently visible, non-gesture entry point to the card actions popover, on every grid card, for TalkBack/switch-access users who cannot long-press (AC2, AC4; plan L3).

**Placement:** the cover's **top-start** corner — the mirror image of `LibraryStatusDot`, which already owns top-end. This keeps the two corner affordances visually paired (a common "status badge / overflow badge" pairing), guarantees zero collision with the status dot at any screen width, and keeps it away from the FAB (bottom-end of the screen) and the title/caption text below the cover.

- Visual disc: **28dp** diameter, `CircleShape`, offset `top = 6dp, start = 6dp` from the cover's top-start corner — the same 6dp/6dp inset `LibraryStatusDot` already uses on the opposite corner, so the two badges read as a matched pair.
- Touch target: **48dp**, using the same "outer `Box` with `defaultMinSize(48.dp, 48.dp)`, inner visual element centered and smaller" pattern already established for `LibraryFilterChip`'s 48dp target around its ~31dp visual pill — do not reach for `Modifier.minimumInteractiveComponentSize()` (same reason documented on the chip: it would inflate the *visual* disc to 48dp too). The outer touch `Box` is itself aligned `TopStart` inside the card's cover `Box`, so it is centered on the same point as the visual disc; it is allowed to extend a few dp past the cover's top/start edges into `gridCellPadding`'s gap, which has no competing touch target (open question 5 — flag to FE, non-blocking).
- Fill: `ink` at **55% opacity** — reuse the exact opacity value `BookCover`'s `dimmed` lent-overlay already uses (`.alpha(0.55f)`), applied here as a background scrim instead, not a new number. This is deliberately a **dark** scrim with a **light** glyph, not the light "paper-ring" treatment `LibraryStatusDot` uses — a light disc on a near-white preset/cover would fail the 3:1 non-text contrast minimum (WCAG 1.4.11) at the exact corner most likely to sit over light cover art; a dark scrim plus a light glyph is the standard "badge over photo" pattern (it is also, concretely, what the AC's own reference point — Android's per-app uninstall "✕" badge — looks like: a dark disc, light glyph, over unpredictable artwork) and holds a floor of 3:1+ against both very light and very dark covers because the glyph's contrast is anchored to the disc, not to the cover.
- Add a **1dp ring at `paper` 30% opacity** around the disc's edge (same idea as `LibraryStatusDot`'s ring, inverted) so the disc's own boundary stays legible on a cover that happens to match ink's value closely (a near-black cover). This is the one deviation from "just reuse 0.55 opacity" worth calling out: without the ring, a near-black cover plus a 55%-opacity ink scrim can themselves sit close in luminance. The ring is decorative-contrast-only, not a semantics change.
- Glyph: hand-drawn three-dot "kebab" (⋮), `Canvas`-drawn exactly like `PlusGlyph`/the search glyph (plan L8 — no new icon dependency), colour `paper` (full opacity, drawn over the scrim), three filled circles ~3dp diameter stacked vertically with 3dp gaps, sized to roughly 12dp tall inside the 28dp disc (matching `PlusGlyph`'s own ~0.42 glyph-to-container ratio: `22dp`/`52dp` FAB ≈ 0.42, `12dp`/`28dp` ≈ 0.43). `contentDescription = null` on the glyph — it is decorative; the accessible label lives on the touch target (plan 4.1: `"More options for <title>"`).
- This button is a **child node**, not merged into the card's `semantics(mergeDescendants = true)` block — already decided by the architect (plan L3); this spec adds no new semantics requirement beyond what 4.1 already specifies.

**States:**
- Default: as above, at rest.
- Pressed: default M3 ripple on the touch `Box`, clipped to the 48dp circle (not the 28dp visual disc, so the ripple doesn't look clipped to a smaller circle than the touch target — use `Modifier.indication(interactionSource, ripple(bounded = true, radius = 24.dp))` or the default `Box` ripple sized to the outer `Box`).
- Disabled: none — this control has no disabled state; it is always available while the card itself is visible (there is no "removal in flight for this card" visual, consistent with plan L7's no-Snackbar-on-success rule; a concurrent remove on another card does not change this card's "⋮").
- Overflow/long content: N/A — fixed-size icon-only control, no text to overflow.
- The control itself never changes appearance when the popover it owns is open — the popover's own presence is the only "active" signal (no need for a pressed/active colour on the disc).

### 2. Card actions popover (`DropdownMenu`)

**Purpose and when it appears:** opened by either long-press on the card or a tap on "⋮" (architect's plan L1/L4 — one composable, one state, two triggers). Contains exactly one item.

**Styling**, mapped from Visual Spec §7.3 ("Popover") and §3.1/§3.2, which already lists "popover" against the same radius/shadow tokens used elsewhere in the app (these are already treated as dp 1:1 by existing code, e.g. #31's sheet corners):

- Shape: `RoundedCornerShape(14.dp)` (`--radius` token, §3.1 — "Cards, panels, stat tiles, popover").
- Container colour: `MaterialTheme.colorScheme.background` (`paper`, `#F3EEE3`) — matches `--bg`, which §7.3 and §1.1 both specify for "popovers".
- Elevation: `shadowElevation = 8.dp`, `tonalElevation = 0.dp` on the `DropdownMenu`'s `Surface` — approximates `--shadow-3` ("Sheet, dialogs, popover, toast"; §3.2), the heaviest of the three shadow tokens, not `--shadow-1`.
- Padding: 6dp around the menu content (§7.3's "padding 6px").
- Width: **intrinsic** (`wrapContentWidth`), not a fixed 260dp — §7.3's 260dp applies to the profile popover, which has an avatar/name header; this menu has a single short text+icon item and should size to its content, with a sensible minimum (`defaultMinSize(minWidth = 180.dp)`) so the touch target for the one item still reads comfortably.
- Anchoring: the `DropdownMenu`'s default M3 behaviour — anchored to the enclosing `Box` (the whole card, per plan 4.1), not to the touch/long-press point (open question 2 above). Rationale: a long-press can land anywhere on the cover, so anchoring to the touch point would make the popover jump to a different position on every long-press, which reads as inconsistent across the two triggers even though the underlying action is identical (and the AC's spirit — "no divergent behaviour" — reasonably extends from logic to the visible result). Anchoring to the card gives one predictable popover position regardless of trigger. No custom `offset` is needed: M3's default placement (directly below the anchor, flipping above when it would overflow the bottom of the screen) already satisfies K9 (bottom-row cards near the FAB) without extra specification.
- Dismiss: outside tap or back closes it with no side effect (already specified by the architect, plan 4.1's `onDismissRequest`).

**Menu item ("Remove book"):**

- Text: `stringResource(R.string.library_card_remove)` = "Remove book" (reuses `edit_book_remove`'s exact wording — confirmed, no divergent copy; open question 4).
- Leading icon: hand-drawn trash glyph (`Canvas`, plan L8), **18dp** icon slot (§7.3: "18px icon slot"), simple outline glyph — a rectangle body + a short horizontal line for the lid, round caps, 1.5dp stroke, consistent with the app's other hand-drawn glyphs' line weight.
- **Colour — the one deliberate deviation from a literal reading of §7.3:** §7.3's own precedent for a destructive item ("Sign out") specifies "terracotta text and icon". **Do not use literal terracotta for this item's text/icon.** `docs/ux/decisions.md` (2026-10-27) already measured terracotta-on-paper at ~3.0–3.39:1, failing WCAG AA 4.5:1 for normal-size text, and fixed the same problem elsewhere by substituting the `overdue`/`error` token for small text while reserving terracotta for *filled* surfaces. This menu item is terracotta text on a paper background — exactly the failing combination — so it must use `MaterialTheme.colorScheme.error` (the `overdue` token, `#9B2C2C`, already verified AA-passing on paper by `LibraryContrastTest`) for both the label and the trash glyph, matching `RemoveBookDialog`'s own use of `colorScheme.error` for its warning text and confirm button. This keeps every red/destructive text token in the Library feature on the same AA-safe colour, rather than introducing a second, failing one.
- Row sizing: standard M3 `DropdownMenuItem` padding is acceptable (§7.3's "9px 12px" padding is already close to M3's default `DropdownMenuItem` content padding); no custom override needed beyond colour and the leading icon.
- Press/ripple: default M3 ripple is sufficient; no custom "terracotta-at-12%" hover state is needed on a touch-first surface with no hover concept — ripple already gives the needed feedback, and tinting it with `error` at low alpha is an optional, non-blocking polish FE may add if trivial.
- Tapping it closes the menu and calls `onRemoveBookRequest(book.id)` (already specified, plan 4.1) — this spec adds no new state.

**States:** default (above); there is no loading/empty/error state for the menu itself — a remove-in-flight or a failure both surface *after* the menu has already closed (plan L6/L7), so the menu composable itself is always either closed or showing exactly this one item.

## Responsive behavior

- At 320dp width (~84dp card content width), the 28dp visual disc plus 48dp touch target fit inside the cover's top-start corner without touching the card's title or caption below, and without the touch target reaching as far as the status dot on the opposite corner (13dp dot + 6dp inset on one side, 48dp target centered 6dp from the other side, on an ~84dp-wide cover — comfortable clearance; re-verify in the pixel/layout test the plan already calls for in §5 "Layout stability").
- At 2x font scale: the "⋮" is icon-only and fixed-size, so font scale does not affect it. The popover's single text item wraps/grows with font scale exactly as any other `DropdownMenuItem` text does; no fixed height is imposed on the menu that could clip it.
- No separate tablet/landscape behavior is specified here — out of scope for this ticket (3-column grid is unchanged).

## Accessibility

- "⋮" is its own focus stop (already decided, plan L3/AC4), role Button, `contentDescription = "More options for <title>"`, 48dp touch target (meets WCAG 2.5.8 without relying on the AA "24dp + spacing" exception).
- The trash glyph and kebab glyph are both decorative (`contentDescription = null`); nothing is announced twice.
- Colour is never the only signal distinguishing this item from a non-destructive one: the label text itself says "Remove book", so a colour-blind user loses no information from the `error`-token colour choice above — colour is reinforcement, not the only channel (WCAG 1.4.1, same rule already cited by this repo's own overdue/weight convention in `Type.kt`).
- Contrast: the "⋮" disc-to-cover boundary targets WCAG 1.4.11 (3:1 non-text) via the dark-scrim-plus-ring design above; the kebab/trash glyphs target 3:1 minimum against their own disc/menu background respectively (both comfortably exceeded: paper-on-ink-55%, and error-token-on-paper already measured AA-passing by `LibraryContrastTest`). No further manual contrast check is needed beyond what a FE/QA visual pass against a white and a black test cover should confirm per plan K8.
- Reduced motion: the popover's open/close animation is M3's default `DropdownMenu` transition, which already respects system "reduce motion" (no custom animation introduced here).
- Focus management: opening the popover should move accessibility focus to its one item (M3 `DropdownMenu` default behaviour); dismissing returns focus to whichever trigger opened it ("⋮" or the card) — default Compose/M3 behaviour, no custom handling specified.

## Acceptance criteria (UX)

- Given a Library grid card with any cover (preset or image, including a near-white or near-black image), when the card renders, then the "⋮" badge is visible with at least 3:1 contrast against the cover at its corner.
- Given the "⋮" and the status dot both drawn on the same card, when the card renders at 320dp width, then the two badges never visually overlap.
- Given the card actions popover is open (via either trigger), when it renders, then it shows exactly one item, "Remove book", in the `error`/`overdue` token colour (never literal `terracotta` text), with a 14dp-radius, `paper`-background, shadow-3-equivalent surface.
- Given a user taps "Remove book" in the popover, when the `RemoveBookDialog` opens, then its title, body, open-loan warning (if any) and button copy are pixel-for-pixel identical to the dialog Edit opens (already enforced structurally by the architect's single-call-site decision; this spec adds no divergent copy anywhere in the new surfaces).
- Given the popover is anchored near the bottom of the visible grid, when it opens, then it flips above the card rather than rendering off-screen (M3 default; no custom offset required to satisfy this).

## Technical considerations for SA/FE

- No new dependency: both glyphs (kebab, trash) are hand-drawn `Canvas` composables, per the existing precedent (`PlusGlyph`, the search glyph) and the architect's own decision L8.
- No new design tokens needed in `colors.xml` — this spec reuses `ink`, `paper`, and the existing `error`/`overdue` colour role; it does not introduce a `scrim` or `danger` colour resource.
- The 48dp touch target bleeding slightly past the cover's top/start edge into `gridCellPadding`'s gap (open question 5) should be checked against the plan's own "Layout stability" fitness function (§5): confirm it does not register a hit on an adjacent row's or column's card when both are laid out, and does not shift any measured bounds the existing `GridCellPaddingTest`/`LibraryGridPixelTest` assert on.
- The ripple radius note (28dp visual, but ripple ideally bounded to the full 48dp touch circle) is a minor visual judgment call for FE — either radius is acceptable; a ripple bounded to 48dp more clearly communicates the true touch target, but is not blocking.

## Out of scope

- The "Currently reading" and "Lent out" row cards get no delete entry point (already out of scope per the architect's plan).
- Any multi-select, undo, or success Snackbar — unchanged from #34/#35's plan (no Snackbar on success, per plan L7).
- Tablet/landscape-specific layout.
- A TalkBack custom action directly on the card node (architect's plan, §10, "considered and not added").
