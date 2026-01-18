package com.wetzone.mp3playerboom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.abs

class WaveformView(
    context: Context,
    private val waveform: FloatArray
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f
        color = 0xFFFFFFFF.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (waveform.isEmpty()) return

        val centerY = height / 2f
        val step = waveform.size.toFloat() / width

        var index = 0f
        for (x in 0 until width) {
            val i = index.toInt().coerceIn(0, waveform.size - 1)
            val amp = abs(waveform[i])
            val lineHeight = amp * centerY
            canvas.drawLine(
                x.toFloat(),
                centerY - lineHeight,
                x.toFloat(),
                centerY + lineHeight,
                paint
            )
            index += step
        }
    }
}
