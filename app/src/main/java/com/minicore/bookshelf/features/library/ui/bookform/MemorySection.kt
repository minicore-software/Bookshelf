package com.minicore.bookshelf.features.library.ui.bookform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.minicore.bookshelf.R
import com.minicore.bookshelf.core.designsystem.BookshelfTheme

const val MEMORY_SECTION_TEST_TAG = "book_form_memory_section"
const val MEMORY_WHY_FIELD_TEST_TAG = "book_form_memory_why_field"
const val MEMORY_WHERE_FIELD_TEST_TAG = "book_form_memory_where_field"
const val MEMORY_WHAT_FIELD_TEST_TAG = "book_form_memory_what_field"

/**
 * The "Memory" section (plan #33 section 3.3, decision D15): three independent, optional
 * multiline fields. Lives in `bookform/` (not `editbook/`) because Add may gain it later (plan
 * #33 decision E14) - it has no dependency on anything Edit-only.
 */
@Composable
fun MemorySection(
    memoryWhy: String,
    onMemoryWhyChange: (String) -> Unit,
    memoryWhere: String,
    onMemoryWhereChange: (String) -> Unit,
    memoryWhat: String,
    onMemoryWhatChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.testTag(MEMORY_SECTION_TEST_TAG), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.book_form_memory_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.book_form_memory_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = memoryWhy,
                onValueChange = onMemoryWhyChange,
                modifier = Modifier.fillMaxWidth().testTag(MEMORY_WHY_FIELD_TEST_TAG),
                label = { Text(stringResource(R.string.book_form_memory_why_label)) },
                placeholder = { Text(stringResource(R.string.book_form_memory_why_placeholder)) },
                enabled = enabled,
                minLines = 3,
                maxLines = 8,
            )
            OutlinedTextField(
                value = memoryWhere,
                onValueChange = onMemoryWhereChange,
                modifier = Modifier.fillMaxWidth().testTag(MEMORY_WHERE_FIELD_TEST_TAG),
                label = { Text(stringResource(R.string.book_form_memory_where_label)) },
                placeholder = { Text(stringResource(R.string.book_form_memory_where_placeholder)) },
                enabled = enabled,
                minLines = 3,
                maxLines = 8,
            )
            OutlinedTextField(
                value = memoryWhat,
                onValueChange = onMemoryWhatChange,
                modifier = Modifier.fillMaxWidth().testTag(MEMORY_WHAT_FIELD_TEST_TAG),
                label = { Text(stringResource(R.string.book_form_memory_what_label)) },
                placeholder = { Text(stringResource(R.string.book_form_memory_what_placeholder)) },
                enabled = enabled,
                minLines = 3,
                maxLines = 8,
            )
        }
    }
}

@Preview(name = "MemorySection")
@Composable
private fun MemorySectionPreview() {
    BookshelfTheme {
        MemorySection(
            memoryWhy = "",
            onMemoryWhyChange = {},
            memoryWhere = "",
            onMemoryWhereChange = {},
            memoryWhat = "",
            onMemoryWhatChange = {},
            enabled = true,
        )
    }
}
