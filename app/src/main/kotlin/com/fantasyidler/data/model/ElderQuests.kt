package com.fantasyidler.data.model

/**
 * Isle main-quest chain. Twelve quests across four acts. Each quest has a stable id,
 * a predicate that runs against the player's current stats (dungeon runs, kills,
 * inventory, elder skill levels, armor pieces owned), and a linear "requires prior
 * quest complete" gate — so the acts unfold in order like a story chain.
 *
 * Completion is persistent: once a quest's predicate has been met, its id is added
 * to PlayerFlags.elderQuestsCompleted and stays there. Consuming quest materials
 * afterwards (smelting the ore, eating the fish) does not undo the completion.
 */
object ElderQuests {

    data class Snapshot(
        val dungeonRuns: Map<String, Int>,
        val enemyKills: Map<String, Int>,
        val inventory: Map<String, Int>,
    )

    data class Quest(
        val id: String,
        val roman: String,
        val actLabel: String,
        val title: String,
        val objective: String,
        val reward: String,
        /** Current progress toward [target]. */
        val counter: (Snapshot) -> Int,
        val target: Int,
    )

    private val ELDER_PIECES = listOf(
        "elder_helm", "elder_platebody", "elder_platelegs", "elder_boots",
        "elder_cape", "elder_shield", "elder_signet_ring", "elder_amulet",
    )
    private val COASTAL_PIECES = listOf("coastal_helm", "coastal_platebody", "coastal_platelegs", "coastal_boots")
    private val GROVE_PIECES   = listOf("grove_helm", "grove_platebody", "grove_platelegs", "grove_boots")
    private val VOLCANIC_PIECES = listOf("volcanic_helm", "volcanic_platebody", "volcanic_platelegs", "volcanic_boots")

    val CHAIN: List<Quest> = listOf(
        // Act I: The Landing
        Quest(
            id        = "act1_first_steps",
            roman     = "I.1",
            actLabel  = "Act I: The Landing",
            title     = "First Steps",
            objective = "Clear Beach & Cliffs 8 times so Rowan can safely walk the shore.",
            reward    = "Reveals the isle's inhabited past.",
            counter   = { it.dungeonRuns["beach_and_cliffs"] ?: 0 },
            target    = 8,
        ),
        Quest(
            id        = "act1_rowans_cache",
            roman     = "I.2",
            actLabel  = "Act I: The Landing",
            title     = "Rowan's Cache",
            objective = "Deliver 500 Mythrite Ore. Rowan needs it for her workshop repairs.",
            reward    = "Unlocks notes about the isle's unnatural geology.",
            counter   = { it.inventory["mythrite_ore"] ?: 0 },
            target    = 500,
        ),
        Quest(
            id        = "act1_first_cooking",
            roman     = "I.3",
            actLabel  = "Act I: The Landing",
            title     = "Rations for the Voyage",
            objective = "Cook 250 Tidepool Crab. Fresh food for the long trip inland.",
            reward    = "Unlocks Act II. Reveals a fragment of the Elders' history.",
            counter   = { it.inventory["tidepool_crab"] ?: 0 },
            target    = 250,
        ),

        // Act II: The Buried Library
        Quest(
            id        = "act2_cutting_vines",
            roman     = "II.1",
            actLabel  = "Act II: The Buried Library",
            title     = "Cutting the Vines",
            objective = "Clear the Ancient Forest 15 times to reach the library ruins.",
            reward    = "First glimpse of the Grand Ritual.",
            counter   = { it.dungeonRuns["ancient_forest"] ?: 0 },
            target    = 15,
        ),
        Quest(
            id        = "act2_library_salvage",
            roman     = "II.2",
            actLabel  = "Act II: The Buried Library",
            title     = "Library Salvage",
            objective = "Gather 800 Abyssal Ore to shore up the collapsing shelves.",
            reward    = "Rowan can now translate the Elders' script.",
            counter   = { it.inventory["abyssal_ore"] ?: 0 },
            target    = 800,
        ),
        Quest(
            id        = "act2_first_armor",
            roman     = "II.3",
            actLabel  = "Act II: The Buried Library",
            title     = "Match the Elders",
            objective = "Craft 4 Coastal armor pieces. The Elders wore something eerily similar.",
            reward    = "Unlocks Act III. Rowan compares the pieces to what she's read.",
            counter   = { COASTAL_PIECES.count { key -> (it.inventory[key] ?: 0) >= 1 } },
            target    = 4,
        ),

        // Act III: The Sealing Site
        Quest(
            id        = "act3_ascent",
            roman     = "III.1",
            actLabel  = "Act III: The Sealing Site",
            title     = "Volcano Ascent",
            objective = "Clear Volcano Peak 20 times to reach the ancient sealing altar.",
            reward    = "The seal's location and shape.",
            counter   = { it.dungeonRuns["volcano_peak"] ?: 0 },
            target    = 20,
        ),
        Quest(
            id        = "act3_voidsteel_study",
            roman     = "III.2",
            actLabel  = "Act III: The Sealing Site",
            title     = "Voidsteel Study",
            objective = "Gather 1200 Voidsteel Ore. Its structure matches the seal's alloy.",
            reward    = "Rowan identifies how the seal is weakening.",
            counter   = { it.inventory["voidsteel_ore"] ?: 0 },
            target    = 1200,
        ),
        Quest(
            id        = "act3_grove_gear",
            roman     = "III.3",
            actLabel  = "Act III: The Sealing Site",
            title     = "Grove Gear",
            objective = "Craft all 4 Grove armor pieces. The seal's proximity demands stronger plate.",
            reward    = "Unlocks Act IV. Rowan tells you what remains inside.",
            counter   = { GROVE_PIECES.count { key -> (it.inventory[key] ?: 0) >= 1 } },
            target    = 4,
        ),

        // Act IV: The Last Elder
        Quest(
            id        = "act4_descent",
            roman     = "IV.1",
            actLabel  = "Act IV: The Last Elder",
            title     = "Descent",
            objective = "Clear the Abyssal Depths 25 times to open the sealed chamber.",
            reward    = "The Last Elder's voice, faint at first, then clearer.",
            counter   = { it.dungeonRuns["abyssal_depths"] ?: 0 },
            target    = 25,
        ),
        Quest(
            id        = "act4_full_set",
            roman     = "IV.2",
            actLabel  = "Act IV: The Last Elder",
            title     = "Assemble the Full Elder Set",
            objective = "Craft all 8 Elder BIS armor pieces. Only a full set can survive the seal.",
            reward    = "The final revelation about the Ritual's true cost.",
            counter   = { ELDER_PIECES.count { key -> (it.inventory[key] ?: 0) >= 1 } },
            target    = 8,
        ),
        Quest(
            id        = "act4_face_last_elder",
            roman     = "IV.3",
            actLabel  = "Act IV: The Last Elder",
            title     = "Face the Last Elder",
            objective = "Defeat the Last Elder at the bottom of the Abyssal Depths.",
            reward    = "The Ancient Signet, the Isle Champion title, and Rowan's truth.",
            counter   = { it.enemyKills["last_elder"] ?: 0 },
            target    = 1,
        ),
    )

