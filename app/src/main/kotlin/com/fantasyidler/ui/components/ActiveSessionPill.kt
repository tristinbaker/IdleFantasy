package com.fantasyidler.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.ui.components.foundation.ActiveSessionPill as FoundationActiveSessionPill

/** Thin proxy — see [com.fantasyidler.ui.components.foundation.ActiveSessionPill]. */
@Composable
fun ActiveSessionPill(
    session: SkillSession,
    onAbandon: () -> Unit,
    onDebugFinish: () -> Unit,
    modifier: Modifier = Modifier,
) = FoundationActiveSessionPill(
    session = session,
    onAbandon = onAbandon,
    onDebugFinish = onDebugFinish,
    modifier = modifier,
)
