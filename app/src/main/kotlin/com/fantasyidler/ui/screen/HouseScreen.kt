package com.fantasyidler.ui.screen

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.data.json.HouseSpriteRect
import com.fantasyidler.data.json.HouseTileDef
import com.fantasyidler.data.model.HouseRoom
import com.fantasyidler.repository.HouseBillLine
import com.fantasyidler.repository.HouseDirection
import com.fantasyidler.repository.HouseRepository
import com.fantasyidler.ui.viewmodel.HouseEditMode
import com.fantasyidler.ui.viewmodel.HouseUiState
import com.fantasyidler.ui.viewmodel.HouseViewModel
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.drawableByName
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatXp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import com.fantasyidler.data.json.HouseCostTier
import com.fantasyidler.data.json.HouseTilesData
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.floor

// ---------------------------------------------------------------------------
// Atlas
// ---------------------------------------------------------------------------

private var houseAtlasCache: ImageBitmap? = null

private fun loadHouseAtlas(context: Context): ImageBitmap? =
    houseAtlasCache ?: runCatching {
        context.assets.open("sprites/house_atlas.png")
            .use { BitmapFactory.decodeStream(it) }
            ?.asImageBitmap()
    }.getOrNull()?.also { houseAtlasCache = it }

private val bannerBitmapCache = mutableMapOf<String, ImageBitmap?>()

private fun loadBannerBitmap(context: Context, icon: String): ImageBitmap? =
    bannerBitmapCache.getOrPut(icon) {
        context.drawableByName(icon)?.let { id ->
            runCatching { BitmapFactory.decodeResource(context.resources, id)?.asImageBitmap() }.getOrNull()
        }
    }

// Canvas layout: GRID_SIZE columns; two extra rows on top so north-wall faces can overhang.
private const val GRID = HouseRepository.GRID_SIZE
private const val TOP_MARGIN_CELLS = 2
private const val CANVAS_ROWS = GRID + TOP_MARGIN_CELLS
// Placement grid subdivisions per cell (items snap to half cells).
private const val SUBC = HouseRepository.SUB
private const val TOP_MARGIN_P = TOP_MARGIN_CELLS * SUBC

/**
 * Whether a one-finger drag starting on the house canvas must be consumed by
 * the canvas or handed to the parent vertical scroll (issue #1953).
 *
 * At 1x zoom a pan is a no-op (offsets clamp to zero), so Select-mode drags
 * on empty ground only swallow page scrolls — hand them off. Everything that
 * edits (moving a piece, painting) or pans a zoomed view stays owned.
 */
internal fun shouldCanvasConsumeDrag(hitPiece: Boolean, placing: Boolean, zoomed: Boolean): Boolean =
    hitPiece || placing || zoomed

private val FloorDark = Color(0xFF17120E)
private val TrimWood = Color(0xFF7A5233)
private val TrimDark = Color(0xFF2A1A12)
private val GhostOk = Color(0x664CAF50)
private val GhostBad = Color(0x66E53935)

// ---------------------------------------------------------------------------
// Resident character
// ---------------------------------------------------------------------------

/**
 * A free full cell for the character to stand on: nearest to the largest room's
 * center that no furniture footprint covers. Walls (grid lines) and wall decor
 * don't block standing. Falls back to the largest room's center.
 */
private fun residentSpot(state: HouseUiState, tileDef: (String) -> HouseTileDef?): Pair<Float, Float>? {
    val rooms = state.house.rooms.sortedByDescending { it.w * it.h }
    for (room in rooms) {
        val centerX = room.x + room.w / 2f
        val centerY = room.y + room.h / 2f
        val cells = buildList {
            for (cx in room.x until room.x + room.w)
                for (cy in room.y until room.y + room.h) add(cx to cy)
        }.sortedBy { (cx, cy) ->
            abs(cx + 0.5f - centerX) + abs(cy + 0.5f - centerY)
        }
        for ((cx, cy) in cells) {
            val hx = cx * SUBC
            val hy = cy * SUBC
            val blocked = state.house.placements.any { p ->
                val def = tileDef(p.item) ?: return@any false
                if (def.wallMounted || def.anchor == "left_edge" || def.anchor == "top_edge")
                    return@any false
                p.x < hx + SUBC && hx < p.x + def.footprintW * SUBC &&
                    p.y < hy + SUBC && hy < p.y + def.footprintH * SUBC
            }
            if (!blocked) return (cx + 0.5f) to (cy + 0.5f)
        }
    }
    val r = rooms.firstOrNull() ?: return null
    return (r.x + r.w / 2f) to (r.y + r.h / 2f)
}

/** Draws the character's static frame into a DrawScope (used by the screenshot export). */
private fun DrawScope.drawResident(context: Context, state: HouseUiState, cell: Float, spot: Pair<Float, Float>) {
    val layers = characterLayerPaths(
        state.characterRace.ifBlank { "human" }, state.characterSkinTone,
        state.characterHairStyle, state.characterHairColor,
        state.characterEyeStyle, state.characterBeardStyle, state.characterBeardColor,
    )
    val h = cell * 1.8f
    val w = h * CharacterLayerPaths.FRAME_W / CharacterLayerPaths.FRAME_H
    val left = spot.first * cell - w / 2
    val top = (spot.second + TOP_MARGIN_CELLS) * cell - h
    val dstOff = IntOffset(left.toInt(), top.toInt())
    val dstSize = IntSize(w.toInt(), h.toInt())
    val srcSize = IntSize(CharacterLayerPaths.FRAME_W, CharacterLayerPaths.FRAME_H)
    val shift = IntOffset(0, if (layers.smallRace) (2f * h / CharacterLayerPaths.FRAME_H).toInt() else 0)
    fun drawPath(path: String?, shifted: Boolean) {
        path ?: return
        val bmp = loadLayer(context, path) ?: return
        drawImage(bmp, srcOffset = IntOffset(0, 0), srcSize = srcSize,
            dstOffset = if (shifted) dstOff + shift else dstOff, dstSize = dstSize,
            filterQuality = FilterQuality.None)
    }
    drawPath(layers.body, shifted = false)
    drawPath(layers.eyes, shifted = true)
    drawPath(layers.head, shifted = true)
    drawPath(layers.beard, shifted = true)
    drawPath(layers.action, shifted = false)
}

// ---------------------------------------------------------------------------
// Screenshot export
// ---------------------------------------------------------------------------

