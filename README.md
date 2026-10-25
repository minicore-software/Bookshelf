# Bookshelf — Personal Library Android App

Bookshelf is an Android app for managing your personal library. It keeps a simple, searchable record of every book you own, tracks your reading pace, and remembers who borrowed what — so the books you treasure never get lost in the shuffle.

It focuses on simplicity, offline-first reliability, and a calm paper-and-ink look that gets out of the way of the books.

---

## ✨ Purpose

Bookshelf was built with three main goals:

- 📚 A simple database for your books — add, organize and browse what you own
- 📖 Track your reads — progress, pace, and how a book is going
- 🤝 Track borrow & lend — know who has a book and when it's due back

It intentionally avoids enterprise-level overengineering while still keeping a scalable architecture.

---

## 🧱 Tech Stack

See [CLAUDE.md](CLAUDE.md) for the full version table.

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Architecture:** MVVM + Repository pattern
- **Local Storage:** Room
- **Async:** Coroutines + Flow
- **Dependency Injection:** Hilt
- **Navigation:** Navigation Compose
- **Scanning:** CameraX + ML Kit Barcode Scanning (on-device ISBN lookup)

---

## 🏗 Architecture Overview

Bookshelf follows a layered, feature-first MVVM architecture:

### Presentation Layer
- Jetpack Compose screens
- ViewModels
- StateFlow-based UI state management

### Domain Layer (lightweight)
- Business logic use cases (only where meaningful)

### Data Layer
- Room (local database)
- Repository abstraction

No backend, no accounts — Bookshelf is local-first and single-user.

---

## 📦 Core Domain Model

- **Book** — canonical representation of a book you own (title, author, ISBN, cover, genre, page count, status)
- **ReadingLogEntry** — a progress update over time (current page, mood, note)
- **Loan** — a lending record (borrower, loaned date, due date, return date, notes)

See [CLAUDE.md](CLAUDE.md) for the full field-level model and package layout.

---

## 📱 Key Features

### v1 Core Features
- Organize your library: add books by search or barcode scan, browse by shelf/status
- Track reading progress: current page, mood, notes, per-book reading log
- Lend & borrow tracking: mark a book as lent with borrower, due date and reminder; see overdue/on-loan/returned at a glance
- Reading insights: books/pages read, streaks, genre breakdown

### Future
- Deliberately out of v1 for now: shelves/tags, price tracking, multi-device sync — see [CLAUDE.md](CLAUDE.md) for the full domain model

---

## 🎨 Design

The product design lives in [`project/`](project) as a Design canvas — one `.dc.html` per screen (Library, Book Detail, Discover & Add, Update Progress, Reading Stats, Settings, Lend a Book, My Loans), indexed by `project/canvas.json`.

**Palette:** paper `#F3EEE3` · ink `#231F20` · forest `#2F4538` · terracotta `#C1693C` · gold `#C9A227`
**Type:** Fraunces (serif, headings) + Inter (sans, UI)

---

## 🚀 Getting Started

```bash
# Build a debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run instrumented tests (requires a connected device/emulator)
./gradlew connectedAndroidTest

# Lint
./gradlew lint
```

Open the project in Android Studio and run the `app` configuration to launch it on a device/emulator, or install the debug APK directly:

```bash
./gradlew installDebug
```

**App ID:** `com.minicore.bookshelf` | **minSdk:** 29 | **targetSdk:** 36

See [CLAUDE.md](CLAUDE.md) for the full build command reference and tech stack versions.

---

## 🖼 Regenerating the launcher icon

The adaptive launcher icon (`app/src/main/res/mipmap-*/ic_launcher*`, `mipmap-anydpi/ic_launcher*.xml`, `values/ic_launcher_background.xml`) is generated from `resources/icon.svg` by a dev-only Node script. It never runs as part of the Gradle build. Never hand-edit the generated files — re-run the script instead.

Prerequisite: Node LTS.

```bash
cd tools/launcher-icon
npm ci
npm run generate   # renders the mipmaps and writes the XML/color resource
npm run check       # fitness-function checks: safe zone, dimensions, background, drift
```

`npm run generate` also writes preview aids (mask previews under circle/squircle/rounded-square/teardrop, a side-by-side against the original SVG) to the gitignored `tools/launcher-icon/out/` for visual review, and `npm run check` is the fitness function that must pass before committing. Commit the regenerated files under `app/src/main/res/` whenever `resources/icon.svg` changes.

---

## 🌅 Regenerating the splash screen

The splash artwork (`app/src/main/res/drawable-*/splash_screen.png`) is rasterized from `resources/splash-screen.svg` by a dev-only Node script, mirroring the launcher-icon tooling above. It never runs as part of the Gradle build. Never hand-edit the generated PNGs — re-run the script instead.

Prerequisite: Node LTS.

```bash
cd tools/splash-screen
npm ci
npm run generate   # renders the density-bucketed PNGs
npm run check       # fitness-function checks: dimensions, opacity, wordmark presence, font hash, drift
```

The "Bookshelf" wordmark is rendered using a committed, version-pinned Playfair Display Bold font (`tools/splash-screen/fonts/`) rather than relying on system fonts — `resvg` silently drops the wordmark if it can't find a matching font, so `npm run check` specifically verifies wordmark pixels are present. Commit the regenerated PNG under `app/src/main/res/` whenever `resources/splash-screen.svg` changes.

---

## 🔠 The bundled Fraunces font

The Library screen's "Bookshelf" title is rendered with a bundled, static Fraunces SemiBold font (`app/src/main/res/font/fraunces_semibold.ttf`), the same reasoning as the splash screen above: a downloadable or system-fallback font can silently resolve to the wrong typeface, and Bookshelf is local-first and offline. See the provenance and SHA-256 comment in `core/designsystem/Type.kt` for where the file came from and how it was produced, and `app/src/main/assets/licenses/Fraunces-OFL.txt` for its SIL OFL 1.1 license (shipped outside `res/font/`, which AAPT2 restricts to font files only).

No static 24pt SemiBold cut of Fraunces is published upstream, so the committed file is produced from Google Fonts' variable font by the dev-only `tools/fraunces-font/generate.py` script. It never runs as part of the Gradle build. Never hand-edit the committed `.ttf` — re-run the script instead.

Prerequisite: Python 3 with `pip install -r tools/fraunces-font/requirements.txt`.

```bash
cd tools/fraunces-font
python generate.py generate   # writes a fresh instance to out/fraunces_semibold.ttf
python generate.py check       # verifies the pinned source hash and that the committed
                                # font is byte-equal to a freshly generated instance
```

Commit the regenerated file under `app/src/main/res/font/` whenever the pinned source or axis pins change, and run `check` before committing.

---

## 🔠 The bundled Inter font

The Library header's tagline and search placeholder are rendered with a bundled, static Inter Regular font (`app/src/main/res/font/inter_regular.ttf`), for the same offline-first reasoning as Fraunces above. Unlike Fraunces, upstream publishes a ready-made static instance directly — no local instancing step is needed. See the provenance and SHA-256 comment in `core/designsystem/Type.kt` for where the file came from, and `app/src/main/assets/licenses/Inter-OFL.txt` for its SIL OFL 1.1 license (shipped outside `res/font/`, which AAPT2 restricts to font files only).

---

## 📄 License

See [LICENSE](LICENSE).
