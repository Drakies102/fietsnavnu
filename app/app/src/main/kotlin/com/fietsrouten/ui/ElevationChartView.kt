package com.fietsrouten.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.fietsrouten.R

class ElevationChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var elevations: List<Double> = emptyList()
    private var minEle = 0.0
    private var maxEle = 0.0

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
    }

    init {
        val brand = ContextCompat.getColor(context, R.color.brand_primary)
        fillPaint.color = (brand and 0x00FFFFFF) or 0x33000000
        linePaint.color = brand
        labelPaint.color = ContextCompat.getColor(context, R.color.text_secondary)
    }

    fun setElevations(data: List<Double>) {
        elevations = data
        minEle = data.minOrNull() ?: 0.0
        maxEle = data.maxOrNull() ?: 0.0
        invalidate()
    }

    fun hasSignificantElevation(): Boolean =
        elevations.size >= 2 && (maxEle - minEle) >= 5.0

    override fun onDraw(canvas: Canvas) {
        if (elevations.size < 2) return
        val w = width.toFloat()
        val h = height.toFloat()
        val padTop = 12f
        val padBottom = 24f
        val chartH = h - padTop - padBottom
        val range = (maxEle - minEle).takeIf { it > 0 } ?: 1.0

        fun xOf(i: Int) = w * i / (elevations.lastIndex).toFloat()
        fun yOf(ele: Double) = padTop + chartH * (1.0 - (ele - minEle) / range).toFloat()

        val path = Path().apply {
            moveTo(0f, h)
            elevations.forEachIndexed { i, ele -> lineTo(xOf(i), yOf(ele)) }
            lineTo(w, h)
            close()
        }
        canvas.drawPath(path, fillPaint)

        val line = Path().apply {
            elevations.forEachIndexed { i, ele ->
                if (i == 0) moveTo(xOf(i), yOf(ele)) else lineTo(xOf(i), yOf(ele))
            }
        }
        canvas.drawPath(line, linePaint)

        // Min / max labels
        canvas.drawText("${minEle.toInt()}m", 4f, h - 6f, labelPaint)
        canvas.drawText("${maxEle.toInt()}m", 4f, padTop + 20f, labelPaint)
    }
}
