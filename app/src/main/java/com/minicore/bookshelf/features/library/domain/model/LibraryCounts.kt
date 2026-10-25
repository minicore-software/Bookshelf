package com.minicore.bookshelf.features.library.domain.model

/**
 * Chip badge counts (plan section 5/8). `lentOut` counts books with an open loan - it is
 * independent of status: a lent book is also counted under its status, and [all] is the sum of
 * [reading], [finished] and [wantToRead].
 */
data class LibraryCounts(
    val all: Int,
    val reading: Int,
    val finished: Int,
    val wantToRead: Int,
    val lentOut: Int,
)
