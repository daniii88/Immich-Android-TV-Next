package nl.giejay.android.tv.immich.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import nl.giejay.android.tv.immich.map.MapFootprint
import nl.giejay.android.tv.immich.map.MapViewport
import nl.giejay.android.tv.immich.map.footprintCells
import java.nio.ByteBuffer
import kotlin.math.ln

/**
 * Turns the coastline and border vectors into the dot matrix of the map: for the view on screen, the land is rasterised
 * into a tiny bitmap with one pixel per dot, and every lit pixel becomes a dot. So the map stays a pixel graphic at
 * every zoom and gets finer the closer you come, because each dot looks up the real shape (1:50m data) instead of the
 * coarse mask of the world view. Borders become a second, brighter set of dots.
 *
 * Rasterising a view costs about a millisecond for a screen of dots; the result is kept until the view changes.
 */
/** The photos drawn over the land: [highlight] is a country name (its photos in [focusColor], the others in [otherColor]) or null (all in [focusColor]). */
internal class FootprintStyle(val footprint: MapFootprint, val highlight: String?, val focusColor: Int, val otherColor: Int)

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

    /**
     * The dots of [view] as one finished bitmap of [width] x [height] pixels, so a frame costs a single image draw
     * instead of tens of thousands of points. Safe to call from a background thread (one view at a time).
     */
    @Synchronized
    fun render(
        view: MapViewport, width: Int, height: Int, step: Int, dot: Int, landColor: Int, borderColor: Int,
        footprint: FootprintStyle? = null,
    ): Bitmap {
        update(view, width / step, height / step, step, dot)
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val target = Canvas(out)
        val paint = Paint().apply { isAntiAlias = false; strokeCap = Paint.Cap.SQUARE; strokeWidth = dot.toFloat() }
        paint.color = landColor
        target.drawPoints(landPoints, paint)
        paint.color = borderColor
        target.drawPoints(borderPoints, paint)
        if (footprint != null) drawFootprint(target, paint, footprint, view, width / step, height / step, step, dot)
        return out
    }

    /**
     * Lights the dots where photos were taken, on the same grid as the land: a soft glow around each so a single photo
     * in a far-away place still shows on the world map, and a solid dot on top, stronger the more photos. The
     * highlighted country is drawn in [FootprintStyle.focusColor], the rest of the footprint in [FootprintStyle.otherColor].
     */
    private fun drawFootprint(target: Canvas, paint: Paint, style: FootprintStyle, view: MapViewport, cols: Int, rows: Int, step: Int, dot: Int) {
        if (cols < 1 || rows < 1) return
        val cells = footprintCells(style.footprint, view, cols, rows, style.highlight)
        val max = cells.max
        if (max == 0) return
        val logMax = ln(max + 1.0)
        val inset = (step - dot) / 2 + dot / 2f
        val glow = Paint().also { it.isAntiAlias = true; it.style = Paint.Style.FILL }
        val glowRadius = step * 2.2f
        for (cell in cells.all.indices) {
            if (cells.all[cell] == 0) continue
            glow.color = withAlpha(if (cells.bright[cell] > 0) style.focusColor else style.otherColor, if (cells.bright[cell] > 0) 0.30 else 0.14)
            target.drawCircle((cell % cols) * step + inset, (cell / cols) * step + inset, glowRadius, glow)
        }
        paint.strokeWidth = step.toFloat() // the lit dot fills its cell, larger than a land dot
        for (cell in cells.all.indices) {
            val all = cells.all[cell]
            if (all == 0) continue
            val bright = cells.bright[cell] > 0
            val strength = 0.7 + 0.3 * ln((if (bright) cells.bright[cell] else all) + 1.0) / logMax
            paint.color = withAlpha(if (bright) style.focusColor else style.otherColor, strength)
            target.drawPoint((cell % cols) * step + inset, (cell / cols) * step + inset, paint)
        }
    }

    private fun withAlpha(color: Int, alpha: Double): Int = (color and 0xFFFFFF) or ((alpha * 255).toInt().coerceIn(0, 255) shl 24)

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
