# Bookshelf — Visual Design Specification

The visual reference for building Bookshelf. All values come from the prototype (`styles.css`, `auth-styles.css`). When this doc and the prototype disagree, the prototype wins.

**Mood:** cozy, warm, countryside. Think English or Irish cottages in spring. Soft surfaces, warm shadows, slab-serif headings, no hard borders.

---

## 1. Color

### 1.1 Tokens

| Token | Hex | Name | Use |
|---|---|---|---|
| `--bg` | `#F5F1E8` | Warm Cream | App background, sheets, dialogs, popovers |
| `--surface` | `#E8E1D4` | Soft Linen | Cards, inputs, panels, segmented tracks |
| `--surface-2` | `#EFE9DC` | Light Linen | Focused search field |
| `--hover` | `#D6E2D3` | Light Moss | Card / menu / secondary-button hover |
| `--sage` | `#7A9E7E` | Sage Green | Primary buttons, focus rings, "Available" |
| `--forest` | `#4F6F52` | Forest Green | Headings, primary hover, active segment text |
| `--wood` | `#A67C52` | Warm Wood | "On loan" status, Lend-out button, Reader role |
| `--terracotta` | `#C97C5D` | Terracotta | Stars, overdue, danger, errors |
| `--yellow` | `#E6D8A3` | Faded Yellow | Loan tag, loan callout |
| `--ink` | `#3A3A3A` | Soft Charcoal | Primary text, toast background |
| `--ink-muted` | `#6B5E53` | Muted Brown-Grey | Secondary text, labels, metadata |
| `--ink-faint` | `#9C8F82` | Faint Brown | Placeholders, separators dots, empty values |

**Button text on sage/wood/terracotta fills:** `#FAF7EE` (off-white).
**Loan callout gradient end:** `#EFE3B6`.

### 1.2 Alpha colors

| Value | Use |
|---|---|
| `rgba(107,94,83,.22)` | Top-bar dashed bottom border |
| `rgba(107,94,83,.18)` | Sheet/section dashed separators, popover separator |
| `rgba(107,94,83,.14)` | Table rows, key/value rows |
| `rgba(107,94,83,.25)` | Empty-state dashed border, scrollbar thumb |
| `rgba(122,158,126,.25)` | Search focus ring (3px) |
| `rgba(122,158,126,.20)` | Input focus ring (3px) |
| `rgba(122,158,126,.18)` | Success flash background |
| `rgba(201,124,93,.25)` | Empty (dim) star |
| `rgba(201,124,93,.12)` | Danger hover, error box background |
| `rgba(166,124,82,.35)` | Borrowed card dashed border |
| `rgba(166,124,82,.06)` | Table header tint |
| `rgba(79,111,82,.15)` | Admin role chip background |
| `rgba(166,124,82,.15)` | Reader role chip background |
| `rgba(58,46,32,.35)` | Modal scrim (plus `backdrop-filter: blur(2px)`) |

### 1.3 Semantic mapping

| Meaning | Color |
|---|---|
| Available / on the shelf | `--sage` |
| Borrowed / on loan | `--wood` |
| Overdue | `--terracotta` (text weight 600) |
| Rating stars (filled) | `--terracotta` |
| Danger / destructive | `--terracotta` |
| Success message | `--forest` on sage 18% |

### 1.4 Cover color presets

The user picks one when no cover image is uploaded. `fg` is the color of the title/author text on the cover.

| # | Background | Foreground |
|---|---|---|
| 1 | `#4F6F52` | `#E6D8A3` |
| 2 | `#7A9E7E` | `#F5F1E8` |
| 3 | `#A67C52` | `#F5F1E8` |
| 4 | `#C97C5D` | `#F5F1E8` |
| 5 | `#6B5E53` | `#E8E1D4` |
| 6 | `#E6D8A3` | `#4F6F52` |

The cover fill is `linear-gradient(155deg, bg 0%, bg darkened 12% 100%)`, where darkening subtracts 12% of 255 from each RGB channel.

---

## 2. Typography

### 2.1 Families

| Token | Stack | Role |
|---|---|---|
| `--serif` | `"Roboto Slab", Georgia, serif` | Headings, titles, numbers, names, quotes |
| `--sans` | `"Inter", system-ui, -apple-system, "Segoe UI", sans-serif` | Body, UI, labels |

