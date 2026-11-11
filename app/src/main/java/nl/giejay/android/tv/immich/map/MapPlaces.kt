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
    /** The name of the place when the bubble is a single city; a group has no city's name (see [countries]). */
    val singleName: String?
        get() = cities.singleOrNull()?.city

    /** The countries of the bubble, the one with the most photos first. */
    val countries: List<String>
        get() = cities.filter { it.country != null }
            .groupBy { it.country!! }
            .entries.sortedByDescending { (_, group) -> group.sumOf { it.count } }
            .map { it.key }
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
    val groups = mutableListOf<CityGroup>()
    for (city in cities.sortedByDescending { it.count }) {
        val group = groups.firstOrNull { distanceDegrees(it.centre(), city.lat to city.lon) <= radiusDegrees }
        if (group != null) group.add(city) else groups += CityGroup(city)
    }
    return groups.map { group ->
        val (lat, lon) = group.centre()
        MapCluster(lat = lat, lon = lon, count = group.count, cities = group.cities)
    }.sortedByDescending { it.count }
}

/**
 * The cities of a growing bubble with their photo-weighted centre kept as running sums, so that testing a city against
 * a bubble costs the same however many cities it holds (recomputing the centre each time made a library with
 * thousands of places quadratic). Longitudes are summed as offsets from the first city, so places on either side of the
 * date line (179 and -179) average to 180 and not to 0.
 */
private class CityGroup(first: PlaceCity) {
    val cities = mutableListOf(first)
    var count = first.count
        private set
    private val reference = first.lon
    private var latSum = first.lat * first.count
    private var lonOffsetSum = 0.0 // wrapDegrees(first.lon - reference) is 0

    fun add(city: PlaceCity) {
        cities += city
        count += city.count
        latSum += city.lat * city.count
        lonOffsetSum += wrapDegrees(city.lon - reference) * city.count
    }

    fun centre(): Pair<Double, Double> = latSum / count to wrapDegrees(reference + lonOffsetSum / count)
}

/** Into [-180, 180). */
internal fun wrapDegrees(degrees: Double): Double = ((degrees + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

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
