package com.minicore.bookshelf.core.database.dao

import com.minicore.bookshelf.core.model.BookStatus

/** One row of `SELECT status, COUNT(*) ... GROUP BY status` (plan section 4, ADR-001 S8). */
data class StatusCountRow(
    val status: BookStatus,
    val count: Int,
)
