package com.fantasyidler.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Explore
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
    )
    object Adventure : Screen(
        route        = "adventure",
        labelRes     = R.string.nav_adventure,
        icon         = Icons.Outlined.Explore,
        selectedIcon = Icons.Filled.Explore,
    )
    object Crafting : Screen(
        route        = "crafting",
        labelRes     = R.string.nav_crafting,
        icon         = Icons.Outlined.Build,
        selectedIcon = Icons.Filled.Build,
    )
    object Quests : Screen(
        // Kept as a routable sub-screen reached from Adventure. Not a tab.
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

    object MinigameHub : Screen(
        route    = "minigame_hub",
        labelRes = R.string.adv_minigame_hub,
        icon     = Icons.Filled.LocationCity,
    )

    object Perks : Screen(
        route    = "perks",
        labelRes = R.string.perks_title,
        icon     = Icons.Filled.AutoAwesome,
    )

    companion object {
        val bottomNavItems = listOf(Skills, Combat, Adventure, Crafting, Profile)
    }
}
