package com.dewijones92.totum.ui.group

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dewijones92.totum.R
import com.dewijones92.totum.domain.SourceGroup
import com.dewijones92.totum.domain.SourceId

/**
 * "Which groups is this source in?" — the whole of group management, in one dialog.
 *
 * A checklist rather than a screen because membership is a toggle and a source is usually
 * in one or two groups: a dedicated management screen would be a longer road to the same
 * two taps. Creating a group is on the same dialog for the same reason — the moment you
 * want a new group is the moment you have something to put in it, and sending the user
 * elsewhere to make an empty one first is a worse version of the same journey.
 */
@Composable
internal fun GroupPicker(
    sourceId: SourceId,
    groups: List<SourceGroup>,
    onToggle: (SourceGroup) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.groups_title)) },
        text = {
            Column {
                if (groups.isEmpty()) {
                    Text(stringResource(R.string.groups_none_yet))
                }
                LazyColumn(modifier = Modifier.heightIn(max = LIST_MAX_HEIGHT)) {
                    items(groups, key = { it.id.value }) { group ->
                        ListItem(
                            headlineContent = { Text(group.name) },
                            leadingContent = {
                                Checkbox(checked = sourceId in group, onCheckedChange = { onToggle(group) })
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.groups_new)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                // Blank names are rejected by the domain type, so the button simply cannot
                // offer to make one — better than an error explaining a rule after the fact.
                enabled = newName.isNotBlank(),
                onClick = {
                    onCreate(newName.trim())
                    newName = ""
                },
            ) { Text(stringResource(R.string.groups_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

private val LIST_MAX_HEIGHT = 280.dp
