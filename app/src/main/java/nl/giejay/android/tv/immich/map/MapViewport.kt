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

/**
 * The view that frames [cities] with some room around them, in the proportions of the world map. [topRoom] (a fraction
 * of the view's height) is kept free above the northernmost place, so the photo pins standing on the places fit above
 * them instead of hanging below and covering the places further south; the view grows when the places need it.
 */
fun viewportFor(cities: List<PlaceCity>, topRoom: Double = 0.0): MapViewport {
    val world = MapViewport.WORLD
    val aspect = world.width / world.height
    val north = cities.maxOf { it.lat }
    val south = cities.minOf { it.lat }
    val lonSpan = cities.maxOf { it.lon } - cities.minOf { it.lon }
    val latSpan = north - south
    val room = topRoom.coerceIn(0.0, 0.6)
    // the places fill at most what is left under the room for the pins, with a margin at the bottom
    val heightForPins = latSpan / (1 - room - BOTTOM_MARGIN)
    val width = min(world.width, max(MapViewport.MIN_WIDTH_DEGREES, max(max(lonSpan * 1.8, latSpan * 1.8 * aspect), heightForPins * aspect)))
    val height = width / aspect
    // centred when there is room enough, else moved down so the room above the northernmost place is free
    val above = max((height - latSpan) / 2, room * height)
    return MapViewport.around(
        lat = north + above - height / 2,
        lon = (cities.maxOf { it.lon } + cities.minOf { it.lon }) / 2,
        width = width,
    )
}

/** The room kept under the southernmost place, as a fraction of the view's height. */
private const val BOTTOM_MARGIN = 0.08
