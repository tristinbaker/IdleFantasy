package com.fantasyidler.ui.screen

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.ui.theme.ScaledSheetContent

// ── Face-crop region within each 64×36 sprite frame ─────────────────────────
private const val FACE_X = 13
private const val FACE_Y = 9
private const val FACE_W = 32
private const val FACE_H = 18

// ── Thumbnail display sizes ──────────────────────────────────────────────────
private val STYLE_THUMB_W = 80.dp   // hair & beard
private val STYLE_THUMB_H = 45.dp
private val EYE_THUMB_W   = 112.dp  // eyes (much larger — eye detail is subtle)
private val EYE_THUMB_H   = 63.dp

// ── Color palettes ───────────────────────────────────────────────────────────

private val SKIN_COLORS = mapOf(
    1 to Color(0xFFB88A86),
    2 to Color(0xFFAC756C),
    3 to Color(0xFF8B5E53),
    4 to Color(0xFF6F5053),
    5 to Color(0xFFA4A385),
    6 to Color(0xFF938D9B),
    7 to Color(0xFFA09B90),
    8 to Color(0xFF8A8B6F),
    9 to Color(0xFF748B80),
)

private val HAIR_COLOR_VALUES = mapOf(
    "a" to Color(0xFF974D58),
    "b" to Color(0xFFC1725F),
    "c" to Color(0xFF6D3853),
    "d" to Color(0xFF534B58),
    "e" to Color(0xFFD56E5E),
    "f" to Color(0xFF948D99),
    "g" to Color(0xFFC1B9C0),
    "h" to Color(0xFF5B70B0),
    "i" to Color(0xFF506E65),
    "j" to Color(0xFFC15683),
    "k" to Color(0xFF8A71A4),
)

private val RACES = listOf("human", "elf", "dwarf", "halfling", "gnome", "orc")

// ── Asset loading helper ─────────────────────────────────────────────────────

private fun loadAsset(context: Context, path: String): ImageBitmap? =
    try { context.assets.open(path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }
    catch (_: Exception) { null }

private fun DrawScope.blitSprite(
    bmp: ImageBitmap,
    srcFrameX: Int = 0,
    yOff: Int = 0,
) = drawImage(
    bmp,
    srcOffset = IntOffset(srcFrameX + FACE_X, FACE_Y + yOff),
    srcSize   = IntSize(FACE_W, FACE_H),
    dstOffset = IntOffset(0, 0),
    dstSize   = IntSize(size.width.toInt(), size.height.toInt()),
    filterQuality = FilterQuality.None,
)

// ── Thumbnail composables ────────────────────────────────────────────────────

@Composable
private fun SpriteThumbnail(
    bodyPath:    String?,
    featurePath: String?,
    yOff: Int = 0,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context    = LocalContext.current
    val bodyBmp    = remember(bodyPath)    { bodyPath?.let    { loadAsset(context, it) } }
    val featureBmp = remember(featurePath) { featurePath?.let { loadAsset(context, it) } }

    val border = if (selected) MaterialTheme.colorScheme.primary
                 else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Box(
        modifier = Modifier
            .size(STYLE_THUMB_W, STYLE_THUMB_H)
            .clip(RoundedCornerShape(4.dp))
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            // Hair/beard art is generic across races (unlike the body), so its crop
            // window never needs the race-specific yOff shift.
            bodyBmp?.let    { blitSprite(it, yOff = yOff) }
            featureBmp?.let { blitSprite(it) }
        }
    }
}

@Composable
private fun EyeThumbnail(
    bodyPath: String?,
    eyePath:  String?,
    yOff: Int = 0,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context  = LocalContext.current
    val bodyBmp  = remember(bodyPath) { bodyPath?.let { loadAsset(context, it) } }
    val eyesBmp  = remember(eyePath)  { eyePath?.let  { loadAsset(context, it) } }

    val border = if (selected) MaterialTheme.colorScheme.primary
                 else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Box(
        modifier = Modifier
            .size(EYE_THUMB_W, EYE_THUMB_H)
            .clip(RoundedCornerShape(4.dp))
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            // Eye art is generic across races (unlike the body), so its crop window
            // never needs the race-specific yOff shift.
            bodyBmp?.let { blitSprite(it, yOff = yOff) }
            eyesBmp?.let { blitSprite(it) }
        }
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary
                 else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 2.dp else 1.dp, border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            val lum = color.red * 0.299f + color.green * 0.587f + color.blue * 0.114f
            val tint = if (lum > 0.55f) Color.Black.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.90f)
            Icon(
                imageVector        = Icons.Filled.Check,
                contentDescription = null,
                tint               = tint,
                modifier           = Modifier.size(16.dp),
            )
        }
    }
}

