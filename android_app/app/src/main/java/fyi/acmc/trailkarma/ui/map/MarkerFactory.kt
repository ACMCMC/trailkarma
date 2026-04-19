package fyi.acmc.trailkarma.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import fyi.acmc.trailkarma.models.ReportType

object MarkerFactory {
    fun createMarkerDrawable(context: Context, type: ReportType): BitmapDrawable {
        val size = 96
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centerX = size / 2f
        val centerY = size / 2f
        val radius = (size / 2f) - 4

        val (bgColor, iconDrawer) = when (type) {
            ReportType.hazard -> Pair(0xFFD50000.toInt(), { _canvas: Canvas, _x: Float, _y: Float, _size: Float ->
                drawWarningTriangle(_canvas, _x, _y, _size)
            })
            ReportType.water -> Pair(0xFF00B8CC.toInt(), { _canvas: Canvas, _x: Float, _y: Float, _size: Float ->
                drawWaterDroplet(_canvas, _x, _y, _size)
            })
            ReportType.species -> Pair(0xFF007ACC.toInt(), { _canvas: Canvas, _x: Float, _y: Float, _size: Float ->
                drawPaw(_canvas, _x, _y, _size)
            })
        }

        // Draw colored circle background
        val bgPaint = Paint().apply {
            color = bgColor
            isAntiAlias = true
        }
        canvas.drawCircle(centerX, centerY, radius, bgPaint)

        // Draw white border
        val borderPaint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            isAntiAlias = true
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }
        canvas.drawCircle(centerX, centerY, radius, borderPaint)

        // Draw icon
        iconDrawer(canvas, centerX, centerY, radius * 0.6f)

        return BitmapDrawable(context.resources, bitmap)
    }

    private fun drawWarningTriangle(canvas: Canvas, centerX: Float, centerY: Float, size: Float) {
        val path = Path().apply {
            moveTo(centerX, centerY - size)
            lineTo(centerX + size, centerY + size * 0.5f)
            lineTo(centerX - size, centerY + size * 0.5f)
            close()
        }
        val paint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, paint)

        // Draw exclamation mark
        val textPaint = Paint().apply {
            color = 0xFFD50000.toInt()
            textSize = size * 0.8f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        canvas.drawText("!", centerX, centerY + size * 0.3f, textPaint)
    }

    private fun drawWaterDroplet(canvas: Canvas, centerX: Float, centerY: Float, size: Float) {
        val path = Path().apply {
            moveTo(centerX, centerY - size)
            quadTo(centerX - size * 0.6f, centerY - size * 0.3f, centerX - size * 0.5f, centerY)
            quadTo(centerX, centerY + size, centerX, centerY + size)
            quadTo(centerX, centerY + size, centerX + size * 0.5f, centerY)
            quadTo(centerX + size * 0.6f, centerY - size * 0.3f, centerX, centerY - size)
            close()
        }
        val paint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, paint)
    }

    private fun drawPaw(canvas: Canvas, centerX: Float, centerY: Float, size: Float) {
        val paint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        // Center pad
        canvas.drawCircle(centerX, centerY, size * 0.35f, paint)

        // Top-left toe
        canvas.drawCircle(centerX - size * 0.5f, centerY - size * 0.6f, size * 0.2f, paint)
        // Top-right toe
        canvas.drawCircle(centerX + size * 0.5f, centerY - size * 0.6f, size * 0.2f, paint)
        // Bottom-left toe
        canvas.drawCircle(centerX - size * 0.35f, centerY + size * 0.5f, size * 0.2f, paint)
        // Bottom-right toe
        canvas.drawCircle(centerX + size * 0.35f, centerY + size * 0.5f, size * 0.2f, paint)
    }

    fun createUserMarkerDrawable(context: Context): BitmapDrawable {
        val size = 80
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centerX = size / 2f
        val centerY = size / 2f

        // Draw blue circle background
        val paint = Paint().apply {
            color = 0xFF007ACC.toInt()
            isAntiAlias = true
        }
        canvas.drawCircle(centerX, centerY, (size / 2f) - 4, paint)

        // Draw white border
        val borderPaint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            isAntiAlias = true
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        canvas.drawCircle(centerX, centerY, (size / 2f) - 4, borderPaint)

        // Draw inner white dot (location pin)
        canvas.drawCircle(centerX, centerY, 8f, Paint().apply { color = 0xFFFFFFFF.toInt() })

        return BitmapDrawable(context.resources, bitmap)
    }
}
