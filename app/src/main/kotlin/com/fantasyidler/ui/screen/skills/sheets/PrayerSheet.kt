package com.fantasyidler.ui.screen.skills.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.R
import com.fantasyidler.data.json.BoneData
import com.fantasyidler.ui.components.foundation.BigStepper
import com.fantasyidler.ui.components.foundation.ChunkyButton
import com.fantasyidler.ui.components.foundation.ChunkyButtonVariant
import com.fantasyidler.ui.components.foundation.ChunkySheet
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import com.fantasyidler.util.formatDurationMs

/**
 * Prayer bottom sheet. Two-step flow: pick a bone in inventory, then dial in
 * the quantity to bury via [BigStepper].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerSheet(
    availableBones: Map<String, BoneData>,
    inventory: Map<String, Int>,
    prayerLevel: Int,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    onStart: (String, Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ChunkySheet(
        onDismissRequest = onDismissRequest,
        sheetState       = sheetState,
    ) {
        PrayerSheetBody(
            availableBones    = availableBones,
            inventory         = inventory,
            prayerLevel       = prayerLevel,
            isStarting        = isStarting,
            hasActiveSession  = hasActiveSession,
            isQueueFull       = isQueueFull,
            sessionDurationMs = sessionDurationMs,
            onStart           = onStart,
        )
    }
}

@Composable
internal fun PrayerSheetBody(
    availableBones: Map<String, BoneData>,
    inventory: Map<String, Int>,
    prayerLevel: Int,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    onStart: (String, Int) -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val selectedBone = selectedKey?.let { availableBones[it] }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text       = stringResource(R.string.skill_prayer_name),
            style      = tokens.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = tokens.colors.onSurface,
        )
        Spacer(Modifier.height(tokens.spacing.s))
        Text(
            text  = stringResource(R.string.skill_prayer_desc),
            style = tokens.typography.bodyMedium,
            color = tokens.colors.onSurfaceMuted,
        )
        Spacer(Modifier.height(tokens.spacing.m))
        HorizontalDivider(color = tokens.colors.primary.copy(alpha = 0.18f))

        if (selectedBone == null) {
            Spacer(Modifier.height(tokens.spacing.s))
            Text(
                text  = stringResource(R.string.skills_prayer_desc, prayerLevel),
                style = tokens.typography.bodyMedium,
                color = tokens.colors.onSurfaceMuted,
            )
            Spacer(Modifier.height(tokens.spacing.s))
            if (availableBones.isEmpty()) {
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .padding(vertical = tokens.spacing.xxl),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text  = stringResource(R.string.skills_no_bones),
                        style = tokens.typography.bodyMedium,
                        color = tokens.colors.onSurfaceMuted,
                    )
                }
            } else {
                availableBones.forEach { (key, bone) ->
                    val qty = inventory[key] ?: 0
                    BoneRow(
                        bone     = bone,
                        qty      = qty,
                        onClick  = { selectedKey = key },
                    )
                    HorizontalDivider(color = tokens.colors.primary.copy(alpha = 0.10f))
                }
            }
        } else {
            val maxQty = inventory[selectedKey] ?: 0
            BoneQuantityStep(
                bone              = selectedBone,
                selectedKey       = selectedKey ?: return@Column,
                maxQty            = maxQty,
                isStarting        = isStarting,
                hasActiveSession  = hasActiveSession,
                isQueueFull       = isQueueFull,
                sessionDurationMs = sessionDurationMs,
                onBack            = { selectedKey = null },
                onStart           = onStart,
            )
        }
    }
}

@Composable
private fun BoneRow(
    bone: BoneData,
    qty: Int,
    onClick: () -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    val selectLabel = stringResource(R.string.btn_select)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = tokens.spacing.xxl + tokens.spacing.l)
            .semantics(mergeDescendants = true) {
                contentDescription = "${bone.displayName}, $qty in inventory, $selectLabel"
            }
            .clickable(onClick = onClick)
            .padding(vertical = tokens.spacing.m + tokens.spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text  = bone.displayName,
                style = tokens.typography.bodyLarge,
                color = tokens.colors.onSurface,
            )
            Text(
                text  = stringResource(R.string.skills_bone_qty, bone.xpPerBone.toInt(), qty),
                style = tokens.typography.labelSmall,
                color = tokens.colors.onSurfaceMuted,
            )
        }
        Text(
            text  = selectLabel,
            style = tokens.typography.labelSmall,
            color = tokens.colors.primary,
        )
    }
}

@Composable
private fun BoneQuantityStep(
    bone: BoneData,
    selectedKey: String,
    maxQty: Int,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    onBack: () -> Unit,
    onStart: (String, Int) -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    val cap = maxQty.coerceAtLeast(1)
    var qty by remember(selectedKey) { mutableIntStateOf(1) }

    ChunkyButton(
        text     = stringResource(R.string.btn_back_arrow),
        onClick  = onBack,
        variant  = ChunkyButtonVariant.Ghost,
        modifier = Modifier.defaultMinSize(minHeight = tokens.spacing.xxl + tokens.spacing.l),
    )

    Text(
        text       = bone.displayName,
        style      = tokens.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color      = tokens.colors.onSurface,
    )
    Text(
        text  = stringResource(R.string.skills_bone_selected, bone.xpPerBone.toInt(), maxQty),
        style = tokens.typography.bodyMedium,
        color = tokens.colors.onSurfaceMuted,
    )
    Spacer(Modifier.height(tokens.spacing.m))

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        BigStepper(
            value         = qty,
            onValueChange = { qty = it },
            minValue      = 1,
            maxValue      = cap,
            onMax         = { qty = cap },
            onMin         = { qty = 1 },
        )
    }

    Spacer(Modifier.height(tokens.spacing.s))
    Text(
        text       = stringResource(R.string.skills_xp_total, (qty * bone.xpPerBone).toInt()),
        style      = tokens.typography.bodyMedium,
        color      = tokens.colors.primary,
        fontWeight = FontWeight.SemiBold,
        modifier   = Modifier.fillMaxWidth(),
    )
    if (sessionDurationMs > 0) {
        Text(
            text  = "~${(qty.toLong() * (sessionDurationMs / 60)).formatDurationMs()}",
            style = tokens.typography.labelSmall,
            color = tokens.colors.onSurfaceMuted,
        )
    }
    Spacer(Modifier.height(tokens.spacing.l))

    val cta = stringResource(
        if (hasActiveSession) R.string.skills_add_to_queue else R.string.btn_start_burying
    )
    ChunkyButton(
        text     = if (isStarting) stringResource(R.string.skills_loading) else cta,
        onClick  = { onStart(selectedKey, qty) },
        enabled  = !isStarting && qty > 0 && maxQty > 0 && !(hasActiveSession && isQueueFull),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = tokens.spacing.xxl + tokens.spacing.l),
    )
}

@PreviewLightDark
@Composable
private fun PreviewPrayerSheet() {
    FantasyPreviewSurface {
        PrayerSheetBody(
            availableBones = mapOf(
                "bones" to BoneData(
                    name        = "bones",
                    displayName = "Bones",
                    description = "Common bones",
                    xpPerBone   = 4.5,
                ),
                "big_bones" to BoneData(
                    name        = "big_bones",
                    displayName = "Big Bones",
                    description = "Hefty bones",
                    xpPerBone   = 15.0,
                ),
            ),
            inventory         = mapOf("bones" to 47, "big_bones" to 12),
            prayerLevel       = 22,
            isStarting        = false,
            hasActiveSession  = false,
            isQueueFull       = false,
            sessionDurationMs = 1_800_000L,
            onStart           = { _, _ -> },
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewPrayerSheetEmpty() {
    FantasyPreviewSurface {
        PrayerSheetBody(
            availableBones    = emptyMap(),
            inventory         = emptyMap(),
            prayerLevel       = 1,
            isStarting        = false,
            hasActiveSession  = false,
            isQueueFull       = false,
            sessionDurationMs = 1_800_000L,
            onStart           = { _, _ -> },
        )
    }
}
