package com.routine.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.routine.ui.home.HomeScreen
import com.routine.ui.note.NotesScreen
import com.routine.ui.settings.SettingsScreen
import com.routine.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val openTab = intent?.getStringExtra("open_tab")
        val openNote = intent?.getLongExtra("open_note", -1L) ?: -1L

        setContent {
            AppTheme {
                MainScreen(
                    initialTab = openTab,
                    openNoteId = openNote
                )
            }
        }
    }
}

private data class NavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun MainScreen(initialTab: String? = null, openNoteId: Long = -1L) {
    val navController = rememberNavController()
    val startDestination = when (initialTab) {
        "routines" -> "home"
        "notes" -> "notes"
        else -> "home"
    }

    val items = listOf(
        NavItem("home", "Routines", Icons.Filled.Home, Icons.Outlined.Home),
        NavItem("notes", "Notes", Icons.Filled.Edit, Icons.Outlined.Edit),
        NavItem("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                items.forEach { item ->
                    val selected = currentRoute == item.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            composable("home") { HomeScreen() }
            composable("notes") { NotesScreen(highlightNoteId = openNoteId) }
            composable("settings") { SettingsScreen() }
        }
    }
}