Load: Google Fonts, weights 400 / 500 / 600 / 700 for both.

```html
<link href="https://fonts.googleapis.com/css2?family=Roboto+Slab:wght@400;500;600;700&family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
```

Body defaults: Inter 15px, line-height 1.5, color `--ink`, `-webkit-font-smoothing: antialiased`.

### 2.2 Type scale

| Element | Family | Size | Weight | Tracking | Color | Notes |
|---|---|---|---|---|---|---|
| App title (top bar) | Slab | 28px (22 mobile) | 700 | -.01em | forest | |
| App subtitle | Inter | 12.5px | 400 | .02em | ink-muted | |
| Auth heading | Slab | 26px | 600 | -.01em | forest | |
| Auth brand name | Slab | 22px | 700 | -.005em | forest | |
| Sheet title | Slab | 22px | 600 | -.005em | forest | |
| Empty-state heading | Slab | 22px | 600 | — | forest | |
| Expanded book title | Slab | 24px | 600 | -.01em | ink | line-height 1.2 |
| Dialog heading | Slab | 19–20px | 600 | — | forest | |
| Shelf section title | Slab | 17px | 600 | .005em | forest | |
| Page card heading | Slab | 17px | 600 | — | forest | |
| Card title | Slab | 16px | 600 | -.005em | ink | line-height 1.25, max 2 lines |
| Loan callout name | Slab | 16px | 600 | — | ink | |
| Stat value | Slab | 30px (24 mobile) | 600 | -.01em | forest | |
| Table user name | Slab | 14.5px | 600 | — | ink | |
| Form section head | Slab | 13px | 600 | .14em, UPPERCASE | forest | |
| Body / inputs | Inter | 14–14.5px | 400 | — | ink | |
| Buttons | Inter | 14px | 500 | .005em | — | |
| Segmented buttons | Inter | 13px (12 ≤540px) | 500 | — | ink-muted | |
| Card metadata | Inter | 12.5px | 400 | — | ink-muted | `Author · Year` |
| Field label | Inter | 12px | 500 | .10em, UPPERCASE | ink-muted | |
| Stat label | Inter | 12px | 500 | .12em, UPPERCASE | ink-muted | |
| Detail / memory label | Inter | 11px | 500 | .12em, UPPERCASE | ink-muted | |
| Status pill | Inter | 11.5px | 500 | .04em, UPPERCASE | ink-muted | |
| Role chip | Inter | 10.5px | 600 | .10em, UPPERCASE | per role | |
| Cover title | Slab | 12.5px (17 expanded) | 600 | .005em | cover fg | line-height 1.15, `text-wrap: balance` |
| Cover author | Inter | 9.5px (10.5 expanded) | 500 | .04em, UPPERCASE | cover fg, 85% opacity | |
| Recovery code input | Slab | 22px | — | .8em | ink | centered |

### 2.3 Italic slab rule

Quoted text the user wrote, like a comment or a memory, is set in **Roboto Slab italic 400**. It's meant to look like a note in the margin. Use it for:
- Card comment (13px, ink-muted, max 2 lines). Note: on the card itself the comment inherits Inter italic. Only the expanded view and memory cells use slab italic.
- Expanded comment block (14px, ink)
- Memory cells (13.5px, ink)

An empty memory value shows `— not yet written —` in Inter 13px, ink-faint, non-italic.

---

## 3. Shape, depth, motion

### 3.1 Radii

| Token | Value | Use |
|---|---|---|
| `--radius-sm` | 8px | Menu items, icon buttons |
| — | 10px | Inputs, brand mark, info/error boxes |
| — | 12px | Memory cells |
| `--radius` | 14px | Cards, panels, stat tiles, popover |
| `--radius-lg` | 22px | Sheets (mobile top corners), dialogs, auth card, empty state |
| — | 999px | Buttons, search, segmented, chips, pills, avatars |
| — | 6px | Book cover |
| — | 4px | Loan tag, color swatches |

### 3.2 Shadows (warm brown base `rgb(58,46,32)`)

