package com.routine.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(initialTab: String? = null, openNoteId: Long = -1L) {
    val navController = rememberNavController()
    val startDestination = when (initialTab) {
        "routines" -> "home"
        "notes" -> "notes"
        else -> "home"
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = { navController.navigate("home") },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Tasks") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("notes") },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    label = { Text("Notes") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("settings") },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") }
                )
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
