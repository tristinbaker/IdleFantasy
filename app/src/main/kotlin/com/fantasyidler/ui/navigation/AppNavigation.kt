package com.fantasyidler.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.notification.SessionNotificationManager
import com.fantasyidler.ui.screen.BoneAltarScreen
import com.fantasyidler.ui.screen.CarnivalScreen
import com.fantasyidler.ui.screen.TowerScreen
import com.fantasyidler.ui.screen.ChurchScreen
import com.fantasyidler.ui.screen.BuilderScreen
import com.fantasyidler.ui.screen.CombatScreen
import com.fantasyidler.ui.screen.FarmingScreen
import com.fantasyidler.ui.screen.GuildDetailScreen
import com.fantasyidler.ui.screen.GuildHallScreen
import com.fantasyidler.ui.screen.HomeScreen
import com.fantasyidler.ui.screen.InnScreen
import com.fantasyidler.ui.screen.OnboardingScreen
import com.fantasyidler.ui.screen.ProfileScreen
import com.fantasyidler.ui.screen.QuestsScreen
import com.fantasyidler.ui.screen.SeasonalEventScreen
import com.fantasyidler.ui.screen.SettingsScreen
import com.fantasyidler.ui.screen.ShopScreen
import com.fantasyidler.ui.screen.SkillsScreen
import com.fantasyidler.ui.screen.SlayerScreen
import com.fantasyidler.ui.screen.WorkerSkillsScreen
import com.fantasyidler.ui.viewmodel.NavBadgeViewModel
import com.fantasyidler.ui.viewmodel.OnboardingViewModel

