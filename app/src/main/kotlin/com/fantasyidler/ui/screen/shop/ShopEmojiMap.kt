package com.fantasyidler.ui.screen.shop

/**
 * Best-effort emoji placeholder for shop items, used when no `R.drawable.<key>`
 * exists yet. Keyed first by item key (specific items override the category),
 * then by category. Returns null when nothing matches so [EntityIcon] falls
 * back to its first-letter tier-colored disk.
 */
internal fun shopEmojiFor(categoryName: String, itemKey: String): String? {
    keyOverrides[itemKey]?.let { return it }

    val cat = categoryName.lowercase()
    return when {
        "ore"     in cat || "material" in cat -> "🪨"
        "log"     in cat || "wood"     in cat -> "🪵"
        "seed"    in cat || "farm"     in cat -> "🌱"
        "food"    in cat                      -> "🍞"
        "weapon"  in cat                      -> "⚔️"
        "armor"   in cat || "armour"   in cat -> "🛡️"
        "tool"    in cat                      -> "⛏️"
        "rune"    in cat                      -> "🔮"
        "gem"     in cat                      -> "💎"
        "potion"  in cat                      -> "🧪"
        "special" in cat                      -> "✨"
        else                                  -> categoryAgnosticEmoji(itemKey)
    }
}

/** Last-resort substring match against the item key itself. */
private fun categoryAgnosticEmoji(itemKey: String): String? {
    val k = itemKey.lowercase()
    return when {
        "boost"   in k -> "✨"
        "sword"   in k || "axe" in k || "bow" in k -> "⚔️"
        "shield"  in k -> "🛡️"
        "pickaxe" in k -> "⛏️"
        "rod"     in k || "fish" in k -> "🎣"
        "potion"  in k -> "🧪"
        "rune"    in k -> "🔮"
        "gem"     in k || "diamond" in k || "ruby" in k || "emerald" in k -> "💎"
        "bar"     in k -> "🧱"
        "ore"     in k -> "🪨"
        "log"     in k -> "🪵"
        "seed"    in k -> "🌱"
        "bone"    in k -> "🦴"
        "arrow"   in k -> "🏹"
        "raw_"    in k || "cooked" in k -> "🍖"
        else      -> null
    }
}

private val keyOverrides: Map<String, String> = mapOf(
    "xp_boost_48h"   to "⏫",
    "coal"           to "🔥",
    "rune_essence"   to "🔮",
)
