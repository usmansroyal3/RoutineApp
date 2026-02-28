package com.routine.domain

import com.routine.data.model.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

data class PatternResult(
    val intervalHours: Int,
    val preferredHourOfDay: Int,
    val locationLabel: String?,
    val lat: Double?,
    val lng: Double?,
    val confidence: Float,
    val suggestedType: RoutineType
)

@Singleton
class PatternAnalyzer @Inject constructor() {

    companion object {
        const val MIN_LOGS_FOR_ANALYSIS = 3
        const val HIGH_CONFIDENCE = 0.7f
        const val LOW_CONFIDENCE = 0.4f
    }

    /**
     * Analyzes task logs and returns a pattern result if one is found,
     * or null if there's insufficient data or no detectable pattern.
     */
    fun analyze(logs: List<TaskLog>): PatternResult? {
        if (logs.size < MIN_LOGS_FOR_ANALYSIS) return null

        val sorted = logs.sortedBy { it.timestamp }

        // Compute intervals between consecutive logs (in hours)
        val intervals = sorted.zipWithNext { a, b ->
            (b.timestamp - a.timestamp) / 3_600_000.0
        }

        val avgInterval = intervals.average()
        val stddev = stddev(intervals)
        val intervalConsistency = 1f - (stddev / avgInterval).coerceIn(0.0, 1.0).toFloat()

        // Preferred hour of day
        val hours = sorted.map { tsToHour(it.timestamp) }
        val avgHour = hours.average().toInt()
        val hourStddev = stddev(hours.map { it.toDouble() })
        val timeConsistency = 1f - (hourStddev / 12.0).coerceIn(0.0, 1.0).toFloat()

        // Location consistency
        val locations = sorted.mapNotNull { it.locationLabel }
        val locationConsistency = if (locations.size >= sorted.size * 0.8) {
            val dominant = locations.groupingBy { it }.eachCount().maxByOrNull { it.value }
            if (dominant != null && dominant.value >= sorted.size * 0.7) 1f else 0.3f
        } else 0f

        val dominantLocation = logs.mapNotNull { it.locationLabel }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key

        val representativeLog = logs.filter { it.locationLabel == dominantLocation }.firstOrNull()

        // Weighted confidence
        val confidence = (intervalConsistency * 0.5f + timeConsistency * 0.3f + locationConsistency * 0.2f)
            .coerceIn(0f, 1f)

        val type = when {
            locationConsistency >= 0.8f && dominantLocation != null -> RoutineType.LOCATION_TRIGGERED
            intervalConsistency >= 0.7f -> RoutineType.STRICT
            else -> RoutineType.LOOSE
        }

        return PatternResult(
            intervalHours = avgInterval.toInt().coerceAtLeast(1),
            preferredHourOfDay = avgHour,
            locationLabel = dominantLocation,
            lat = representativeLog?.lat,
            lng = representativeLog?.lng,
            confidence = confidence,
            suggestedType = type
        )
    }

    fun shouldPropose(result: PatternResult): Boolean = result.confidence >= LOW_CONFIDENCE

    private fun tsToHour(timestamp: Long): Int {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timestamp
        return cal.get(java.util.Calendar.HOUR_OF_DAY)
    }

    private fun stddev(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }
}
