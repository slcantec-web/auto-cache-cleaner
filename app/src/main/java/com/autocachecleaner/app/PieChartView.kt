package com.autocachecleaner.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Simple donut pie chart. Call [setSlices] with label + value pairs (largest first preferred).
 * Top slices are drawn individually; remaining mass is grouped as "Other".
 */
class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Slice(val label: String, val value: Long, val color: Int)

    private val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val rect = RectF()
    private var slices: List<Slice> = emptyList()
    private var holeColor: Int = 0xFF0D1524.toInt()

    private val palette = intArrayOf(
        0xFF22D3EE.toInt(), // cyan
        0xFFA78BFA.toInt(), // violet
        0xFFF472B6.toInt(), // pink
        0xFF34D399.toInt(), // green
        0xFFFBBF24.toInt(), // amber
        0xFF60A5FA.toInt(), // blue
        0xFFFB7185.toInt(), // rose
        0xFF2DD4BF.toInt(), // teal
        0xFFC084FC.toInt(), // purple
        0xFF4ADE80.toInt(), // lime
        0xFF64748B.toInt()  // Other
    )

    fun setHoleColor(color: Int) {
        holeColor = color
        invalidate()
    }

    /** Builds slices from app cache list (already sorted largest-first). Max [maxSlices] shown. */
    fun setFromApps(apps: List<AppCacheInfo>, maxSlices: Int = 8) {
        if (apps.isEmpty()) {
            slices = emptyList()
            invalidate()
            return
        }
        val top = apps.take(maxSlices)
        val rest = apps.drop(maxSlices).sumOf { it.cacheBytes }
        val built = ArrayList<Slice>(top.size + 1)
        top.forEachIndexed { i, app ->
            built.add(Slice(app.label, app.cacheBytes, palette[i % (palette.size - 1)]))
        }
        if (rest > 0) {
            built.add(Slice("Other", rest, palette.last()))
        }
        slices = built
        invalidate()
    }

    fun currentSlices(): List<Slice> = slices

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        val pad = size * 0.04f
        val left = (width - size) / 2f + pad
        val top = (height - size) / 2f + pad
        rect.set(left, top, left + size - 2 * pad, top + size - 2 * pad)

        val total = slices.sumOf { it.value }
        if (total <= 0 || slices.isEmpty()) {
            emptyPaint.color = 0xFF1E2A40.toInt()
            canvas.drawOval(rect, emptyPaint)
            return
        }

        var start = -90f
        for (s in slices) {
            val sweep = (s.value.toFloat() / total.toFloat()) * 360f
            slicePaint.color = s.color
            canvas.drawArc(rect, start, sweep, true, slicePaint)
            start += sweep
        }

        val cx = rect.centerX()
        val cy = rect.centerY()
        val holeR = (rect.width() / 2f) * 0.55f
        holePaint.color = holeColor
        canvas.drawCircle(cx, cy, holeR, holePaint)
    }
}
