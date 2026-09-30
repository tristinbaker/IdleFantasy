package com.fantasyidler.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fantasyidler.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Replication for #1864 + #1865: carnival idle games show English fallback
 * in queue/banner instead of the translated `carnival_*` strings.
 *
 * `HomeCards` queue/active/worker cards and `GameStrings.activityName()`
 * have no `"carnival"` branch, so they fall through to `itemName()` which
 * looks up `item_<key>_name` (missing) and title-cases the key in English.
 */
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [34])
class GameStringsCarnivalTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `activityName resolves carnival wizards_duel from carnival string domain`() {
        // "Wizard's Duel" (resource) vs "Wizards Duel" (toTitleCase English fallback).
        // This discriminates even under the default English locale.
        val expected = context.getString(R.string.carnival_wizards_duel)
        assertEquals(expected, GameStrings.activityName(context, "carnival", "wizards_duel"))
    }

    @Test
    fun `activityName resolves all four carnival idle games from carnival string domain`() {
        val cases = mapOf(
            "archery_range" to R.string.carnival_archery_range,
            "strongman_competition" to R.string.carnival_strongman_competition,
            "wizards_duel" to R.string.carnival_wizards_duel,
            "fishing_derby" to R.string.carnival_fishing_derby,
        )
        for ((key, resId) in cases) {
            assertEquals(
                "carnival key $key",
                context.getString(resId),
                GameStrings.activityName(context, "carnival", key),
            )
        }
    }

    @Test
    @Config(manifest = Config.NONE, sdk = [34], qualifiers = "it")
    fun `activityName resolves carnival archery_range in Italian`() {
        // Italian resource: "Campo di tiro con l'arco" vs English fallback "Archery Range".
        val itContext: Context = ApplicationProvider.getApplicationContext()
        val expected = itContext.getString(R.string.carnival_archery_range)
        assertEquals(expected, GameStrings.activityName(itContext, "carnival", "archery_range"))
    }
}
