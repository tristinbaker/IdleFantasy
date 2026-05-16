package com.fantasyidler.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.AccountCircle
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
    )
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

    companion object {
        val bottomNavItems = listOf(Skills, Combat, Home, Quests, Profile)
    }
}
