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
        canvas.drawColor(Color.rgb(248, 247, 252))

        paint.color = Color.WHITE
        canvas.drawRoundRect(RectF(64f, 64f, 1016f, 1286f), 44f, 44f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = Color.rgb(90, 63, 214)
        paint.textSize = 54f
        canvas.drawText("RANK5", 120f, 160f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.rgb(98, 95, 107)
        paint.textSize = 34f
        paint.color = Color.rgb(239, 237, 247)
        canvas.drawRoundRect(RectF(120f, 190f, 184f, 254f), 16f, 16f, paint)
        paint.textSize = 38f
        canvas.drawText(data.deckEmoji, 132f, 236f, paint)
        paint.color = Color.rgb(98, 95, 107)
        paint.textSize = 34f
        val deckTitle = ellipsize(data.deckName, paint, 710f)
        canvas.drawText(deckTitle, 204f, 232f, paint)
        if (data.deckNames.size > 1) {
            paint.textSize = 27f
            canvas.drawText(ellipsize(data.deckNames.joinToString(" · "), paint, 830f), 120f, 278f, paint)
        }
        canvas.drawText(
            "${if (data.mode == "coop") "CO-OP" else "VERSUS"}  ·  ${data.rounds} ROUNDS",
            120f,
            if (data.deckNames.size > 1) 326f else 286f,
            paint,
        )

        if (data.mode == "coop") {
            drawCoop(canvas, paint, data)
        } else {
            drawVersus(canvas, paint, data)
        }

        paint.color = Color.rgb(98, 95, 107)
        paint.textSize = 30f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Rank your friends. Know them better.", 120f, 1210f, paint)
        paint.color = Color.rgb(90, 63, 214)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Play Rank5", 120f, 1255f, paint)
        return bitmap
    }

    private fun drawCoop(canvas: Canvas, paint: Paint, data: ResultCardData) {
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = Color.rgb(34, 29, 21)
        paint.textSize = 108f
        canvas.drawText(formatNumber(data.teamScore), 120f, 470f, paint)

        paint.color = Color.rgb(107, 98, 85)
        paint.textSize = 34f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("team points · ${formatNumber(data.maxTeamScore)} possible", 120f, 530f, paint)

        paint.color = Color.rgb(34, 29, 21)
        paint.textSize = 36f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("ROUND BREAKDOWN", 120f, 650f, paint)
        var y = 730f
        data.roundScores.take(6).forEachIndexed { index, score ->
            paint.color = Color.rgb(243, 237, 227)
            canvas.drawRoundRect(RectF(120f, y - 48f, 900f, y + 28f), 20f, 20f, paint)
            paint.color = Color.rgb(34, 29, 21)
            paint.textSize = 32f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("ROUND ${index + 1}", 154f, y, paint)
            paint.color = Color.rgb(178, 58, 29)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${formatNumber(score)} pts", 866f, y, paint)
            paint.textAlign = Paint.Align.LEFT
            y += 92f
        }
    }

    private fun drawVersus(canvas: Canvas, paint: Paint, data: ResultCardData) {
        val winner = data.standings.firstOrNull()
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = Color.rgb(34, 29, 21)
        paint.textSize = 68f
        canvas.drawText("${ellipsize(winner?.nickname ?: "Winner", paint, 760f)} wins", 120f, 420f, paint)

        var y = 540f
        data.standings.forEach { standing ->
            paint.color = if (standing.rank == 1) Color.rgb(245, 197, 68) else Color.rgb(243, 237, 227)
            canvas.drawRoundRect(RectF(120f, y - 58f, 900f, y + 38f), 24f, 24f, paint)
            paint.color = Color.rgb(34, 29, 21)
            paint.textSize = 34f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("${standing.rank}", 154f, y, paint)
            val name = ellipsize(standing.nickname, paint, 430f)
            canvas.drawText(name, 220f, y, paint)
            paint.color = Color.rgb(178, 58, 29)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${formatNumber(standing.score)} pts", 866f, y, paint)
            paint.textAlign = Paint.Align.LEFT
            y += 116f
        }
        if (data.morePlayers > 0) {
            paint.color = Color.rgb(107, 98, 85)
            paint.textSize = 30f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("+${data.morePlayers} more players", 120f, y + 12f, paint)
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
