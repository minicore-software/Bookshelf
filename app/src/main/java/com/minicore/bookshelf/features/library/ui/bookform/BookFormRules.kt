package com.minicore.bookshelf.features.library.ui.bookform

/**
 * The pure form rules shared by Add (`ui/addbook/AddBookViewModel`) and Edit
 * (`ui/editbook/EditBookViewModel`) (plan #33 decision E9). No Android dependency, no
 * `ViewModel` - every function is a straight value-in/value-out transform, tested on the JVM
 * without Robolectric. Lives in `bookform/` alongside the stateless controls it drives
 * ([YearStepperField], [StarRatingInput]) so one copy of the rule set can never drift between
 * the two screens.
 */
object BookFormRules {

    /**
     * The year text field's input filter (plan #31 UX spec section 4): digits only, at most 4
     * characters. Returns `null` when [value] violates either rule, meaning "reject the
     * keystroke, keep the field's current text" - never a partially-sanitized value.
     */
    fun sanitizeYearText(value: String): String? {
        if (value.isNotEmpty() && (value.length > 4 || !value.all(Char::isDigit))) return null
        return value
    }

    /**
     * One chevron tap (plan #31 UX spec section 4): the first tap on an empty field, or a field
     * showing `"0"`, seeds [currentYear] rather than stepping; later taps increment/decrement by
     * [delta], clamped to `1..9999`.
     */
    fun stepYear(currentText: String, delta: Int, currentYear: Int): String {
        require(delta == 1 || delta == -1) { "delta must be +1 or -1, was $delta" }
        val current = currentText.toIntOrNull()
        val next = if (current == null || current == 0) currentYear else (current + delta).coerceIn(1, 9999)
        return next.toString()
    }

    /** `"0"` or unparsable/out-of-range text -> `null` (plan #31 section 4.4 / UX spec section 4). */
    fun parsePublishedYear(yearText: String): Int? = yearText.toIntOrNull()?.takeIf { it in 1..9999 }

    /** Tapping the currently-set star clears it (plan #31 A6 rating rule, decision D8). */
    fun toggleRating(current: Int?, tapped: Int): Int? {
        require(tapped in 1..5) { "tapped must be in 1..5, was $tapped" }
        return if (current == tapped) null else tapped
    }
}
