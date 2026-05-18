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
import com.fantasyidler.ui.components.FantasyTopHud
import com.fantasyidler.ui.components.GlobalGameOverlay
import com.fantasyidler.ui.motion.FantasyMotion
import com.fantasyidler.ui.screen.AdventureScreen
import com.fantasyidler.ui.screen.CombatScreen
import com.fantasyidler.ui.screen.CraftingScreen
import com.fantasyidler.ui.screen.FarmingScreen
import com.fantasyidler.ui.screen.OnboardingScreen
import com.fantasyidler.ui.screen.ProfileScreen
import com.fantasyidler.ui.screen.QuestsScreen
import com.fantasyidler.ui.screen.SettingsScreen
import com.fantasyidler.ui.screen.ShopScreen
import com.fantasyidler.ui.screen.SkillsScreen
import com.fantasyidler.ui.viewmodel.HomeViewModel
import com.fantasyidler.ui.viewmodel.OnboardingViewModel
import com.fantasyidler.ui.viewmodel.RootHudViewModel

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

    val tabSubScreens: Map<String, Set<String>> = mapOf(
        "adventure" to setOf("shop", "settings", "quests"),
        "skills"    to setOf("farming"),
    )

    val tabRoutes = Screen.bottomNavItems.map { it.route }.toSet()
    val showHud = currentDestination?.route in tabRoutes

    val hudVm: RootHudViewModel = hiltViewModel()
    val hudState by hudVm.uiState.collectAsState()

    // HomeViewModel is hoisted to the root scope so the four global flows it
    // owns (sessionSummary dialog, what's-new dialog, character-setup sheet,
    // session-collect trigger) fire from any tab. PR #9.5 renames this to
    // GlobalGameViewModel and drops the now-unused dashboard fields.
    val globalVm: HomeViewModel = hiltViewModel()
    val globalState by globalVm.uiState.collectAsState()

    Scaffold(
        topBar = {
            if (showHud) {
                FantasyTopHud(
                    coins         = hudState.coins,
                    combatLevel   = hudState.combatLevel,
                    activeSession = hudState.activeSession,
                    onProfile  = {
                        navController.navigate(Screen.Profile.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    },
                    onShop     = { navController.navigate(Screen.Shop.route) },
                    onSession  = {
                        // If there's anything to claim, claim from any tab.
                        // Otherwise navigate to Skills so the player can start
                        // a session.
                        val session = hudState.activeSession
                        val canClaim = globalState.pendingCollectCount > 0 ||
                                       (session != null && (session.completed ||
                                        System.currentTimeMillis() >= session.endsAt))
                        if (canClaim) {
                            globalVm.collectSession()
                        } else {
                            navController.navigate(Screen.Skills.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        }
                    },
                    onSettings = { navController.navigate(Screen.Settings.route) },
                )
            }
        },
        bottomBar = {
            NavigationBar {
                Screen.bottomNavItems.forEach { screen ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == screen.route } == true

                    // The centre tab gets the bigger pill treatment. Was Home; now Adventure.
                    val isHub = screen is Screen.Adventure

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            val currentRoute = currentDestination?.route
                            val isInSubScreen = tabSubScreens[screen.route]?.contains(currentRoute) == true
                            if (isInSubScreen) {
                                navController.popBackStack(screen.route, inclusive = false)
                            } else {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = !isHub
                                }
                            }
                        },
                        icon = {
                            if (isHub) {
                                // Larger filled circle for the centre hub button (Adventure)
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
            startDestination = Screen.Adventure.route,
            modifier         = Modifier.padding(innerPadding),
            enterTransition    = FantasyMotion.NavEnter,
            exitTransition     = FantasyMotion.NavExit,
            popEnterTransition = FantasyMotion.NavPopEnter,
            popExitTransition  = FantasyMotion.NavPopExit,
        ) {
            composable(Screen.Skills.route)   {
                SkillsScreen(onNavigateToFarming = { navController.navigate(Screen.Farming.route) })
            }
            composable(
                route = Screen.Farming.route,
                enterTransition    = FantasyMotion.SubEnter,
                exitTransition     = FantasyMotion.SubExit,
                popEnterTransition = FantasyMotion.SubEnter,
                popExitTransition  = FantasyMotion.SubExit,
            ) { entry ->
                FarmingScreen(onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() })
            }
            composable(Screen.Combat.route)   { CombatScreen() }
            composable(Screen.Adventure.route) {
                AdventureScreen(
                    onOpenQuests       = { navController.navigate(Screen.Quests.route) },
                    onOpenAchievements = {
                        navController.navigate(Screen.Profile.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    },
                    onEnterDungeon     = {
                        // Deep-link the chosen dungeon: drop to Combat. The
                        // CombatScreen will display the dungeon list — full
                        // pre-selection wiring is left for a follow-up.
                        navController.navigate(Screen.Combat.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Screen.Crafting.route) { CraftingScreen() }
            composable(Screen.Quests.route)   { QuestsScreen() }
            composable(Screen.Profile.route)  { ProfileScreen() }
            composable(
                route = Screen.Settings.route,
                enterTransition    = FantasyMotion.SubEnter,
                exitTransition     = FantasyMotion.SubExit,
                popEnterTransition = FantasyMotion.SubEnter,
                popExitTransition  = FantasyMotion.SubExit,
            ) { entry ->
                SettingsScreen(
                    onBack           = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                    onReopenTutorial = { onboardingVm.reopen() },
                )
            }
            composable(
                route = Screen.Shop.route,
                enterTransition    = FantasyMotion.SubEnter,
                exitTransition     = FantasyMotion.SubExit,
                popEnterTransition = FantasyMotion.SubEnter,
                popExitTransition  = FantasyMotion.SubExit,
            ) { entry ->
                ShopScreen(onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() })
            }
        }

        // Root-level overlay: dialogs/sheets that fire from any tab.
        GlobalGameOverlay(
            sessionSummary          = globalState.sessionSummary,
            showWhatsNew            = globalState.showWhatsNew,
            characterSetupDone      = globalState.characterSetupDone,
            characterName           = globalState.characterName,
            onSummaryConsumed       = globalVm::summaryConsumed,
            onDismissWhatsNew       = globalVm::dismissWhatsNew,
            onSaveCharacter         = { name, gender, race ->
                globalVm.saveCharacterProfile(name, gender, race)
            },
            onDismissCharacterSetup = globalVm::dismissCharacterSetup,
        )
    }
}
