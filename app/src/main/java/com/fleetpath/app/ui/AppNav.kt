package com.fleetpath.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fleetpath.app.FleetPathApp
import com.fleetpath.app.data.AppSettings
import com.fleetpath.app.ui.screens.ActiveTripScreen
import com.fleetpath.app.ui.screens.AlertsScreen
import com.fleetpath.app.ui.screens.InsightsScreen
import com.fleetpath.app.ui.screens.LiveScreen
import com.fleetpath.app.ui.screens.PermissionsScreen
import com.fleetpath.app.ui.screens.PlanRouteScreen
import com.fleetpath.app.ui.screens.SettingsScreen
import com.fleetpath.app.ui.screens.SignInScreen
import com.fleetpath.app.ui.screens.TripDetailScreen
import com.fleetpath.app.ui.screens.TripHistoryScreen
import com.fleetpath.app.ui.theme.FP
import com.fleetpath.app.util.LatLon
import kotlinx.coroutines.flow.StateFlow

@Composable
fun rememberApp(): FleetPathApp {
    val context = LocalContext.current
    return remember { context.applicationContext as FleetPathApp }
}

object Routes {
    const val SIGN_IN = "signin"
    const val PERMISSIONS = "permissions"
    const val LIVE = "live"
    const val TRIPS = "trips"
    const val PLAN = "plan"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"
    const val ALERTS = "alerts"
    const val ACTIVE = "active"
    const val TRIP = "trip/{id}"
    fun trip(id: Long) = "trip/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.LIVE, "Live", Icons.Outlined.LocationOn),
    Tab(Routes.TRIPS, "Trips", Icons.Outlined.History),
    Tab(Routes.PLAN, "Plan", Icons.Outlined.Explore),
    Tab(Routes.INSIGHTS, "Insights", Icons.Outlined.BarChart),
    Tab(Routes.SETTINGS, "Settings", Icons.Outlined.Settings),
)

@Composable
fun FleetPathRoot(openTrip: StateFlow<Long?>, onTripOpened: () -> Unit) {
    val app = rememberApp()
    val settingsState = app.settings.settings.collectAsStateWithLifecycle<AppSettings?>(initialValue = null)
    val settings = settingsState.value
    if (settings == null) {
        Box(Modifier.fillMaxSize().background(Color.White))
        return
    }
    val start = remember {
        when {
            !settings.signedIn -> Routes.SIGN_IN
            !settings.onboarded -> Routes.PERMISSIONS
            else -> Routes.LIVE
        }
    }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    val pendingTrip by openTrip.collectAsStateWithLifecycle()
    LaunchedEffect(pendingTrip, settings.signedIn) {
        val id = pendingTrip
        if (id != null && settings.signedIn) {
            nav.navigate(Routes.trip(id))
            onTripOpened()
        }
    }

    fun goTab(target: String) {
        nav.navigate(target) {
            popUpTo(Routes.LIVE) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            if (tabs.any { it.route == route }) {
                Column {
                    HorizontalDivider(color = FP.Line)
                    NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = { goTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = FP.Blue,
                                    selectedTextColor = FP.Blue,
                                    indicatorColor = FP.BlueSoft,
                                    unselectedIconColor = FP.Subtle,
                                    unselectedTextColor = FP.Subtle,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = start, modifier = Modifier.padding(padding)) {
            composable(Routes.SIGN_IN) {
                SignInScreen(onDone = {
                    nav.navigate(Routes.PERMISSIONS) { popUpTo(Routes.SIGN_IN) { inclusive = true } }
                })
            }
            composable(Routes.PERMISSIONS) {
                PermissionsScreen(onContinue = {
                    if (nav.previousBackStackEntry != null) {
                        nav.popBackStack()
                    } else {
                        nav.navigate(Routes.LIVE) { popUpTo(Routes.PERMISSIONS) { inclusive = true } }
                    }
                })
            }
            composable(Routes.LIVE) {
                LiveScreen(
                    onAlerts = { nav.navigate(Routes.ALERTS) },
                    onActiveTrip = { nav.navigate(Routes.ACTIVE) { launchSingleTop = true } },
                    onPlan = { goTab(Routes.PLAN) },
                    onPermissions = { nav.navigate(Routes.PERMISSIONS) },
                )
            }
            composable(Routes.ACTIVE) {
                ActiveTripScreen(
                    onBack = { nav.popBackStack() },
                    onSaved = { id -> nav.navigate(Routes.trip(id)) { popUpTo(Routes.ACTIVE) { inclusive = true } } },
                )
            }
            composable(Routes.TRIPS) {
                TripHistoryScreen(onOpenTrip = { nav.navigate(Routes.trip(it)) })
            }
            composable(Routes.TRIP, arguments = listOf(navArgument("id") { type = NavType.LongType })) { backStackEntry ->
                TripDetailScreen(
                    tripId = backStackEntry.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onPlanAgain = { goTab(Routes.PLAN) },
                )
            }
            composable(Routes.PLAN) {
                PlanRouteScreen(
                    onStarted = { nav.navigate(Routes.ACTIVE) { launchSingleTop = true } },
                    onPermissions = { nav.navigate(Routes.PERMISSIONS) },
                )
            }
            composable(Routes.INSIGHTS) { InsightsScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onPermissions = { nav.navigate(Routes.PERMISSIONS) },
                    onSignedOut = { nav.navigate(Routes.SIGN_IN) { popUpTo(nav.graph.id) { inclusive = true } } },
                )
            }
            composable(Routes.ALERTS) {
                AlertsScreen(onBack = { nav.popBackStack() }, onOpenTrip = { nav.navigate(Routes.trip(it)) })
            }
        }
    }
}

/** Opens turn-by-turn navigation in Google Maps, or any maps app as a fallback. */
fun openNavigation(context: Context, dest: LatLon) {
    val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${dest.lat},${dest.lon}"))
        .setPackage("com.google.android.apps.maps")
    try {
        context.startActivity(nav)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${dest.lat},${dest.lon}?q=${dest.lat},${dest.lon}")))
        } catch (e2: ActivityNotFoundException) {
            // No maps app installed.
        }
    }
}
