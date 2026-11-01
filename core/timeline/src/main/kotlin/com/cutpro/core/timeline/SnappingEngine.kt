package com.cutpro.core.timeline

import kotlin.math.abs

/**
 * Magnetic snapping engine calculating candidate snap points across the multitrack timeline.
 * Snaps moving clip edges to playhead, adjacent clips, project boundaries, or zero.
 */
class SnappingEngine(
    val defaultSnapThresholdDp: Float = 16f
) {
    /**
     * Finds the closest candidate point within the snapping threshold.
     *
     * @param movingEdgeUs Position of the moving edge in microseconds.
     * @param candidatePointsUs Candidate snap points in microseconds.
     * @param pixelsPerSecond Timeline zoom scale (pixels per second).
     * @param densityDpi Display density in DPI.
     * @param snapThresholdDp Threshold distance in density-independent pixels (dp).
     * @return Snapped position in microseconds, or null if no candidate within threshold.
     */
    fun findSnapPoint(
        movingEdgeUs: Long,
        candidatePointsUs: List<Long>,
        pixelsPerSecond: Float,
        densityDpi: Float,
        snapThresholdDp: Float = defaultSnapThresholdDp
    ): Long? {
        if (pixelsPerSecond <= 0f || candidatePointsUs.isEmpty()) {
            return null
        }

        val dpToPixels = snapThresholdDp.toDouble() * (densityDpi.toDouble() / 160.0)
        val thresholdUs = kotlin.math.round((dpToPixels / pixelsPerSecond.toDouble()) * 1_000_000.0).toLong()


        var closestDistanceUs = Long.MAX_VALUE
        var bestSnapPoint: Long? = null

        for (candidate in candidatePointsUs) {
            val distance = abs(movingEdgeUs - candidate)
            if (distance <= thresholdUs && distance < closestDistanceUs) {
                closestDistanceUs = distance
                bestSnapPoint = candidate
            }
        }

        return bestSnapPoint
    }
}
