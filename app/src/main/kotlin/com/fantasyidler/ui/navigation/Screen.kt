package com.fantasyidler.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Celebration
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

sealed class Screen(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
) {
    object Skills : Screen(
        route        = "skills",
        labelRes     = R.string.nav_skills,
        icon         = Icons.Outlined.ShowChart,
        selectedIcon = Icons.Filled.ShowChart,
    )
    object Combat : Screen(
        route        = "combat",
        labelRes     = R.string.nav_combat,
        icon         = Icons.Outlined.Shield,
        selectedIcon = Icons.Filled.Shield,
    ) {
        const val gearRoute = "combat/gear"
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
        icon         = Icons.Outlined.MenuBook,
        selectedIcon = Icons.Filled.MenuBook,
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
    )

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
        icon     = Icons.Filled.ShowChart,
    ) {
        fun routeWithSlot(slot: Int) = "worker_skills?initialSlot=$slot"
    }

    object GuildHall : Screen(
        route    = "guild_hall",
        labelRes = R.string.guild_hall_title,
        icon     = Icons.Filled.Group,
    )

    object Church : Screen(
        route    = "church",
        labelRes = R.string.church_title,
        icon     = Icons.Filled.Star,
    )

    object GuildDetail : Screen(
        route    = "guild_detail/{guild}",
        labelRes = R.string.guild_hall_title,
        icon     = Icons.Filled.Group,
    ) {
        fun createRoute(guild: String) = "guild_detail/$guild"
    }

    object Mercantile : Screen(
        route    = "mercantile",
        labelRes = R.string.skill_mercantile,
        icon     = Icons.Filled.ShoppingCart,
    )

    object Expeditions : Screen(
        route        = "expeditions",
        labelRes     = R.string.nav_expeditions,
        icon         = Icons.Outlined.Explore,
        selectedIcon = Icons.Filled.Explore,
    )

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

    companion object {
        val bottomNavItems = listOf(Skills, Combat, Home, Quests, Profile)
    }
}
