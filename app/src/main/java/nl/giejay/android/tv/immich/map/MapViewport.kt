package nl.giejay.android.tv.immich.map

import kotlin.math.max
import kotlin.math.min

/**
 * The part of the world the map shows, in degrees. It always has the proportions of the map area, so zooming never
 * distorts it. The world view is the unzoomed map.
 */
data class MapViewport(val lonMin: Double, val lonMax: Double, val latTop: Double, val latBottom: Double) {
    val width: Double get() = lonMax - lonMin
    val height: Double get() = latTop - latBottom

    /** Position on the map, 0 to 1 from the left edge. */
    fun x(lon: Double): Float = ((lon - lonMin) / width).toFloat()

    /** Position on the map, 0 to 1 from the top edge. */
    fun y(lat: Double): Float = ((latTop - lat) / height).toFloat()

    /** Whether a place lies inside, with a margin as a fraction of the size so bubbles at the edge are kept. */
    fun contains(lat: Double, lon: Double, margin: Double = 0.0): Boolean =
        lon in (lonMin - width * margin)..(lonMax + width * margin) && lat in (latBottom - height * margin)..(latTop + height * margin)

    companion object {
        val WORLD = MapViewport(-180.0, 180.0, WorldDots.LAT_TOP, WorldDots.LAT_BOTTOM)

        /** The smallest view, so a single city is not zoomed into a blur of dots. */
        const val MIN_WIDTH_DEGREES = 10.0

        /** A view of the given width centred on a position, moved into the world if it would stick out. */
        fun around(lat: Double, lon: Double, width: Double): MapViewport {
            val world = WORLD
            val w = width.coerceIn(MIN_WIDTH_DEGREES, world.width)
            val height = w / (world.width / world.height)
            val lonMin = (lon - w / 2).coerceIn(world.lonMin, world.lonMax - w)
            val latTop = (lat + height / 2).coerceIn(world.latBottom + height, world.latTop)
            return MapViewport(lonMin, lonMin + w, latTop, latTop - height)
        }
    }
}

/** The view that frames [cities] with some room around them, in the proportions of the world map. */
fun viewportFor(cities: List<PlaceCity>): MapViewport {
    val world = MapViewport.WORLD
    val aspect = world.width / world.height
    val lonSpan = cities.maxOf { it.lon } - cities.minOf { it.lon }
    val latSpan = cities.maxOf { it.lat } - cities.minOf { it.lat }
    val width = min(world.width, max(MapViewport.MIN_WIDTH_DEGREES, max(lonSpan * 1.8, latSpan * 1.8 * aspect)))
    return MapViewport.around(
        lat = (cities.maxOf { it.lat } + cities.minOf { it.lat }) / 2,
        lon = (cities.maxOf { it.lon } + cities.minOf { it.lon }) / 2,
        width = width,
    )
}
