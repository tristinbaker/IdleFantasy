package com.fantasyidler.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.ui.graphics.vector.ImageVector
import com.fantasyidler.R
import com.fantasyidler.ui.screen.CombatTabName

sealed class Screen(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
) {
    object Skills : Screen(
        route        = "skills",
        labelRes     = R.string.nav_skills,
        icon         = Icons.AutoMirrored.Outlined.ShowChart,
        selectedIcon = Icons.AutoMirrored.Filled.ShowChart,
    ) {
        const val openSkillRoute = "skills/open/{openSkill}"
        fun routeWithSkill(skill: String) = "skills/open/$skill"
    }
    object Combat : Screen(
        route        = "combat",
        labelRes     = R.string.nav_combat,
        icon         = Icons.Outlined.Shield,
        selectedIcon = Icons.Filled.Shield,
    ) {
        const val openTabRoute = "combat/tab/{tab}"
        fun startWithTab(tab: CombatTabName) = "combat/tab/${tab.name}"
        const val presetDungeonRoute = "combat/preset_dungeon/{dungeonKey}"
        fun presetDungeonRoute(key: String) = "combat/preset_dungeon/$key"
        const val presetBossRoute = "combat/preset_boss/{bossKey}"
        fun presetBossRoute(key: String) = "combat/preset_boss/$key"
    }
    object Home : Screen(
        route        = "home",
        labelRes     = R.string.nav_home,
        icon         = Icons.Outlined.Home,
        selectedIcon = Icons.Filled.Home,
    )
    object Quests : Screen(
        route        = "quests",
        labelRes     = R.string.nav_quests,
        icon         = Icons.AutoMirrored.Outlined.MenuBook,
        selectedIcon = Icons.AutoMirrored.Filled.MenuBook,
    )
    object Profile : Screen(
        route        = "profile",
        labelRes     = R.string.nav_profile,
        icon         = Icons.Outlined.AccountCircle,
        selectedIcon = Icons.Filled.AccountCircle,
    )

    object Settings : Screen(
        route        = "settings",
        labelRes     = R.string.settings_title,
        icon         = Icons.Outlined.Settings,
        selectedIcon = Icons.Filled.Settings,
    ) {
        const val homeScreenRoute = "settings/home_screen"
        const val saveSlotsRoute = "settings/save_slots"
        const val artCreditsRoute = "settings/art_credits"
        const val themeSettingsRoute = "settings/theme"
        const val themeEditorRoute = "settings/theme_editor?source={source}&blank={blank}"
        fun themeEditorRouteWithSource(source: String, blankName: Boolean) =
            "settings/theme_editor?source=$source&blank=$blankName"
    }

    object Shop : Screen(
        route    = "shop",
        labelRes = R.string.label_shop,
        icon     = Icons.Filled.ShoppingCart,
    )

    object Farming : Screen(
        route    = "farming",
        labelRes = R.string.skill_farming_name,
        icon     = Icons.Filled.ShoppingCart,
    )

    object Inn : Screen(
        route    = "inn",
        labelRes = R.string.inn_title,
        icon     = Icons.Filled.ShoppingCart,
    )

    object WorkerSkills : Screen(
        route    = "worker_skills?initialSlot={initialSlot}",
        labelRes = R.string.worker_skills_title_nav,
        icon     = Icons.AutoMirrored.Filled.ShowChart,
    ) {
        fun routeWithSlot(slot: Int) = "worker_skills?initialSlot=$slot"
    }

    object GuildHall : Screen(
        route    = "guild_hall",
        labelRes = R.string.guild_hall_title,
        icon     = Icons.Filled.Group,
    )

    object PrestigeDetail : Screen(
        route    = "prestige_detail/{skill}",
        labelRes = R.string.prestige_title,
        icon     = Icons.Filled.Star,
    ) {
        fun createRoute(skill: String) = "prestige_detail/$skill"
    }

    object Church : Screen(
        route    = "church",
        labelRes = R.string.church_title,
        icon     = Icons.Filled.Star,
    )

    object Monument : Screen(
        route    = "monument",
        labelRes = R.string.monument_title,
        icon     = Icons.Filled.AccountBalance,
    )

    object GuildDetail : Screen(
        route    = "guild_detail/{guild}",
        labelRes = R.string.guild_hall_title,
        icon     = Icons.Filled.Group,
    ) {
        fun createRoute(guild: String) = "guild_detail/$guild"
    }

    object Slayer : Screen(
        route    = "slayer",
        labelRes = R.string.slayer_title,
        icon     = Icons.Filled.Shield,
    )

    object Builder : Screen(
        route    = "builder",
        labelRes = R.string.builder_title,
        icon     = Icons.Filled.Star,
    )

    object House : Screen(
        route    = "house",
        labelRes = R.string.house_title,
        icon     = Icons.Filled.Home,
    )

    object BoneAltar : Screen(
        route    = "bone_altar",
        labelRes = R.string.bone_altar_title,
        icon     = Icons.Filled.Star,
    )

    object Carnival : Screen(
        route    = "carnival",
        labelRes = R.string.carnival_title,
        icon     = Icons.Filled.Celebration,
    )

    object PetArena : Screen(
        route    = "pet_arena",
        labelRes = R.string.pet_arena_title,
        icon     = Icons.Filled.Pets,
    )

    object Tower : Screen(
        route    = "tower",
        labelRes = R.string.tower_title,
        icon     = Icons.Filled.Star,
    )

    object SeasonalEvent : Screen(
        route    = "seasonal_event",
        labelRes = R.string.seasonal_event_title,
        icon     = Icons.Filled.Star,
    )

    object ElderIsleShop : Screen(
        route    = "elder_isle_shop",
        labelRes = R.string.elder_isle_shop_title,
        icon     = Icons.Filled.ShoppingCart,
    )

    object ElderArmorMaster : Screen(
        route    = "elder_armor_master",
        labelRes = R.string.elder_isle_armor_master_title,
        icon     = Icons.Filled.Shield,
    )

    object LoreMaster : Screen(
        route    = "lore_master",
        labelRes = R.string.elder_isle_lore_master_title,
        icon     = Icons.AutoMirrored.Filled.MenuBook,
    )

    companion object {
        val bottomNavItems = listOf(Skills, Combat, Home, Quests, Profile)
    }
}
