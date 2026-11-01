package com.cutpro.core.timeline

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SnappingEngineTest {

    private val engine = SnappingEngine(defaultSnapThresholdDp = 16f)

    @Test
    fun findSnapPoint_snapsToExactCandidateWithinThreshold() {
        val candidates = listOf(0L, 1_000_000L, 3_000_000L)
        val snap = engine.findSnapPoint(
            movingEdgeUs = 1_020_000L,
            candidatePointsUs = candidates,
            pixelsPerSecond = 100.0f,
            densityDpi = 160.0f,
            snapThresholdDp = 16.0f
        )
        assertThat(snap).isEqualTo(1_000_000L)
    }

    @Test
    fun findSnapPoint_returnsNullBeyondThreshold() {
        val candidates = listOf(0L, 5_000_000L)
        val snap = engine.findSnapPoint(
            movingEdgeUs = 2_500_000L,
            candidatePointsUs = candidates,
            pixelsPerSecond = 100.0f,
            densityDpi = 160.0f,
            snapThresholdDp = 16.0f
        )
        assertThat(snap).isNull()
    }

    @Test
    fun findSnapPoint_selectsClosestCandidate() {
        val candidates = listOf(1_000_000L, 1_100_000L)
        val snap = engine.findSnapPoint(
            movingEdgeUs = 1_080_000L,
            candidatePointsUs = candidates,
            pixelsPerSecond = 100.0f,
            densityDpi = 160.0f,
            snapThresholdDp = 16.0f
        )
        assertThat(snap).isEqualTo(1_100_000L)
    }

    @Test
    fun findSnapPoint_snapsToOrigin() {
        val candidates = listOf(0L, 5_000_000L)
        val snap = engine.findSnapPoint(
            movingEdgeUs = 30_000L,
            candidatePointsUs = candidates,
            pixelsPerSecond = 100.0f,
            densityDpi = 160.0f,
            snapThresholdDp = 16.0f
        )
        assertThat(snap).isEqualTo(0L)
    }

    @Test
    fun findSnapPoint_zeroPixelsPerSecond_returnsNull() {
        val candidates = listOf(1_000_000L)
        val snap = engine.findSnapPoint(
            movingEdgeUs = 1_010_000L,
            candidatePointsUs = candidates,
            pixelsPerSecond = 0f,
            densityDpi = 160.0f
        )
        assertThat(snap).isNull()
    }

    @Test
    fun findSnapPoint_emptyCandidates_returnsNull() {
        val snap = engine.findSnapPoint(
            movingEdgeUs = 1_000_000L,
            candidatePointsUs = emptyList(),
            pixelsPerSecond = 100f,
            densityDpi = 160f
        )
        assertThat(snap).isNull()
    }

    @Test
    fun findSnapPoint_exactThresholdEdge() {
        // threshold_us = (16 * (160/160) / 100) * 1_000_000 = 160_000 us
        val snap = engine.findSnapPoint(
            movingEdgeUs = 1_160_000L,
            candidatePointsUs = listOf(1_000_000L),
            pixelsPerSecond = 100.0f,
            densityDpi = 160.0f,
            snapThresholdDp = 16.0f
        )
        assertThat(snap).isEqualTo(1_000_000L)
    }

    @Test
    fun findSnapPoint_justOutsideThresholdEdge_returnsNull() {
        val snap = engine.findSnapPoint(
            movingEdgeUs = 1_160_001L,
            candidatePointsUs = listOf(1_000_000L),
            pixelsPerSecond = 100.0f,
            densityDpi = 160.0f,
            snapThresholdDp = 16.0f
        )
        assertThat(snap).isNull()
    }

    @Test
    fun findSnapPoint_negativeMovingEdge_snapsToZero() {
        val snap = engine.findSnapPoint(
            movingEdgeUs = -10_000L,
            candidatePointsUs = listOf(0L),
            pixelsPerSecond = 100.0f,
            densityDpi = 160.0f
        )
        assertThat(snap).isEqualTo(0L)
    }

    @Test
    fun findSnapPoint_equidistantCandidates() {
        val snap = engine.findSnapPoint(
            movingEdgeUs = 1_050_000L,
            candidatePointsUs = listOf(1_000_000L, 1_100_000L),
            pixelsPerSecond = 100.0f,
            densityDpi = 160.0f
        )
        assertThat(snap).isIn(listOf(1_000_000L, 1_100_000L))
    }
}
