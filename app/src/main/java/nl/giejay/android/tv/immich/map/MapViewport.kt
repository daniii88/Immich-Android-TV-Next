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

    /**
     * Cities closer than this (in degrees) merge into one bubble. It is a fraction of the view, so bubbles merge before
     * they would overlap on screen at every zoom, and a library with thousands of places in one country shows as a few
     * bubbles on the world map and falls apart into cities as you zoom in.
     */
    val clusterRadius: Double get() = CLUSTER_RADIUS_FRACTION * width

    companion object {
        val WORLD = MapViewport(-180.0, 180.0, WorldDots.LAT_TOP, WorldDots.LAT_BOTTOM)

        /** The smallest view, so a single city is not zoomed into a blur of dots. */
        const val MIN_WIDTH_DEGREES = 10.0

        /** Bubbles closer than this fraction of the map width would touch (a bubble with its ring is up to 8 % of the map wide). */
        const val CLUSTER_RADIUS_FRACTION = 0.08

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

/** The bubbles of [viewport]: the cities in view, merged as finely as the zoom allows. */
fun clustersFor(cities: List<PlaceCity>, viewport: MapViewport): List<MapCluster> =
    clusterCities(cities.filter { viewport.contains(it.lat, it.lon, margin = 0.05) }, viewport.clusterRadius)

/** Whether zooming into [cluster] shows its cities apart, which is when the zoom is worth it (else OK shows its places). */
fun canZoomInto(cluster: MapCluster, current: MapViewport): Boolean {
    if (cluster.cities.size < 2) return false
    val target = viewportFor(cluster.cities)
    return target.width < current.width * 0.9 && clusterCities(cluster.cities, target.clusterRadius).size > 1
}
