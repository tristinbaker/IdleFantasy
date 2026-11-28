package com.fantasyidler.ui.screen

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay

private const val FRAME_W = 64
private const val FRAME_H = 36
private const val EYE_FRAMES = 18

private val SMALL_RACES = setOf("halfling", "gnome", "dwarf")
private val HGD_RACES   = setOf("halfling", "gnome", "dwarf")
private val ELF_GNOME   = setOf("elf", "gnome")

internal fun loadLayer(context: Context, path: String): ImageBitmap? =
    try { context.assets.open(path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }
    catch (_: Exception) { null }

/** Asset paths for one static character frame, in draw order. */
internal data class CharacterLayerPaths(
    val body: String,
    val eyes: String,
    val head: String?,
    val beard: String?,
    val action: String,
    /** Small races draw eyes/hair/beard shifted down by 2 source pixels. */
    val smallRace: Boolean,
) {
    companion object {
        const val FRAME_W = 64
        const val FRAME_H = 36
    }
}

internal fun characterLayerPaths(
    race: String,
    skinTone: Int,
    hairStyle: Int,
    hairColor: String,
    eyeStyle: Int,
    beardStyle: Int,
    beardColor: String,
): CharacterLayerPaths {
    val raceKey    = race.lowercase()
    val isSmall    = raceKey in SMALL_RACES
    val isHgd      = raceKey in HGD_RACES
    val isElfGnome = raceKey in ELF_GNOME

    val base         = "sprites/characters/generic"
    val hairFolder   = if (isElfGnome) "hair/elf_gnome" else "hair/standard"
    val beardFolder  = if (isElfGnome) "beard/elf_gnome" else "beard/generic"
    val actionFolder = if (isHgd) "action/hgd" else "action/standard"
    val actionPrefix = if (isHgd) "action_generic_hgd" else "action_generic"
    val hairNum  = hairStyle.toString().padStart(2, '0')
    val eyeNum   = eyeStyle.toString().padStart(2, '0')
    val beardNum = beardStyle.toString().padStart(2, '0')

    return CharacterLayerPaths(
        body = "$base/body/${raceKey}_skin$skinTone.png",
        eyes = "$base/eyes/eyes$eyeNum.png",
        head = when {
            hairStyle == 0 -> null
            isElfGnome -> "$base/$hairFolder/Hair${hairNum}_eg_${hairColor}_skin$skinTone.png"
            else -> "$base/$hairFolder/Hair${hairNum}_${hairColor}_skin$skinTone.png"
        },
        beard = when {
            beardStyle == 0 -> null
            isElfGnome -> "$base/$beardFolder/Beard${beardNum}_eg_$beardColor.png"
            else -> "$base/$beardFolder/Beard${beardNum}_$beardColor.png"
        },
        action = "$base/$actionFolder/${actionPrefix}_skin$skinTone.png",
        smallRace = isSmall,
    )
}

@Composable
fun CharacterSprite(
    race:        String,
    skinTone:    Int,
    hairStyle:   Int,
    hairColor:   String,
    eyeStyle:    Int,
    beardStyle:  Int,
    beardColor:  String,
    modifier:    Modifier = Modifier,
) {
    val context    = LocalContext.current
    val raceKey    = race.lowercase()
    val isSmall    = raceKey in SMALL_RACES
    val isHgd      = raceKey in HGD_RACES
    val isElfGnome = raceKey in ELF_GNOME

    val base         = "sprites/characters/generic"
    val hairFolder   = if (isElfGnome) "hair/elf_gnome" else "hair/standard"
    val beardFolder  = if (isElfGnome) "beard/elf_gnome" else "beard/generic"
    val actionFolder = if (isHgd) "action/hgd" else "action/standard"
    val actionPrefix = if (isHgd) "action_generic_hgd" else "action_generic"

    fun hairNum()  = hairStyle.toString().padStart(2, '0')
    fun eyeNum()   = eyeStyle.toString().padStart(2, '0')
    fun beardNum() = beardStyle.toString().padStart(2, '0')

    val bodyPath   = remember(raceKey, skinTone) { "$base/body/${raceKey}_skin$skinTone.png" }
    val eyesPath   = remember(eyeStyle)          { "$base/eyes/eyes${eyeNum()}.png" }
    val headPath   = remember(hairStyle, hairColor, skinTone, isElfGnome) {
        if (hairStyle == 0) null
        else if (isElfGnome) "$base/$hairFolder/Hair${hairNum()}_eg_${hairColor}_skin$skinTone.png"
        else "$base/$hairFolder/Hair${hairNum()}_${hairColor}_skin$skinTone.png"
    }
    val beardPath  = remember(beardStyle, beardColor, isElfGnome) {
        if (beardStyle == 0) null
        else if (isElfGnome) "$base/$beardFolder/Beard${beardNum()}_eg_$beardColor.png"
        else "$base/$beardFolder/Beard${beardNum()}_$beardColor.png"
    }
    val actionPath = remember(raceKey, skinTone, actionFolder) { "$base/$actionFolder/${actionPrefix}_skin$skinTone.png" }

    val bodyBmp   = remember(bodyPath)   { loadLayer(context, bodyPath) }
    val eyesBmp   = remember(eyesPath)   { loadLayer(context, eyesPath) }
    val headBmp   = remember(headPath)   { headPath?.let  { loadLayer(context, it) } }
    val beardBmp  = remember(beardPath)  { beardPath?.let { loadLayer(context, it) } }
    val actionBmp = remember(actionPath) { loadLayer(context, actionPath) }

    var eyeFrame by remember { mutableIntStateOf(0) }
    StartedEffect(Unit) {
        eyeFrame = 0
        while (true) {
            delay(2000L + (Math.random() * 2000).toLong())
            for (f in 1 until EYE_FRAMES) { eyeFrame = f; delay(80L) }
            eyeFrame = 0
        }
    }

    Canvas(modifier = modifier) {
        val w       = size.width.toInt()
        val h       = size.height.toInt()
        val dst     = IntSize(w, h)
        val fullSrc = IntSize(FRAME_W, FRAME_H)
        val zero    = IntOffset.Zero
        val yOff    = if (isSmall) (2f * h / FRAME_H).toInt() else 0
        val shiftDst = IntOffset(0, yOff)

        fun draw(bmp: ImageBitmap, srcOff: IntOffset = zero, dstOff: IntOffset = zero) =
            drawImage(bmp, srcOffset = srcOff, srcSize = fullSrc, dstOffset = dstOff, dstSize = dst, filterQuality = FilterQuality.None)

        bodyBmp?.let  { draw(it) }

        eyesBmp?.let  { draw(it, srcOff = IntOffset(eyeFrame * FRAME_W, 0), dstOff = shiftDst) }
        headBmp?.let  { draw(it, dstOff = shiftDst) }
        beardBmp?.let { draw(it, dstOff = shiftDst) }

        actionBmp?.let { draw(it) }
    }
}

/** Valid skin tone range for a given race. */
fun skinToneRange(race: String): IntRange = when (race.lowercase()) {
    "elf", "gnome" -> 1..6
    "orc"          -> 6..9
    else           -> 1..4
}

val HAIR_COLORS  = ('a'..'k').map { it.toString() }
val BEARD_COLORS = HAIR_COLORS