@Composable
fun AppNavigation(
    pendingNavigateTo: String? = null,
    onNavigateConsumed: () -> Unit = {},
) {
    val onboardingVm: OnboardingViewModel = hiltViewModel()
    val showOnboarding by onboardingVm.showOnboarding.collectAsState()
    val navBadgeVm: NavBadgeViewModel = hiltViewModel()
    val questsClaimable by navBadgeVm.questsClaimableCount.collectAsState()

    // Show onboarding as a full-screen overlay until complete.
    // null = still loading from DB; don't flash the overlay.
    if (showOnboarding == true) {
        OnboardingScreen(onComplete = onboardingVm::complete)
        return
    }

    val navController = rememberNavController()

    LaunchedEffect(pendingNavigateTo) {
        if (pendingNavigateTo == SessionNotificationManager.NAVIGATE_FARMING) {
            navController.navigate(Screen.Skills.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            navController.navigate(Screen.Farming.route)
            onNavigateConsumed()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val tabSubScreens: Map<String, Set<String>> = mapOf(
        "home"   to setOf("shop", "settings", "inn", Screen.WorkerSkills.route, "guild_hall", "guild_detail/{guild}", "church", "slayer", "carnival", Screen.SeasonalEvent.route),
        "skills" to setOf("farming", "mercantile", Screen.Slayer.route, Screen.BoneAltar.route),
    )

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
                            val currentRoute = currentDestination?.route
                            val isInSubScreen = tabSubScreens[screen.route]?.contains(currentRoute) == true
                            if (isInSubScreen && navController.popBackStack(screen.route, inclusive = false)) {
                                // popped back to the tab root
                            } else {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = !isHome && screen !is Screen.Profile
                                }
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
                                val showQuestBadge = screen is Screen.Quests && questsClaimable > 0
                                if (showQuestBadge) {
                                    BadgedBox(badge = { Badge() }) {
                                        Icon(
                                            imageVector        = if (selected) screen.selectedIcon else screen.icon,
                                            contentDescription = stringResource(screen.labelRes),
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector        = if (selected) screen.selectedIcon else screen.icon,
                                        contentDescription = stringResource(screen.labelRes),
                                    )
                                }
                            }
                        },
                        label = { Text(stringResource(screen.labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.sp) },
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
                SkillsScreen(
                    onNavigateToSlayer    = { navController.navigate(Screen.Slayer.route) },
                    onNavigateToBoneAltar = { navController.navigate(Screen.BoneAltar.route) },
                )
            }
            composable(Screen.Farming.route) { entry ->
                FarmingScreen(onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() })
            }
            composable(Screen.Combat.route)   { CombatScreen(onNavigateToTower = { navController.navigate(Screen.Tower.route) }) }
            composable(Screen.Home.route)     {
                HomeScreen(
                    onNavigateToSettings     = { navController.navigate(Screen.Settings.route) },
                    onNavigateToShop         = { navController.navigate(Screen.Shop.route) },
                    onNavigateToInn          = { navController.navigate(Screen.Inn.route) },
                    onNavigateToWorkerSkills = { slot -> navController.navigate(Screen.WorkerSkills.routeWithSlot(slot)) },
                    onNavigateToGuildHall    = { navController.navigate(Screen.GuildHall.route) },
                    onNavigateToChurch       = { navController.navigate(Screen.Church.route) },
                    onNavigateToSlayer       = { navController.navigate(Screen.Slayer.route) },
                    onNavigateToBuilder      = { navController.navigate(Screen.Builder.route) },
                    onNavigateToCarnival     = { navController.navigate(Screen.Carnival.route) },
                    onNavigateToSeasonalEvent = { navController.navigate(Screen.SeasonalEvent.route) },
                )
            }
            composable(Screen.Quests.route)   { QuestsScreen() }
            composable(Screen.Profile.route)  { ProfileScreen(onNavigateToCombat = { navController.navigate(Screen.Combat.gearRoute) }) }
            composable(Screen.Combat.gearRoute) { CombatScreen(startOnGear = true) }
            composable(
                route     = Screen.Combat.presetDungeonRoute,
                arguments = listOf(navArgument("dungeonKey") { type = NavType.StringType }),
            ) { entry ->
                CombatScreen(initialDungeonKey = entry.arguments?.getString("dungeonKey"))
            }
            composable(
                route     = Screen.Combat.presetBossRoute,
                arguments = listOf(navArgument("bossKey") { type = NavType.StringType }),
            ) { entry ->
                CombatScreen(initialBossKey = entry.arguments?.getString("bossKey"))
            }
            composable(Screen.Settings.route) { entry ->
                SettingsScreen(
                    onBack           = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                    onReopenTutorial = { onboardingVm.reopen() },
                )
            }
            composable(Screen.Shop.route) { entry ->
                ShopScreen(onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() })
            }
            composable(Screen.Inn.route) { entry ->
                InnScreen(
                    onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                    onNavigateToWorkerSkills = { slot ->
                        navController.popBackStack()
                        navController.navigate(Screen.WorkerSkills.routeWithSlot(slot))
                    },
                )
            }
            composable(
                route     = Screen.WorkerSkills.route,
                arguments = listOf(navArgument("initialSlot") { type = NavType.IntType; defaultValue = 1 }),
            ) { entry ->
                val initialSlot = entry.arguments?.getInt("initialSlot") ?: 1
                WorkerSkillsScreen(
                    initialSlot = initialSlot,
                    onBack      = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                )
            }
            composable(Screen.GuildHall.route) { entry ->
                GuildHallScreen(
                    onBack             = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                    onNavigateToGuild  = { guild -> navController.navigate(Screen.GuildDetail.createRoute(guild)) },
                )
            }
            composable(Screen.GuildDetail.route) { entry ->
                GuildDetailScreen(
                    onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                )
            }
            composable(Screen.Church.route) { entry ->
                ChurchScreen(
                    onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                )
            }
            composable(Screen.Slayer.route) { entry ->
                SlayerScreen(
                    onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                )
            }
            composable(Screen.BoneAltar.route) { entry ->
                BoneAltarScreen(
                    onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                )
            }
            composable(Screen.Builder.route) { entry ->
                BuilderScreen(
                    onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                )
            }
            composable(Screen.Carnival.route) { entry ->
                CarnivalScreen(
                    onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                )
            }
            composable(Screen.Tower.route) { entry ->
                TowerScreen(
                    onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                )
            }
            composable(Screen.SeasonalEvent.route) { entry ->
                SeasonalEventScreen(
                    onBack               = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                    onNavigateToExpedition = { key -> navController.navigate(Screen.Combat.presetDungeonRoute(key)) },
                    onNavigateToBoss       = { key -> navController.navigate(Screen.Combat.presetBossRoute(key)) },
                )
            }
        }
    }
}