| Token | Value | Use |
|---|---|---|
| `--shadow-1` | `0 1px 2px rgba(58,46,32,.04), 0 2px 8px rgba(58,46,32,.04)` | Cards at rest, primary button, active segment |
| `--shadow-2` | `0 2px 6px rgba(58,46,32,.06), 0 12px 28px rgba(58,46,32,.08)` | Card hover |
| `--shadow-3` | `0 8px 24px rgba(58,46,32,.10), 0 28px 64px rgba(58,46,32,.14)` | Sheet, dialogs, popover, toast, auth card |

### 3.3 Borders

Don't use solid borders to separate things. Use spacing first. Where a line is needed, make it **1px dashed** with one of the alpha browns from §1.2. The only solid border is the sheet's left edge: `1px solid rgba(107,94,83,.12)`.

### 3.4 Background grain

```css
body::before {
  content: ""; position: fixed; inset: 0; pointer-events: none; z-index: 0;
  background-image:
    radial-gradient(circle at 20% 30%, rgba(166,124,82,.035), transparent 40%),
    radial-gradient(circle at 80% 70%, rgba(122,158,126,.04), transparent 45%);
}
```
App content sits at `z-index: 1` above it.

### 3.5 Motion

| Interaction | Spec |
|---|---|
| Hover color/shadow | 150ms ease |
| Card hover | 180ms: background → `--hover`, shadow-1 → shadow-2, `translateY(-2px)` |
| Button press | `translateY(1px)`, 80ms |
| Sheet open | `transform .28s cubic-bezier(.2,.8,.2,1)` (slides from right on desktop, from bottom on mobile) |
| Scrim | opacity 200ms |
| Dialog | opacity + `translate(-50%,-48%) → (-50%,-50%)`, 200ms |
| Toast | opacity + 20px rise, 250ms; auto-dismiss 2.2s |
| Star input hover | `scale(1.1)`, 120ms |

### 3.6 Focus

`:focus-visible { outline: 2px solid var(--sage); outline-offset: 2px; border-radius: 6px; }`
Inputs instead get a sage border plus a 3px sage ring at 20% opacity, and their background changes to `--bg`.

---

## 4. Layout

| Item | Value |
|---|---|
| App max width | 1320px, centered |
| App padding | 28px 28px 80px (mobile: 16px 14px 80px) |
| Profile page content max | 1100px |
| Top bar | flex, space-between, align end, gap 24px, wraps; 20px bottom padding + dashed border; 28px margin below |
| Stats strip | 4-column grid, gap 14px, 28px below (mobile: 2 cols, gap 10px) |
| Filter/sort row | filter left, sort pushed right (`margin-left:auto`), 22px below |
| Book grid | `repeat(auto-fill, minmax(260px, 1fr))`, gap 16px (mobile: 1 col, gap 12px) |
| Between shelves | 32px |
| Shelf header → grid | 12px |
| Sheet width | `min(560px, 100%)`, full height |
| Sheet padding | 22px 28px (mobile 18px sides) |

### Breakpoints

| Width | Changes |
|---|---|
| ≤ 540px | Segmented controls go to 12px text and 6×10px padding |
| ≤ 720px | Compact padding; 2-col stats; 1-col book grid; 76×108 cover; sheet becomes a bottom sheet (`max-height: 92vh`, 22px top radius); form `row-2` stacks; profile grid stacks |
| ≤ 820px | Admin table hides the "Member since" and "Last sign-in" columns |
| ≥ 900px | Memory grid in the expanded view becomes 3 columns |

---

## 5. Components

### 5.1 Brand mark
44×44px (38 on mobile, 40 on auth), radius 10px, `linear-gradient(160deg, forest, sage)`, a cream "B" in Slab 700 22px, shadow-1. Next to it: the app title and subtitle, gap 14px.

### 5.2 Buttons
All buttons: pill shape (999px), padding 9px 16px, Inter 14/500, inline-flex, 8px icon gap, nowrap.

| Variant | Rest | Hover |
|---|---|---|
| Primary | sage bg, `#FAF7EE` text, shadow-1 | forest bg |
| Secondary | surface bg, ink text | hover (moss) bg |
| Ghost | transparent, ink-muted | surface bg, ink |
| Danger | transparent, terracotta | terracotta 12% bg |
| Danger (confirm) | terracotta bg, `#FAF7EE` | — |
| Lend out | wood bg, `#FAF7EE` | — |
| Full-width (auth) | width 100%, centered, padding 11px 16px | — |

