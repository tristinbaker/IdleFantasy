package com.fantasyidler.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.data.model.MainStory
import com.fantasyidler.repository.StoryRepository
import com.fantasyidler.ui.viewmodel.StoryUiState
import com.fantasyidler.ui.viewmodel.StoryViewModel
import com.fantasyidler.ui.viewmodel.TitleCatalog
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

@Composable
private fun chapterTitle(chapter: MainStory.Chapter): String =
    chapter.titleRes?.let { stringResource(it) }
        ?: GameStrings.dungeonName(LocalContext.current, chapter.dungeonKey.orEmpty())

@Composable
private fun chapterHeading(index: Int): String {
    val chapter = MainStory.CHAPTERS[index]
    return if (index == 0) chapterTitle(chapter)
    else stringResource(R.string.story_chapter_number, index) + ": " + chapterTitle(chapter)
}

// ---------------------------------------------------------------------------
// Note dialog
// ---------------------------------------------------------------------------

/**
 * One story note, paged. [onReadLater] null = reread mode from the Story tab (no
 * "Read later", and dismissing just closes).
 */
@Composable
fun StoryNoteDialog(
    noteId: String,
    onClose: () -> Unit,
    onReadLater: (() -> Unit)?,
) {
    val note = MainStory.noteById(noteId)
    if (note == null) {
        // A note id this build no longer knows (older save): drop it rather than show a blank.
        LaunchedEffect(noteId) { onClose() }
        return
    }
    val chapterIndex = MainStory.chapterIndexOfNote(noteId) ?: 0
    var page by remember(noteId) { mutableIntStateOf(0) }
    val current = note.pages[page.coerceIn(0, note.pages.lastIndex)]
    val isLast = page >= note.pages.lastIndex

    AlertDialog(
        onDismissRequest = { (onReadLater ?: onClose)() },
        icon  = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
        title = { Text(chapterHeading(chapterIndex), fontWeight = FontWeight.Bold) },
        text  = {
            // Keyed on page so each page opens scrolled to the top.
            val scroll = remember(noteId, page) { ScrollState(0) }
            Column(Modifier.verticalScroll(scroll)) {
                if (current.speaker == MainStory.Speaker.THADDIUS) {
                    Text(
                        text  = stringResource(R.string.story_speaker_thaddius),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(stringResource(current.text), style = MaterialTheme.typography.bodyMedium)
                if (note.pages.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text  = stringResource(R.string.story_page_indicator, page + 1, note.pages.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (isLast) onClose() else page++ }) {
                Text(stringResource(if (isLast) R.string.story_btn_close else R.string.story_btn_next))
            }
        },
        dismissButton = onReadLater?.let { later ->
            { TextButton(onClick = later) { Text(stringResource(R.string.story_btn_read_later)) } }
        },
    )
}

// ---------------------------------------------------------------------------
// App-wide host: the one-time prompt and queued pop-up notes
// ---------------------------------------------------------------------------

@Composable
fun StoryNoteHost(viewModel: StoryViewModel) {
    val state by viewModel.state.collectAsState()
    val homeDialogOpen by viewModel.homeDialogOpen.collectAsState()
    if (!state.loaded || state.onElderIsle || homeDialogOpen) return

    if (state.promptPending && !state.started) {
        if (!state.characterSetupDone) return
        AlertDialog(
            onDismissRequest = viewModel::declinePrompt,
            icon  = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
            title = { Text(stringResource(R.string.story_prompt_title), fontWeight = FontWeight.Bold) },
            text  = { Text(stringResource(R.string.story_prompt_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::begin) { Text(stringResource(R.string.story_prompt_begin)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::declinePrompt) { Text(stringResource(R.string.story_prompt_not_now)) }
            },
        )
        return
    }

    val next = if (state.popupsEnabled) state.popupQueue.firstOrNull() else null
    if (next != null) {
        StoryNoteDialog(
            noteId      = next,
            onClose     = { viewModel.markRead(next) },
            onReadLater = { viewModel.readLater(next) },
        )
        return
    }

    // Thaddius suggests naming the island in his welcome note; ask once, after it is read.
    if (state.started && !state.islandNameAsked && ISLAND_NAME_NOTE in state.read) {
        IslandNameDialog(
            initial = state.islandName,
            onSave  = viewModel::setIslandName,
            onSkip  = viewModel::skipIslandName,
        )
    }
}

private const val ISLAND_NAME_NOTE = "landing_welcome"

@Composable
private fun IslandNameDialog(
    initial: String,
    onSave: (String) -> Unit,
    onSkip: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text(stringResource(R.string.story_island_dialog_title), fontWeight = FontWeight.Bold) },
        text  = {
            Column {
                Text(stringResource(R.string.story_island_dialog_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it.take(StoryRepository.ISLAND_NAME_MAX) },
                    label         = { Text(stringResource(R.string.story_island_hint)) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.story_island_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text(stringResource(R.string.story_island_skip)) }
        },
    )
}

// ---------------------------------------------------------------------------
// Story tab (Quests screen)
// ---------------------------------------------------------------------------

@Composable
fun StoryTabContent(viewModel: StoryViewModel) {
    val state by viewModel.state.collectAsState()
    var openNote by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by rememberSaveable { mutableStateOf(false) }

    if (renaming) {
        IslandNameDialog(
            initial = state.islandName,
            onSave  = { viewModel.setIslandName(it); renaming = false },
            onSkip  = { renaming = false },
        )
    }

    openNote?.let { id ->
        StoryNoteDialog(
            noteId      = id,
            onClose     = { viewModel.markRead(id); openNote = null },
            onReadLater = null,
        )
    }

    if (!state.started) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Surface(
                shape    = RoundedCornerShape(16.dp),
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.story_begin_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.story_begin_body), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = viewModel::begin) { Text(stringResource(R.string.story_begin_button)) }
                }
            }
        }
        return
    }

    val total = MainStory.CHAPTERS.size
    val done = state.chapterIndex.coerceIn(0, total)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = done.coerceAtMost(total - 1) + 1)
    LazyColumn(
        state               = listState,
        modifier            = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Surface(
                shape    = RoundedCornerShape(16.dp),
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text     = if (state.islandName.isNotBlank()) stringResource(R.string.story_island_label, state.islandName)
                                       else stringResource(R.string.story_begin_title),
                            style    = MaterialTheme.typography.labelLarge,
                            color    = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        if (ISLAND_NAME_NOTE in state.read) {
                            TextButton(onClick = { renaming = true }) {
                                Text(stringResource(if (state.islandName.isNotBlank()) R.string.story_island_rename else R.string.story_island_name_button))
                            }
                        }
                    }
                    Text(
                        text       = stringResource(R.string.story_progress, done, total),
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { done / total.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                    if (done >= total) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.story_complete), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        itemsIndexed(MainStory.CHAPTERS, key = { _, c -> c.id }) { index, chapter ->
            StoryChapterCard(index, chapter, state, onOpenNote = { openNote = it })
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun StoryChapterCard(
    index: Int,
    chapter: MainStory.Chapter,
    state: StoryUiState,
    onOpenNote: (String) -> Unit,
) {
    val complete = index < state.chapterIndex
    val active = index == state.chapterIndex
    val locked = index > state.chapterIndex

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        complete -> Icons.Filled.Check
                        locked   -> Icons.Filled.Lock
                        else     -> Icons.AutoMirrored.Filled.MenuBook
                    },
                    contentDescription = null,
                    tint     = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text       = if (locked) stringResource(R.string.story_chapter_number, index) + ": " + stringResource(R.string.story_chapter_locked)
                                 else chapterHeading(index),
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (locked) return@Column

            if (active && chapter.objectives.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                val context = LocalContext.current
                chapter.objectives.forEach { obj ->
                    val objDone = MainStory.isObjectiveDone(chapter, obj, state.counters)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Icon(
                            imageVector        = if (objDone) Icons.Filled.Check else Icons.Outlined.Circle,
                            contentDescription = null,
                            tint               = if (objDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier           = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text  = obj.dungeonArg?.let { stringResource(obj.label, GameStrings.dungeonName(context, it)) }
                                ?: stringResource(obj.label),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                MainStory.missingItems(chapter, state.inventory).forEach { item ->
                    Text(
                        text     = stringResource(R.string.story_obj_requires, GameStrings.itemName(context, item)),
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 24.dp, top = 2.dp),
                    )
                }
                if (chapter.rewardCoins > 0 || chapter.rewardTitle != null) {
                    Spacer(Modifier.height(6.dp))
                    val titleName = chapter.rewardTitle?.let { id -> TitleCatalog.ALL.find { it.id == id }?.let { stringResource(it.nameRes) } }
                    Text(
                        text  = when {
                            titleName != null && chapter.rewardCoins > 0 ->
                                stringResource(R.string.story_reward_coins_title, chapter.rewardCoins.formatCoins(), titleName)
                            titleName != null -> stringResource(R.string.story_reward_title, titleName)
                            else -> stringResource(R.string.story_reward_coins, chapter.rewardCoins.formatCoins())
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val notes = state.delivered.filter { MainStory.chapterIndexOfNote(it) == index }
            if (notes.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Text(
                    text     = stringResource(R.string.story_notes_header),
                    style    = MaterialTheme.typography.labelMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
                notes.forEach { id ->
                    val unread = id !in state.read
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.fillMaxWidth().clickable { onOpenNote(id) }.padding(vertical = 6.dp),
                    ) {
                        Text(
                            text       = previewOf(id),
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                            maxLines   = 1,
                            modifier   = Modifier.weight(1f),
                        )
                        if (unread) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text  = stringResource(R.string.story_note_new),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** First words of a note's first page, as its row label in the Story tab. */
@Composable
private fun previewOf(noteId: String): String {
    val first = MainStory.noteById(noteId)?.pages?.firstOrNull() ?: return ""
    val text = stringResource(first.text).trimStart('“', '"')
    return if (text.length <= 48) text else text.take(48).substringBeforeLast(' ') + "…"
}
