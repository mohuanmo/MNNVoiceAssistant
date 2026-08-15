package com.example.mnnvoiceassistant.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlinx.coroutines.*

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6200EE")
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    private var isAnimating = false
    private var amplitude = 0f
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var animationJob: Job? = null

    fun startAnimating() {
        isAnimating = true
        animationJob = scope.launch {
            while (isActive && isAnimating) {
                amplitude = (Math.random() * 0.6 + 0.2).toFloat()
                invalidate()
                delay(80)
            }
        }
    }

    fun stopAnimating() {
        isAnimating = false
        amplitude = 0f
        animationJob?.cancel()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isAnimating) return

        val centerY = height / 2f
        val maxHeight = height * 0.4f * amplitude
        val barWidth = 6f
        val gap = 8f
        val bars = (width / (barWidth + gap)).toInt()

        for (i in 0 until bars) {
            val x = i * (barWidth + gap) + gap
            val barHeight = (Math.random() * maxHeight).toFloat()
            canvas.drawLine(x, centerY - barHeight / 2, x, centerY + barHeight / 2, paint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
    }
}