**Link button:** forest 13px/500 text, underlined (forest at 35%, 3px offset). Hover turns it terracotta. A danger variant is terracotta.

### 5.3 Search field
Pill, surface bg, padding 9px 14px, 16px magnifier icon in ink-muted, 14px input, ink-faint placeholder. Width flexes from 220 to 320px (full width on mobile). On focus: surface-2 bg plus a 3px sage ring at 25%.

### 5.4 Segmented control
Surface pill track with 3px padding and 2px gap. Buttons are pills, 7px 13px padding, 13px/500 ink-muted text. **Active** button: bg `--bg`, forest text, shadow-1.
- Filter: `All · Available · On loan`
- Sort: `Recent · Title · Author · ★` (no label)
- Admin sort: `Name · Email · Role · Recent`
- Role picker in the user dialog: `Reader · Admin`

### 5.5 Stat tile
Surface bg, 14px radius, padding 16px 18px, overflow hidden. Uppercase label, then a Slab 30px value. Decorative circle: 60px, `top/right: -10px`, opacity 15%, in sage / wood / terracotta / yellow (overdue turns terracotta when > 0). The rating value is followed by a terracotta ★ at 16px.

### 5.6 Shelf header
Slab 17/600 forest title, then a count chip (Inter 12/500 ink-muted, surface bg, pill, padding 2px 8px).
Sections: **On the shelf**, **Out on loan**. They only show when the filter is "All" and the search is empty. Otherwise the grid is flat with no headers.

### 5.7 Book card

```
┌──────────────────────────────────────────┐
│ ┌──────┐  Title (Slab 16/600, 2 lines)    │
│ │cover │  Author · Year (12.5 muted)      │
│ │88×124│  ★★★★☆ (13px terracotta)          │
│ │      │  "Comment…" (13 italic, 2 lines)│
│ └──────┘                                  │
└──────────────────────────────────────────┘
```
- Grid `88px 1fr`, gap 14px, padding 14px, surface bg, 14px radius, shadow-1, `align-items: start`
- No status pill and no year stamp on the card, because the shelf header already says that
- Hover: moss bg, shadow-2, 2px lift
- Fully clickable (`role=button`, Enter/Space opens it)
- Mobile: `76px 1fr` grid, 12px padding, 76×108 cover

**Rating:** inline-flex, gap 2px, `white-space: nowrap`, line-height 1, each star `flex: 0 0 auto`. Dim stars use terracotta at 25%.
> ⚠️ Don't give dim stars a generic class like `.empty`. The empty-state container uses `.empty-state`, and a clash will give every star 80px of padding and a dashed border.

### 5.8 Borrowed card (the "gap on the shelf")
The card should look like a book is missing from the shelf, with a note pinned where it was.
- Background: 45° stripes of wood at 6% and 3%, 8px each, over surface (on hover, 8% and 4% over moss)
- Border: `1px dashed rgba(166,124,82,.35)`, no shadow
- Cover: `grayscale(.55) brightness(.92)`, opacity .55
- Title, meta, rating and comment: opacity .55

**Loan tag** (pinned top-right):
- Absolute, `top/right: 12px` (8px mobile), yellow bg, ink text, 4px radius, padding 6px 10px, `rotate(2deg)`, shadow `0 2px 6px rgba(58,46,32,.12)`, max-width 60% (70% mobile)
- Lines: `ON LOAN TO` (9px, .14em, ink-muted), then the borrower (Slab 12.5/600), then the due date (10.5px ink-muted)
- Pin: an 8px cream dot centered on the left edge (`left:-4px`) with an inset 1px ring at 15% opacity
- Due text: `Overdue Nd` / `Due today` / `Due in Nd` (≤7 days) / `Due MMM D, YYYY`

### 5.9 Book cover
- Card size 88×124, expanded 140×200 (mobile 100×144), 6px radius, padding 10px 9px, flex column with space-between (title at top, author at bottom)
- Depth: `inset -3px 0 6px rgba(0,0,0,.12), inset 2px 0 4px rgba(255,255,255,.12), 0 4px 10px rgba(58,46,32,.12)`
- Spine: a 1px vertical line at `left: 6px`, `rgba(0,0,0,.15)`
- **With an image:** padding 0, `<img>` absolutely fills it with `object-fit: cover`. The image always replaces the color.

