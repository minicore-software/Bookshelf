# Store uploaded cover images as downscaled, EXIF-free app-private copies referenced by a relative path

* **Status:** Accepted
* **Group:** Data
* **Deciders:** software-architect (design delegated by the BOOKSHELF-0031 ticket owner); TBD — confirm in PR review
* **Date:** 2026-10-27

## Context and Problem Statement

#31 (Add a book manually) lets the user upload a cover image (epic D3). ADR-002 settled that `books.cover_uri` and `books.cover_color` are mutually exclusive, but it explicitly deferred two questions to #31: what `cover_uri` actually contains, and whether the app draws the image at all (today `BookCover` ignores the URI). The answer goes into user data from the first save, so changing it later needs a data migration.

**Assumptions:**

* Single user, local-first, no backend (CLAUDE.md). minSdk 29, so the framework `ImageDecoder` (API 28+) is available.
* Covers are displayed at most at about 140×200 dp (Visual Spec §5.9), which is ≤ 560×800 px at xxxhdpi. A 600 px long edge is enough for display.
* Android Auto Backup has a 25 MB per-app cloud quota. If the quota is exceeded, the backup is skipped entirely, database included.
* Nothing writes `cover_uri` before #31, so there is no existing data in another format.

## Decision Drivers

* A cover must survive the user deleting or moving the original photo, a reboot, and an app update.
* The app must never crash or hang on a hostile, huge or corrupt image.
* Photo metadata (GPS location in EXIF) must not be kept.
* The database backup must never be put at risk by cover files.
* Prefer no new dependency. The images are local and small.
* Leave room for remote covers (a future scan/search lookup) without breaking stored values.

## Considered Options

* **Option A: Copy into app storage. Decode with `ImageDecoder`, sized from the header to ≤ 600 px long edge, honouring EXIF orientation. Composite onto an opaque background and re-encode as JPEG q80 (no EXIF). Store as `filesDir/covers/<uuid>.jpg`. `cover_uri` = the relative reference `covers/<uuid>.jpg`.** The draft goes to `cacheDir/cover-drafts/` first and is moved on save.
  * Pros: independent of the gallery and of URI grants. Bounded size (typically 30–80 KB). EXIF stripped by construction. The relative reference survives a different `filesDir` absolute path (restore onto another user or profile). A strict reference pattern makes path traversal impossible.
  * Cons: the app owns the file lifecycle (draft cleanup, rollback when the insert fails, deletion on book removal in #34). Duplicate storage of a photo the user already has. Lossy re-encode.
* **Option B: Store the picker's `content://` URI, with `takePersistableUriPermission` where supported.**
  * Pros: no copy, no storage cost, no file lifecycle.
  * Cons: Photo Picker / SAF grants are not reliably persistable across all providers and API levels. The cover disappears when the photo is deleted or moved. The URI is meaningless after a backup restore or on a new device. Every render decodes a full-resolution original.
  * Rejected because: it fails the first driver.
* **Option C: Store the image as a BLOB in Room.**
  * Pros: atomic with the row, one backup unit.
  * Cons: bloats `bookshelf.db` and every query that selects `b.*` (the Library projections do), CursorWindow limits around 2 MB per row, and it puts the database itself over the backup quota sooner. It contradicts ADR-002's `cover_uri TEXT`.
  * Rejected because: cost to every Library read, and backup risk.
* **Option D: Keep the original file (copy without re-encoding).**
  * Pros: no quality loss, simpler.
  * Cons: 3–10 MB per cover, keeps EXIF GPS, and decoding cost on every render.
  * Rejected because: it fails the privacy and backup drivers.

**Image loading:** a hand-written `CoverImageLoader` (`ImageDecoder` + a small LRU cache, off the main thread) was chosen over adding **Coil**. Coil earns its place for remote images, disk caching and transformations, and none of those exist yet. The files are already downscaled. Revisit when remote covers arrive.

**Backup scope** for `files/covers/`:

* Included in `device-transfer` (chosen), so a phone-to-phone move keeps covers.
* **Excluded from `cloud-backup` and the pre-API-31 `full-backup-content`** (chosen). Including them would let a large library push the app over the 25 MB quota, and then the whole backup, database included, would be skipped. After a cloud restore an image cover falls back to its id-derived colour, which `BookCover` already does for a missing file.

## Decision Outcome

**Chosen option:** "Option A (downscaled, EXIF-free app-private copy referenced by `covers/<uuid>.jpg`)", with a hand-written loader and covers excluded from cloud backup. It is the only option that keeps a cover after the original is gone (driver 1), bounds memory and size (drivers 2 and 4), strips location metadata (driver 3) and adds no dependency (driver 5). A reference shape with no scheme leaves `https://…` free for future remote covers (driver 6).

### Consequences

* **Good:** covers are durable, small and private. Rendering one cover is cheap enough for the Library grid.
* **Good:** no new dependency and no new permission (the Photo Picker needs none). The manifest is unchanged apart from the backup rules.
* **Bad:** the app owns a file lifecycle. Drafts are discarded on cancel or replace. A committed file is deleted if the insert fails. #33 must delete a replaced file only after `updateBook` succeeds, and #34 must delete the file after the row delete. A missed path leaves an orphan file (wasted space, never a crash).
* **Bad:** cloud restore loses image covers (they fall back to a colour). Device-to-device transfer keeps them.
* **Bad:** lossy re-encode at 600 px. Not suited to a future zoomable full-screen cover view without re-import.
* **Constraint:** `cover_uri` holds either `NULL` or a reference matching `^covers/[0-9a-f-]{36}\.jpg$`. Any other value is treated as missing (colour fallback), never resolved. New shapes (such as `https://`) need their own resolver branch and an ADR update.
* **Constraint:** only `CoverImageStore` (`core/coverimage`) reads or writes `files/covers/` and `cache/cover-drafts/`. `BookCover` draws images only through `LocalCoverImageLoader`, whose default is a no-op (colour), so `core/designsystem` stays free of IO.
* **Constraint:** never log image references together with book content (`BookLoggingTest` rule).
* **Follow-up:** #33 and #34 lifecycle hooks as above. Revisit Coil and backup scope when remote covers or a sync feature appear.

## Related

* **Requirement:** [#31](https://github.com/minicore-software/Bookshelf/issues/31) (cover upload, AC4), epic [#29](https://github.com/minicore-software/Bookshelf/issues/29) decisions D3, D9.
* **Decisions:** [ADR-002](ADR-002-Store-Book-Cover-As-Uri-Or-Preset-Key-With-Trigger-Enforced-Exclusivity.md). Resolves its #31 follow-up and does not supersede it. [ADR-001](ADR-001-Model-Book-And-Loan-Tables-With-Trigger-Enforced-Open-Loan.md) (single build path, unchanged).
* **Artifacts:** [docs/plans/31-add-book-manually.md](../plans/31-add-book-manually.md) (sections 4.3, 4.4, 5).
