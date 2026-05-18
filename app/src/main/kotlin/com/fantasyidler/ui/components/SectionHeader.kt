package com.fantasyidler.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fantasyidler.ui.theme.GoldPrimary
import java.util.Locale

/**
 * Section header with a short gold accent bar prefix and tracked-out uppercase
 * label — gives the dark fantasy UI a heraldic, game-y feel rather than the
 * flat "Material list section" look. Use above every grouped card region.
 *
 * @param showDivider kept for source compatibility but ignored; the gold
 *                    accent bar replaces the previous top divider.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") showDivider: Boolean = true,
) {
    Row(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            modifier = Modifier
                .size(width = 14.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(GoldPrimary),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
        )
    }
}
