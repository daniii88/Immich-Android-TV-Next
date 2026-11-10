package nl.giejay.android.tv.immich.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import nl.giejay.android.tv.immich.map.MapViewport
import java.nio.ByteBuffer

/**
 * Turns the coastline and border vectors into the dot matrix of the map: for the view on screen, the land is rasterised
 * into a tiny bitmap with one pixel per dot, and every lit pixel becomes a dot. So the map stays a pixel graphic at
 * every zoom and gets finer the closer you come, because each dot looks up the real shape (1:50m data) instead of the
 * coarse mask of the world view. Borders become a second, brighter set of dots.
 *
 * Rasterising a view costs about a millisecond for a screen of dots; the result is kept until the view changes.
 */
internal class LandDots(vectors: MapVectors) {
    private val land: Path = vectors.land.asAndroidPath()
    private val borders: Path = vectors.borders.asAndroidPath()

    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null
    private var key: DotsKey? = null

    /** Positions of the dots on land, as x, y, x, y ... in pixels of the map area. */
    var landPoints: FloatArray = FloatArray(0)
        private set

    /** Positions of the dots on a country border. */
    var borderPoints: FloatArray = FloatArray(0)
        private set

    private data class DotsKey(val view: MapViewport, val cols: Int, val rows: Int, val step: Int, val dot: Int)

    private val landPaint = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL; color = 0xFF000000.toInt() }
    private val borderPaint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = 0xFF000000.toInt()
    }

    /**
     * Brings the dots up to date for [view]: a grid of [cols] x [rows] dots, [step] pixels apart, each a square of
     * [dot] pixels. Whole pixels only: dots on a fractional grid beat against the pixel grid and show as stripes.
     */
    fun update(view: MapViewport, cols: Int, rows: Int, step: Int, dot: Int) {
        val wanted = DotsKey(view, cols, rows, step, dot)
        if (wanted == key || cols < 1 || rows < 1) return
        key = wanted
        val bmp = bitmap?.takeIf { it.width == cols && it.height == rows }
            ?: Bitmap.createBitmap(cols, rows, Bitmap.Config.ALPHA_8).also {
                bitmap = it
                canvas = Canvas(it)
            }
        val canvas = canvas!!
        val scaleX = cols / view.width.toFloat()
        val scaleY = rows / view.height.toFloat()
        landPoints = raster(bmp, canvas, land, landPaint, scaleX, scaleY, view, step, dot)
        borderPaint.strokeWidth = 1.1f / scaleX // about one dot wide
        borderPoints = raster(bmp, canvas, borders, borderPaint, scaleX, scaleY, view, step, dot)
    }

    private fun raster(
        bmp: Bitmap, canvas: Canvas, path: Path, paint: Paint,
        scaleX: Float, scaleY: Float, view: MapViewport, step: Int, dot: Int,
    ): FloatArray {
        bmp.eraseColor(0)
        canvas.save()
        // degrees (x = longitude, y = minus the latitude) to dots: the view's corner to the origin, then scale
        canvas.scale(scaleX, scaleY)
        canvas.translate(-view.lonMin.toFloat(), view.latTop.toFloat())
        canvas.drawPath(path, paint)
        canvas.restore()
        val pixels = ByteArray(bmp.width * bmp.height)
        bmp.copyPixelsToBuffer(ByteBuffer.wrap(pixels))
        var lit = 0
        for (b in pixels) if (b.toInt() and 0xFF >= 128) lit++
        val points = FloatArray(lit * 2)
        var next = 0
        // the centre of a dot-sized square sitting in the middle of its cell, on whole pixels
        val inset = (step - dot) / 2 + dot / 2f
        for (i in pixels.indices) {
            if (pixels[i].toInt() and 0xFF >= 128) {
                points[next++] = (i % bmp.width) * step + inset
                points[next++] = (i / bmp.width) * step + inset
            }
        }
        return points
    }
}

/** Distance between dots in pixels: [base] in the world view, finer the more is zoomed in (down to 60 %). */
internal fun dotStep(base: Float, viewWidthDegrees: Double): Float {
    val detail = ((150.0 - viewWidthDegrees) / 90.0).coerceIn(0.0, 1.0).toFloat()
    return base * (1f - 0.4f * detail)
}

/** The spacing in whole pixels (at least 3) and the side of the square dot (2 px up to a spacing of 4, else 3). */
internal fun dotPixels(step: Float): Pair<Int, Int> {
    val whole = Math.round(step).coerceAtLeast(3)
    return whole to if (whole <= 4) 2 else 3
}
