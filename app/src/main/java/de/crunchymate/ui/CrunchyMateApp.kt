package de.crunchymate.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import de.crunchymate.AppContainer
import de.crunchymate.R
import de.crunchymate.ui.components.CompactNavigationBar
import de.crunchymate.ui.components.CompactNavigationItem
import de.crunchymate.ui.components.EmptyState
import de.crunchymate.ui.detail.DetailDestination
import de.crunchymate.ui.detail.DetailViewModel
import de.crunchymate.ui.intro.IntroDialogHost
import de.crunchymate.ui.licenses.LicensesDestination
import de.crunchymate.ui.licenses.LicensesViewModel
import de.crunchymate.ui.search.AnimeGridDestination
import de.crunchymate.ui.search.GridMode
import de.crunchymate.ui.search.SearchViewModel
import de.crunchymate.ui.settings.SettingsDestination
import de.crunchymate.ui.settings.SettingsViewModel
import de.crunchymate.ui.theme.Dimens
import kotlin.reflect.KClass

private enum class TopLevel(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    Search(SearchRoute, SearchRoute::class, R.string.nav_search, R.drawable.ic_nav_search),
    Watched(WatchedRoute, WatchedRoute::class, R.string.nav_watched, R.drawable.ic_nav_watched),
    Meh(MehRoute, MehRoute::class, R.string.nav_meh, R.drawable.ic_nav_meh),
    Settings(SettingsRoute, SettingsRoute::class, R.string.nav_settings, R.drawable.ic_nav_settings),
}

@Composable
fun CrunchyMateApp(container: AppContainer) {
    val appViewModel = viewModel { AppViewModel(container::initializeCore, { container.settingsStore }) }
    val startup by appViewModel.state.collectAsStateWithLifecycle()
    when (val state = startup) {
        StartupState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is StartupState.Failed ->
            EmptyState(
                icon = R.drawable.ic_state_error,
                title = stringResource(R.string.startup_error_title),
                subtitle = state.reason?.let { stringResource(it) },
            )
        StartupState.Ready -> {
            val syncRunning by container.syncScheduler.anyRunning.collectAsStateWithLifecycle(initialValue = false)
            AppShell(container, syncRunning)
            IntroDialogHost(container.settingsStore)
        }
    }
}

@Composable
private fun AppShell(
    container: AppContainer,
    syncRunning: Boolean,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBottomBar = TopLevel.entries.any { destination.isOn(it) }
    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                CompactNavigationBar {
                    TopLevel.entries.forEach { item ->
                        CompactNavigationItem(
                            selected = destination.isOn(item),
                            onClick = { navController.navigateTopLevel(item.route) },
                            icon = { NavIcon(item, syncRunning) },
                            label = stringResource(item.label),
                        )
                    }
                }
            }
        },
    ) { padding ->
        AppNavHost(container, navController, Modifier.padding(padding))
    }
}

private fun NavDestination?.isOn(item: TopLevel): Boolean = this?.hierarchy?.any { it.hasRoute(item.routeClass) } == true

// Nur die Einstellungen zeigen einen Punkt, solange eine Synchronisation läuft
@Composable
private fun NavIcon(
    item: TopLevel,
    syncRunning: Boolean,
) {
    BadgedBox(badge = { if (item == TopLevel.Settings && syncRunning) Badge() }) {
        Icon(painterResource(item.icon), contentDescription = null, modifier = Modifier.size(Dimens.NavigationIcon))
    }
}

// Tabs behalten ihren Zustand beim Wechsel; Zurück aus einem Tab führt zur Suche
private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppNavHost(
    container: AppContainer,
    navController: NavHostController,
    modifier: Modifier,
) {
    val openDetail: (String) -> Unit = { id -> navController.navigate(DetailRoute(id)) }
    NavHost(navController, startDestination = SearchRoute, modifier = modifier) {
        composable<SearchRoute> { GridTab(container, GridMode.Search, openDetail) }
        composable<WatchedRoute> { GridTab(container, GridMode.Watched, openDetail) }
        composable<MehRoute> { GridTab(container, GridMode.Meh, openDetail) }
        composable<SettingsRoute> {
            SettingsDestination(
                viewModel {
                    SettingsViewModel(
                        container.gateway,
                        container.settingsStore,
                        container.syncScheduler,
                        container.deviceAuthenticator,
                    )
                },
                onOpenLicenses = { navController.navigate(LicensesRoute) },
            )
        }
        composable<LicensesRoute> {
            LicensesDestination(
                viewModel = viewModel { LicensesViewModel(container.noticesSource) },
                onBack = dropUnlessResumed { navController.popBackStack() },
            )
        }
        composable<DetailRoute> { entry ->
            val id = entry.toRoute<DetailRoute>().id
            DetailDestination(
                viewModel = viewModel { DetailViewModel(id, container.gateway, container.settingsStore) },
                // Doppeltippen auf Zurück darf nicht auch den Tab darunter entfernen
                onBack = dropUnlessResumed { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun GridTab(
    container: AppContainer,
    mode: GridMode,
    onOpen: (String) -> Unit,
) {
    // Nach dem ersten Tippen ist der Tab nicht mehr RESUMED; ein zweites Tippen öffnet kein weiteres Detail
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    AnimeGridDestination(
        mode = mode,
        viewModel = viewModel { SearchViewModel(mode, container.gateway, container.settingsStore) },
        onOpen = { id -> if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) onOpen(id) },
    )
}
