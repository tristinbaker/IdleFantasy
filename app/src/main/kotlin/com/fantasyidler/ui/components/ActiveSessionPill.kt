package com.fantasyidler.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fantasyidler.BuildConfig
import com.fantasyidler.R
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.toCountdown
import kotlinx.coroutines.delay

@Composable
fun ActiveSessionPill(
    session: SkillSession,
    onAbandon: () -> Unit,
    onDebugFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val endsAt = session.endsAt
    LaunchedEffect(endsAt) {
        while (System.currentTimeMillis() < endsAt) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
        now = System.currentTimeMillis()
    }

    val isDone = session.completed || now >= endsAt

    val skillLabel = when (session.skillName) {
        "combat" -> context.getString(R.string.label_combat)
        else -> GameStrings.skillName(context, session.skillName)
    }
    val skillEmoji = GameStrings.skillEmoji(session.skillName)
    val activityLabel = session.activityKey
        .replace('_', ' ')
        .replaceFirstChar { it.uppercase() }
        .takeIf { session.activityKey.isNotEmpty() }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isDone) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = if (isDone) stringResource(R.string.label_session_complete)
                       else stringResource(R.string.label_session_active),
                style = MaterialTheme.typography.labelMedium,
                color = if (isDone) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append("$skillEmoji $skillLabel")
                    if (activityLabel != null) append(" — $activityLabel")
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isDone) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer,
            )

            if (!isDone) {
                Spacer(Modifier.height(8.dp))
                val secondsRemaining = ((endsAt - now) / 1_000L).coerceAtLeast(0L)
                AnimatedCounter(
                    value = secondsRemaining,
                    format = { endsAt.toCountdown() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(8.dp))

            Row {
                OutlinedButton(onClick = onAbandon) {
                    Text(stringResource(R.string.btn_abandon))
                }
                if (BuildConfig.DEBUG && !isDone) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onDebugFinish) {
                        Text("[Debug] Finish Now")
                    }
                }
            }
        }
    }
}