    /**
     * Display progress for a quest row (issue #2039).
     *
     * Mainland quests accumulate via QuestRepository.addProgress and never regress.
     * Isle counters read live inventory, so a validated quest would visually drop to
     * 0/500 after selling or smelting its objective even though its id stays in
     * elderQuestsCompleted. A validated quest therefore always displays [target];
     * only the next open quest shows the live counter.
     */
    fun displayProgress(quest: Quest, snapshot: Snapshot, completed: Set<String>): Int =
        if (quest.id in completed) quest.target
        else quest.counter(snapshot).coerceAtMost(quest.target)

    /**
     * Walks the chain in order and marks quests complete only if every prior quest is
     * already complete or completes in the same pass. Prevents late-game predicates
     * (owning the full Elder set, killing the Last Elder) from marking their quests
     * done before Act I is ever finished — especially on save imports where the
     * inventory already satisfies many predicates.
     */
    fun newlyCompleted(alreadyCompleted: Set<String>, snapshot: Snapshot): Set<String> {
        val newly = mutableSetOf<String>()
        for (q in CHAIN) {
            val done = q.id in alreadyCompleted || q.id in newly
            if (done) continue
            // Not done yet — only allowed to complete if predicate met AND this is the
            // very next open quest (all prior quests already complete).
            if (q.counter(snapshot) >= q.target) {
                newly += q.id
                // Continue scanning: subsequent quests may also complete in-order.
            } else {
                // First unmet quest in the chain blocks any later completion.
                break
            }
        }
        return newly
    }

    /**
     * Linear-chain unlock: quest N is only available once every prior quest is complete.
     * Returns the set of quest ids currently unlocked (visible/actionable) — including all
     * completed ones and the very next open quest.
     */
    fun unlockedIds(completed: Set<String>): Set<String> {
        val open = mutableSetOf<String>()
        for (q in CHAIN) {
            open += q.id
            if (q.id !in completed) break
        }
        return open
    }
}
