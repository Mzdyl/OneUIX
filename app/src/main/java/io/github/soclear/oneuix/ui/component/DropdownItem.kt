package io.github.soclear.oneuix.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun DropdownItem(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    options: List<Pair<String, String>>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    SelectItem(
        title = title,
        modifier = modifier,
        summary = summary,
        icon = icon,
        entries = options.map { it.second },
        selectedIndex = options.indexOfFirst { it.first == selectedOption },
        onSelectedIndexChange = { index -> onOptionSelected(options[index].first) }
    )
}
