package com.fantasyidler.ui.screen.skills

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.fantasyidler.ui.viewmodel.QuestIndicator

private const val SUPERSCRIPTS = "⁰¹²³⁴⁵⁶⁷⁸⁹"

private fun superscriptCount(count: Int): String =
    if (count < 2) "" else count.toString().map { SUPERSCRIPTS[it - '0'] }.joinToString("")

/**
 * Per-category quest icons shown next to an activity or skill name: one emoji per quest
 * category, dimmed when none of that category's quests are completable, with a superscript
 * count when several quests of the same category target the same thing.
 */
@Composable
internal fun QuestIndicatorIcons(indicators: List<QuestIndicator>) {
    if (indicators.isEmpty()) return
    indicators.groupBy { it.category }.entries.sortedBy { it.key }.forEach { (_, list) ->
        val alpha = if (list.any { it.isCompletable }) 1.0f else 0.38f
        Text(
            text     = " ${list.first().emoji}${superscriptCount(list.size)}",
            style    = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.alpha(alpha),
        )
    }
}