### 5.10 Slide-over sheet
- Desktop: fixed to the right, full height, `min(560px,100%)`, bg `--bg`, shadow-3, starts off-screen at `translateX(100%)`
- Mobile: bottom sheet, starts at `translateY(100%)`, 92vh max, 22px top corners
- Header: Slab 22/600 forest title and a ghost ✕ button, dashed bottom border
- Body scrolls. Footer has a dashed top border, right-aligned buttons with a 10px gap, and a far-left danger button when present
- Closes on Esc, a scrim click, or ✕
- One sheet hosts every view: **New book**, **Edit book**, **Book** (expanded), **Lend out**

### 5.11 Form fields
- Field: column, 6px gap, 16px below
- Label: 12px uppercase .10em ink-muted
- Input/textarea: surface bg, transparent 1px border, 10px radius, padding 11px 14px, 14.5px text. On focus: `--bg` bg, sage border, 3px sage ring at 20%
- Textarea: vertical resize, min-height 80px
- `row-2`: 2-column grid with 12px gap (stacks on mobile)
- Section head: Slab 13/600 uppercase .14em forest, with a dashed top rule (not on the first one). An optional hint after it uses Inter 11px ink-faint

**Star input:** 26px stars, 4px gap. Off stars are terracotta at 25%, on or hover-preview stars are terracotta, hover scales to 1.1. Clicking the current value clears the rating.

**Cover field:**
- No image: a secondary `Upload an image…` button, then `OR PICK A COLOR` (11.5px uppercase muted), then 6 swatches at 32×44px with 4px radius and 8px gap. The selected swatch gets `0 0 0 3px var(--bg), 0 0 0 5px var(--sage)`
- Image set: a surface row (padding 10px, radius 10px) with a 56×80 thumbnail, the label "Custom cover image", and **Replace** (secondary) and **Remove** (ghost) buttons, both 12px

**Book form sections:** Book → Memory (optional) → Borrowing. Borrowing only appears when **editing**, never when adding.

### 5.12 Expanded book view
- Header grid `140px 1fr`, gap 22px: cover, then the title, `Author · Year` (14px muted), 18px stars and a status pill
- **Status pill:** 7px dot plus uppercase 11.5px label. `On the shelf` (sage dot) or `On loan` (wood dot)
- **Detail block:** surface, 14px radius, padding 14px 16px. 11px uppercase label, then the value in Slab italic 14px (`.plain` switches it to Inter normal)
- **Loan callout** (when borrowed): `linear-gradient(180deg, #E6D8A3, #EFE3B6)`, 14px radius, grid `1fr auto`. Shows "ON LOAN TO", the borrower in Slab 16/600, "Lent … · Due …" (overdue in terracotta 600), and a primary **Mark returned** button
- **Available block:** "ON THE SHELF / Lend it to a friend?" with a wood **Lend out** button
- **Memory grid:** cells use surface bg, 12px radius, padding 12px 14px. Labels are `❦ WHY`, `✿ WHERE`, `✸ FEELING`. 1 column by default, 3 columns at ≥900px
- Footer: ghost **Close**, secondary **✎ Edit**

### 5.13 Confirm dialog
Centered, `min(420px, 92%)`, `--bg`, 22px radius, padding 24px 26px 20px, shadow-3, z-index 70 over its own scrim. Slab 19/600 forest title, 14px muted body, right-aligned ghost Cancel and a confirm button (terracotta fill when destructive).

### 5.14 Toast
Fixed bottom-center 28px up, ink bg, cream text, pill, padding 12px 22px, 13.5px, shadow-3, z-index 80.
Messages: "Added to your shelf", "Saved", "Removed", "Lent to {name}", "Back on the shelf".

### 5.15 Empty states (`.empty-state`)
Surface bg, 22px radius, `1px dashed rgba(107,94,83,.25)`, padding 80px 20px, centered.
Glyph in Slab 42px forest, then the heading in Slab 22/600 forest, then 14px muted copy (max 420px), then a button.
- No books: `❦` · "An empty shelf" · primary **+ Add your first book**
- No matches: `∅` · "Nothing matches" · secondary **Clear filters**

