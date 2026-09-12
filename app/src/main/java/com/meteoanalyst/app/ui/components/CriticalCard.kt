package com.meteoanalyst.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meteoanalyst.app.domain.CriticalFlag
import com.meteoanalyst.app.domain.ProProfile
import com.meteoanalyst.app.ui.theme.GoodGreen
import com.meteoanalyst.app.ui.theme.WarnRed

/**
 * Карточка критичных условий для выбранного профиля:
 * «Можно ли работать/летать/ловить в ближайшие часы».
 */
@Composable
fun CriticalConditionsCard(
    profile: ProProfile,
    flags: List<CriticalFlag>,
    hoursWindow: Int,
    modifier: Modifier = Modifier
) {
    val hasCritical = flags.any { it.critical }
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (hasCritical) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (hasCritical) WarnRed else GoodGreen,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = when {
                    hasCritical -> "Опасные условия · ${profile.title}"
                    flags.isEmpty() -> "Условия в норме · ${profile.title}"
                    else -> "Внимание · ${profile.title}"
                },
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "ближ. $hoursWindow ч",
                style = MaterialTheme.typography.labelMedium,
                color = com.meteoanalyst.app.ui.theme.TextTertiary
            )
        }
        if (flags.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            flags.forEach { flag ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(vertical = 3.dp)
                ) {
                    Text(
                        text = if (flag.critical) "•" else "·",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (flag.critical) WarnRed else com.meteoanalyst.app.ui.theme.AccentAmber
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = flag.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = com.meteoanalyst.app.ui.theme.TextSecondary
                    )
                }
            }
        } else {
            Spacer(Modifier.height(10.dp))
            Text(
                "Критичные пороги профиля не превышены",
                style = MaterialTheme.typography.bodyMedium,
                color = com.meteoanalyst.app.ui.theme.TextSecondary
            )
        }
    }
}
