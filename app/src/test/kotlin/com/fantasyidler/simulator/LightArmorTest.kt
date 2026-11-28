package com.fantasyidler.simulator

import com.fantasyidler.data.json.CraftingRecipe
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EnemyData
import com.fantasyidler.data.json.EquipmentData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * The 2.1.0 ranged (hide) and magic (robe) armour lines: every piece is craftable from
 * obtainable materials, and wearing the right line beats wearing plate for that style.
 */
class LightArmorTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun dataFile(name: String): File =
        listOf("src/main/assets/data/$name", "app/src/main/assets/data/$name")
            .map(::File).firstOrNull { it.exists() }
            ?: error("Could not locate asset $name from ${File(".").absolutePath}")

    private val equipment: Map<String, EquipmentData> by lazy { json.decodeFromString(dataFile("equipment.json").readText()) }
    private val recipes: Map<String, CraftingRecipe> by lazy { json.decodeFromString(dataFile("recipes/crafting.json").readText()) }
    private val enemies: Map<String, EnemyData> by lazy { json.decodeFromString(dataFile("enemies.json").readText()) }

    private val armourTypes = mapOf("ranged_armour" to "ranged", "magic_armour" to "magic")

    @Test
    fun `every hide and robe recipe makes wearable armour of its style`() {
        val armour = recipes.filterValues { it.type in armourTypes }
        assertEquals("7 tiers x 4 pieces x 2 styles", 56, armour.size)
        for ((key, recipe) in armour) {
            val item = equipment[key] ?: error("$key has a recipe but no equipment entry")
            val style = armourTypes.getValue(recipe.type)
            assertEquals(key, style, item.combatStyle)
            assertTrue(key, item.slot in setOf("head", "body", "legs", "boots"))
            val offense = if (style == "ranged") item.rangedAttackBonus else item.magicAttackBonus
            assertTrue("$key needs $style offense", (offense ?: 0) > 0)
        }
    }

    @Test
    fun `every armour material can be obtained`() {
        val dropped = Json.parseToJsonElement(dataFile("enemies.json").readText()).jsonObject.values.flatMap { enemy ->
            enemy.jsonObject["drop_table"]?.jsonArray.orEmpty().map { it.jsonObject.getValue("item").jsonPrimitive.content }
        }.toSet()
        val runes = Json.parseToJsonElement(dataFile("runes.json").readText()).jsonObject.keys
        val crafted = recipes.keys
        val inputs = recipes.filterValues { it.type in armourTypes || it.type == "material" }.values.flatMap { it.materials.keys }.toSet()
        for (material in inputs) {
            assertTrue("$material has no drop, rune or recipe source", material in dropped || material in runes || material in crafted)
        }
    }

    private fun dungeon(key: String) =
        json.decodeFromString<DungeonData>(dataFile("dungeons/$key.json").readText()).copy(safeZone = true)

    /** Total XP over [seeds] safe-zone hours: more damage means more kills means more XP. */
    private fun xpOver(seeds: IntRange, run: (Random) -> SkillSimulator.Result): Long =
        seeds.sumOf { seed -> run(Random(seed)).frames.sumOf { it.xpGain.toLong() } }

    private fun setTotal(keys: List<String>, stat: (EquipmentData) -> Int?) = keys.sumOf { stat(equipment.getValue(it)) ?: 0 }

    @Test
    fun `a level 50 ranger out-damages plate in serpentskin`() {
        val hide = listOf("serpentskin_coif", "serpentskin_body", "serpentskin_chaps", "serpentskin_boots")
        val plate = listOf("runite_full_helmet", "runite_platebody", "runite_platelegs", "runite_boots")
        val bow = equipment.getValue("yew_longbow")
        fun ranger(armour: List<String>, rng: Random) = CombatSimulator.simulateDungeon(
            dungeon = dungeon("dragon_lair"), enemies = enemies,
            playerAttack = 50, playerStrength = 50, playerDefence = 50 + setTotal(armour) { it.defenseBonus }, playerHp = 60,
            combatStyle = "ranged", playerRanged = 50,
            weaponAttackBonus = (bow.rangedAttackBonus ?: 0) + setTotal(armour) { it.rangedAttackBonus },
            rangedGearStrengthBonus = (bow.rangedStrengthBonus ?: 0) + setTotal(armour) { it.rangedStrengthBonus },
            availableArrows = mapOf("runite_arrow" to 100_000), arrowStrengthBonuses = mapOf("runite_arrow" to 49),
            random = rng,
        )
        val inHide = xpOver(1..10) { ranger(hide, it) }
        val inPlate = xpOver(1..10) { ranger(plate, it) }
        assertTrue("hide $inHide vs plate $inPlate", inHide > inPlate)
    }

    @Test
    fun `a level 50 mage out-damages plate in arcane robes`() {
        val robes = listOf("arcane_hat", "arcane_robe_top", "arcane_robe_bottom", "arcane_boots")
        val plate = listOf("runite_full_helmet", "runite_platebody", "runite_platelegs", "runite_boots")
        val staff = equipment.getValue("staff_of_death")
        fun mage(armour: List<String>, rng: Random) = CombatSimulator.simulateDungeon(
            dungeon = dungeon("dragon_lair"), enemies = enemies,
            playerAttack = 50, playerStrength = 50, playerDefence = 50 + setTotal(armour) { it.defenseBonus }, playerHp = 60,
            combatStyle = "magic", playerMagic = 50,
            weaponAttackBonus = (staff.magicAttackBonus ?: 0) + setTotal(armour) { it.magicAttackBonus },
            spellMaxHit = 20 + (staff.magicDamageBonus ?: 0) + setTotal(armour) { it.magicDamageBonus },
            random = rng,
        )
        val inRobes = xpOver(1..10) { mage(robes, it) }
        val inPlate = xpOver(1..10) { mage(plate, it) }
        assertTrue("robes $inRobes vs plate $inPlate", inRobes > inPlate)
    }
}