### 5.16 Icons
Inline SVG, 24-unit viewBox, `stroke="currentColor"`, round caps and joins, stroke width 2 (2.2 for plus). Sizes: search 16, plus 16, close 20, edit 14. Profile menu glyphs: ◐ Profile, ⚑ Manage users, ↪ Sign out.

---

## 6. Auth screens

### 6.1 Shell
Full-viewport flex that centers its content, 24px padding, on the cream background with the grain.

### 6.2 Auth card
`--bg`, 22px radius, shadow-3, padding 36px 36px 30px, `width: min(420px,100%)`.
Order: brand row (40px mark plus "Bookshelf" in Slab 22/700 forest, 26px below), then the heading (Slab 26/600 forest), then the subtitle (14px muted, 22px below), then fields (14px apart), then actions.

- **Error box:** terracotta at 12% bg, terracotta text, 10px radius, padding 10px 12px, 13px
- **Info box:** surface bg, muted text, same sizing
- **Footer line:** centered 13px muted, with an underlined forest link button inside

### 6.3 Screens & copy

| Screen | Heading | Subtitle | Primary | Links |
|---|---|---|---|---|
| Sign in | Welcome back | Sign in to your shelf and pick up where you left off. | Sign in | "Forgot password?" (above button), "New here? **Request access**" |
| Request access | Request access | Bookshelf is invite-only. Send a request and an admin will let you in. | Send request | "Already have access? **Sign in**" |
| Request sent | (same) | info box: "Thanks — your request is in…" | Back to sign in | — |
| Recover 1 | Recover your password | Enter your email and we'll send a 6-digit code. | Send code | Back to sign in |
| Recover 2 | Check your inbox | We sent a 6-digit code to **{email}**. | Verify | Use a different email |
| Recover 3 | Set a new password | At least 4 characters… | Update password | — |
| Recover 4 | All set | Your password has been updated. Sign in to continue. | Back to sign in | — |

**Code input:** digits only, maxlength 6, Slab 22px, `letter-spacing: .8em`, centered, padding 12px. Below it is a hint ("Six digits.") in 12px ink-faint.

---

## 7. Profile

### 7.1 Avatar
A circle. If there's an image it uses `object-fit: cover`. Otherwise it shows initials (first letter of up to 2 words, uppercase) in Slab 600 cream on `linear-gradient(155deg, sage, forest)`, with the font size at 42% of the diameter.
Sizes: 32 (pill), 36 (table), 44 (popover), 72 (profile page).

### 7.2 Profile pill (top bar, after Add book)
Pill, surface bg, padding 4px 12px 4px 4px, gap 10px. A 32px avatar, then the name (13.5/500 ink, max 130px, ellipsis). Hover: moss bg and shadow-1.

### 7.3 Popover
Absolute, 8px below the pill and right-aligned, 260px wide, `--bg`, 14px radius, shadow-3, padding 6px, z-index 40. Closes on an outside click.
- Head: 44px avatar, name (Slab 15/600), email (12px muted), and for admins an `ADMINISTRATOR` chip (10px .12em, forest on moss, pill)
- 1px separators at `rgba(107,94,83,.18)`
- Items: full width, padding 9px 12px, 8px radius, 13.5px, 18px icon slot in muted. Hover: moss bg
- **Sign out** is the danger item: terracotta text and icon, with terracotta at 12% on hover

### 7.4 Profile page (`#/profile`)
Same top bar. The brand block shows the title "Your profile" and goes back to the shelf on click. The toolbar has a ghost "← Back to shelf" button and the profile pill.
A 2-column grid with an 18px gap (1 column ≤720px), holding cards with surface bg, 14px radius and 24px padding:
- **Identity:** a 72px avatar with an Upload avatar button (secondary) and Remove (ghost), then Display name, Email and **Save changes**
- **Password:** New, Confirm, **Update password** (disabled while empty). Then a separator and **Account** key/value rows (Role, Member since, Last sign-in): 13.5px muted keys, ink 600 values, dashed separators

**Flash message** under the header: 10px radius, padding 10px 16px, 13.5px. `ok` is forest text on sage at 18%. `err` is terracotta on terracotta at 15%.

---

## 8. Admin (`#/admin`, admins only)

### 8.1 Toolbar
A search field ("Search by name or email", flex 1, min 240px), a segmented sort, and a primary **+ New user** button. 18px below.

