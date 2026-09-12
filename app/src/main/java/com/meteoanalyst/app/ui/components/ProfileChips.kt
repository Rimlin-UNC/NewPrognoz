package com.meteoanalyst.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meteoanalyst.app.domain.ProProfile
import com.meteoanalyst.app.ui.theme.AccentCyan

/**
 * Выбор профессионального профиля (кровельщик/лётчик/рыбак/…).
 * Профиль определяет набор критичных порогов и флагов.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileChipsRow(
    selected: ProProfile,
    onSelect: (ProProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp)
    ) {
        items(ProProfile.entries) { profile ->
            FilterChip(
                selected = profile == selected,
                onClick = { onSelect(profile) },
                label = { Text("${profile.emoji} ${profile.title}") },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                    selectedContainerColor = AccentCyan.copy(alpha = 0.25f),
                    selectedLabelColor = AccentCyan
                )
            )
        }
    }
}
