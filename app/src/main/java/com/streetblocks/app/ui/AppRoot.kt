package com.streetblocks.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.streetblocks.app.ui.builder.BuilderScreen
import com.streetblocks.app.ui.equipment.EquipmentScreen
import com.streetblocks.app.ui.history.HistoryScreen
import com.streetblocks.app.ui.home.HomeScreen
import com.streetblocks.app.ui.home.TemplatesScreen
import com.streetblocks.app.ui.programs.ProgramDetailScreen
import com.streetblocks.app.ui.programs.ProgramWizardScreen
import com.streetblocks.app.ui.programs.ProgramsScreen
import com.streetblocks.app.ui.session.SessionScreen
import com.streetblocks.app.ui.settings.SettingsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Accueil", Icons.Filled.Home),
    Tab("programs", "Programmes", Icons.Filled.CalendarMonth),
    Tab("equipment", "Matériel", Icons.Filled.FitnessCenter),
    Tab("history", "Historique", Icons.Filled.History),
    Tab("settings", "Réglages", Icons.Filled.Settings),
)

@Composable
fun AppRoot(openSessionRequest: Boolean, onOpenSessionHandled: () -> Unit) {
    val nav = rememberNavController()
    val container = LocalAppContainer.current

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    LaunchedEffect(openSessionRequest) {
        if (openSessionRequest) {
            val st = container.engine.state.value
            if (st.running || st.finished) nav.navigate("session") { launchSingleTop = true }
            onOpenSessionHandled()
        }
    }

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") {
                HomeScreen(
                    onCreate = { nav.navigate("builder/0") },
                    onEdit = { id -> nav.navigate("builder/$id") },
                    onTemplates = { nav.navigate("templates") },
                    onOpenSession = { nav.navigate("session") { launchSingleTop = true } },
                    onOpenProgram = { id -> nav.navigate("program/$id") },
                )
            }
            composable("programs") {
                ProgramsScreen(
                    onCreate = { nav.navigate("program/new") },
                    onOpen = { id -> nav.navigate("program/$id") },
                )
            }
            composable("program/new") {
                ProgramWizardScreen(
                    onBack = { nav.popBackStack() },
                    onCreated = { id -> nav.navigate("program/$id") { popUpTo("program/new") { inclusive = true } } },
                )
            }
            composable(
                "program/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { e ->
                ProgramDetailScreen(
                    programId = e.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onEditPlanned = { pid -> nav.navigate("planned/$pid") },
                    onOpenSession = { nav.navigate("session") { launchSingleTop = true } },
                )
            }
            composable(
                "planned/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { e ->
                BuilderScreen(
                    workoutId = e.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onOpenSession = { nav.navigate("session") { launchSingleTop = true } },
                    planned = true,
                )
            }
            composable("equipment") { EquipmentScreen() }
            composable("history") { HistoryScreen() }
            composable("settings") { SettingsScreen() }
            composable("templates") {
                TemplatesScreen(
                    onBack = { nav.popBackStack() },
                    onCreated = { id -> nav.navigate("builder/$id") { popUpTo("templates") { inclusive = true } } },
                )
            }
            composable(
                "builder/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { e ->
                BuilderScreen(
                    workoutId = e.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onOpenSession = { nav.navigate("session") { launchSingleTop = true } },
                )
            }
            composable("session") {
                SessionScreen(onExit = { if (!nav.popBackStack()) nav.navigate("home") })
            }
        }
    }
}
