@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.util.DisplayMetrics
import android.util.TypedValue
import java.util.Collections
import java.util.Locale
import java.util.regex.Pattern

object DimensionConverter {

    // -- Initialize dimension string to constant lookup.
    @JvmField
    val dimensionConstantLookup: Map<String, Int> = initDimensionConstantLookup()
    private fun initDimensionConstantLookup(): Map<String, Int> {
        val m: MutableMap<String, Int> = HashMap()
        m["px"] = TypedValue.COMPLEX_UNIT_PX
        m["dip"] = TypedValue.COMPLEX_UNIT_DIP
        m["dp"] = TypedValue.COMPLEX_UNIT_DIP
        m["sp"] = TypedValue.COMPLEX_UNIT_SP
        m["pt"] = TypedValue.COMPLEX_UNIT_PT
        m["in"] = TypedValue.COMPLEX_UNIT_IN
        m["mm"] = TypedValue.COMPLEX_UNIT_MM
        return Collections.unmodifiableMap(m)
    }
    // -- Initialize pattern for dimension string.
    private val DIMENSION_PATTERN = Pattern.compile("^\\s*(\\d+(\\.\\d+)*)\\s*([a-zA-Z]+)\\s*$")

    @JvmStatic
    fun stringToDimensionPixelSize(dimension: String, metrics: DisplayMetrics): Int {
        // -- Mimics TypedValue.complexToDimensionPixelSize(int data, DisplayMetrics metrics).
        val internalDimension = stringToInternalDimension(dimension)
        val value = internalDimension.value
        val f = TypedValue.applyDimension(internalDimension.unit, value, metrics)
        val res = (f + 0.5f).toInt()
        if (res != 0) return res
        if (value == 0f) return 0
        if (value > 0) return 1
        return -1
    }

    @JvmStatic
    fun stringToDimension(dimension: String, metrics: DisplayMetrics): Float {
        // -- Mimics TypedValue.complexToDimension(int data, DisplayMetrics metrics).
        val internalDimension = stringToInternalDimension(dimension)
        return TypedValue.applyDimension(internalDimension.unit, internalDimension.value, metrics)
    }

    private fun stringToInternalDimension(dimension: String): InternalDimension {
        // -- Match target against pattern.
        val matcher = DIMENSION_PATTERN.matcher(dimension)
        if (matcher.matches()) {
            // -- Match found.
            // -- Extract value.
            val value = matcher.group(1)!!.toFloat()
            // -- Extract dimension units.
            val unit = matcher.group(3)!!.lowercase(Locale.ROOT)
            // -- Get Android dimension constant.
            val dimensionUnit = dimensionConstantLookup[unit]
            if (dimensionUnit == null) {
                // -- Invalid format.
                throw NumberFormatException()
            } else {
                // -- Return valid dimension.
                return InternalDimension(value, dimensionUnit)
            }
        } else {
            // -- Invalid format.
            throw NumberFormatException()
        }
    }

    private class InternalDimension(var value: Float, var unit: Int)
}
