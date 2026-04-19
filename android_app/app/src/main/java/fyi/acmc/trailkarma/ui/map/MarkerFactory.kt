package fyi.acmc.trailkarma.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import fyi.acmc.trailkarma.models.ReportType

object MarkerFactory {
    fun createMarkerDrawable(context: Context, type: ReportType, size: Int = 48): BitmapDrawable {
        val emoji = when (type) {
            ReportType.hazard -> "⚠️"
            ReportType.water -> "💧"
            ReportType.species -> "🐾"
        }
        return createEmojiDrawable(context, emoji, size)
    }

    fun createUserMarkerDrawable(context: Context, size: Int = 40): BitmapDrawable {
        return createEmojiDrawable(context, "📍", size)
    }

    private fun createEmojiDrawable(context: Context, emoji: String, size: Int): BitmapDrawable {
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val textPaint = Paint().apply {
            textSize = (size * 0.8f)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val textBounds = android.graphics.Rect()
        textPaint.getTextBounds(emoji, 0, emoji.length, textBounds)
        canvas.drawText(emoji, size / 2f, (size / 2f) + (textBounds.height() / 2f), textPaint)

        return BitmapDrawable(context.resources, bitmap)
    }
}
