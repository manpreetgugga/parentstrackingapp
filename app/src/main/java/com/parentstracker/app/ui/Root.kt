package com.parentstracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.parentstracker.app.ui.screens.DashboardScreen
import com.parentstracker.app.ui.screens.JoinScreen
import com.parentstracker.app.ui.screens.MapScreen
import com.parentstracker.app.ui.screens.OnboardingScreen
import com.parentstracker.app.ui.screens.PeopleScreen
import com.parentstracker.app.ui.screens.SettingsScreen
import kotlinx.coroutines.flow.StateFlow

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Filled.Home),
    MAP("map", "Map", Icons.Filled.Map),
    PEOPLE("people", "People", Icons.Filled.People),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}

@Composable
fun ParentsTrackerRoot(
    pendingJoinCode: StateFlow<String?>,
    onJoinCodeHandled: () -> Unit,
    pendingSos: StateFlow<Boolean>,
    onSosHandled: () -> Unit,
) {
    val vm: AppViewModel = viewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sos by pendingSos.collectAsStateWithLifecycle()
    LaunchedEffect(sos) {
        if (sos) {
            vm.requestSos()
            onSosHandled()
        }
    }
    val current = settings
    // Senior mode: all text one size larger, for parents' phones.
    val density = LocalDensity.current
    val scaled = if (current?.seniorMode == true) Density(density.density, density.fontScale * SENIOR_FONT_SCALE) else density
    CompositionLocalProvider(LocalDensity provides scaled) {
        when {
            current == null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            !current.onboardingDone -> OnboardingScreen(vm)
            else -> MainScaffold(vm, pendingJoinCode, onJoinCodeHandled)
        }
    }
}

private const val SENIOR_FONT_SCALE = 1.25f

/** Switches bottom-bar tab, keeping each tab's state. Pass restore = false when the route carries new arguments. */
private fun NavHostController.switchTab(route: String, restore: Boolean = true) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = restore }
    launchSingleTop = true
    restoreState = restore
}

@Composable
private fun MainScaffold(vm: AppViewModel, pendingJoinCode: StateFlow<String?>, onJoinCodeHandled: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val joinCode by pendingJoinCode.collectAsStateWithLifecycle()
    val sosRequested by vm.sosRequested.collectAsStateWithLifecycle()
    LaunchedEffect(sosRequested) {
        if (sosRequested) nav.switchTab(Tab.HOME.route)
    }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(joinCode) {
        joinCode?.let {
            nav.navigate("join?code=$it")
            onJoinCodeHandled()
        }
    }

    val onTab = Tab.entries.any { route?.startsWith(it.route) == true }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (onTab) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route?.startsWith(tab.route) == true,
                            onClick = { nav.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.HOME.route,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Tab.HOME.route) {
                DashboardScreen(
                    vm = vm,
                    onOpenMap = { uid -> if (uid == null) nav.switchTab(Tab.MAP.route) else nav.switchTab("map?focus=$uid", restore = false) },
                    onOpenPeople = { nav.switchTab(Tab.PEOPLE.route) },
                    onOpenSettings = { nav.switchTab(Tab.SETTINGS.route) },
                )
            }
            composable(
                route = "map?focus={focus}",
                arguments = listOf(navArgument("focus") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                MapScreen(vm = vm, focusUid = entry.arguments?.getString("focus"))
            }
            composable(Tab.PEOPLE.route) {
                PeopleScreen(
                    vm = vm,
                    onJoin = { nav.navigate("join") },
                    onShowOnMap = { uid -> nav.switchTab("map?focus=$uid", restore = false) },
                )
            }
            composable(Tab.SETTINGS.route) { SettingsScreen(vm) }
            composable(
                route = "join?code={code}",
                arguments = listOf(navArgument("code") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                JoinScreen(
                    vm = vm,
                    initialCode = entry.arguments?.getString("code"),
                    onBack = { nav.popBackStack() },
                    onFinished = { nav.switchTab(Tab.HOME.route) },
                )
            }
        }
    }
}
