package com.fantasyidler.ui.screen.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.data.json.SkillingDungeonData
import com.fantasyidler.util.GameStrings

@Composable
internal fun NotesTab(
    skillingDungeons: Map<String, SkillingDungeonData>,
    skillingDungeonNotes: Map<String, Int>,
    unlockedDungeons: List<String>,
) {
    val SKILL_ORDER = listOf("mining", "woodcutting", "fishing", "agility", "thieving")
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { } // An empty item still has an 8dp gap from the top due to the vertical arrangement
        SKILL_ORDER.forEach { skill ->
            val dungeons = skillingDungeons.entries
                .filter { (_, d) -> d.skill == skill }
                .sortedBy { (_, d) -> d.levelRequired }
            if (dungeons.isNotEmpty()) {
                item {
                    Text(
                        text = GameStrings.skillName(LocalContext.current, skill),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                dungeons.forEach { (key, dungeon) ->
                    val notesFound = skillingDungeonNotes[key] ?: 0
                    if (notesFound > 0) {
                        item(key = key) {
                            DungeonNotesCard(
                                dungeonKey = key,
                                dungeon = dungeon,
                                notesFound = notesFound,
                                combatDungeonUnlocked = unlockedDungeons.contains(dungeon.unlockDungeon),
                            )
                        }
                    } else {
                        item(key = "$key-locked") {
                            Text(
                                text = "${
                                    GameStrings.skillingDungeonName(
                                        LocalContext.current,
                                        key,
                                        dungeon.displayName
                                    )
                                }: ???",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun DungeonNotesCard(
    dungeonKey: String,
    dungeon: SkillingDungeonData,
    notesFound: Int,
    combatDungeonUnlocked: Boolean,
) {
    val context = LocalContext.current
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = GameStrings.skillingDungeonName(context, dungeonKey, dungeon.displayName),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            val revealedNotes =
                dungeon.noteTexts.take(notesFound.coerceAtMost(dungeon.noteTexts.size))
            revealedNotes.forEachIndexed { index, text ->
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(20.dp),
                    )
                    Text(
                        text = GameStrings.skillingDungeonNote(context, dungeonKey, index, text),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = FontStyle.Italic,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val remaining = dungeon.noteThreshold - notesFound
            if (remaining > 0) {
                repeat(remaining) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "???",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }
            }
            if (combatDungeonUnlocked) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.expedition_dungeon_unlocked),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}