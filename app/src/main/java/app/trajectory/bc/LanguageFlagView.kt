package app.trajectory.bc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class LanguageFlagView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var bulgarian: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        paint.style = Paint.Style.FILL
        if (bulgarian) {
            val band = height / 3f
            paint.color = Color.WHITE
            canvas.drawRect(0f, 0f, width, band, paint)
            paint.color = 0xFF00966E.toInt()
            canvas.drawRect(0f, band, width, band * 2f, paint)
            paint.color = 0xFFD62612.toInt()
            canvas.drawRect(0f, band * 2f, width, height, paint)
        } else {
            drawUnionJack(canvas, width, height)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = resources.displayMetrics.density
        paint.color = 0xFF3A4250.toInt()
        canvas.drawRect(0f, 0f, width, height, paint)
    }

    private fun drawUnionJack(canvas: Canvas, width: Float, height: Float) {
        paint.style = Paint.Style.FILL
        paint.color = 0xFF012169.toInt()
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.strokeCap = Paint.Cap.BUTT
        paint.color = Color.WHITE
        paint.strokeWidth = height * 0.22f
        canvas.drawLine(0f, 0f, width, height, paint)
        canvas.drawLine(width, 0f, 0f, height, paint)
        paint.color = 0xFFC8102E.toInt()
        paint.strokeWidth = height * 0.09f
        canvas.drawLine(0f, 0f, width, height, paint)
        canvas.drawLine(width, 0f, 0f, height, paint)
        paint.style = Paint.Style.FILL
        val whiteBar = height * 0.34f
        val redBar = height * 0.18f
        paint.color = Color.WHITE
        canvas.drawRect((width - whiteBar) / 2f, 0f, (width + whiteBar) / 2f, height, paint)
        canvas.drawRect(0f, (height - whiteBar) / 2f, width, (height + whiteBar) / 2f, paint)
        paint.color = 0xFFC8102E.toInt()
        canvas.drawRect((width - redBar) / 2f, 0f, (width + redBar) / 2f, height, paint)
        canvas.drawRect(0f, (height - redBar) / 2f, width, (height + redBar) / 2f, paint)
    }
}
