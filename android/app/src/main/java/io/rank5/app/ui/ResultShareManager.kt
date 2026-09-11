package io.rank5.app.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import io.rank5.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object ResultShareManager {
    private const val WIDTH = 1080
    private const val HEIGHT = 1350
    // Export canvas is authored at 3x; these mirror 20 dp and 12 dp app radii.
    private const val ROOMY_RADIUS = 60f
    private const val COMPACT_RADIUS = 36f
    private const val HERO_TEXT = 108f
    private const val HEADING_TEXT = 54f
    private const val BODY_TEXT = 34f
    private const val LABEL_TEXT = 30f
    private const val META_TEXT = 27f

    // The exported card mirrors the app palette: violet + rose and neutrals only.
    private val background = Color.rgb(248, 247, 252)
    private val surface = Color.WHITE
    private val surfaceVariant = Color.rgb(239, 237, 247)
    private val ink = Color.rgb(23, 21, 31)
    private val muted = Color.rgb(98, 95, 107)
    private val violet = Color.rgb(90, 63, 214)
    private val rose = Color.rgb(179, 38, 75)

    suspend fun share(context: Context, data: ResultCardData) {
        val uri = withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "shared_results")
            check(directory.exists() || directory.mkdirs()) { "Could not create share directory" }
            val file = File(directory, "rank5-result.png")
            FileOutputStream(file).use { stream ->
                val bitmap = render(data)
                try {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        "Could not encode result card"
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                file,
            )
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "Rank5 result", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share your Rank5 result"))
    }

    private fun render(data: ResultCardData): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(background)

        paint.color = surface
        canvas.drawRoundRect(RectF(64f, 64f, 1016f, 1286f), ROOMY_RADIUS, ROOMY_RADIUS, paint)

        drawBrandMark(canvas, paint, left = 120f, top = 100f, size = 72f)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = violet
        paint.textSize = HEADING_TEXT
        canvas.drawText("RANK5", 208f, 154f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = surfaceVariant
        canvas.drawRoundRect(RectF(120f, 190f, 184f, 254f), COMPACT_RADIUS, COMPACT_RADIUS, paint)
        paint.textSize = BODY_TEXT
        canvas.drawText(data.deckEmoji, 132f, 236f, paint)
        paint.color = muted
        paint.textSize = BODY_TEXT
        val deckTitle = ellipsize(data.deckName, paint, 710f)
        canvas.drawText(deckTitle, 204f, 232f, paint)
        if (data.deckNames.size > 1) {
            paint.textSize = META_TEXT
            canvas.drawText(ellipsize(data.deckNames.joinToString(" · "), paint, 830f), 120f, 278f, paint)
        }
        canvas.drawText(
            "${data.rounds} ROUNDS",
            120f,
            if (data.deckNames.size > 1) 326f else 286f,
            paint,
        )

        drawCoop(canvas, paint, data)

        paint.color = muted
        paint.textSize = LABEL_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Rank your friends. Know them better.", 120f, 1210f, paint)
        paint.color = violet
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Play Rank5", 120f, 1255f, paint)
        return bitmap
    }

    /** Matches `res/drawable/rank5_brand_mark.xml` in 108-unit viewport space. */
    private fun drawBrandMark(canvas: Canvas, paint: Paint, left: Float, top: Float, size: Float) {
        paint.style = Paint.Style.FILL
        val scale = size / 108f
        val barHeight = 8f * scale
        val radius = 4f * scale
        val originX = left + 26f * scale
        val widths = floatArrayOf(56f, 47f, 38f, 29f, 20f)
        val tops = floatArrayOf(24f, 37f, 50f, 63f, 76f)
        widths.forEachIndexed { index, width ->
            paint.color = if (index == widths.lastIndex) rose else violet
            val barTop = top + tops[index] * scale
            canvas.drawRoundRect(
                RectF(originX, barTop, originX + width * scale, barTop + barHeight),
                radius,
                radius,
                paint,
            )
        }
    }

    private fun drawCoop(canvas: Canvas, paint: Paint, data: ResultCardData) {
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = ink
        paint.textSize = HERO_TEXT
        canvas.drawText(formatNumber(data.teamScore), 120f, 470f, paint)

        paint.color = muted
        paint.textSize = BODY_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("team points · ${formatNumber(data.maxTeamScore)} possible", 120f, 530f, paint)

        paint.color = ink
        paint.textSize = BODY_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("ROUND BREAKDOWN", 120f, 650f, paint)
        var y = 730f
        data.roundScores.take(6).forEachIndexed { index, score ->
            paint.color = surfaceVariant
            canvas.drawRoundRect(RectF(120f, y - 48f, 900f, y + 28f), COMPACT_RADIUS, COMPACT_RADIUS, paint)
            paint.color = ink
            paint.textSize = BODY_TEXT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("ROUND ${index + 1}", 154f, y, paint)
            paint.color = rose
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${formatNumber(score)} pts", 866f, y, paint)
            paint.textAlign = Paint.Align.LEFT
            y += 92f
        }
    }

    private fun ellipsize(value: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(value) <= maxWidth) return value
        var shortened = value
        while (shortened.isNotEmpty() && paint.measureText("$shortened…") > maxWidth) {
            shortened = shortened.dropLast(1)
        }
        return "$shortened…"
    }

    private fun formatNumber(value: Int): String = String.format(Locale.US, "%,d", value)
}
