package com.fantasyidler.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.simulator.CombatSimulator
import com.fantasyidler.ui.motion.pressScale
import com.fantasyidler.ui.theme.GoldPrimary

@Composable
fun DungeonCard(
    dungeon: DungeonData,
    unlocked: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    survivalRating: CombatSimulator.SurvivalRating? = null,
) {
    val dimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = unlocked,
                onClick = onTap,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = dungeon.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (unlocked) MaterialTheme.colorScheme.onSurface else dimColor,
            )
            Text(
                text = dungeon.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (unlocked) MaterialTheme.colorScheme.onSurfaceVariant
                        else dimColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (unlocked && survivalRating != null) {
                val (ratingText, ratingColor) = when (survivalRating) {
                    CombatSimulator.SurvivalRating.LIKELY -> "Looks manageable" to MaterialTheme.colorScheme.primary
                    CombatSimulator.SurvivalRating.RISKY -> "Risky with current setup" to MaterialTheme.colorScheme.tertiary
                    CombatSimulator.SurvivalRating.UNLIKELY -> "Likely to die" to MaterialTheme.colorScheme.error
                }
                Text(
                    text = ratingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = ratingColor,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Lv. ${dungeon.recommendedLevel}",
            style = MaterialTheme.typography.labelMedium,
            color = if (unlocked) GoldPrimary else dimColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
