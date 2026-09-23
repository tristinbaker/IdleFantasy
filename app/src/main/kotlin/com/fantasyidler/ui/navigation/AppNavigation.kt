package com.fantasyidler.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.fantasyidler.data.model.CombatGuilds
import com.fantasyidler.data.model.Skills
import com.fantasyidler.notification.SessionNotificationManager
import com.fantasyidler.ui.screen.AppBannerHost
import com.fantasyidler.ui.screen.BoneAltarScreen
import com.fantasyidler.ui.screen.HouseScreen
import com.fantasyidler.ui.screen.CarnivalScreen
import com.fantasyidler.ui.screen.TowerScreen
import com.fantasyidler.ui.screen.ChurchScreen
import com.fantasyidler.ui.screen.MonumentScreen
import com.fantasyidler.ui.screen.BuilderScreen
import com.fantasyidler.ui.screen.CombatScreen
import com.fantasyidler.ui.screen.ElderArmorMasterScreen
import com.fantasyidler.ui.screen.ElderIsleShopScreen
import com.fantasyidler.ui.screen.LoreMasterScreen
import com.fantasyidler.ui.screen.FarmingScreen
import com.fantasyidler.ui.screen.GuildDetailScreen
import com.fantasyidler.ui.screen.GuildHallScreen
import com.fantasyidler.ui.screen.PrestigeDetailScreen
import com.fantasyidler.ui.screen.HomeScreen
import com.fantasyidler.ui.screen.InnScreen
import com.fantasyidler.ui.screen.OnboardingScreen
import com.fantasyidler.ui.screen.ProfileScreen
import com.fantasyidler.ui.screen.QuestsScreen
import com.fantasyidler.ui.screen.SeasonalEventScreen
import com.fantasyidler.ui.screen.HomeScreenSettingsScreen
import com.fantasyidler.ui.screen.ArtCreditsScreen
import com.fantasyidler.ui.screen.CombatTabName
import com.fantasyidler.ui.screen.SaveSlotsScreen
import com.fantasyidler.ui.screen.SettingsScreen
import com.fantasyidler.ui.screen.ThemeEditorScreen
import com.fantasyidler.ui.screen.ThemeSettingsScreen
import com.fantasyidler.ui.screen.ShopScreen
import com.fantasyidler.ui.screen.SkillsScreen
import com.fantasyidler.ui.screen.SlayerScreen
import com.fantasyidler.ui.screen.WorkerSkillsScreen
import com.fantasyidler.ui.viewmodel.NavBadgeViewModel
import com.fantasyidler.ui.viewmodel.OnboardingViewModel
import com.fantasyidler.ui.viewmodel.SettingsViewModel

