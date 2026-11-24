package com.fantasyidler.ui.viewmodel

import com.fantasyidler.R
import com.fantasyidler.data.model.MainStory

data class TitleDefinition(
    val id: String,
    val nameRes: Int,
    val requirementRes: Int,
)

object TitleCatalog {
    val ALL: List<TitleDefinition> = listOf(
        TitleDefinition("master_smith",      R.string.title_master_smith_name,      R.string.title_master_smith_requirement),
        TitleDefinition("head_chef",         R.string.title_head_chef_name,         R.string.title_head_chef_requirement),
        TitleDefinition("master_miner",      R.string.title_master_miner_name,      R.string.title_master_miner_requirement),
        TitleDefinition("master_angler",     R.string.title_master_angler_name,     R.string.title_master_angler_requirement),
        TitleDefinition("master_woodcutter", R.string.title_master_woodcutter_name, R.string.title_master_woodcutter_requirement),
        TitleDefinition("master_fletcher",   R.string.title_master_fletcher_name,   R.string.title_master_fletcher_requirement),
        TitleDefinition("master_artisan",    R.string.title_master_artisan_name,    R.string.title_master_artisan_requirement),
        TitleDefinition("runemaster",        R.string.title_runemaster_name,        R.string.title_runemaster_requirement),
        TitleDefinition("master_herbalist",  R.string.title_master_herbalist_name,  R.string.title_master_herbalist_requirement),
        TitleDefinition("master_builder",    R.string.title_master_builder_name,    R.string.title_master_builder_requirement),
        TitleDefinition("devout",            R.string.title_devout_name,            R.string.title_devout_requirement),
        TitleDefinition("master_thief",      R.string.title_master_thief_name,      R.string.title_master_thief_requirement),
        TitleDefinition("flamekeeper",       R.string.title_flamekeeper_name,       R.string.title_flamekeeper_requirement),
        TitleDefinition("slayer",            R.string.title_slayer_name,            R.string.title_slayer_requirement),
        TitleDefinition("godslayer",         R.string.title_godslayer_name,         R.string.title_godslayer_requirement),
        TitleDefinition("patron_of_the_realm", R.string.title_patron_of_the_realm_name, R.string.title_patron_of_the_realm_requirement),
        TitleDefinition("warlord",           R.string.title_warlord_name,           R.string.title_warlord_requirement),
        TitleDefinition("marksman",          R.string.title_marksman_name,          R.string.title_marksman_requirement),
        TitleDefinition("archmage",          R.string.title_archmage_name,          R.string.title_archmage_requirement),
        TitleDefinition("merchant_prince",   R.string.title_merchant_prince_name,   R.string.title_merchant_prince_requirement),
        TitleDefinition("pathfinder",        R.string.title_pathfinder_name,        R.string.title_pathfinder_requirement),
        TitleDefinition("master_farmer",     R.string.title_master_farmer_name,     R.string.title_master_farmer_requirement),
        TitleDefinition("isle_champion",     R.string.title_isle_champion_name,     R.string.title_isle_champion_requirement),
        TitleDefinition(MainStory.DUSTER_TITLE, R.string.title_duster_name,         R.string.title_duster_requirement),
        TitleDefinition(MainStory.BANE_TITLE,   R.string.title_bane_of_kurzul_name, R.string.title_bane_of_kurzul_requirement),
    )
}
