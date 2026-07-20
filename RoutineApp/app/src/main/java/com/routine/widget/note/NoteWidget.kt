package com.routine.widget.note

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.routine.data.model.Note
import com.routine.data.model.Reminder
import com.routine.domain.NoteParser
import com.routine.domain.ReminderScheduler
import com.routine.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ─── Widget Provider ─────────────────────────────────────────────────────────

class NoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = NoteWidget()
}

class NoteWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { NoteWidgetContent() }
    }

    @Composable
    fun NoteWidgetContent() {
        val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
        val preview = prefs[stringPreferencesKey("note_preview")] ?: "Tap to add notes + reminders"
        val hasReminders = prefs[booleanPreferencesKey("has_reminders")] ?: false

        val background = ColorProvider(
            day = androidx.compose.ui.graphics.Color(0xFFFDF6E5),
            night = androidx.compose.ui.graphics.Color(0xFF201D15)
        )
        val titleColor = ColorProvider(
            day = androidx.compose.ui.graphics.Color(0xFF875200),
            night = androidx.compose.ui.graphics.Color(0xFFFFB868)
        )
        val bodyColor = ColorProvider(
            day = androidx.compose.ui.graphics.Color(0xFF4A4438),
            night = androidx.compose.ui.graphics.Color(0xFFD9D3C4)
        )

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(background)
                .cornerRadius(20.dp)
                .padding(12.dp)
                .clickable(actionRunCallback<OpenNoteAction>())
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "📝",
                        style = TextStyle(fontSize = 16.sp)
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        text = "Notes",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = titleColor
                        )
                    )
                    if (hasReminders) {
                        Spacer(GlanceModifier.width(4.dp))
                        Text(
                            text = "⏰",
                            style = TextStyle(fontSize = 12.sp)
                        )
                    }
                }
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    text = preview,
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = bodyColor
                    ),
                    maxLines = 4
                )
            }
        }
    }
}

class OpenNoteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val intent = Intent(context, NoteEntryActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("widget_id", appWidgetId)
        }
        context.startActivity(intent)
    }
}

// ─── Note Entry Activity ──────────────────────────────────────────────────────

@AndroidEntryPoint
class NoteEntryActivity : ComponentActivity() {

    @Inject lateinit var noteParser: NoteParser
    @Inject lateinit var reminderScheduler: ReminderScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val widgetId = intent.getIntExtra("widget_id", -1)
        val noteId = intent.getLongExtra("open_note", -1L)

        setContent {
            AppTheme {
                NoteEntryScreen(
                    widgetId = widgetId,
                    noteId = noteId,
                    noteParser = noteParser,
                    reminderScheduler = reminderScheduler,
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEntryScreen(
    widgetId: Int,
    noteId: Long,
    noteParser: NoteParser,
    reminderScheduler: ReminderScheduler,
    onDismiss: () -> Unit
) {
    val entryPoint = com.routine.di.RepositoryEntryPoint.get(
        androidx.compose.ui.platform.LocalContext.current
    )
    val noteRepo = entryPoint.noteRepository()
    val settingsRepo = entryPoint.settingsRepository()

    var noteContent by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var parsedReminders by remember { mutableStateOf<List<com.routine.data.model.ParsedReminder>>(emptyList()) }
    var showParsed by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Load existing note if editing
    LaunchedEffect(noteId) {
        if (noteId != -1L) {
            noteRepo.getNote(noteId)?.let { noteContent = it.content }
        }
        delay(100)
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📝 Note") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    isSaving = true
                                    val apiKey = settingsRepo.getApiKey()
                                    saveNote(
                                        context, noteRepo, noteParser, reminderScheduler,
                                        noteContent, widgetId, noteId, apiKey
                                    )
                                    isSaving = false
                                    onDismiss()
                                }
                            },
                            enabled = noteContent.isNotBlank()
                        ) {
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            TextField(
                value = noteContent,
                onValueChange = {
                    noteContent = it
                    showParsed = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        "Write anything...\n\nExample:\n• Push final changes and raise a PR at EOD - 1600\n• Water plants tomorrow morning",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                textStyle = MaterialTheme.typography.bodyLarge
            )

            // Preview extracted reminders
            AnimatedVisibility(visible = parsedReminders.isNotEmpty() && showParsed) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(
                        "⏰ Reminders to be set:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    parsedReminders.forEach { r ->
                        Text(
                            "• ${r.task} — ${r.relative ?: r.time ?: r.date}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

private suspend fun saveNote(
    context: Context,
    noteRepo: com.routine.data.repository.NoteRepository,
    noteParser: NoteParser,
    reminderScheduler: ReminderScheduler,
    content: String,
    widgetId: Int,
    existingNoteId: Long,
    apiKey: String
) {
    val note = if (existingNoteId != -1L) {
        Note(id = existingNoteId, content = content, widgetId = widgetId)
    } else {
        Note(content = content, widgetId = widgetId)
    }

    val savedId = noteRepo.saveNote(note)
    if (widgetId != -1) noteRepo.assignWidget(savedId, widgetId)

    // Parse reminders via Claude API
    val parsed = noteParser.parse(content, apiKey)
    if (parsed.isNotEmpty()) {
        reminderScheduler.scheduleFromParsed(savedId, parsed)
    }

    // Update widget preview
    updateNoteWidgetState(context, widgetId, content, parsed.isNotEmpty())
}

private suspend fun updateNoteWidgetState(
    context: Context,
    appWidgetId: Int,
    content: String,
    hasReminders: Boolean
) {
    val preview = content.lines().firstOrNull()?.take(80) ?: ""
    val glanceId = GlanceAppWidgetManager(context)
        .getGlanceIds(NoteWidget::class.java)
        .firstOrNull { GlanceAppWidgetManager(context).getAppWidgetId(it) == appWidgetId }
        ?: return

    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
        prefs.toMutablePreferences().apply {
            this[stringPreferencesKey("note_preview")] = preview
            this[booleanPreferencesKey("has_reminders")] = hasReminders
        }
    }
    NoteWidget().update(context, glanceId)
}
