package com.minicore.bookshelf.features.library.ui.bookdetail

/**
 * Pure presentation rules for the Book Detail modal (plan #32 section 3), kept out of the
 * composables so they are directly unit-testable.
 */

/** Rule 3: `Author · 1939`; author blank -> year only; year null -> author only; both missing
 * -> `null` (the line is hidden). [bothFormat] is `R.string.book_detail_author_year` ("%1$s ·
 * %2$d") - kept string-parameterised, like [ratingAccessibilityLabel], so this stays
 * plain-Kotlin testable without a Compose rule, and so the separator lives in `strings.xml`, not
 * hardcoded here. */
fun authorYearLine(author: String?, year: Int?, bothFormat: String): String? {
    val trimmedAuthor = author?.takeIf { it.isNotBlank() }
    return when {
        trimmedAuthor != null && year != null -> String.format(bothFormat, trimmedAuthor, year)
        trimmedAuthor != null -> trimmedAuthor
        year != null -> year.toString()
        else -> null
    }
}

/** The 2-state status label (plan decision D16/V5): derived *only* from whether there is an open
 * loan, never [com.minicore.bookshelf.core.model.Book.status]. */
enum class BookDetailStatus { OnTheShelf, LentOut }

fun bookDetailStatus(hasOpenLoan: Boolean): BookDetailStatus =
    if (hasOpenLoan) BookDetailStatus.LentOut else BookDetailStatus.OnTheShelf

/** Rule 4 (F5): `rating == null` -> [notRatedText] ("Not rated"), otherwise [ratedFormat]
 * ("Rated %d of 5") filled in with [rating] - kept string-parameterised so it stays plain-Kotlin
 * testable without a Compose rule. */
fun ratingAccessibilityLabel(rating: Int?, notRatedText: String, ratedFormat: String): String =
    if (rating == null) notRatedText else String.format(ratedFormat, rating)
