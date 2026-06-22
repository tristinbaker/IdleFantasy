package com.fantasyidler.data.model

enum class TownBonusType { WORKER_XP, GUILD_QUEST_REDUCTION, BLESSING_DURATION, CARNIVAL_GAMES }

data class TownBuildingTier(
    val constructionLevelRequired: Int,
    val coinCost: Long,
    val materials: Map<String, Int>,
)

data class TownBuildingDef(
    val key: String,
    val bonusType: TownBonusType,
    val tiers: List<TownBuildingTier>,
)

object TownBuildings {
    val INN = TownBuildingDef(
        key = "inn",
        bonusType = TownBonusType.WORKER_XP,
        tiers = listOf(
            TownBuildingTier(20,   50_000, mapOf("plank" to 200, "oak_plank" to 100, "iron_nail" to 500)),
            TownBuildingTier(45,  250_000, mapOf("oak_plank" to 500, "willow_plank" to 200, "steel_nail" to 1_500)),
            TownBuildingTier(70, 1_000_000, mapOf("willow_plank" to 1_000, "maple_plank" to 1_000, "mithril_nail" to 3_000)),
        ),
    )

    val GUILD_HALL = TownBuildingDef(
        key = "guild_hall",
        bonusType = TownBonusType.GUILD_QUEST_REDUCTION,
        tiers = listOf(
            TownBuildingTier(25,   75_000, mapOf("oak_plank" to 300, "iron_nail" to 600)),
            TownBuildingTier(50,  350_000, mapOf("willow_plank" to 600, "steel_nail" to 1_500)),
            TownBuildingTier(75, 1_500_000, mapOf("maple_plank" to 1_500, "yew_plank" to 500, "mithril_nail" to 3_000)),
        ),
    )

    val CHURCH = TownBuildingDef(
        key = "church",
        bonusType = TownBonusType.BLESSING_DURATION,
        tiers = listOf(
            TownBuildingTier(30,   100_000, mapOf("oak_plank" to 200, "carved_stone" to 400, "steel_nail" to 500)),
            TownBuildingTier(55,   500_000, mapOf("willow_plank" to 500, "stone_block" to 600, "steel_nail" to 1_500)),
            TownBuildingTier(80, 2_000_000, mapOf("yew_plank" to 800, "stone_block" to 1_000, "mithril_nail" to 3_000)),
        ),
    )

    val FAIRGROUNDS = TownBuildingDef(
        key = "fairgrounds",
        bonusType = TownBonusType.CARNIVAL_GAMES,
        tiers = listOf(
            TownBuildingTier(35,  150_000, mapOf("oak_plank" to 250, "willow_plank" to 100, "steel_nail" to 600)),
            TownBuildingTier(55,  600_000, mapOf("willow_plank" to 600, "maple_plank" to 200, "steel_nail" to 1_500)),
            TownBuildingTier(75, 1_500_000, mapOf("maple_plank" to 800, "yew_plank" to 400, "mithril_nail" to 2_500)),
        ),
    )

    val ALL = listOf(INN, GUILD_HALL, CHURCH, FAIRGROUNDS)
    private val BY_KEY = ALL.associateBy { it.key }
    fun byKey(key: String): TownBuildingDef? = BY_KEY[key]
}