### 8.2 Users table
Container: surface bg, 14px radius, overflow hidden.
Columns: `1.6fr 100px 130px 130px 220px`, gap 14px, padding 12px 18px. At ≤820px it becomes `1.6fr 90px 200px` (date columns hidden).

| Part | Style |
|---|---|
| Header row | wood at 6% tint, 11px uppercase .10em muted, 600 |
| Row | dashed bottom border at 14%, 13.5px |
| User cell | 36px avatar, name (Slab 14.5/600) + optional `YOU` chip (10px, forest on moss), email (12.5 muted), optional "Must reset password" (11px terracotta) |
| Role chip | pill, padding 4px 10px. **Admin**: forest on forest 15%. **Reader**: wood on wood 15% |
| Dates | 12.5px muted, `MMM D, YYYY`, or "—" if never |
| Actions | right-aligned link buttons with a 14px gap: Edit · Reset password · Delete (danger, hidden on your own row) |

### 8.3 Dialogs
Base: fixed and centered, `--bg`, 22px radius, padding 28px, shadow-3, `min(420px, 92vw)` (user dialog max 460px), z-index 60. Title in Slab 20/600 forest. Footer is right-aligned with a 10px gap.
- **Create / Edit user:** Name, Email, Role (segmented), Initial password (create only, placeholder `changeme`), and a checkbox "Force password reset on next sign-in" (checked by default on create)
- **Reset password:** New password, and a checkbox "Make user choose a new one on next sign-in" (checked by default)
- **Delete:** the standard confirm dialog. "Delete {name}?" with the body "The account "{email}" will be removed. This cannot be undone." and a terracotta **Delete user** button

---

## 9. Z-index scale

| Layer | z |
|---|---|
| Background grain | 0 |
| App content | 1 |
| Profile popover | 40 |
| Scrim | 50 |
| Sheet / admin dialogs | 60 |
| Confirm-dialog scrim | 65 |
| Confirm dialog | 70 |
| Toast | 80 |

---

## 10. Tailwind theme (drop-in)

```js
export default {
  theme: {
    extend: {
      colors: {
        bg: '#F5F1E8', surface: '#E8E1D4', surface2: '#EFE9DC', hover: '#D6E2D3',
        sage: '#7A9E7E', forest: '#4F6F52', wood: '#A67C52', terracotta: '#C97C5D', yellow: '#E6D8A3',
        ink: '#3A3A3A', 'ink-muted': '#6B5E53', 'ink-faint': '#9C8F82', onfill: '#FAF7EE',
      },
      fontFamily: {
        serif: ['"Roboto Slab"', 'Georgia', 'serif'],
        sans: ['Inter', 'system-ui', '-apple-system', '"Segoe UI"', 'sans-serif'],
      },
      borderRadius: { sm: '8px', md: '10px', DEFAULT: '14px', lg: '22px', cover: '6px', tag: '4px' },
      boxShadow: {
        soft1: '0 1px 2px rgba(58,46,32,.04), 0 2px 8px rgba(58,46,32,.04)',
        soft2: '0 2px 6px rgba(58,46,32,.06), 0 12px 28px rgba(58,46,32,.08)',
        soft3: '0 8px 24px rgba(58,46,32,.10), 0 28px 64px rgba(58,46,32,.14)',
        cover: 'inset -3px 0 6px rgba(0,0,0,.12), inset 2px 0 4px rgba(255,255,255,.12), 0 4px 10px rgba(58,46,32,.12)',
      },
      screens: { xs: '540px', sm: '720px', md: '820px', lg: '900px' },
      transitionTimingFunction: { sheet: 'cubic-bezier(.2,.8,.2,1)' },
    },
  },
};
```

> The breakpoints here are written as min-widths. The prototype's rules are max-width (≤540 / ≤720 / ≤820). If you build mobile-first, flip them: use the mobile values as the base, then apply the desktop values at `sm:`/`md:`.

---

## 11. Source files

- `styles.css`: tokens, dashboard, cards, sheet, forms, dialogs, toast, responsive rules
- `auth-styles.css`: auth screens, profile pill/popover, profile page, admin table and dialogs
- `Bookshelf.html` / `Bookshelf-standalone.html`: the live reference
