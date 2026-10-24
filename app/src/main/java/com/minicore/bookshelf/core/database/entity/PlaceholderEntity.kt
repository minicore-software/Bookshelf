package com.minicore.bookshelf.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Exists only so [androidx.room.Database] accepts a non-empty entity list while no feature
 * owns a real entity yet (Room 2.8.5 rejects `entities = []`).
 *
 * Removal rule: the first ticket that adds a real entity (the library `Book` entity) removes
 * this class. If no build has been distributed to anyone by then, replace it in place and keep
 * `version = 1` (regenerate the schema JSON, developers uninstall their debug build). If a build
 * has been distributed, bump to `version = 2` with an `@AutoMigration` and
 * `@DeleteTable(tableName = "scaffold_placeholder")` instead.
 */
@Entity(tableName = "scaffold_placeholder")
data class PlaceholderEntity(
    @PrimaryKey val id: Int
)
