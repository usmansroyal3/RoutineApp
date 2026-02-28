package com.routine.domain

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.routine.data.model.ParsedReminder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

// ─── Claude API DTOs ─────────────────────────────────────────────────────────

data class ClaudeMessage(val role: String, val content: String)
data class ClaudeRequest(
    val model: String = "claude-haiku-4-5-20251001",
    val max_tokens: Int = 1024,
    val messages: List<ClaudeMessage>
)
data class ClaudeContentBlock(val type: String, val text: String?)
data class ClaudeResponse(val content: List<ClaudeContentBlock>)

interface ClaudeApiService {
    @POST("v1/messages")
    suspend fun sendMessage(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = "2023-06-01",
        @Body request: ClaudeRequest
    ): ClaudeResponse
}

// ─── NoteParser ──────────────────────────────────────────────────────────────

@Singleton
class NoteParser @Inject constructor() {

    private val gson = Gson()

    private fun buildClient(): ClaudeApiService {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        return Retrofit.Builder()
            .baseUrl("https://api.anthropic.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ClaudeApiService::class.java)
    }

    suspend fun parse(noteContent: String, apiKey: String): List<ParsedReminder> {
        if (apiKey.isBlank()) return emptyList()

        val now = Date()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        val prompt = """
Extract all reminders from the following note. Return ONLY a valid JSON array, no explanation.
Each item must have:
- "task": string (what needs to be done)
- "date": "today" | "tomorrow" | "YYYY-MM-DD"
- "time": "HH:mm" in 24-hour format, or null if not specified
- "relative": the original time phrase used (e.g. "EOD", "in 2 hours"), or null
- "recurring": true or false

Rules:
- EOD means 17:00
- morning means 09:00
- afternoon means 14:00
- evening means 19:00
- night means 21:00
- If no date is given, assume today
- If no time is given, set time to null

Current date: ${dateFormat.format(now)}
Current time: ${timeFormat.format(now)}

Note:
$noteContent

Return only the JSON array. Example: [{"task":"Push changes","date":"today","time":"16:00","relative":"EOD","recurring":false}]
        """.trimIndent()

        return try {
            val api = buildClient()
            val response = api.sendMessage(
                apiKey = apiKey,
                request = ClaudeRequest(
                    messages = listOf(ClaudeMessage(role = "user", content = prompt))
                )
            )
            val text = response.content.firstOrNull { it.type == "text" }?.text ?: return emptyList()
            parseJsonArray(text)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseJsonArray(raw: String): List<ParsedReminder> {
        return try {
            // Strip markdown code fences if present
            val cleaned = raw.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()

            val array = JsonParser.parseString(cleaned).asJsonArray
            array.map { element ->
                val obj = element.asJsonObject
                ParsedReminder(
                    task = obj.get("task")?.asString ?: "",
                    date = obj.get("date")?.asString ?: "today",
                    time = obj.get("time")?.takeIf { !it.isJsonNull }?.asString,
                    relative = obj.get("relative")?.takeIf { !it.isJsonNull }?.asString,
                    recurring = obj.get("recurring")?.asBoolean ?: false
                )
            }.filter { it.task.isNotBlank() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
