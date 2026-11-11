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
    /** The region (state, province) most of the photos name; null when the geocoding has none. */
    val state: String? = null,
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
                state = group.mapNotNull { it.state?.trim()?.ifEmpty { null } }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key,
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

/** The photos of one region (state) within a bubble: its places together, for a bubble too big to list place by place. */
data class MapRegion(
    val state: String,
    val country: String?,
    val cities: List<PlaceCity>,
) {
    val count: Int get() = cities.sumOf { it.count }
    /** A photo of the biggest place, for the thumbnail. */
    val coverAssetId: String get() = cities.first().coverAssetId
}

/**
 * The regions of [cities], the one with the most photos first. Places without a region name are not part of any
 * region (the geocoding does not always have one).
 */
fun regionsOf(cities: List<PlaceCity>): List<MapRegion> =
    cities.filter { it.state != null }
        .groupBy { it.state!! to it.country }
        .map { (key, group) -> MapRegion(key.first, key.second, group.sortedByDescending { it.count }) }
        .sortedByDescending { it.count }

/** A country or region must hold at least this share of a bubble's photos to be named; a few photos across a border do not count. */
const val CLUSTER_NAME_MIN_SHARE = 0.10

/** A bubble wider than this (degrees, roughly 700 km) is named by its country, a smaller one by its regions or its biggest city. */
const val CLUSTER_NAME_COUNTRY_EXTENT_DEGREES = 6.0

/** The larger side of the box around the bubble's cities, in degrees (longitudes counted across the date line). */
private fun MapCluster.extentDegrees(): Double {
    val reference = cities.first().lon
    val offsets = cities.map { wrapDegrees(it.lon - reference) }
    val lats = cities.map { it.lat }
    return maxOf(lats.max() - lats.min(), offsets.max() - offsets.min())
}

/** What a bubble is called; the screen turns it into text. A group is never named after one of its cities alone. */
sealed interface ClusterName {
    /** A single city. */
    data class City(val city: String) : ClusterName

    /** A group over several countries: the countries that matter, [more] others, and the number of places. */
    data class Countries(val shown: List<String>, val more: Int, val places: Int) : ClusterName

    /** A group over several regions of one country. */
    data class Regions(val shown: List<String>, val more: Int, val places: Int) : ClusterName

    /** A group in one region: around its biggest city, with the region when the geocoding has one. */
    data class Around(val city: String, val region: String?, val places: Int) : ClusterName
}

/**
 * Names a bubble by what is in it: its countries (those with a share of the photos) when it is several or the bubble
 * is wide, else several regions, else "around" its biggest city and its region, so that at the closest zoom the bubbles of one country still tell which
 * part of it they are.
 */
fun MapCluster.name(minShare: Double = CLUSTER_NAME_MIN_SHARE): ClusterName {
    singleName?.let { return ClusterName.City(it) }
    val places = cities.size
    fun significant(counts: Map<String, Int>): List<String> =
        counts.entries.sortedByDescending { it.value }.filter { it.value >= count * minShare }.map { it.key }
    val countries = significant(cities.filter { it.country != null }.groupBy { it.country!! }.mapValues { (_, v) -> v.sumOf { it.count } })
    if (countries.size >= 2 || (countries.isNotEmpty() && extentDegrees() > CLUSTER_NAME_COUNTRY_EXTENT_DEGREES)) {
        return ClusterName.Countries(countries.take(2), (countries.size - 2).coerceAtLeast(0), places)
    }
    val regions = significant(cities.filter { it.state != null }.groupBy { it.state!! }.mapValues { (_, v) -> v.sumOf { it.count } })
    if (regions.size >= 2) return ClusterName.Regions(regions.take(2), regions.size - 2, places)
    return ClusterName.Around(cities.first().city, regions.firstOrNull() ?: cities.firstNotNullOfOrNull { it.state }, places)
}
