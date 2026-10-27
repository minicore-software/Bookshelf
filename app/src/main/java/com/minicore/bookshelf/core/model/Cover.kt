package com.minicore.bookshelf.core.model

/**
 * A [Book]'s cover: either an uploaded image or one of 6 preset colours, never both (epic D9,
 * ADR-002 S1). The sealed type makes "both" unrepresentable in Kotlin; the database enforces the
 * same invariant for every other writer via `BookCoverInvariantCallback`'s triggers.
 */
sealed interface Cover {
    data class Image(val uri: String) : Cover
    data class Preset(val preset: CoverPreset) : Cover
}
