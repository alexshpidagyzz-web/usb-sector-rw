package com.example.usb_sector_rw

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.min

class FrequencyGaugeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class DisplayUnit {
        HERTZ,
        RENTGEN
    }

    var displayUnit: DisplayUnit = DisplayUnit.HERTZ
        set(value) {
            field = value
            invalidate()
        }

    var frequency: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    var error: Float? = null // null = нет погрешности
        set(value) {
            field = value
            invalidate()
        }

    var accuracy: UInt = 1u
        set(value) {
            field = value
            invalidate()
        }

    var colorZoneProvider: ((Float) -> Int)? = null

    private val paintArc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 60f
        strokeCap = Paint.Cap.ROUND
    }

    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val radius = min(w, h) / 2 - 100f
        val cx = w / 2
        val cy = h / 2 + 80f
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        var displayValue = frequency

        paintArc.color = Color.LTGRAY
        canvas.drawArc(rect, 180f, 180f, false, paintArc)

        val minValue = 1f
        val maxValue = 10_000f

        val clampedValue = displayValue.coerceIn(minValue, maxValue)
        val logMin = log10(minValue)
        val logMax = log10(maxValue)
        val logValue = log10(clampedValue)

        displayValue = when (displayUnit) {
            DisplayUnit.HERTZ -> frequency
            DisplayUnit.RENTGEN -> frequency * DOEZ_COEFF
        }

        val normalized = (logValue - logMin) / (logMax - logMin)
        val angle = normalized.coerceIn(0f, 1f) * 180f

        paintArc.color = colorZoneProvider?.invoke(frequency) ?: Color.GRAY
        canvas.drawArc(rect, 180f, angle, false, paintArc)

        val freqLabel = when (displayUnit) {
            DisplayUnit.HERTZ -> if (frequency >= 1000f)
                String.format("%.${accuracy}f кГц", frequency / 1000f)
            else
                String.format("%.${accuracy}f Гц", frequency)

            DisplayUnit.RENTGEN -> {
                val rentgen = frequency * DOEZ_COEFF
                when {
                    rentgen >= 1000f -> String.format("%.${accuracy}f кР", rentgen / 1000f)
                    rentgen >= 1f -> String.format("%.${accuracy}f Р", rentgen)
                    rentgen >= 0.001f -> String.format("%.${accuracy}f мР", rentgen * 1000f)
                    rentgen >= 0.000001f -> String.format("%.${accuracy}f мкР", rentgen * 1_000_000f)
                    else -> String.format("%.${accuracy}f нР", rentgen * 1_000_000_000f)
                }
            }
        }

        fun formatWithSignificantDigits(value: Float, digits: Int, unit: String): String {
            if (value == 0.0f) {
                return "± 0 $unit"
            }

            val absValue = kotlin.math.abs(value)
            val intDigits = kotlin.math.floor(kotlin.math.log10(absValue)).toInt() + 1
            val fracDigits = (digits - intDigits).coerceAtLeast(0)

            return String.format("± %.${fracDigits}f %s", value, unit)
        }

        val errLabel = error?.let {
            val errValue = when (displayUnit) {
                DisplayUnit.HERTZ -> it
                DisplayUnit.RENTGEN -> it * DOEZ_COEFF
            }

            val digits = floor(log10((displayValue / errValue).toDouble())).toInt().coerceAtLeast(1)

            val (scaledErr, unit) = when (displayUnit) {
                DisplayUnit.HERTZ -> {
                    if (errValue >= 1000f)
                        errValue / 1000f to "кГц"
                    else
                        errValue to "Гц"
                }

                DisplayUnit.RENTGEN -> {
                    when {
                        errValue >= 1000f -> errValue / 1000f to "кР"
                        errValue >= 1f -> errValue to "Р"
                        errValue >= 0.001f -> errValue * 1000f to "мР"
                        errValue >= 0.000001f -> errValue * 1_000_000f to "мкР"
                        else -> errValue * 1_000_000_000f to "нР"
                    }
                }
            }

            formatWithSignificantDigits(scaledErr, digits, unit)
        }

        val scaleMap = mapOf(
            0u to 1.00f,
            1u to 0.95f,
            2u to 0.90f,
            3u to 0.85f,
            4u to 0.80f,
            5u to 0.75f,
            7u to 0.70f
        )
        val scale = scaleMap[accuracy] ?: 0.75f

        val maxTextWidth = radius * 1.8f
        val baseTextSizeFreq = h / 8f
        paintText.textSize = baseTextSizeFreq * scale

        var textSizeFreq = paintText.textSize
        while (paintText.measureText(freqLabel) > maxTextWidth && textSizeFreq > 10f) {
            textSizeFreq *= 0.95f
            paintText.textSize = textSizeFreq
        }
        canvas.drawText(freqLabel, cx, cy - radius / 10, paintText)

        errLabel?.let {
            val baseTextSizeErr = h / 12f
            paintText.textSize = baseTextSizeErr * scale

            var textSizeErr = paintText.textSize
            while (paintText.measureText(it) > maxTextWidth && textSizeErr > 10f) {
                textSizeErr *= 0.95f
                paintText.textSize = textSizeErr
            }
            canvas.drawText(it, cx, cy - radius / 10 + h / 10f, paintText)
        }
    }
}