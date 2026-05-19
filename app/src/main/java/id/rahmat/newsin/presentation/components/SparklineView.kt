package id.rahmat.newsin.presentation.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import id.rahmat.newsin.R
import kotlin.math.abs
import kotlin.math.max

class SparklineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        alpha = 24
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0x223C4650
    }
    private var points: List<Float> = emptyList()
    private var positive = true
    private var detailMode = false

    fun submit(values: List<Float>, isPositive: Boolean, largeChart: Boolean = false) {
        points = values
        positive = isPositive
        detailMode = largeChart
        val color = if (largeChart) 0xFF0C6DB3.toInt() else context.getColor(if (isPositive) R.color.newsin_positive else R.color.newsin_negative)
        linePaint.color = color
        fillPaint.color = color
        linePaint.strokeWidth = if (largeChart) 3f else 4f
        fillPaint.alpha = if (largeChart) 18 else 24
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < 2 || width == 0 || height == 0) return

        val minValue = points.minOrNull() ?: return
        val maxValue = points.maxOrNull() ?: return
        val rawRange = maxValue - minValue
        val reference = max(abs(maxValue), abs(minValue)).coerceAtLeast(0.000001f)
        val range = if (rawRange <= reference * 0.000001f) reference * 0.01f else rawRange
        val stepX = width.toFloat() / (points.size - 1)
        val topPad = 6f
        val bottomPad = 6f
        val chartHeight = max(1f, height - topPad - bottomPad)
        val path = Path()

        if (detailMode) {
            repeat(5) { index ->
                val y = topPad + (chartHeight * index / 4f)
                canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            }
        }

        points.forEachIndexed { index, value ->
            val x = index * stepX
            val normalized = if (rawRange <= reference * 0.000001f) 0.5f else (value - minValue) / range
            val y = height - bottomPad - (normalized * chartHeight)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        val fill = Path(path).apply {
            lineTo(width.toFloat(), height.toFloat())
            lineTo(0f, height.toFloat())
            close()
        }
        if (!detailMode || height > 140) canvas.drawPath(fill, fillPaint)
        canvas.drawPath(path, linePaint)
    }
}
