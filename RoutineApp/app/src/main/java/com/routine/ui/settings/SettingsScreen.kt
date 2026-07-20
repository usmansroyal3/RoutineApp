package com.routine.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routine.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val apiKey: StateFlow<String> = settingsRepository.apiKeyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun saveApiKey(key: String) = viewModelScope.launch {
        settingsRepository.saveApiKey(key)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val apiKey by vm.apiKey.collectAsState()
    var apiKeyInput by remember(apiKey) { mutableStateOf(apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // API Key section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "🤖 Claude API Key",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Required for smart reminder parsing in notes. Get yours at console.anthropic.com",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = {
                            apiKeyInput = it
                            saved = false
                        },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showKey)
                            VisualTransformation.None
                        else
                            PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            vm.saveApiKey(apiKeyInput.trim())
                            saved = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (saved) "✓ Saved" else "Save API Key")
                    }
                }
            }

            // How it works
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("How it works", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Task widgets: Tap when you do a task. After 3+ taps, the app detects your pattern and proposes a routine.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Note widgets: Tap to open a note pad. Write anything — times and tasks are extracted automatically and reminders are set.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Gentle nudges: When a routine is overdue you get one reminder around your usual time, then a spaced-out follow-up or two — never hourly nagging, and never at night (22:00–08:00). Mark it done or snooze it right from the notification.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text(
                "RoutineApp v1.0 — built for forgetful humans 🌱",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}