// ── Section label helper ─────────────────────────────────────────────────────

@Composable
private fun AppearanceSection(label: String, content: @Composable () -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium,
         color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    content()
    Spacer(Modifier.height(14.dp))
}

// ── Race display names ────────────────────────────────────────────────────────

@Composable
private fun raceName(key: String) = when (key) {
    "human"    -> stringResource(R.string.character_race_human)
    "elf"      -> stringResource(R.string.character_race_elf)
    "dwarf"    -> stringResource(R.string.character_race_dwarf)
    "halfling" -> stringResource(R.string.character_race_halfling)
    "gnome"    -> stringResource(R.string.character_race_gnome)
    "orc"      -> stringResource(R.string.character_race_orc)
    else       -> key.replaceFirstChar { it.uppercase() }
}

// ── Main composable ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterCustomizationSheet(
    race:             String,
    initialSkin:      Int,
    initialHair:      Int,
    initialHairColor: String,
    initialEye:       Int,
    initialBeard:     Int,
    initialBeardColor: String,
    onSave:   (skin: Int, hair: Int, hairColor: String, eye: Int, beard: Int, beardColor: String, race: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedRace   by remember { mutableStateOf(race.lowercase().ifBlank { "human" }) }
    var skin           by remember { mutableIntStateOf(initialSkin) }
    var hair           by remember { mutableIntStateOf(initialHair) }
    var hairColor      by remember { mutableStateOf(initialHairColor) }
    var eye            by remember { mutableIntStateOf(initialEye) }
    var beard          by remember { mutableIntStateOf(initialBeard) }
    var beardColor     by remember { mutableStateOf(initialBeardColor) }

    val base        = "sprites/characters/generic"
    val raceKey     = selectedRace
    val isSmall     = raceKey in setOf("halfling", "gnome", "dwarf")
    val isElfGnome  = raceKey in setOf("elf", "gnome")
    val yOff        = if (isSmall) 2 else 0
    val hairFolder  = if (isElfGnome) "hair/elf_gnome" else "hair/standard"
    val beardFolder = if (isElfGnome) "beard/elf_gnome" else "beard/generic"

    val bodyPath = "$base/body/${raceKey}_skin$skin.png"

    fun hairPath(style: Int) = when {
        style == 0 -> null
        isElfGnome -> "$base/$hairFolder/Hair${style.toString().padStart(2,'0')}_eg_${hairColor}_skin$skin.png"
        else       -> "$base/$hairFolder/Hair${style.toString().padStart(2,'0')}_${hairColor}_skin$skin.png"
    }
    fun beardPath(style: Int) = when {
        style == 0 -> null
        isElfGnome -> "$base/$beardFolder/Beard${style.toString().padStart(2,'0')}_eg_$beardColor.png"
        else       -> "$base/$beardFolder/Beard${style.toString().padStart(2,'0')}_$beardColor.png"
    }
    fun eyePath(style: Int) = "$base/eyes/eyes${style.toString().padStart(2,'0')}.png"

    val skinRange = skinToneRange(selectedRace)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        dragHandle       = { BottomSheetDefaults.DragHandle() },
    ) {
        ScaledSheetContent {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text       = stringResource(R.string.appearance_title),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))

            // ── Preview ──────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 192.dp, height = 108.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            ) {
                CharacterSprite(
                    race       = selectedRace,
                    skinTone   = skin,
                    hairStyle  = hair,
                    hairColor  = hairColor,
                    eyeStyle   = eye,
                    beardStyle = beard,
                    beardColor = beardColor,
                    modifier   = Modifier.matchParentSize(),
                )
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(14.dp))

            // ── Race ─────────────────────────────────────────────────────────
            AppearanceSection(stringResource(R.string.character_race)) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding        = PaddingValues(horizontal = 4.dp),
                ) {
                    items(RACES) { r ->
                        FilterChip(
                            selected = selectedRace == r,
                            onClick  = {
                                selectedRace = r
                                val range = skinToneRange(r)
                                if (skin !in range) skin = range.first
                            },
                            label    = { Text(raceName(r)) },
                        )
                    }
                }
            }

            HorizontalDivider()
            Spacer(Modifier.height(14.dp))

            // ── Skin tone ────────────────────────────────────────────────────
            AppearanceSection(stringResource(R.string.appearance_skin_tone)) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding        = PaddingValues(horizontal = 4.dp),
                ) {
                    items(skinRange.toList()) { tone ->
                        ColorSwatch(
                            color    = SKIN_COLORS[tone] ?: Color.Gray,
                            selected = skin == tone,
                            onClick  = { skin = tone },
                        )
                    }
                }
            }

            // ── Eyes ─────────────────────────────────────────────────────────
            AppearanceSection(stringResource(R.string.appearance_eyes)) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding        = PaddingValues(horizontal = 4.dp),
                ) {
                    items((1..12).toList()) { style ->
                        EyeThumbnail(
                            bodyPath = bodyPath,
                            eyePath  = eyePath(style),
                            yOff     = yOff,
                            selected = eye == style,
                            onClick  = { eye = style },
                        )
                    }
                }
            }

            // ── Hair style ───────────────────────────────────────────────────
            AppearanceSection(stringResource(R.string.appearance_hair_style)) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding        = PaddingValues(horizontal = 4.dp),
                ) {
                    item {
                        SpriteThumbnail(
                            bodyPath    = bodyPath,
                            featurePath = null,
                            yOff        = yOff,
                            selected    = hair == 0,
                            onClick     = { hair = 0 },
                        )
                    }
                    items((1..10).toList()) { style ->
                        SpriteThumbnail(
                            bodyPath    = bodyPath,
                            featurePath = hairPath(style),
                            yOff        = yOff,
                            selected    = hair == style,
                            onClick     = { hair = style },
                        )
                    }
                }
            }

            // ── Hair color ───────────────────────────────────────────────────
            if (hair > 0) {
                AppearanceSection(stringResource(R.string.appearance_hair_color)) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding        = PaddingValues(horizontal = 4.dp),
                    ) {
                        items(HAIR_COLORS) { c ->
                            ColorSwatch(
                                color    = HAIR_COLOR_VALUES[c] ?: Color.Gray,
                                selected = hairColor == c,
                                onClick  = { hairColor = c },
                            )
                        }
                    }
                }
            }

            // ── Beard style ──────────────────────────────────────────────────
            AppearanceSection(stringResource(R.string.appearance_beard)) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding        = PaddingValues(horizontal = 4.dp),
                ) {
                    item {
                        SpriteThumbnail(
                            bodyPath    = bodyPath,
                            featurePath = null,
                            yOff        = yOff,
                            selected    = beard == 0,
                            onClick     = { beard = 0 },
                        )
                    }
                    items((1..3).toList()) { style ->
                        SpriteThumbnail(
                            bodyPath    = bodyPath,
                            featurePath = beardPath(style),
                            yOff        = yOff,
                            selected    = beard == style,
                            onClick     = { beard = style },
                        )
                    }
                }
            }

            // ── Beard color ──────────────────────────────────────────────────
            if (beard > 0) {
                AppearanceSection(stringResource(R.string.appearance_beard_color)) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding        = PaddingValues(horizontal = 4.dp),
                    ) {
                        items(BEARD_COLORS) { c ->
                            ColorSwatch(
                                color    = HAIR_COLOR_VALUES[c] ?: Color.Gray,
                                selected = beardColor == c,
                                onClick  = { beardColor = c },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick  = { onSave(skin, hair, hairColor, eye, beard, beardColor, selectedRace) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.appearance_save))
            }

            Spacer(Modifier.height(8.dp))
        }
        }
    }
}
