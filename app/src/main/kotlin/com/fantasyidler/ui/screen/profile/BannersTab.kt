package com.fantasyidler.ui.screen.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.ui.viewmodel.SeasonalBannerDisplay
import com.fantasyidler.util.drawableByName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun BannersTab(banners: List<SeasonalBannerDisplay>) {
    if (banners.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.profile_banners_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val context = LocalContext.current
    LazyVerticalGrid(
        // Adaptive so labels keep enough width to wrap on word boundaries in the
        // narrow rail layout instead of breaking mid-word (issue #1433)
        columns = GridCells.Adaptive(104.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(banners, key = { it.eventId }) { banner ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val bannerId = banner.bannerIcon?.let { context.drawableByName(it) }
                if (bannerId != null) {
                    Image(
                        painter = painterResource(bannerId),
                        contentDescription = null,
                        colorFilter = if (banner.earned) null else ColorFilter.colorMatrix(
                            ColorMatrix().apply { setToSaturation(0f) }),
                        alpha = if (banner.earned) 1f else 0.4f,
                        modifier = Modifier.height(72.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp, 72.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp)
                            ),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = banner.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (banner.earned) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    color = if (banner.earned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (banner.earned && banner.earnedAtMs != null) {
                    Text(
                        text = stringResource(
                            R.string.profile_banners_earned_on,
                            dateFormat.format(Date(banner.earnedAtMs))
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                } else if (!banner.earned) {
                    Text(
                        text = stringResource(R.string.profile_banners_unobtained),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}