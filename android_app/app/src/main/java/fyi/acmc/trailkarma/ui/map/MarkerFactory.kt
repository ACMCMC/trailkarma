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

        val (bgColor, emoji) = when (type) {
            ReportType.hazard -> Pair(0xFFD50000.toInt(), "⚠️")
            ReportType.water -> Pair(0xFF00B8CC.toInt(), "💧")
            ReportType.species -> Pair(0xFF007ACC.toInt(), "🐾")
        }

        // Draw colored circle background
        val bgPaint = Paint().apply {
            color = bgColor
            isAntiAlias = true
            setShadowLayer(4f, 0f, 2f, 0x80000000.toInt())
        }
        canvas.drawCircle(centerX, centerY, radius, bgPaint)

        // Draw white border
        val borderPaint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            isAntiAlias = true
            strokeWidth = 6f
            style = Paint.Style.STROKE
        }
        canvas.drawCircle(centerX, centerY, radius, borderPaint)

        // Draw Emoji
        val textPaint = Paint().apply {
            textSize = size * 0.45f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val textBounds = android.graphics.Rect()
        textPaint.getTextBounds(emoji, 0, emoji.length, textBounds)
        canvas.drawText(emoji, centerX, centerY + (textBounds.height() / 2f), textPaint)

        return BitmapDrawable(context.resources, bitmap)
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
