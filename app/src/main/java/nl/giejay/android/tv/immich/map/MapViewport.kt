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

    /** Cities closer than this merge into one bubble: [MAP_CLUSTER_RADIUS_DEGREES] on the world map, finer the more is zoomed in. */
    val clusterRadius: Double get() = MAP_CLUSTER_RADIUS_DEGREES * width / WORLD.width

    companion object {
        val WORLD = MapViewport(-180.0, 180.0, WorldDots.LAT_TOP, WorldDots.LAT_BOTTOM)

        /** The smallest view, so a single city is not zoomed into a blur of dots. */
        const val MIN_WIDTH_DEGREES = 10.0
    }
}

/** The view that frames [cities] with some room around them, in the proportions of the world map. */
fun viewportFor(cities: List<PlaceCity>): MapViewport {
    val world = MapViewport.WORLD
    val aspect = world.width / world.height
    val lonSpan = cities.maxOf { it.lon } - cities.minOf { it.lon }
    val latSpan = cities.maxOf { it.lat } - cities.minOf { it.lat }
    val width = min(world.width, max(MapViewport.MIN_WIDTH_DEGREES, max(lonSpan * 1.8, latSpan * 1.8 * aspect)))
    val height = width / aspect
    val centreLon = (cities.maxOf { it.lon } + cities.minOf { it.lon }) / 2
    val centreLat = (cities.maxOf { it.lat } + cities.minOf { it.lat }) / 2
    // keep the view inside the world
    val lonMin = (centreLon - width / 2).coerceIn(world.lonMin, world.lonMax - width)
    val latTop = (centreLat + height / 2).coerceIn(world.latBottom + height, world.latTop)
    return MapViewport(lonMin, lonMin + width, latTop, latTop - height)
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