@Composable
fun AppNavigation(
    pendingNavigateTo: String? = null,
    onNavigateConsumed: () -> Unit = {},
) {
    val onboardingVm: OnboardingViewModel = hiltViewModel()
    val showOnboarding by onboardingVm.showOnboarding.collectAsState()
    val navBadgeVm: NavBadgeViewModel = hiltViewModel()
    val questsClaimable by navBadgeVm.questsClaimableCount.collectAsState()
    val hasCombatPrestige by navBadgeVm.hasCombatPrestige.collectAsState()
    val hasSkillPrestige by navBadgeVm.hasSkillPrestige.collectAsState()
    val settingsVm: SettingsViewModel = hiltViewModel()
    val showPrestigeNotifications by settingsVm.showPrestigeNotifications.collectAsState()

    // Show onboarding as a full-screen overlay until complete.
    // null = still loading from DB; don't flash the overlay.
    if (showOnboarding == true) {
        OnboardingScreen(onComplete = onboardingVm::complete)
        return
    }

    val navController = rememberNavController()

    LaunchedEffect(pendingNavigateTo) {
        when (pendingNavigateTo) {
            SessionNotificationManager.NAVIGATE_FARMING -> {
                navController.navigate(Screen.Skills.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
                navController.navigate(Screen.Farming.route)
                onNavigateConsumed()
            }
            SessionNotificationManager.NAVIGATE_SAVE_SLOTS -> {
                navController.navigate(Screen.Home.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
                navController.navigate(Screen.Settings.saveSlotsRoute)
                onNavigateConsumed()
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val tabSubScreens: Map<String, Set<String>> = mapOf(
        "home"   to setOf("shop", "settings", "inn", Screen.WorkerSkills.route, "guild_hall", "guild_detail/{guild}", "church", "slayer", "carnival", Screen.SeasonalEvent.route, Screen.Skills.openSkillRoute, Screen.Combat.startWithTab(CombatTabName.DUNGEONS)),
        "skills" to setOf("farming", "mercantile", Screen.Slayer.route, Screen.BoneAltar.route, Screen.PrestigeDetail.route),
        "combat" to setOf(Screen.Tower.route),
        "profile" to setOf(Screen.Combat.startWithTab(CombatTabName.GEAR), Screen.PrestigeDetail.route),
        "quests" to setOf(Screen.Skills.openSkillRoute, Screen.Slayer.route, Screen.Combat.route),
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar(
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
            ) {
                Screen.bottomNavItems.forEach { screen ->
                    val onTabRoot = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    val onSkillsDeepLink = screen is Screen.Skills && currentDestination?.route == Screen.Skills.openSkillRoute
                    val selected = onTabRoot || onSkillsDeepLink

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
                                    restoreState = !isHome
                                }
                                if (screen is Screen.Profile) {
                                    // restoreState can bring back another tab's screen on top of
                                    // profile (e.g. combat gear, issue #1511); drop it so the
                                    // Profile button always lands on the profile view itself.
                                    navController.popBackStack(screen.route, inclusive = false)
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
                                val showCombatBadge = screen is Screen.Combat && hasCombatPrestige && showPrestigeNotifications
                                val showSkillBadge = screen is Screen.Skills && hasSkillPrestige && showPrestigeNotifications
                                if (showQuestBadge || showCombatBadge || showSkillBadge) {
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
            paneComposable(Screen.Skills.route)   {
                BoundSkillsScreen(navController)
            }
            paneComposable(
                route     = Screen.Skills.openSkillRoute,
                arguments = listOf(navArgument("openSkill") { type = NavType.StringType }),
            ) { entry ->
                BoundSkillsScreen(
                    navController = navController,
                    openSkill     = entry.arguments?.getString("openSkill"),
                )
            }
            paneComposable(Screen.Farming.route) { entry ->
                FarmingScreen(onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() })
            }
            paneComposable(Screen.Home.route)     {
                HomeScreen(
                    onNavigateToSettings     = { navController.navigate(Screen.Settings.route) },
                    onNavigateToSaveSlots    = { navController.navigate(Screen.Settings.saveSlotsRoute) },
                    onNavigateToShop         = { navController.navigate(Screen.Shop.route) },
                    onNavigateToInn          = { navController.navigate(Screen.Inn.route) },
                    onNavigateToWorkerSkills = { slot -> navController.navigate(Screen.WorkerSkills.routeWithSlot(slot)) },
                    onNavigateToGuildHall    = { navController.navigate(Screen.GuildHall.route) },
                    onNavigateToChurch       = { navController.navigate(Screen.Church.route) },
                    onNavigateToMonument     = { navController.navigate(Screen.Monument.route) },
                    onNavigateToSlayer       = { navController.navigate(Screen.Slayer.route) },
                    onNavigateToBuilder      = { navController.navigate(Screen.Builder.route) },
                    onNavigateToHouse        = { navController.navigate(Screen.House.route) },
                    onNavigateToCarnival     = { navController.navigate(Screen.Carnival.route) },
                    onNavigateToSeasonalEvent = { navController.navigate(Screen.SeasonalEvent.route) },
                    onNavigateToElderIsleShop = { navController.navigate(Screen.ElderIsleShop.route) },
                    onNavigateToElderArmorMaster = { navController.navigate(Screen.ElderArmorMaster.route) },
                    onNavigateToLoreMaster       = { navController.navigate(Screen.LoreMaster.route) },
                )
            }
            paneComposable(Screen.Quests.route)   {
                QuestsScreen(
                    onNavigateToSkill = { skill ->
                        when (skill) {
                            "combat"      -> navController.navigate(Screen.Combat.route) { launchSingleTop = true }
                            Skills.SLAYER -> navController.navigate(Screen.Slayer.route) { launchSingleTop = true }
                            else          -> navController.navigate(Screen.Skills.routeWithSkill(skill)) { launchSingleTop = true }
                        }
                    },
                )
            }
            paneComposable(Screen.Profile.route)  {
                ProfileScreen(
                    onNavigateToCombat   = { navController.navigate(Screen.Combat.startWithTab(CombatTabName.GEAR)) },
                    onNavigateToPrestige = { skill -> navController.navigate(Screen.PrestigeDetail.createRoute(skill)) },
                )
            }
            paneComposable(Screen.Combat.route)   {
                BoundCombatScreen(navController)
            }
            paneComposable(
                route     = Screen.Combat.openTabRoute,
                arguments = listOf(navArgument("tab") { type = NavType.EnumType(CombatTabName::class.java) }),
            ) { entry ->
                BoundCombatScreen(
                    navController = navController,
                    startingPage  = entry.arguments?.getString("tab")?.let { CombatTabName.valueOf(it) }
                )
            }
            paneComposable(
                route     = Screen.Combat.presetDungeonRoute,
                arguments = listOf(navArgument("dungeonKey") { type = NavType.StringType }),
            ) { entry ->
                BoundCombatScreen(
                    navController     = navController,
                    initialDungeonKey = entry.arguments?.getString("dungeonKey"),
                )
            }
            paneComposable(
                route     = Screen.Combat.presetBossRoute,
                arguments = listOf(navArgument("bossKey") { type = NavType.StringType }),
            ) { entry ->
                BoundCombatScreen(
                    navController     = navController,
                    initialBossKey = entry.arguments?.getString("bossKey"),
                )
            }
            paneComposable(Screen.Settings.route) { entry ->
                SettingsScreen(
                    onBack                         = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                    onReopenTutorial               = { onboardingVm.reopen() },
                    onNavigateToHomeScreenSettings = { navController.navigate(Screen.Settings.homeScreenRoute) },
                    onNavigateToThemeSettings      = { navController.navigate(Screen.Settings.themeSettingsRoute) },
                    onNavigateToSaveSlots          = { navController.navigate(Screen.Settings.saveSlotsRoute) },
                    onNavigateToArtCredits         = { navController.navigate(Screen.Settings.artCreditsRoute) },
                )
            }
            paneComposable(Screen.Settings.homeScreenRoute) { entry ->
                HomeScreenSettingsScreen(
                    onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                )
            }
            paneComposable(Screen.Settings.saveSlotsRoute) { entry ->
                SaveSlotsScreen(
                    onBack     = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                    onSwitched = {
                        // Rebuild the whole back stack on the new character. Also drop every
                        // tab's saved sub-screen stack (and its ViewModels): without this the
                        // Skills tab restores the previous character's remembered screen, e.g.
                        // the bone altar with their session tallies (issue #1550).
                        Screen.bottomNavItems.forEach { navController.clearBackStack(it.route) }
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        }
                    },
                )
            }
            paneComposable(Screen.Settings.artCreditsRoute) { entry ->
                ArtCreditsScreen(
                    onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                )
            }
            paneComposable(Screen.Settings.themeSettingsRoute) { entry ->
                ThemeSettingsScreen(
                    onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                    onNavigateToThemeEditor = { source, blank -> navController.navigate(Screen.Settings.themeEditorRouteWithSource(source, blank)) },
                )
            }
            paneComposable(
                route     = Screen.Settings.themeEditorRoute,
                arguments = listOf(
                    navArgument("source") { type = NavType.StringType; defaultValue = "dark" },
                    navArgument("blank")  { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                ThemeEditorScreen(
                    source    = entry.arguments?.getString("source") ?: "dark",
                    blankName = entry.arguments?.getBoolean("blank") ?: false,
                    onBack    = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                )
            }
            paneComposable(Screen.Shop.route) { entry ->
                ShopScreen(onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() })
            }
            paneComposable(Screen.Inn.route) { entry ->
                InnScreen(
                    onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                    onNavigateToWorkerSkills = { slot ->
                        navController.popBackStack()
                        navController.navigate(Screen.WorkerSkills.routeWithSlot(slot))
                    },
                )
            }
            paneComposable(
                route     = Screen.WorkerSkills.route,
                arguments = listOf(navArgument("initialSlot") { type = NavType.IntType; defaultValue = 1 }),
            ) { entry ->
                val initialSlot = entry.arguments?.getInt("initialSlot") ?: 1
                WorkerSkillsScreen(
                    initialSlot = initialSlot,
                    onBack      = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                )
            }
            paneComposable(Screen.PrestigeDetail.route) { entry ->
                PrestigeDetailScreen(
                    onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                )
            }
            paneComposable(Screen.GuildHall.route) { entry ->
                GuildHallScreen(
                    onBack             = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                    onNavigateToGuild  = { guild -> navController.navigate(Screen.GuildDetail.createRoute(guild)) },
                )
            }
            paneComposable(Screen.GuildDetail.route) { entry ->
                GuildDetailScreen(
                    onBack             = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                    onNavigateToSkill  = { skill ->
                        when (skill) {
                            Skills.SLAYER -> navController.navigate(Screen.Slayer.route) { launchSingleTop = true }
                            in CombatGuilds.ALL -> navController.navigate(Screen.Combat.startWithTab(CombatTabName.DUNGEONS)) { launchSingleTop = true }
                            else -> navController.navigate(Screen.Skills.routeWithSkill(skill)) { launchSingleTop = true }
                        }
                    },
                )
            }
            paneComposable(Screen.Church.route) { entry ->
                ChurchScreen(
                    onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                )
            }
            paneComposable(Screen.Monument.route) { entry ->
                MonumentScreen(
                    onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                )
            }
            paneComposable(Screen.Slayer.route) { entry ->
                SlayerScreen(
                    onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                    onNavigateToPrestige = { skill -> navController.navigate(Screen.PrestigeDetail.createRoute(skill)) },
                )
            }
            paneComposable(Screen.BoneAltar.route) { entry ->
                BoneAltarScreen(
                    onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                )
            }
            paneComposable(Screen.Builder.route) { entry ->
                BuilderScreen(
                    onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                    onNavigateToSeaSerpent = { navController.navigate(Screen.Combat.presetBossRoute("sea_serpent")) },
                )
            }
            paneComposable(Screen.House.route) { entry ->
                HouseScreen(
                    onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                )
            }
            paneComposable(Screen.Carnival.route) { entry ->
                CarnivalScreen(
                    onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                )
            }
            paneComposable(Screen.Tower.route) { entry ->
                TowerScreen(
                    onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                )
            }
            paneComposable(Screen.SeasonalEvent.route) { entry ->
                SeasonalEventScreen(
                    onBack               = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                    onNavigateToExpedition = { key -> navController.navigate(Screen.Combat.presetDungeonRoute(key)) },
                    onNavigateToBoss       = { key -> navController.navigate(Screen.Combat.presetBossRoute(key)) },
                )
            }
            paneComposable(Screen.ElderIsleShop.route) { entry ->
                ElderIsleShopScreen(
                    onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                )
            }
            paneComposable(Screen.ElderArmorMaster.route) { entry ->
                ElderArmorMasterScreen(
                    onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                )
            }
            paneComposable(Screen.LoreMaster.route) { entry ->
                LoreMasterScreen(
                    onBack = { if (navController.currentBackStackEntry == entry) navController.popBackStack() },
                )
            }
        }
    }
    AppBannerHost()
}

// ---------------------------------------------------------------------------
// Bound screens — Used when multiple routes lead to the same screen to improve code reuse
// ---------------------------------------------------------------------------

@Composable
private fun BoundSkillsScreen(
    navController: NavController,
    openSkill: String? = null,
) {
    SkillsScreen(
        openSkill             = openSkill,
        onNavigateToSlayer    = { navController.navigate(Screen.Slayer.route) },
        onNavigateToBoneAltar = { navController.navigate(Screen.BoneAltar.route) },
        onNavigateToPrestige  = { skill -> navController.navigate(Screen.PrestigeDetail.createRoute(skill)) },
    )
}

@Composable
private fun BoundCombatScreen(
    navController: NavController,
    startingPage: CombatTabName? = null,
    initialDungeonKey: String? = null,
    initialBossKey: String? = null
) {
    CombatScreen(
        startingPage = startingPage,
        initialDungeonKey = initialDungeonKey,
        initialBossKey = initialBossKey,
        onNavigateToTower    = { navController.navigate(Screen.Tower.route) },
        onNavigateToPrestige = { skill -> navController.navigate(Screen.PrestigeDetail.createRoute(skill)) },
    )
}

/**
 * Like [composable], but eats all touch input while the pane is animating out, so taps
 * during a navigation transition can't reach the outgoing screen's buttons (issue #1497).
 */
private fun NavGraphBuilder.paneComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(route, arguments) { entry ->
        Box {
            content(entry)
            if (transition.targetState == EnterExitState.PostExit) {
                Box(
                    Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                }
                            }
                        }
                )
            }
        }
    }
}
