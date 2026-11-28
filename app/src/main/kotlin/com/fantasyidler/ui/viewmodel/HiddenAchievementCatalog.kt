package com.fantasyidler.ui.viewmodel

import com.fantasyidler.R
import com.fantasyidler.repository.HiddenAchievements as H

data class HiddenAchievementDefinition(
    val id: String,
    val nameRes: Int,
    val descRes: Int,
    val emoji: String,
)

/** Display data for [H.ALL], in the same order (pinned by HiddenAchievementsTest). */
object HiddenAchievementCatalog {
    val ALL: List<HiddenAchievementDefinition> = listOf(
        HiddenAchievementDefinition(H.CURSED,           R.string.achievement_hidden_cursed_name,              R.string.achievement_hidden_cursed_desc,              "🌑"),
        HiddenAchievementDefinition(H.DIVINE_DENIED,    R.string.achievement_hidden_divine_denied_name,       R.string.achievement_hidden_divine_denied_desc,       "🙏"),
        HiddenAchievementDefinition(H.PET_HATER,        R.string.achievement_hidden_pet_hater_name,           R.string.achievement_hidden_pet_hater_desc,           "😾"),
        HiddenAchievementDefinition(H.BONE_DRY,         R.string.achievement_hidden_bone_dry_name,            R.string.achievement_hidden_bone_dry_desc,            "🏜️"),
        HiddenAchievementDefinition(H.VOID_STARES_BACK, R.string.achievement_hidden_void_stares_back_name,    R.string.achievement_hidden_void_stares_back_desc,    "👁️"),
        HiddenAchievementDefinition(H.GREEN_THUMB,      R.string.achievement_hidden_green_thumb_of_fate_name, R.string.achievement_hidden_green_thumb_of_fate_desc, "🥀"),
        HiddenAchievementDefinition(H.DEJA_VU,          R.string.achievement_hidden_deja_vu_name,             R.string.achievement_hidden_deja_vu_desc,             "🔁"),
        HiddenAchievementDefinition(H.BACK_TO_BACK,     R.string.achievement_hidden_back_to_back_name,        R.string.achievement_hidden_back_to_back_desc,        "⚡"),
        HiddenAchievementDefinition(H.TWICE_BLESSED,    R.string.achievement_hidden_twice_blessed_name,       R.string.achievement_hidden_twice_blessed_desc,       "🍀"),
        HiddenAchievementDefinition(H.EXTERMINATOR,     R.string.achievement_hidden_exterminator_name,        R.string.achievement_hidden_exterminator_desc,        "☠️"),
        HiddenAchievementDefinition(H.REGULAR,          R.string.achievement_hidden_regular_name,             R.string.achievement_hidden_regular_desc,             "🗝️"),
        HiddenAchievementDefinition(H.SKY_500,          R.string.achievement_hidden_sky_500_name,             R.string.achievement_hidden_sky_500_desc,             "☁️"),
        HiddenAchievementDefinition(H.SKY_1000,         R.string.achievement_hidden_sky_1000_name,            R.string.achievement_hidden_sky_1000_desc,            "🌌"),
        HiddenAchievementDefinition(H.SEEN_IT_ALL,      R.string.achievement_hidden_seen_it_all_name,         R.string.achievement_hidden_seen_it_all_desc,         "🔍"),
        HiddenAchievementDefinition(H.TITLE_COLLECTOR,  R.string.achievement_hidden_title_collector_name,     R.string.achievement_hidden_title_collector_desc,     "🎖️"),
        HiddenAchievementDefinition(H.VETERAN,          R.string.achievement_hidden_veteran_name,             R.string.achievement_hidden_veteran_desc,             "🎂"),
        HiddenAchievementDefinition(H.OSSUARY,          R.string.achievement_hidden_ossuary_name,             R.string.achievement_hidden_ossuary_desc,             "⚰️"),
        HiddenAchievementDefinition(H.HOARDER,          R.string.achievement_hidden_hoarder_name,             R.string.achievement_hidden_hoarder_desc,             "📦"),
        HiddenAchievementDefinition(H.SCROOGE,          R.string.achievement_hidden_scrooge_name,             R.string.achievement_hidden_scrooge_desc,             "💰"),
        HiddenAchievementDefinition(H.NAKED,            R.string.achievement_hidden_naked_and_afraid_name,    R.string.achievement_hidden_naked_and_afraid_desc,    "🙈"),
        HiddenAchievementDefinition(H.BRONZE_AGE,       R.string.achievement_hidden_bronze_age_name,          R.string.achievement_hidden_bronze_age_desc,          "🥉"),
        HiddenAchievementDefinition(H.IRON_WILL,        R.string.achievement_hidden_iron_will_name,           R.string.achievement_hidden_iron_will_desc,           "⛓️"),
        HiddenAchievementDefinition(H.CORPSE,           R.string.achievement_hidden_professional_corpse_name, R.string.achievement_hidden_professional_corpse_desc, "👻"),
        HiddenAchievementDefinition(H.SPEEDRUN,         R.string.achievement_hidden_speedrun_any_name,        R.string.achievement_hidden_speedrun_any_desc,        "⏱️"),
        HiddenAchievementDefinition(H.BUYERS_REMORSE,   R.string.achievement_hidden_buyers_remorse_name,      R.string.achievement_hidden_buyers_remorse_desc,      "😭"),
        HiddenAchievementDefinition(H.INDECISIVE,       R.string.achievement_hidden_indecisive_name,          R.string.achievement_hidden_indecisive_desc,          "🤔"),
        HiddenAchievementDefinition(H.JUNK_DEALER,      R.string.achievement_hidden_junk_dealer_name,         R.string.achievement_hidden_junk_dealer_desc,         "🗑️"),
        HiddenAchievementDefinition(H.FESTIVAL_GOER,    R.string.achievement_hidden_festival_goer_name,       R.string.achievement_hidden_festival_goer_desc,       "🎪"),
        HiddenAchievementDefinition(H.DEVOTED,          R.string.achievement_hidden_devoted_name,             R.string.achievement_hidden_devoted_desc,             "📅"),
        HiddenAchievementDefinition(H.ELDER_OBSESSION,  R.string.achievement_hidden_elder_obsession_name,     R.string.achievement_hidden_elder_obsession_desc,     "🐉"),
    )
}
