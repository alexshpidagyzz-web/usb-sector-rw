package com.example.usb_sector_rw

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.appcompat.app.AppCompatDelegate
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

class CustomGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val points = mutableListOf<Pair<Long, Float>>() // X = timeMillis, Y = value

    private val paint = Paint().apply {
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val axisPaint = Paint().apply {
        strokeWidth = 2f
        textSize = 32f
        isAntiAlias = true
    }

    var isVertical: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private var minX = Long.MAX_VALUE
    private var maxX = Long.MIN_VALUE
    private var minY = Float.MAX_VALUE
    private var maxY = Float.MIN_VALUE

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        updateColorsBasedOnTheme()
    }

    private fun updateColorsBasedOnTheme() {
        val isDarkTheme = when (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
            android.content.res.Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }

        val lineColor = if (isDarkTheme) Color.CYAN else Color.BLUE
        val axisColor = if (isDarkTheme) Color.WHITE else Color.BLACK

        paint.color = lineColor
        axisPaint.color = axisColor
    }

    fun addPoint(value: Float) {
        val time = System.currentTimeMillis()
        points.add(Pair(time, value))

        minX = minOf(minX, time)
        maxX = maxOf(maxX, time)
        minY = minOf(minY, value)
        maxY = maxOf(maxY, value)

        if (minX == maxX) maxX += 1000
        if (minY == maxY) maxY += 1f

        invalidate()
    }

    fun clearPoints() {
        points.clear()
        minX = Long.MAX_VALUE
        maxX = Long.MIN_VALUE
        minY = Float.MAX_VALUE
        maxY = Float.MIN_VALUE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < 2) return

        val paddingX = width * 0.07f
        val paddingY = height * 0.07f
        val graphWidth = width - 2 * paddingX
        val graphHeight = height - 2 * paddingY

        val timeSpan = (maxX - minX).toFloat()
        val valueSpan = (maxY - minY)

        val step = max(1, points.size / (width / 5))

        for (i in step until points.size step step) {
            val (prevTime, prevValue) = points[i - step]
            val (currTime, currValue) = points[i]

            val (x1, y1, x2, y2) = if (isVertical) {
                val x1 = paddingX + ((prevTime - minX) / timeSpan) * graphWidth
                val y1 = height - paddingY - ((prevValue - minY) / valueSpan) * graphHeight
                val x2 = paddingX + ((currTime - minX) / timeSpan) * graphWidth
                val y2 = height - paddingY - ((currValue - minY) / valueSpan) * graphHeight
                arrayOf(x1, y1, x2, y2)
            } else {
                val x1 = paddingX + ((prevValue - minY) / valueSpan) * graphWidth
                val y1 = height - paddingY - ((prevTime - minX) / timeSpan) * graphHeight
                val x2 = paddingX + ((currValue - minY) / valueSpan) * graphWidth
                val y2 = height - paddingY - ((currTime - minX) / timeSpan) * graphHeight
                arrayOf(x1, y1, x2, y2)
            }

            canvas.drawLine(x1, y1, x2, y2, paint)
        }

        // Оси и подписи
        if (isVertical) {
            drawVerticalAxes(canvas, paddingX, paddingY, graphWidth, graphHeight)
        } else {
            drawHorizontalAxes(canvas, paddingX, paddingY, graphWidth, graphHeight)
        }
    }

    private fun drawVerticalAxes(canvas: Canvas, paddingX: Float, paddingY: Float, graphWidth: Float, graphHeight: Float) {
        // Подпись оси X — время
        val axisLabel = "Время"
        val axisLabelWidth = axisPaint.measureText(axisLabel)
        canvas.drawText(
            axisLabel,
            width - paddingX - axisLabelWidth,
            height.toFloat() - 10f,
            axisPaint
        )

        canvas.save()
        canvas.rotate(-90f, 20f, height / 2f)
        canvas.drawText("Значение", 20f, height / 2f, axisPaint)
        canvas.restore()

        val labelStep = max(1, points.size / 5)
        for (i in points.indices step labelStep) {
            val (time, _) = points[i]
            val x = paddingX + ((time - minX).toFloat() / (maxX - minX)) * graphWidth
            val label = timeFormat.format(Date(time))
            val textWidth = axisPaint.measureText(label)
            canvas.save()
            canvas.rotate(-75f, x, height - paddingY + 10f)
            canvas.drawText(label, x - textWidth / 2, height - paddingY + 10f, axisPaint)
            canvas.restore()
        }

        val yStep = (maxY - minY) / 4
        for (i in 0..4) {
            val value = minY + i * yStep
            val y = height - paddingY - ((value - minY) / (maxY - minY)) * graphHeight
            canvas.drawText(String.format("%.2f", value), 10f, y + 10f, axisPaint)
        }
    }

    private fun drawHorizontalAxes(canvas: Canvas, paddingX: Float, paddingY: Float, graphWidth: Float, graphHeight: Float) {
        // Подпись оси X — значение
        val axisLabel = "Значение"
        val axisLabelWidth = axisPaint.measureText(axisLabel)
        canvas.drawText(
            axisLabel,
            width - paddingX - axisLabelWidth,
            height.toFloat() - 10f,
            axisPaint
        )

        canvas.save()
        canvas.rotate(-90f, 20f, height / 2f)
        canvas.drawText("Время", 20f, height / 2f, axisPaint)
        canvas.restore()

        val labelStep = max(1, points.size / 5)
        for (i in points.indices step labelStep) {
            val (time, _) = points[i]
            val y = height - paddingY - ((time - minX).toFloat() / (maxX - minX)) * graphHeight
            val label = timeFormat.format(Date(time))
            val textWidth = axisPaint.measureText(label)
            canvas.save()
            canvas.rotate(-75f, paddingX - 5f, y)
            canvas.drawText(label, paddingX - textWidth - 10f, y + 10f, axisPaint)
            canvas.restore()
        }

        val xStep = (maxY - minY) / 4
        for (i in 0..4) {
            val value = minY + i * xStep
            val x = paddingX + ((value - minY) / (maxY - minY)) * graphWidth
            canvas.drawText(String.format("%.2f", value), x - 20f, height - 5f, axisPaint)
        }
    }
}