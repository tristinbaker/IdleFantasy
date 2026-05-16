package com.fantasyidler.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.ui.screen.CombatScreen
import com.fantasyidler.ui.screen.FarmingScreen
import com.fantasyidler.ui.screen.HomeScreen
import com.fantasyidler.ui.screen.OnboardingScreen
import com.fantasyidler.ui.screen.ProfileScreen
import com.fantasyidler.ui.screen.QuestsScreen
import com.fantasyidler.ui.screen.SettingsScreen
import com.fantasyidler.ui.screen.ShopScreen
import com.fantasyidler.ui.screen.SkillsScreen
import com.fantasyidler.ui.viewmodel.OnboardingViewModel

@Composable
fun AppNavigation() {
    val onboardingVm: OnboardingViewModel = hiltViewModel()
    val showOnboarding by onboardingVm.showOnboarding.collectAsState()

    // Show onboarding as a full-screen overlay until complete.
    // null = still loading from DB; don't flash the overlay.
    if (showOnboarding == true) {
        OnboardingScreen(onComplete = onboardingVm::complete)
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.bottomNavItems.forEach { screen ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == screen.route } == true

                    val isHome = screen is Screen.Home

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            if (isHome) {
                                // Larger filled circle for the centre Home button
                                Surface(
                                    shape  = CircleShape,
                                    color  = if (selected) MaterialTheme.colorScheme.primary
                                             else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            imageVector        = if (selected) screen.selectedIcon else screen.icon,
                                            contentDescription = stringResource(screen.labelRes),
                                            tint               = if (selected) MaterialTheme.colorScheme.onPrimary
                                                                 else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier           = Modifier.size(24.dp),
                                        )
                                    }
                                }
                            } else {
                                Icon(
                                    imageVector        = if (selected) screen.selectedIcon else screen.icon,
                                    contentDescription = stringResource(screen.labelRes),
                                )
                            }
                        },
                        label = { Text(stringResource(screen.labelRes)) },
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Home.route,
            modifier         = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Skills.route)   {
                SkillsScreen(onNavigateToFarming = { navController.navigate(Screen.Farming.route) })
            }
            composable(Screen.Farming.route) {
                FarmingScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Combat.route)   { CombatScreen() }
            composable(Screen.Home.route)     {
                HomeScreen(
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToShop     = { navController.navigate(Screen.Shop.route) },
                )
            }
            composable(Screen.Quests.route)   { QuestsScreen() }
            composable(Screen.Profile.route)  { ProfileScreen() }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack           = { navController.popBackStack() },
                    onReopenTutorial = { onboardingVm.reopen() },
                )
            }
            composable(Screen.Shop.route)     {
                ShopScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