/** Renders the house to a PNG in the cache dir and opens the system share sheet. */
private fun shareHouseImage(
    context: Context,
    state: HouseUiState,
    tiles: HouseTilesData,
    atlas: ImageBitmap,
    tileDef: (String) -> HouseTileDef?,
) {
    val cell = 48f
    val w = (GRID * cell).toInt()
    val h = (CANVAS_ROWS * cell).toInt()
    val image = ImageBitmap(w, h)
    CanvasDrawScope().draw(
        Density(1f), LayoutDirection.Ltr,
        Canvas(image),
        Size(w.toFloat(), h.toFloat()),
    ) {
        drawHouseWorld(state, tiles, atlas, context, tileDef,
            selectedPlacement = null, movingIndex = null)
        residentSpot(state, tileDef)?.let { drawResident(context, state, cell, it) }
    }
    val dir = File(context.cacheDir, "images").apply { mkdirs() }
    val file = File(dir, "my_house.png")
    FileOutputStream(file).use {
        image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseScreen(
    onBack: () -> Unit = {},
    viewModel: HouseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val atlas = remember { loadHouseAtlas(context) }
    val editing = state.editing

    AppBannerEffect(state.snackbarMessage, viewModel::snackbarConsumed)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.house_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (atlas != null && !state.isLoading) {
                        if (editing) {
                            TextButton(onClick = {
                                viewModel.setEditing(false)
                            }) { Text(stringResource(R.string.house_done)) }
                        } else {
                            IconButton(onClick = {
                                shareHouseImage(context, state, viewModel.gameData.houseTiles, atlas) {
                                    viewModel.houseRepo.tileDef(it)
                                }
                            }) {
                                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.house_share))
                            }
                            TextButton(onClick = { viewModel.setEditing(true) }) {
                                Text(stringResource(R.string.house_edit))
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading || atlas == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            // Cap the canvas by the viewport height: width-driven sizing let it tower past
            // the fold on landscape tablets, where its drag handlers covered the whole
            // viewport and swallowed every scroll gesture (issue #1742).
            val heightBoundWidth = maxHeight * GRID.toFloat() / CANVAS_ROWS
            val canvasMaxWidth = if (maxWidth < heightBoundWidth) maxWidth else heightBoundWidth
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (editing) HouseHeaderRow(state, viewModel)
                HouseCanvas(state, viewModel, atlas, editing, canvasMaxWidth)
                if (editing) {
                    ModeBanner(state, viewModel)
                    BillBar(state, viewModel)
                    HousePalette(state, viewModel, atlas)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    state.selectedPlacement?.let { index ->
        PlacementSheet(index, state, viewModel)
    }
    state.selectedRoom?.let { index ->
        RoomSheet(index, state, viewModel)
    }
    if (state.groundPickerOpen && atlas != null) {
        GroundSheet(state, viewModel, atlas)
    }
    if (state.billSheetOpen) {
        BillSheet(state, viewModel)
    }
    if (state.blueprintSheetOpen) {
        BlueprintSheet(state, viewModel)
    }
    if (state.discardConfirmOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.setDiscardConfirmOpen(false) },
            title = { Text(stringResource(R.string.house_discard_confirm_title)) },
            text = { Text(stringResource(R.string.house_discard_confirm_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::discardDraft) {
                    Text(stringResource(R.string.house_discard_build), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setDiscardConfirmOpen(false) }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Header: room purchase
// ---------------------------------------------------------------------------

@Composable
private fun HouseHeaderRow(state: HouseUiState, viewModel: HouseViewModel) {
    val nextRoom = viewModel.houseRepo.nextRoomCost(state.roomCount)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = stringResource(R.string.house_rooms_owned, state.roomCount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.house_construction_level, state.constructionLevel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (nextRoom != null) {
            val cost = viewModel.discountedTier(nextRoom, 1, state)
            OutlinedButton(onClick = { viewModel.enterPlaceRoom() }) {
                Text(
                    if (state.constructionLevel >= nextRoom.level)
                        stringResource(R.string.house_add_room,
                            stringResource(R.string.house_cost_coins, cost.coins.formatCoins()))
                    else stringResource(R.string.house_add_room_locked, nextRoom.level)
                )
            }
        }
    }
}

@Composable
private fun ModeBanner(state: HouseUiState, viewModel: HouseViewModel) {
    val mode = state.mode
    val text = when (mode) {
        is HouseEditMode.PlaceItem -> stringResource(
            R.string.house_mode_place_item, viewModel.itemDisplayName(mode.key))
        HouseEditMode.PlaceRoom -> stringResource(R.string.house_mode_place_room)
        is HouseEditMode.MoveRoom -> stringResource(R.string.house_mode_move_room)
        HouseEditMode.Select -> return
    }
    val costLine = when (mode) {
        is HouseEditMode.PlaceItem -> viewModel.costSummary(mode.key, state)
        HouseEditMode.PlaceRoom -> viewModel.roomCostSummary(state)
        else -> null
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = text, style = MaterialTheme.typography.bodySmall)
                if (!costLine.isNullOrBlank()) {
                    Text(
                        text = costLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = { viewModel.cancelMode() }) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bill of sale + blueprints
// ---------------------------------------------------------------------------

@Composable
private fun BillBar(state: HouseUiState, viewModel: HouseViewModel) {
    val bill = state.bill ?: return
    val affordable = viewModel.canAffordBill(bill, state)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    !bill.isEmpty -> stringResource(R.string.house_bill_cost, bill.netCoins.formatCoins())
                    state.hasDraftChanges -> stringResource(R.string.house_bill_changes_free)
                    else -> stringResource(R.string.house_bill_no_changes)
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (!bill.isEmpty) FontWeight.Bold else null,
                color = when {
                    !bill.isEmpty && !affordable -> MaterialTheme.colorScheme.error
                    !bill.isEmpty || state.hasDraftChanges -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { viewModel.setBillSheetOpen(true) },
                enabled = !bill.isEmpty || state.hasDraftChanges,
            ) { Text(stringResource(R.string.house_bill_open)) }
            TextButton(onClick = { viewModel.setBlueprintSheetOpen(true) }) {
                Text(stringResource(R.string.house_blueprints))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillSheet(state: HouseUiState, viewModel: HouseViewModel) {
    val bill = state.bill ?: return
    val context = LocalContext.current
    val affordable = viewModel.canAffordBill(bill, state)
    ModalBottomSheet(onDismissRequest = { viewModel.setBillSheetOpen(false) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.house_bill_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (bill.lines.isEmpty()) {
                Text(
                    text = stringResource(R.string.house_bill_changes_free),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            bill.lines.forEach { line ->
                val credit = line.kind == HouseBillLine.Kind.SHRINK_CREDIT
                val label = when (line.kind) {
                    HouseBillLine.Kind.ITEM ->
                        "${viewModel.itemDisplayName(line.itemKey ?: "")} x${line.units}"
                    HouseBillLine.Kind.NEW_ROOM ->
                        stringResource(R.string.house_bill_new_room, line.roomNumber)
                    HouseBillLine.Kind.EXPAND ->
                        stringResource(R.string.house_bill_expand_room, line.roomNumber, line.units)
                    HouseBillLine.Kind.SHRINK_CREDIT ->
                        stringResource(R.string.house_bill_shrink_credit, line.roomNumber, line.units)
                }
                val costParts = buildList {
                    if (line.coins > 0) add(stringResource(R.string.house_cost_coins, line.coins.formatCoins()))
                    line.materials.forEach { (k, v) -> add("$v ${GameStrings.itemName(context, k)}") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(
                        text = (if (credit) "+" else "") + costParts.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (credit) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1.4f),
                    )
                }
            }
            val netMaterials = bill.netMaterials().filterValues { it > 0 }
            if (bill.netCoins > 0 || netMaterials.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.house_bill_materials_header),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (bill.netCoins > 0) {
                    val enough = state.coins >= bill.netCoins
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            stringResource(R.string.house_bill_coins_total, bill.netCoins.formatCoins()),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (enough) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.error,
                        )
                        Text(
                            state.coins.formatCoins(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                netMaterials.entries.sortedBy { it.key }.forEach { (key, need) ->
                    val have = state.inventory[key] ?: 0
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            GameStrings.itemName(context, key),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (have >= need) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.error,
                        )
                        Text(
                            "$have / $need",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (have >= need) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (bill.xp > 0) {
                Text(
                    text = stringResource(R.string.house_bill_xp_total, bill.xp.formatXp()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (state.constructionLevel < bill.requiredLevel) {
                Text(
                    text = stringResource(R.string.house_bill_level_required, bill.requiredLevel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.setDiscardConfirmOpen(true) },
                    enabled = state.hasDraftChanges || !bill.isEmpty,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.house_discard_build), color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = viewModel::purchaseBuild,
                    enabled = affordable && (state.hasDraftChanges || !bill.isEmpty),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(
                        if (bill.isEmpty) R.string.house_apply_changes else R.string.house_purchase_build))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlueprintSheet(state: HouseUiState, viewModel: HouseViewModel) {
    var nameDialogSlot by remember { mutableStateOf<Int?>(null) }
    var loadConfirmSlot by remember { mutableStateOf<Int?>(null) }
    ModalBottomSheet(onDismissRequest = { viewModel.setBlueprintSheetOpen(false) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.house_blueprints),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            (0 until HouseRepository.BLUEPRINT_SLOTS).forEach { slot ->
                val bp = state.blueprints.firstOrNull { it.slot == slot }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = bp?.name ?: stringResource(R.string.house_blueprint_slot_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (bp != null) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { nameDialogSlot = slot }) {
                            Text(stringResource(R.string.house_blueprint_save))
                        }
                        TextButton(onClick = { loadConfirmSlot = slot }, enabled = bp != null) {
                            Text(stringResource(R.string.house_blueprint_load))
                        }
                        TextButton(onClick = { viewModel.deleteBlueprint(slot) }, enabled = bp != null) {
                            Text(stringResource(R.string.house_blueprint_delete),
                                color = if (bp != null) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    nameDialogSlot?.let { slot ->
        var name by remember(slot) {
            mutableStateOf(state.blueprints.firstOrNull { it.slot == slot }?.name ?: "")
        }
        AlertDialog(
            onDismissRequest = { nameDialogSlot = null },
            title = { Text(stringResource(R.string.house_blueprint_save)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text(stringResource(R.string.house_blueprint_name_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveBlueprint(slot, name.trim())
                        nameDialogSlot = null
                    },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.house_blueprint_save)) }
            },
            dismissButton = {
                TextButton(onClick = { nameDialogSlot = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }
    loadConfirmSlot?.let { slot ->
        val bp = state.blueprints.firstOrNull { it.slot == slot } ?: return@let
        AlertDialog(
            onDismissRequest = { loadConfirmSlot = null },
            title = { Text(stringResource(R.string.house_blueprint_load_title)) },
            text = { Text(stringResource(R.string.house_blueprint_load_message, bp.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.loadBlueprint(slot)
                    loadConfirmSlot = null
                }) { Text(stringResource(R.string.house_blueprint_load)) }
            },
            dismissButton = {
                TextButton(onClick = { loadConfirmSlot = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// The grid canvas
// ---------------------------------------------------------------------------

@Composable
private fun HouseCanvas(state: HouseUiState, viewModel: HouseViewModel, atlas: ImageBitmap, editing: Boolean, maxCanvasWidth: Dp) {
    val context = LocalContext.current
    val tiles = viewModel.gameData.houseTiles
    var ghostCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // Placement index being dragged to a new spot; hidden from its old cell while dragging.
    var movingIndex by remember { mutableStateOf<Int?>(null) }
    val mode = state.mode

    // View transform: pinch to zoom, two-finger pan. One finger stays for editing.
    var viewScale by remember { mutableFloatStateOf(1f) }
    var viewOffset by remember { mutableStateOf(Offset.Zero) }

    // Maps a screen-space touch point back into untransformed canvas space.
    fun toContent(p: Offset, w: Int, h: Int): Offset {
        val c = Offset(w / 2f, h / 2f)
        return (p - viewOffset - c) / viewScale + c
    }

    Box(
        modifier = Modifier
            .widthIn(max = maxCanvasWidth)
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .aspectRatio(GRID.toFloat() / CANVAS_ROWS)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .pointerInput(mode, state.house, editing) {
                detectTapGestures { offset ->
                    if (!editing) return@detectTapGestures
                    val p = toContent(offset, size.width, size.height)
                    val (cx, cy) = offsetToCell(p, size.width)
                    handleCellTap(cx, cy, state, viewModel)
                }
            }
            .pointerInput(mode, state.house, editing) {
                // One-finger gestures. No-op drags (Select mode on empty ground
                // at 1x zoom) are NOT consumed so the parent vertical scroll
                // keeps working in portrait edit mode (issue #1953); the tap
                // detector below still handles taps on those gestures.
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var panning = false
                    var owned = false
                    if (!editing) {
                        // View mode: one-finger drag always pans.
                        panning = true
                        owned = shouldCanvasConsumeDrag(hitPiece = false, placing = false, zoomed = viewScale > 1f)
                    } else {
                        val (cx, cy) = offsetToCell(toContent(down.position, size.width, size.height), size.width)
                        // Dragging an existing piece always moves it, even in place mode.
                        val hit = placementIndexAt(state, viewModel, cx, cy)
                        if (hit != null) {
                            movingIndex = hit
                            ghostCell = cx to cy
                            owned = true
                        } else if (mode != HouseEditMode.Select) {
                            ghostCell = cx to cy
                            owned = true
                        } else {
                            // One-finger drag on empty ground pans the view.
                            panning = true
                            owned = shouldCanvasConsumeDrag(hitPiece = false, placing = false, zoomed = viewScale > 1f)
                        }
                    }
                    if (!owned) return@awaitEachGesture
                    val slop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    if (slop == null) {
                        ghostCell = null
                        movingIndex = null
                        return@awaitEachGesture
                    }
                    val finished = drag(down.id) { change ->
                        if (panning) {
                            val maxX = (viewScale - 1f) * size.width / 2f
                            val maxY = (viewScale - 1f) * size.height / 2f
                            viewOffset = Offset(
                                (viewOffset.x + (change.position.x - change.previousPosition.x)).coerceIn(-maxX, maxX),
                                (viewOffset.y + (change.position.y - change.previousPosition.y)).coerceIn(-maxY, maxY),
                            )
                        } else if (movingIndex != null || mode != HouseEditMode.Select) {
                            ghostCell = offsetToCell(
                                toContent(change.position, size.width, size.height), size.width)
                        }
                        change.consume()
                    }
                    if (finished) {
                        val cellPos = ghostCell
                        val moving = movingIndex
                        if (cellPos != null) {
                            val (cx, cy) = cellPos
                            if (moving != null) {
                                val def = state.house.placements.getOrNull(moving)
                                    ?.let { viewModel.houseRepo.tileDef(it.item) }
                                val ax = cx - ((def?.footprintW ?: 1) * SUBC - 1) / 2
                                val ay = cy - ((def?.footprintH ?: 1) * SUBC - 1) / 2
                                viewModel.moveItem(moving, ax, ay)
                            } else if (mode != HouseEditMode.Select) {
                                val anchored = anchorForMode(mode, viewModel, cx, cy)
                                viewModel.placeAt(anchored.first, anchored.second)
                            }
                        }
                        ghostCell = null
                        movingIndex = null
                    } else {
                        ghostCell = null
                        movingIndex = null
                    }
                }
            }
            .pointerInput(Unit) {
                // Two-finger transform. Consuming multi-touch changes cancels any
                // in-flight one-finger tap/drag on the same node.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 2) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid()
                            val newScale = (viewScale * zoom).coerceIn(1f, 4f)
                            val c = Offset(size.width / 2f, size.height / 2f)
                            // Keep the content under the fingers stationary while scaling.
                            viewOffset = centroid - (centroid - viewOffset - c) * (newScale / viewScale) - c + pan
                            viewScale = newScale
                            val maxX = (viewScale - 1f) * size.width / 2f
                            val maxY = (viewScale - 1f) * size.height / 2f
                            viewOffset = Offset(
                                viewOffset.x.coerceIn(-maxX, maxX),
                                viewOffset.y.coerceIn(-maxY, maxY),
                            )
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cellDp = maxWidth / GRID
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = viewScale
                        scaleY = viewScale
                        translationX = viewOffset.x
                        translationY = viewOffset.y
                    },
            ) {
                HouseCanvasContent(state, viewModel, atlas, context, tiles, ghostCell, movingIndex)
                if (!editing) ResidentOverlay(state, viewModel, cellDp)
            }
            // Outside the pan/zoom transform so it stays parked in the corner.
            if (editing && state.nudgeIndex != null) {
                NudgePad(
                    onNudge  = viewModel::nudgeSelected,
                    onDone   = viewModel::exitNudge,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                )
            }
        }
    }
}

/** Corner arrow pad: one-unit nudges for the selected piece without a thumb covering it. */
@Composable
private fun NudgePad(onNudge: (Int, Int) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        tonalElevation = 2.dp,
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(2.dp)) {
            IconButton(onClick = { onNudge(0, -1) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onNudge(-1, 0) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                }
                IconButton(onClick = onDone, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { onNudge(1, 0) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
            IconButton(onClick = { onNudge(0, 1) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
            }
        }
    }
}

/** The player's character standing on a free cell of their largest room (view mode). */
@Composable
private fun ResidentOverlay(state: HouseUiState, viewModel: HouseViewModel, cellDp: Dp) {
    val spot = residentSpot(state) { viewModel.houseRepo.tileDef(it) } ?: return
    val spriteH = cellDp * 1.8f
    val spriteW = spriteH * (64f / 36f)
    CharacterSprite(
        race       = state.characterRace.ifBlank { "human" },
        skinTone   = state.characterSkinTone,
        hairStyle  = state.characterHairStyle,
        hairColor  = state.characterHairColor,
        eyeStyle   = state.characterEyeStyle,
        beardStyle = state.characterBeardStyle,
        beardColor = state.characterBeardColor,
        modifier   = Modifier
            // offset, not padding: the sprite is wider than a cell, so a spot in the two
            // leftmost columns puts this x below zero (negative padding crashes, issue on 1.14.4).
            .offset(
                x = cellDp * spot.first - spriteW / 2,
                y = cellDp * (spot.second + TOP_MARGIN_CELLS) - spriteH,
            )
            .height(spriteH)
            .aspectRatio(64f / 36f),
    )
}

/** Draws the whole house world: ground, floors, walls, and every placement. */
private fun DrawScope.drawHouseWorld(
    state: HouseUiState,
    tiles: HouseTilesData,
    atlas: ImageBitmap,
    context: Context,
    tileDef: (String) -> HouseTileDef?,
    selectedPlacement: Int?,
    movingIndex: Int?,
) {
    val cell = size.width / GRID
    // Outdoor ground first, then floors, then wall faces (a south room's wall may
    // overhang a north room's floor, which is the intended look), then furnishings.
    tiles.grounds[state.house.ground]?.let { g ->
        for (gx in 0 until GRID) for (gy in 0 until CANVAS_ROWS) {
            drawAtlas(atlas, g, Rect(gx * cell, gy * cell, (gx + 1) * cell, (gy + 1) * cell))
        }
    }
    state.house.rooms.forEach { room -> drawFloor(room, cell, atlas, tiles.structural) }
    state.house.rooms.forEach { room -> drawWallFace(room, state.house.rooms, cell, atlas, tiles.structural) }

    // Unpurchased draft area (new rooms, expansion strips) gets a translucent tint.
    state.draftRoomTints.forEach { r ->
        drawRect(
            color = Color(0x40FFD54F),
            topLeft = Offset(r.x * cell, (r.y + TOP_MARGIN_CELLS) * cell),
            size = Size(r.w * cell, r.h * cell),
        )
    }

    val placements = state.house.placements.withIndex().sortedBy { (_, p) ->
        val def = tileDef(p.item)
        when {
            def?.wallMounted == true -> Int.MIN_VALUE
            // Table decorations draw after the tables they stand on, including a
            // decoration on the upper rows of a 2x2 table (whose bottom sorts later).
            def?.tableOnly == true -> (p.y + def.footprintH * SUBC) * 2 + 2 * SUBC + 1
            else -> (p.y + (def?.footprintH ?: 1) * SUBC) * 2
        }
    }
    placements.forEach { (index, p) ->
        if (index == movingIndex) return@forEach
        val def = tileDef(p.item) ?: return@forEach
        val room = HouseRepository.roomContainingP(
            state.house.rooms, p.x, p.y, def.footprintW, def.footprintH)
        val bannerIcon = p.item.takeIf { it.startsWith(HouseRepository.BANNER_PREFIX) }
            ?.removePrefix(HouseRepository.BANNER_PREFIX)
        if (bannerIcon != null) {
            loadBannerBitmap(context, bannerIcon)?.let { bmp ->
                drawBanner(bmp, p.x, room, cell, highlight = index == selectedPlacement)
            }
        } else {
            drawPlacement(def, p.x, p.y, room, cell, atlas,
                highlight = index == selectedPlacement,
                alpha = if (index in state.ghostPlacements) 0.55f else 1f)
        }
    }
}

@Composable
private fun HouseCanvasContent(
    state: HouseUiState,
    viewModel: HouseViewModel,
    atlas: ImageBitmap,
    context: Context,
    tiles: HouseTilesData,
    ghostCell: Pair<Int, Int>?,
    movingIndex: Int?,
) {
    val mode = state.mode
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cell = size.width / GRID
        drawHouseWorld(state, tiles, atlas, context, { viewModel.houseRepo.tileDef(it) },
            state.selectedPlacement ?: state.nudgeIndex, movingIndex)

        state.selectedRoom?.let { i ->
            state.house.rooms.getOrNull(i)?.let { room ->
                drawRect(
                    color = Color(0xFF4CAF50),
                    topLeft = Offset(room.x * cell, (room.y + TOP_MARGIN_CELLS) * cell),
                    size = Size(room.w * cell, room.h * cell),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }

        ghostCell?.let { (cx, cy) ->
            val moving = movingIndex
            if (moving != null) {
                drawMoveGhost(moving, viewModel, state, cx, cy, cell, atlas, context)
            } else {
                drawGhost(mode, viewModel, state, cx, cy, cell, atlas, context)
            }
        }
    }
}

private fun placementIndexAt(state: HouseUiState, viewModel: HouseViewModel, cx: Int, cy: Int): Int? =
    state.house.placements.withIndex().lastOrNull { (_, p) ->
        val def = viewModel.houseRepo.tileDef(p.item) ?: return@lastOrNull false
        // Wall decor renders on the wall face above its cell, so it is also
        // hittable on the two cells above its footprint row.
        val topReach = if (def.wallMounted) p.y - 2 * SUBC else p.y
        cx >= p.x && cx < p.x + def.footprintW * SUBC &&
            cy >= topReach && cy < p.y + def.footprintH * SUBC
    }?.index

/** Maps a canvas point to placement (half-cell) coordinates. */
private fun offsetToCell(offset: Offset, widthPx: Int): Pair<Int, Int> {
    val halfPx = widthPx.toFloat() / (GRID * SUBC)
    return floor(offset.x / halfPx).toInt() to (floor(offset.y / halfPx).toInt() - TOP_MARGIN_P)
}

/**
 * Centers the footprint on the touched point and returns the anchor: half-cell units
 * for item placement, full cells for room placement/moving.
 */
private fun anchorForMode(mode: HouseEditMode, viewModel: HouseViewModel, cx: Int, cy: Int): Pair<Int, Int> =
    when (mode) {
        is HouseEditMode.PlaceItem -> {
            val def = viewModel.houseRepo.tileDef(mode.key)
            val fw = (def?.footprintW ?: 1) * SUBC
            val fh = (def?.footprintH ?: 1) * SUBC
            (cx - (fw - 1) / 2) to (cy - (fh - 1) / 2)
        }
        HouseEditMode.PlaceRoom -> {
            val half = (HouseRepository.NEW_ROOM_SIZE - 1) / 2
            (cx / SUBC - half) to (cy / SUBC - half)
        }
        is HouseEditMode.MoveRoom -> {
            val room = viewModel.uiState.value.house.rooms.getOrNull(mode.index)
            (cx / SUBC - ((room?.w ?: 1) - 1) / 2) to (cy / SUBC - ((room?.h ?: 1) - 1) / 2)
        }
        HouseEditMode.Select -> cx to cy
    }

private fun handleCellTap(cx: Int, cy: Int, state: HouseUiState, viewModel: HouseViewModel) {
    when (state.mode) {
        HouseEditMode.Select -> {
            // Topmost hit wins: search placements in reverse draw order.
            val hit = placementIndexAt(state, viewModel, cx, cy)
            if (hit != null) {
                viewModel.select(placement = hit, room = null)
                return
            }
            val fx = cx / SUBC
            val fy = if (cy < 0) (cy - SUBC + 1) / SUBC else cy / SUBC
            val roomIndex = state.house.rooms.indexOfFirst {
                fx >= it.x && fx < it.x + it.w && fy >= it.y && fy < it.y + it.h
            }
            if (roomIndex >= 0) {
                viewModel.select(placement = null, room = roomIndex)
            } else if (cx in 0 until GRID * SUBC && cy in -TOP_MARGIN_P until GRID * SUBC) {
                // Tapping the outdoors opens the ground style picker.
                viewModel.setGroundPickerOpen(true)
            }
        }
        else -> {
            val (ax, ay) = anchorForMode(state.mode, viewModel, cx, cy)
            viewModel.placeAt(ax, ay)
        }
    }
}

// ---------------------------------------------------------------------------
// Draw helpers
// ---------------------------------------------------------------------------

private fun DrawScope.drawAtlas(
    atlas: ImageBitmap,
    src: HouseSpriteRect,
    dst: Rect,
    alpha: Float = 1f,
) {
    drawImage(
        image = atlas,
        srcOffset = IntOffset(src.x, src.y),
        srcSize = IntSize(src.w, src.h),
        dstOffset = IntOffset(dst.left.toInt(), dst.top.toInt()),
        dstSize = IntSize(dst.width.toInt(), dst.height.toInt()),
        alpha = alpha,
        filterQuality = FilterQuality.None,
    )
}

private fun DrawScope.drawFloor(
    room: HouseRoom,
    cell: Float,
    atlas: ImageBitmap,
    structural: Map<String, HouseSpriteRect>,
) {
    val left = room.x * cell
    val top = (room.y + TOP_MARGIN_CELLS) * cell
    val size = Size(room.w * cell, room.h * cell)
    if (room.floor == "brick") {
        val brick = structural["floor_brick"] ?: return
        for (gx in 0 until room.w) for (gy in 0 until room.h) {
            // Each 16px quadrant of the 32px brick sprite tiles one cell seamlessly.
            val src = HouseSpriteRect(brick.x + (gx % 2) * 16, brick.y + (gy % 2) * 16, 16, 16)
            drawAtlas(atlas, src, Rect(left + gx * cell, top + gy * cell, left + (gx + 1) * cell, top + (gy + 1) * cell))
        }
    } else {
        val wood = structural["floor_wood"]
        if (wood != null) {
            for (gx in 0 until room.w) for (gy in 0 until room.h) {
                val src = HouseSpriteRect(wood.x + (gx % 2) * 16, wood.y, 16, 16)
                drawAtlas(atlas, src, Rect(left + gx * cell, top + gy * cell, left + (gx + 1) * cell, top + (gy + 1) * cell))
            }
        } else {
            drawRect(FloorDark, Offset(left, top), size)
        }
    }
    // Wood trim: dark outline with a wood inner line, like the pack's framed floor plates.
    drawRect(TrimDark, Offset(left, top), size, style = Stroke(width = cell * 0.12f))
    drawRect(TrimWood, Offset(left, top), size, style = Stroke(width = cell * 0.06f))
}

private fun DrawScope.drawWallFace(
    room: HouseRoom,
    allRooms: List<HouseRoom>,
    cell: Float,
    atlas: ImageBitmap,
    structural: Map<String, HouseSpriteRect>,
) {
    val bottom = (room.y + TOP_MARGIN_CELLS) * cell
    // A column only shows a wall face on the northernmost room of that column: any
    // other room lying anywhere north of this one suppresses it.
    val covered = BooleanArray(room.w) { i ->
        val cellX = room.x + i
        allRooms.any { other ->
            other !== room && cellX >= other.x && cellX < other.x + other.w &&
                other.y < room.y
        }
    }
    for (i in 0 until room.w) {
        if (covered[i]) continue
        val key = when {
            i == 0 || covered[i - 1] -> "wall_left"
            i == room.w - 1 || covered[i + 1] -> "wall_right"
            else -> "wall_mid"
        }
        val src = structural[key] ?: continue
        val left = (room.x + i) * cell
        val wallH = src.h / 16f * cell
        drawAtlas(atlas, src, Rect(left, bottom - wallH, left + cell, bottom))
    }
}

private fun DrawScope.drawPlacement(
    def: HouseTileDef,
    x: Int,
    y: Int,
    room: HouseRoom?,
    cell: Float,
    atlas: ImageBitmap,
    highlight: Boolean = false,
    alpha: Float = 1f,
) {
    val scale = cell / 16f
    val cellP = cell / SUBC  // pixels per placement (half-cell) unit
    val dstW = def.w * scale
    val dstH = def.h * scale
    val centerX = when (def.anchor) {
        // Straddles the left grid line so a column of pieces reads as one wall.
        "left_edge" -> x * cellP
        else -> (x + def.footprintW * SUBC / 2f) * cellP
    }
    val dst = when {
        def.wallMounted -> {
            // Hangs on the wall face above the room's top row.
            val wallBottom = (((room?.y ?: 0) * SUBC) + TOP_MARGIN_P) * cellP - cell * 0.35f
            Rect(centerX - dstW / 2, wallBottom - dstH, centerX + dstW / 2, wallBottom)
        }
        def.anchor == "top_edge" -> {
            // Wall body rises above the top grid line, its shadow falls just below it.
            val bottom = (y + TOP_MARGIN_P) * cellP + cell * 0.5f
            Rect(centerX - dstW / 2, bottom - dstH, centerX + dstW / 2, bottom)
        }
        def.anchor == "table" -> {
            // Sits on the raised table surface rather than the cell's front edge.
            val bottom = (y + def.footprintH * SUBC + TOP_MARGIN_P) * cellP - cell * 0.4f
            Rect(centerX - dstW / 2, bottom - dstH, centerX + dstW / 2, bottom)
        }
        else -> {
            val bottom = (y + def.footprintH * SUBC + TOP_MARGIN_P) * cellP
            Rect(centerX - dstW / 2, bottom - dstH, centerX + dstW / 2, bottom)
        }
    }
    drawAtlas(atlas, HouseSpriteRect(def.x, def.y, def.w, def.h), dst, alpha)
    if (highlight) {
        drawRect(
            color = Color(0xFF4CAF50),
            topLeft = Offset(dst.left, dst.top),
            size = Size(dst.width, dst.height),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

/** Draws a seasonal banner hanging on the wall face above the room's top row. */
private fun DrawScope.drawBanner(
    bitmap: ImageBitmap,
    x: Int,
    room: HouseRoom?,
    cell: Float,
    highlight: Boolean = false,
    alpha: Float = 1f,
    yFallback: Int = 0,
) {
    val maxH = cell * 1.8f
    val scale = minOf(cell * 0.85f / bitmap.width, maxH / bitmap.height)
    val cellP = cell / SUBC
    val dstW = bitmap.width * scale
    val dstH = bitmap.height * scale
    val centerX = (x + SUBC * 0.5f) * cellP
    val wallBottom = (((room?.y?.times(SUBC)) ?: yFallback) + TOP_MARGIN_P) * cellP - cell * 0.15f
    val dst = Rect(centerX - dstW / 2, wallBottom - dstH, centerX + dstW / 2, wallBottom)
    drawImage(
        image = bitmap,
        srcOffset = IntOffset(0, 0),
        srcSize = IntSize(bitmap.width, bitmap.height),
        dstOffset = IntOffset(dst.left.toInt(), dst.top.toInt()),
        dstSize = IntSize(dst.width.toInt(), dst.height.toInt()),
        alpha = alpha,
    )
    if (highlight) {
        drawRect(
            color = Color(0xFF4CAF50),
            topLeft = Offset(dst.left, dst.top),
            size = Size(dst.width, dst.height),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

/** Ghost while dragging an already-placed piece to a new spot. */
private fun DrawScope.drawMoveGhost(
    index: Int,
    viewModel: HouseViewModel,
    state: HouseUiState,
    cx: Int,
    cy: Int,
    cell: Float,
    atlas: ImageBitmap,
    context: Context,
) {
    val placement = state.house.placements.getOrNull(index) ?: return
    val def = viewModel.houseRepo.tileDef(placement.item) ?: return
    val ax = cx - (def.footprintW * SUBC - 1) / 2
    val ay = cy - (def.footprintH * SUBC - 1) / 2
    val valid = viewModel.canMoveTo(index, ax, ay)
    val room = HouseRepository.roomContainingP(state.house.rooms, ax, ay, def.footprintW, def.footprintH)
    drawGhostRect(def, ax, ay, room, cell, valid)
    val bannerIcon = placement.item.takeIf { it.startsWith(HouseRepository.BANNER_PREFIX) }
        ?.removePrefix(HouseRepository.BANNER_PREFIX)
    if (bannerIcon != null) {
        loadBannerBitmap(context, bannerIcon)?.let { bmp ->
            drawBanner(bmp, ax, room, cell, alpha = 0.7f, yFallback = ay)
        }
    } else {
        drawPlacement(def, ax, ay, room, cell, atlas, alpha = 0.7f)
    }
}

/**
 * Validity tint for a placement ghost. Wall-mounted decor renders on the wall face
 * above its footprint cell, so its tint is drawn up there too.
 */
private fun DrawScope.drawGhostRect(
    def: HouseTileDef,
    ax: Int,
    ay: Int,
    room: HouseRoom?,
    cell: Float,
    valid: Boolean,
) {
    val cellP = cell / SUBC
    val color = if (valid) GhostOk else GhostBad
    if (def.wallMounted && room != null) {
        val bottom = (room.y * SUBC + TOP_MARGIN_P) * cellP - cell * 0.35f
        drawRect(
            color = color,
            topLeft = Offset(ax * cellP, bottom - cell * 1.2f),
            size = Size(def.footprintW * cell, cell * 1.2f),
        )
    } else {
        drawRect(
            color = color,
            topLeft = Offset(ax * cellP, (ay + TOP_MARGIN_P) * cellP),
            size = Size(def.footprintW * cell, def.footprintH * cell),
        )
    }
}

private fun DrawScope.drawGhost(
    mode: HouseEditMode,
    viewModel: HouseViewModel,
    state: HouseUiState,
    cx: Int,
    cy: Int,
    cell: Float,
    atlas: ImageBitmap,
    context: Context,
) {
    when (mode) {
        is HouseEditMode.PlaceItem -> {
            val def = viewModel.houseRepo.tileDef(mode.key) ?: return
            val cellP = cell / SUBC
            val (ax, ay) = anchorForMode(mode, viewModel, cx, cy)
            val valid = viewModel.canPlaceItemAt(mode.key, ax, ay)
            val room = HouseRepository.roomContainingP(
                state.house.rooms, ax, ay, def.footprintW, def.footprintH)
            drawGhostRect(def, ax, ay, room, cell, valid)
            val bannerIcon = mode.key.takeIf { it.startsWith(HouseRepository.BANNER_PREFIX) }
                ?.removePrefix(HouseRepository.BANNER_PREFIX)
            if (bannerIcon != null) {
                loadBannerBitmap(context, bannerIcon)?.let { bmp ->
                    drawBanner(bmp, ax, room, cell, alpha = 0.7f, yFallback = ay)
                }
            } else {
                drawPlacement(def, ax, ay, room, cell, atlas, alpha = 0.7f)
            }
        }
        HouseEditMode.PlaceRoom -> {
            val (ax, ay) = anchorForMode(mode, viewModel, cx, cy)
            val valid = viewModel.canPlaceRoomAt(ax, ay)
            val s = HouseRepository.NEW_ROOM_SIZE
            drawRect(
                color = if (valid) GhostOk else GhostBad,
                topLeft = Offset(ax * cell, (ay + TOP_MARGIN_CELLS) * cell),
                size = Size(s * cell, s * cell),
            )
        }
        is HouseEditMode.MoveRoom -> {
            val room = state.house.rooms.getOrNull(mode.index) ?: return
            val (ax, ay) = anchorForMode(mode, viewModel, cx, cy)
            val valid = viewModel.canMoveRoomTo(mode.index, ax, ay)
            drawRect(
                color = if (valid) GhostOk else GhostBad,
                topLeft = Offset(ax * cell, (ay + TOP_MARGIN_CELLS) * cell),
                size = Size(room.w * cell, room.h * cell),
            )
        }
        HouseEditMode.Select -> {}
    }
}

// ---------------------------------------------------------------------------
// Palette
// ---------------------------------------------------------------------------

private val CATEGORY_ORDER = listOf("furniture", "walls", "wealth", "wall", "banner")

@Composable
private fun HousePalette(state: HouseUiState, viewModel: HouseViewModel, atlas: ImageBitmap) {
    var category by remember { mutableStateOf(CATEGORY_ORDER.first()) }
    val tiles = viewModel.gameData.houseTiles

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(CATEGORY_ORDER, key = { it }) { cat ->
            FilterChip(
                selected = category == cat,
                onClick = { category = cat },
                label = { Text(houseCategoryLabel(cat)) },
            )
        }
    }

    if (category == "banner") {
        if (state.earnedBanners.isEmpty()) {
            Text(
                text = stringResource(R.string.house_banners_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.earnedBanners, key = { it.bannerIcon ?: it.eventId }) { banner ->
                    BannerPaletteCard(banner.bannerIcon ?: return@items, state, viewModel)
                }
            }
        }
        return
    }

    // Big categories split into named subsections (by key prefix) plus "Other".
    val subSections: List<Pair<Int, String>> = when (category) {
        "furniture" -> listOf(
            R.string.house_sub_beds to "bed_",
            R.string.house_sub_bookshelves to "bookshelf_",
            R.string.house_sub_tables to "table_",
            R.string.house_sub_seating to "chair_",
            R.string.house_sub_table_decor to "tabledecor_",
            R.string.house_sub_nightstands to "nightstand_",
            R.string.house_sub_wardrobes to "wardrobe_",
        )
        "wall" -> listOf(
            R.string.house_sub_paintings to "wall_art_",
            R.string.house_sub_wall_shelves to "wallshelf_",
            R.string.house_sub_quest_boards to "questboard_",
        )
        else -> emptyList()
    }
    // Index into subSections; subSections.size means "Other" (no prefix matched).
    var subIndex by remember(category) { mutableStateOf(0) }
    if (subSections.isNotEmpty()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(subSections.size + 1, key = { it }) { i ->
                FilterChip(
                    selected = subIndex == i,
                    onClick = { subIndex = i },
                    label = {
                        Text(stringResource(
                            if (i < subSections.size) subSections[i].first
                            else R.string.house_sub_other))
                    },
                )
            }
        }
    }

    val items = tiles.items.entries
        .filter { (key, def) ->
            if (def.category != category || def.hidden) return@filter false
            if (subSections.isEmpty()) return@filter true
            if (subIndex < subSections.size) key.startsWith(subSections[subIndex].second)
            else subSections.none { key.startsWith(it.second) }
        }
        .sortedBy { it.value.levelRequired }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.key }) { (key, def) ->
            PaletteCard(key, def, state, viewModel, atlas)
        }
    }
}

@Composable
private fun houseCategoryLabel(category: String): String = stringResource(
    when (category) {
        "walls" -> R.string.house_cat_walls
        "wealth" -> R.string.house_cat_wealth
        "wall" -> R.string.house_cat_wall
        "banner" -> R.string.house_cat_banner
        else -> R.string.house_cat_furniture
    }
)

@Composable
private fun BannerPaletteCard(icon: String, state: HouseUiState, viewModel: HouseViewModel) {
    val context = LocalContext.current
    val key = HouseRepository.BANNER_PREFIX + icon
    val placed = state.house.placements.any { it.item == key }
    val selected = (state.mode as? HouseEditMode.PlaceItem)?.key == key
    val resId = remember(icon) { context.drawableByName(icon) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                if (selected) viewModel.cancelMode() else viewModel.enterPlaceItem(key)
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp),
        ) {
            if (resId != null) {
                Image(
                    painter = painterResource(resId),
                    contentDescription = null,
                    modifier = Modifier.height(44.dp),
                )
            } else {
                Box(Modifier.size(44.dp))
            }
            Text(
                text = viewModel.itemDisplayName(key),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (placed) stringResource(R.string.house_banner_placed)
                       else stringResource(R.string.house_free),
                style = MaterialTheme.typography.labelSmall,
                color = if (placed) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PaletteCard(
    key: String,
    def: HouseTileDef,
    state: HouseUiState,
    viewModel: HouseViewModel,
    atlas: ImageBitmap,
) {
    val locked = state.constructionLevel < def.levelRequired
    val stored = state.house.storage[key] ?: 0
    val cost = viewModel.itemCost(def, state)
    val affordable = stored > 0 || viewModel.canAfford(cost, state)
    val selected = (state.mode as? HouseEditMode.PlaceItem)?.key == key

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                if (selected) viewModel.cancelMode() else viewModel.enterPlaceItem(key)
            }
            .let {
                if (selected) it.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                else it
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp),
        ) {
            Canvas(Modifier.size(44.dp)) {
                val scale = minOf(size.width / def.w, size.height / def.h)
                val w = def.w * scale
                val h = def.h * scale
                drawImage(
                    image = atlas,
                    srcOffset = IntOffset(def.x, def.y),
                    srcSize = IntSize(def.w, def.h),
                    dstOffset = IntOffset(
                        ((size.width - w) / 2).toInt(),
                        ((size.height - h) / 2).toInt(),
                    ),
                    dstSize = IntSize(w.toInt(), h.toInt()),
                    alpha = if (locked && stored == 0) 0.35f else 1f,
                    filterQuality = FilterQuality.None,
                )
            }
            Text(
                text = viewModel.itemDisplayName(key),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val context = LocalContext.current
            val costColor = when {
                stored > 0 -> MaterialTheme.colorScheme.tertiary
                locked || !affordable -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            when {
                stored > 0 -> Text(
                    text = stringResource(R.string.house_stored_count, stored),
                    style = MaterialTheme.typography.labelSmall, color = costColor, maxLines = 1,
                )
                locked -> Text(
                    text = stringResource(R.string.house_level_short, def.levelRequired),
                    style = MaterialTheme.typography.labelSmall, color = costColor, maxLines = 1,
                )
                else -> {
                    // Full itemized cost: coins line plus one line per material.
                    if (cost.coins > 0) Text(
                        text = stringResource(R.string.house_cost_coins, cost.coins.formatCoins()),
                        style = MaterialTheme.typography.labelSmall, color = costColor, maxLines = 1,
                    )
                    cost.materials.forEach { (k, v) ->
                        Text(
                            text = "$v ${GameStrings.itemName(context, k)}",
                            style = MaterialTheme.typography.labelSmall, color = costColor,
                            textAlign = TextAlign.Center, maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sheets
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlacementSheet(
    index: Int,
    state: HouseUiState,
    viewModel: HouseViewModel,
) {
    val placement = state.house.placements.getOrNull(index) ?: return
    val isBanner = placement.item.startsWith(HouseRepository.BANNER_PREFIX)
    ModalBottomSheet(onDismissRequest = { viewModel.select(null, null) }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(
                text = viewModel.itemDisplayName(placement.item),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isBanner) stringResource(R.string.house_move_hint)
                       else stringResource(R.string.house_store_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { viewModel.enterNudge() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.house_nudge_item))
            }
            Spacer(Modifier.height(8.dp))
            if (viewModel.houseRepo.tileDef(placement.item)?.rotatesTo != null) {
                OutlinedButton(onClick = { viewModel.rotateSelected() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.house_rotate_item))
                }
                Spacer(Modifier.height(8.dp))
            }
            Button(onClick = { viewModel.removeSelected() }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (isBanner) stringResource(R.string.house_remove_banner)
                    else stringResource(R.string.house_store_item)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomSheet(index: Int, state: HouseUiState, viewModel: HouseViewModel) {
    val room = state.house.rooms.getOrNull(index) ?: return
    val perCell = viewModel.houseRepo.expansionCost(index)
    var showDemolishConfirm by remember { mutableStateOf(false) }
    if (showDemolishConfirm) {
        AlertDialog(
            onDismissRequest = { showDemolishConfirm = false },
            title = { Text(stringResource(R.string.house_demolish_confirm_title)) },
            text = { Text(stringResource(R.string.house_demolish_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDemolishConfirm = false
                    viewModel.demolishSelectedRoom()
                }) { Text(stringResource(R.string.house_demolish_room)) }
            },
            dismissButton = {
                TextButton(onClick = { showDemolishConfirm = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }
    ModalBottomSheet(onDismissRequest = { viewModel.select(null, null) }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(
                text = stringResource(R.string.house_room_n, index + 1),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.house_room_size, room.w, room.h),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            var shrinkMode by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = !shrinkMode,
                    onClick = { shrinkMode = false },
                    label = { Text(stringResource(R.string.house_expand_title)) },
                )
                FilterChip(
                    selected = shrinkMode,
                    onClick = { shrinkMode = true },
                    label = { Text(stringResource(R.string.house_shrink_title)) },
                )
            }
            if (shrinkMode) {
                Text(
                    text = stringResource(R.string.house_shrink_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpandButton(R.string.house_dir_north, HouseDirection.NORTH, room.w, perCell, state, viewModel, shrinkMode, Modifier.weight(1f))
                ExpandButton(R.string.house_dir_south, HouseDirection.SOUTH, room.w, perCell, state, viewModel, shrinkMode, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpandButton(R.string.house_dir_west, HouseDirection.WEST, room.h, perCell, state, viewModel, shrinkMode, Modifier.weight(1f))
                ExpandButton(R.string.house_dir_east, HouseDirection.EAST, room.h, perCell, state, viewModel, shrinkMode, Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.house_floor_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = room.floor == "dark",
                    onClick = { viewModel.setFloor("dark") },
                    label = { Text(stringResource(R.string.house_floor_dark)) },
                )
                FilterChip(
                    selected = room.floor == "brick",
                    onClick = { viewModel.setFloor("brick") },
                    label = {
                        Text(
                            if (state.constructionLevel >= HouseRepository.BRICK_FLOOR_LEVEL)
                                stringResource(R.string.house_floor_brick)
                            else stringResource(R.string.house_floor_brick_locked, HouseRepository.BRICK_FLOOR_LEVEL)
                        )
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.enterMoveRoom() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.house_move_room))
                }
                OutlinedButton(
                    onClick = { showDemolishConfirm = true },
                    enabled = state.roomCount > 1,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.house_demolish_room),
                        color = if (state.roomCount > 1) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroundSheet(state: HouseUiState, viewModel: HouseViewModel, atlas: ImageBitmap) {
    val grounds = viewModel.gameData.houseTiles.grounds
    ModalBottomSheet(onDismissRequest = { viewModel.setGroundPickerOpen(false) }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(
                text = stringResource(R.string.house_outside_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.house_outside_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(grounds.entries.sortedBy { it.key.removePrefix("ground_").toIntOrNull() ?: 0 },
                      key = { it.key }) { (key, rect) ->
                    val selected = state.house.ground == key
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .let {
                                if (selected) it.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                else it
                            }
                            .clickable { viewModel.setGround(key) },
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawAtlas(atlas, HouseSpriteRect(rect.x, rect.y, rect.w, rect.h),
                                Rect(0f, 0f, size.width, size.height))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandButton(
    labelRes: Int,
    dir: HouseDirection,
    cells: Int,
    perCell: HouseCostTier,
    state: HouseUiState,
    viewModel: HouseViewModel,
    shrink: Boolean,
    modifier: Modifier = Modifier,
) {
    val cost = viewModel.discountedTier(perCell, cells, state)
    OutlinedButton(
        onClick = { if (shrink) viewModel.shrinkRoom(dir) else viewModel.expandRoom(dir) },
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(labelRes))
            Text(
                text = (if (shrink) "+" else "") + cost.coins.formatCoins(),
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    shrink -> MaterialTheme.colorScheme.tertiary
                    viewModel.canAfford(cost, state) -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.error
                },
            )
        }
    }
}
