package com.fantasyidler.ui.screen

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fantasyidler.R
import com.fantasyidler.util.GameStrings
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Replication for #1888: the Slayer shop shows `ATK` instead of `ATT`,
 * `DEF` instead of `DIF`, and `Defense 40` instead of `Difesa 40` under the
 * Italian in-app language, even though all translations exist.
 *
 * `ShopRow` hardcodes `"ATK"/"STR"/"DEF"` and builds requirements with
 * `"${skill.replaceFirstChar { it.uppercase() }}"` (locale-blind English).
 */
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [34])
class SlayerShopLabelsTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    @Config(manifest = Config.NONE, sdk = [34], qualifiers = "it")
    fun `stat format resources render Italian abbreviations`() {
        // The resources the fix relies on (same pattern as CarnivalScreen).
        val itContext: Context = ApplicationProvider.getApplicationContext()
        assertEquals("ATT +5", itContext.getString(R.string.carnival_stat_atk, 5))
        assertEquals("FRZ +5", itContext.getString(R.string.carnival_stat_str, 5))
        assertEquals("DIF +5", itContext.getString(R.string.carnival_stat_def, 5))
    }

    @Test
    @Config(manifest = Config.NONE, sdk = [34], qualifiers = "it")
    fun `skillName resolves requirement skills in Italian`() {
        val itContext: Context = ApplicationProvider.getApplicationContext()
        assertEquals("Difesa", GameStrings.skillName(itContext, "defense"))
        assertEquals("Attacco da Mischia", GameStrings.skillName(itContext, "attack"))
        assertEquals("Forza", GameStrings.skillName(itContext, "strength"))
    }

    @Test
    @Config(manifest = Config.NONE, sdk = [34], qualifiers = "it")
    fun `formatSlayerRequirements localizes skill names in Italian`() {
        val itContext: Context = ApplicationProvider.getApplicationContext()
        val text = formatSlayerRequirements(
            mapOf("defense" to 40, "attack" to 10),
        ) { GameStrings.skillName(itContext, it) }
        // Buggy logic yields "Defense 40, Attack 10".
        assertEquals("Difesa 40, Attacco da Mischia 10", text)
    }

    @Test
    @Config(manifest = Config.NONE, sdk = [34], qualifiers = "it")
    fun `slayer_requires wrapper is Italian`() {
        val itContext: Context = ApplicationProvider.getApplicationContext()
        assertEquals(
            "Necessita di: Difesa 40",
            itContext.getString(R.string.slayer_requires, "Difesa 40"),
        )
    }
}
