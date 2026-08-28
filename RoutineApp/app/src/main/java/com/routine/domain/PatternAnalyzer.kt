package com.routine.domain

import com.routine.data.model.RoutineType
import com.routine.data.model.TaskLog
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

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
        // Three intervals are the minimum needed to distinguish a cadence from coincidence.
        const val MIN_LOGS_FOR_ANALYSIS = 4
        const val HIGH_CONFIDENCE = 0.8f
        const val LOW_CONFIDENCE = 0.65f
        private const val MAX_RECENT_LOGS = 10
        private const val MIN_INTERVAL_HOURS = 5.0 / 60.0
    }

    /**
     * Infers cadence from recent completions using medians and median absolute
     * deviation so one missed or accidental completion cannot skew the result.
     */
    fun analyze(
        logs: List<TaskLog>,
        timeZone: TimeZone = TimeZone.getDefault()
    ): PatternResult? {
        val sorted = logs
            .distinctBy(TaskLog::timestamp)
            .sortedBy(TaskLog::timestamp)
            .takeLast(MAX_RECENT_LOGS)
        if (sorted.size < MIN_LOGS_FOR_ANALYSIS) return null

        val rawIntervals = sorted.zipWithNext { first, second ->
            (second.timestamp - first.timestamp) / 3_600_000.0
        }.filter { it >= MIN_INTERVAL_HOURS && it.isFinite() }
        if (rawIntervals.size < MIN_LOGS_FOR_ANALYSIS - 1) return null

        val medianInterval = rawIntervals.median()
        if (medianInterval <= 0.0) return null

        val deviations = rawIntervals.map { abs(it - medianInterval) }
        val mad = deviations.median()
        val tolerance = maxOf(medianInterval * 0.35, mad * 3.0, MIN_INTERVAL_HOURS)
        val inliers = rawIntervals.filter { abs(it - medianInterval) <= tolerance }
        if (inliers.size < 2) return null
        val inlierCoverage = inliers.size.toFloat() / rawIntervals.size
        if (inlierCoverage < 0.7f) return null

        val robustInterval = inliers.median()
        val normalizedMad = inliers.map { abs(it - robustInterval) }.median() / robustInterval
        val intervalConsistency = (1.0 - normalizedMad / 0.5)
            .coerceIn(0.0, 1.0)
            .toFloat()
        val hours = sorted.map { timestampHour(it.timestamp, timeZone) }
        val (preferredHour, timeConsistency) = circularHourStats(hours)

        val normalizedLocations = sorted.mapNotNull { log ->
            log.locationLabel?.trim()?.takeIf(String::isNotEmpty)?.lowercase()?.let { it to log }
        }
        val dominantGroup = normalizedLocations.groupBy({ it.first }, { it.second })
            .maxByOrNull { it.value.size }
        val locationCoverage = dominantGroup?.value?.size?.toFloat()?.div(sorted.size) ?: 0f
        val dominantLog = dominantGroup?.value?.firstOrNull()
        val dominantLocation = dominantLog?.locationLabel?.trim()

        // Cadence is the core signal; time and location only strengthen it.
        val confidence = (
            intervalConsistency * 0.65f +
                inlierCoverage * 0.15f +
                timeConsistency * 0.15f +
                locationCoverage * 0.05f
            ).coerceIn(0f, 1f)

        val type = when {
            locationCoverage >= 0.8f && dominantLocation != null -> RoutineType.LOCATION_TRIGGERED
            intervalConsistency >= 0.75f -> RoutineType.STRICT
            else -> RoutineType.LOOSE
        }

        return PatternResult(
            intervalHours = robustInterval.roundToInt().coerceAtLeast(1),
            preferredHourOfDay = preferredHour,
            locationLabel = dominantLocation,
            lat = dominantLog?.lat,
            lng = dominantLog?.lng,
            confidence = confidence,
            suggestedType = type
        )
    }

    fun shouldPropose(result: PatternResult): Boolean = result.confidence >= LOW_CONFIDENCE

    private fun timestampHour(timestamp: Long, timeZone: TimeZone): Double {
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = timestamp }
        return calendar.get(Calendar.HOUR_OF_DAY) + calendar.get(Calendar.MINUTE) / 60.0
    }

    private fun circularHourStats(hours: List<Double>): Pair<Int, Float> {
        val angles = hours.map { it / 24.0 * 2.0 * PI }
        val meanSin = angles.map(::sin).average()
        val meanCos = angles.map(::cos).average()
        var angle = atan2(meanSin, meanCos)
        if (angle < 0) angle += 2.0 * PI
        val hour = ((angle / (2.0 * PI) * 24.0).roundToInt() % 24 + 24) % 24
        return hour to hypot(meanSin, meanCos).toFloat().coerceIn(0f, 1f)
    }

    private fun List<Double>.median(): Double {
        val ordered = sorted()
        val middle = ordered.size / 2
        return if (ordered.size % 2 == 0) {
            (ordered[middle - 1] + ordered[middle]) / 2.0
        } else {
            ordered[middle]
        }
    }
}
