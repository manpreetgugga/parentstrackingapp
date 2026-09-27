package com.parentstracker.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.parentstracker.app.data.RELATIONS

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RelationChips(
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    options: List<String> = RELATIONS,
) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { relation ->
            FilterChip(
                selected = selected == relation,
                onClick = { onSelect(relation) },
                label = { Text(relation) },
            )
        }
    }
}
