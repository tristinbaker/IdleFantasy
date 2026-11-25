package com.fantasyidler.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.RecentSession
import com.fantasyidler.data.model.Skills
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Issue #2111: altar visits show in Recent Activity but must not change Flow-state streaks. */
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [34])
class AltarRecentActivityFlowTest {

    private val boostRepo by lazy {
        val context: Context = ApplicationProvider.getApplicationContext()
        BoostRepository(GameDataRepository(context, Json { ignoreUnknownKeys = true }))
    }

    private fun session(skill: String) = RecentSession(skillName = skill, activityDisplayName = "", activityKey = "x")
    private fun altar(count: Int) = RecentSession(skillName = Skills.PRAYER, activityDisplayName = "", activityKey = "big_bones", altarCount = count)

    @Test
    fun `an altar visit on top does not break another skill's streak`() {
        val flags = PlayerFlags(recentSessions = listOf(altar(50), session(Skills.SMITHING), session(Skills.SMITHING)))
        assertEquals(3 * 60_000L, boostRepo.flowElapsedMs(flags, Skills.SMITHING, 60_000L))
    }

    @Test
    fun `altar visits do not extend a prayer streak`() {
        val flags = PlayerFlags(recentSessions = listOf(altar(50), altar(20), session(Skills.PRAYER)))
        assertEquals(2 * 60_000L, boostRepo.flowElapsedMs(flags, Skills.PRAYER, 60_000L))
    }
}
