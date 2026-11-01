package com.cutpro.core.motiongraphics.evaluator

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

object LinearRgbColorInterpolator {

    private const val GAMMA = 2.2
    private const val INV_GAMMA = 1.0 / 2.2

    fun interpolate(argb1: Int, argb2: Int, fraction: Float): Int {
        val t = max(0.0f, min(1.0f, fraction)).toDouble()

        val a1 = ((argb1 ushr 24) and 0xFF) / 255.0
        val r1 = ((argb1 ushr 16) and 0xFF) / 255.0
        val g1 = ((argb1 ushr 8) and 0xFF) / 255.0
        val b1 = (argb1 and 0xFF) / 255.0

        val a2 = ((argb2 ushr 24) and 0xFF) / 255.0
        val r2 = ((argb2 ushr 16) and 0xFF) / 255.0
        val g2 = ((argb2 ushr 8) and 0xFF) / 255.0
        val b2 = (argb2 and 0xFF) / 255.0

        // Convert sRGB to Linear RGB
        val r1Lin = r1.pow(GAMMA)
        val g1Lin = g1.pow(GAMMA)
        val b1Lin = b1.pow(GAMMA)

        val r2Lin = r2.pow(GAMMA)
        val g2Lin = g2.pow(GAMMA)
        val b2Lin = b2.pow(GAMMA)

        // Linear interpolation in Linear space
        val aInterp = a1 + (a2 - a1) * t
        val rInterp = (r1Lin + (r2Lin - r1Lin) * t).coerceAtLeast(0.0).pow(INV_GAMMA)
        val gInterp = (g1Lin + (g2Lin - g1Lin) * t).coerceAtLeast(0.0).pow(INV_GAMMA)
        val bInterp = (b1Lin + (b2Lin - b1Lin) * t).coerceAtLeast(0.0).pow(INV_GAMMA)

        val aOut = (aInterp.coerceIn(0.0, 1.0) * 255.0).roundToInt()
        val rOut = (rInterp.coerceIn(0.0, 1.0) * 255.0).roundToInt()
        val gOut = (gInterp.coerceIn(0.0, 1.0) * 255.0).roundToInt()
        val bOut = (bInterp.coerceIn(0.0, 1.0) * 255.0).roundToInt()

        return (aOut shl 24) or (rOut shl 16) or (gOut shl 8) or bOut
    }

    fun interpolate(argb1: Long, argb2: Long, fraction: Float): Long {
        val resultInt = interpolate(argb1.toInt(), argb2.toInt(), fraction)
        return resultInt.toLong() and 0xFFFFFFFFL
    }
}
