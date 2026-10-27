# Store the book cover as an image URI or a preset-colour key, never both, enforced by triggers, migrated by a hand-written Room migration

* **Status:** Accepted
* **Group:** Data
* **Deciders:** software-architect (design delegated by the BOOKSHELF-0030 ticket owner); TBD — confirm in PR review
* **Date:** 2026-10-27

## Context and Problem Statement

The Book Management epic (#29) lets the user give a book either an uploaded cover image or one of 6 preset solid colours, never both (epic D9). Today `books` has only a nullable `cover_uri`. No UI writes it, and `BookCover` derives a placeholder colour from `Math.floorMod(bookId, 6)` at render time. #30 adds `comment`, three memory fields and a cover-colour field. It is also the **first real schema migration** (1 → 2): ADR-001 replaced version 1 in place because nothing had shipped, but #30's acceptance criteria require an upgrade path that keeps data. Three questions have to be answered before #31–#35 write rows that depend on the answers:

1. How is "cover image XOR cover colour" represented and enforced?
2. What does `cover_color` store: a colour value or a palette reference?
3. How are migrations written and wired from now on?

**Assumptions:**

* Single user, local-first, no backend (CLAUDE.md). All writes go through Room in-process.
* The exact 6 preset colour values are not settled. `colors.xml` (`cover_1_bg`…`cover_6_bg`) and the Visual Spec §1.4 table hold different hex values, and CLAUDE.md marks the two as "pending reconciliation".
* minSdk 29 ships SQLite 3.22. That rules out `ALTER TABLE … RENAME COLUMN` (needs 3.25) and the check of a new column's `CHECK` constraint against existing rows (needs 3.37).
* The app uses Room 2.8.5 on the default SupportSQLite path, as ADR-001 assumes.

## Decision Drivers

* No row can ever have both a cover image and a cover colour, whichever path writes it (DAO, repository, a future import, test fixtures).
* A fresh install and an upgraded install must end up with the same schema.
* Changing the palette values when the design is reconciled must not need a data migration.
* Existing rows look exactly as they do today after the upgrade. #30 has no user-visible change.
* The pattern matches what ADR-001 already set up: one build function, triggers re-asserted in `onOpen`, schema JSON reviewed in the diff.

## Considered Options

**Representing and enforcing exclusivity:**

* **Option A: Two nullable columns (`cover_uri`, new `cover_color`), a sealed `Cover` domain type, and `BEFORE INSERT`/`BEFORE UPDATE` triggers on `books` that `RAISE(ABORT)` when both are non-null.** The triggers come from a `RoomDatabase.Callback` (`onCreate` + `onOpen`, `CREATE TRIGGER IF NOT EXISTS`), and the 1 → 2 migration also creates them.
  * Pros: the engine enforces it for every writer. Both columns are Room-native and appear in the schema JSON. It reuses the mechanism, test style and `onOpen` self-repair from ADR-001. In Kotlin, the sealed type means a domain `Book` with both covers cannot be written down.
  * Cons: two more triggers that are not in the schema JSON (same cost as ADR-001). "Neither set" is still allowed at the SQL level, so reads need a fallback for it.
* **Option B: A `CHECK (cover_uri IS NULL OR cover_color IS NULL)` constraint.**
  * Pros: the idiomatic SQL answer, and it is declarative.
  * Cons: Room has no way to declare a `CHECK`, so the `CREATE TABLE` that Room generates on a fresh install would not have it, while upgraded installs would (added with `ADD COLUMN … CHECK`). Two schemas depending on install path. Adding it to the table itself means recreating `books`, which `loans` points to (FK `ON DELETE CASCADE`). That is the riskiest migration available for the least gain.
  * Rejected because: fresh and upgraded databases would differ.
* **Option C: App-level invariant only (sealed domain type + mapper, no SQL enforcement).**
  * Pros: no SQL beyond the columns, nothing outside the schema JSON.
  * Cons: `BookEntity` is a public data class, so any DAO caller, fixture or future import can write both. It is a convention, not an invariant. ADR-001 rejected its Option 2 for the same reason.
  * Rejected because: it fails the first driver. Option A costs two SQL statements more.
* **Option D: One column holding an encoded value (`uri:content://…` / `preset:preset_3`).**
  * Pros: "both" is impossible by construction, and it is fully Room-native.
  * Cons: existing `cover_uri` data has to be rewritten, and the column renamed. On SQLite 3.22 that means recreating the table (see Option B). It adds string parsing to every read, and it contradicts the ticket's field list.
  * Rejected because: highest migration risk, plus parsing cost, for a guarantee Option A already gives.

**What `cover_color` stores:**

* **Stable preset key `preset_1`…`preset_6` (chosen).** The design system resolves it to colours. Reconciling the palette is a resource change. Unknown keys fall back on read (see Consequences).
* **Hex `#RRGGBB` (rejected).** It freezes today's palette into user data. The two candidate palettes already disagree, so reconciling them would need a data migration. It could be added later under its own literal shape if custom colours are ever wanted, without breaking the keys.
* **Ordinal `INTEGER` (rejected).** Same reason ADR-001 rejected ordinals for status: reordering the enum silently remaps rows.

**Backfill for existing rows with no cover:** `'preset_' || ((id % 6) + 1)`, which matches today's render-time `floorMod(id, 6)` index exactly (chosen). The alternatives were leaving them null and keeping the render-time derivation, which fails the AC and keeps two code paths, or one fixed preset for all rows, which visibly changes every existing cover.

**Migration mechanism:**

* **Hand-written `Migration(1, 2)` objects, registered inside `applyBookshelfConfig()` (chosen).** The `ALTER`s, backfill and trigger creation sit together in one reviewable transaction. Tests and production share the same registration path.
* **`@AutoMigration` with an `AutoMigrationSpec.onPostMigrate` backfill (rejected).** It is less code, but the generated SQL is hidden, the custom backfill and trigger SQL still have to be written, and the migration is no longer visible in one place.
* **`fallbackToDestructiveMigration` (rejected, permanently).** It silently wipes user data the first time a migration is missing.

## Decision Outcome

**Chosen option:** "Option A (two columns + sealed `Cover` + triggers)", with `cover_color` storing stable preset keys, the id-derived backfill, and hand-written migrations registered in `applyBookshelfConfig()`. It is the only option that meets the first two drivers together: every writer is covered, and fresh and upgraded schemas are identical. The key format meets the third, and the backfill formula meets the fourth. Cost: two trigger statements, one callback, one migration object, one migration test class and one test dependency (`room-testing`), all in the Room skills the team already uses.

### Consequences

* **Good:** Add/Edit (#31, #33) cannot store an image and a colour together, whichever layer they write through.
* **Good:** palette reconciliation (Visual Spec §1.4 vs `colors.xml`) only touches resources, never data.
* **Good:** every existing book keeps exactly the colour it shows today.
* **Good:** the repo now has a tested, repeatable migration pattern. Later schema changes follow it instead of replacing the version in place.
* **Bad:** two more triggers that are not in `app/schemas/*.json`. Reviewers must check `BookCoverInvariantCallback` as well as the schema diff (same cost as ADR-001).
* **Bad:** "neither set" is possible at the SQL level (direct DAO writes, an unknown key). Reads map it to the id-derived preset rather than failing.
* **Constraint:** every `BookshelfDatabase` instance is still built through `applyBookshelfConfig()`, which now also registers every migration (extends ADR-001's constraint).
* **Constraint:** persisted preset literals `preset_1`…`preset_6` never change without a data migration (same rule as `BookStatus`).
* **Constraint:** the SQL backfill formula and `CoverPreset.forBookId` must agree. A test pins this.
* **Constraint:** new columns are added with no `@ColumnInfo(defaultValue)`, so they match `ALTER TABLE … ADD COLUMN … TEXT` and pass Room's post-migration validation.
* **Constraint:** no `fallbackToDestructiveMigration*` anywhere. No `@Upsert` or `REPLACE` on `books` (extends ADR-001).
* **Constraint:** each version's schema JSON stays committed, and each step `N → N+1` has a migration and a `MigrationTestHelper` test.
* **Follow-up:** #31 decides how the Visual Spec §1.4 palette replaces `cover_N_bg` (index to index), and whether image loading replaces the placeholder for `Cover.Image`.

## Related

* **Requirement:** [#30](https://github.com/minicore-software/Bookshelf/issues/30) (all AC), epic [#29](https://github.com/minicore-software/Bookshelf/issues/29) decisions D3, D9, D11.
* **Decisions:** [ADR-001](ADR-001-Model-Book-And-Loan-Tables-With-Trigger-Enforced-Open-Loan.md). Extends it (same trigger mechanism, same single build path) and does not supersede it.
* **Artifacts:** [docs/plans/30-extend-book-schema.md](../plans/30-extend-book-schema.md) (migration contract, domain/DAO/repository shapes, tests).
