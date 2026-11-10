package nl.giejay.android.tv.immich.map

import nl.giejay.android.tv.immich.api.model.MapMarker
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/** The photos of one city (all photos with the same city and country name), placed at the mean of their positions. */
data class PlaceCity(
    val city: String,
    val country: String?,
    val count: Int,
    val lat: Double,
    val lon: Double,
    /** A photo of the place, for its thumbnail. */
    val coverAssetId: String,
)

/** Cities that lie close together, drawn as one bubble on the world map. [cities] is sorted by photo count, biggest first. */
data class MapCluster(
    val lat: Double,
    val lon: Double,
    val count: Int,
    val cities: List<PlaceCity>,
) {
    /** The biggest city, "+N" for the others in the bubble. */
    val label: String
        get() = cities.first().city + if (cities.size > 1) " +${cities.size - 1}" else ""
}

/** Cities closer than this (degrees on the globe, roughly 300 km) merge into one bubble on the world map. */
const val MAP_CLUSTER_RADIUS_DEGREES = 3.0

/** The places the map offers. Photos without a city name cannot be opened as a place, so they are left out. */
fun groupCities(markers: List<MapMarker>): List<PlaceCity> =
    markers.filter { !it.city.isNullOrBlank() }
        .groupBy { it.city!!.trim() to it.country?.trim()?.ifEmpty { null } }
        .map { (key, group) ->
            PlaceCity(
                city = key.first,
                country = key.second,
                count = group.size,
                lat = group.map { it.lat }.average(),
                lon = group.map { it.lon }.average(),
                coverAssetId = group.first().id,
            )
        }
        .sortedByDescending { it.count }

/**
 * Merges cities that lie close together into bubbles, biggest bubble first. The biggest cities start the bubbles; a
 * smaller city joins the first bubble within [radiusDegrees] of its centre. A bubble sits at the photo-weighted mean
 * of its cities. (A grid would split neighbours that happen to lie on either side of a cell border.)
 */
fun clusterCities(cities: List<PlaceCity>, radiusDegrees: Double = MAP_CLUSTER_RADIUS_DEGREES): List<MapCluster> {
    val groups = mutableListOf<MutableList<PlaceCity>>()
    for (city in cities.sortedByDescending { it.count }) {
        val group = groups.firstOrNull { distanceDegrees(centre(it), city.lat to city.lon) <= radiusDegrees }
        if (group != null) group += city else groups += mutableListOf(city)
    }
    return groups.map { group ->
        val (lat, lon) = centre(group)
        MapCluster(lat = lat, lon = lon, count = group.sumOf { it.count }, cities = group)
    }.sortedByDescending { it.count }
}

private fun centre(group: List<PlaceCity>): Pair<Double, Double> {
    val total = group.sumOf { it.count }
    return group.sumOf { it.lat * it.count } / total to group.sumOf { it.lon * it.count } / total
}

/** Rough distance in degrees: longitude differences shrink towards the poles, and wrap around the date line. */
internal fun distanceDegrees(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val dLat = a.first - b.first
    val rawLon = abs(a.second - b.second)
    val dLon = minOf(rawLon, 360.0 - rawLon) * cos(Math.toRadians((a.first + b.first) / 2))
    return hypot(dLat, dLon)
}

/**
 * The places in a walk through the neighbourhood: it starts at the biggest place and goes on to the closest place not
 * yet listed, so places that lie near each other stand next to each other (not alphabetically, not by size).
 */
fun orderByProximity(cities: List<PlaceCity>): List<PlaceCity> {
    if (cities.size < 3) return cities
    val rest = cities.sortedByDescending { it.count }.toMutableList()
    val ordered = mutableListOf(rest.removeAt(0))
    while (rest.isNotEmpty()) {
        val last = ordered.last()
        val next = rest.minBy { distanceDegrees(last.lat to last.lon, it.lat to it.lon) }
        rest.remove(next)
        ordered += next
    }
    return ordered
}
