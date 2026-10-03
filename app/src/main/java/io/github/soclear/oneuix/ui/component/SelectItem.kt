package io.github.soclear.oneuix.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.soclear.oneuix.R

@Composable
fun SelectItem(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    entries: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = entries.getOrNull(selectedIndex).orEmpty()

    Box(modifier = modifier) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = {
                Column {
                    if (selectedLabel.isNotEmpty()) {
                        Text(selectedLabel, color = MaterialTheme.colorScheme.primary)
                    }
                    summary?.let { Text(it) }
                }
            },
            leadingContent = icon?.let { { Icon(it, null, modifier = Modifier.size(24.dp)) } },
            trailingContent = {
                Icon(ImageVector.vectorResource(R.drawable.expand_more), contentDescription = null)
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { stateDescription = selectedLabel }
                .clickable(role = Role.Button) { expanded = true }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 200.dp, max = 320.dp)
        ) {
            entries.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = {
                        RadioButton(selected = index == selectedIndex, onClick = null)
                    },
                    onClick = {
                        onSelectedIndexChange(index)
                        expanded = false
                    }
                )
            }
        }
    }
}
