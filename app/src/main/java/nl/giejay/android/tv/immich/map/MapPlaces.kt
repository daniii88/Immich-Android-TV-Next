package nl.giejay.android.tv.immich.map

import nl.giejay.android.tv.immich.api.model.MapMarker
import kotlin.math.floor

/**
 * The photos of one city (all photos with the same city, region and country name), placed at the mean of their
 * positions. The region is part of it, so same-named cities of one country (Portland, Oregon and Portland, Maine) stay
 * apart.
 */
data class PlaceCity(
    val city: String,
    val country: String?,
    val count: Int,
    val lat: Double,
    val lon: Double,
    /** A photo of the place, for its thumbnail. */
    val coverAssetId: String,
    /** The region (state, province) of the photos; null when the geocoding has none. */
    val state: String? = null,
) {
    /** Tells same-named places apart, for cards and pins. */
    val key: String get() = "city|$city|${state.orEmpty()}|${country.orEmpty()}"
}

private fun String?.cleaned(): String? = this?.trim()?.ifEmpty { null }

/** The places the map offers. Photos without a city name cannot be opened as a place, so they are left out. */
fun groupCities(markers: List<MapMarker>): List<PlaceCity> =
    markers.filter { !it.city.isNullOrBlank() }
        .groupBy { Triple(it.city!!.trim(), it.state.cleaned(), it.country.cleaned()) }
        .map { (key, group) ->
            PlaceCity(
                city = key.first,
                country = key.third,
                count = group.size,
                lat = group.map { it.lat }.average(),
                lon = group.map { it.lon }.average(),
                coverAssetId = group.first().id,
                state = key.second,
            )
        }
        .sortedByDescending { it.count }

/**
 * One country: its places (the one with the most photos first) and all its photos, those without a place name too, so
 * a country visited only in places without a name still has a card, and its count matches what its search finds.
 * [stateCounts] are the photos per region, likewise with the unnamed places. The pin stands at [lat] / [lon]: the
 * biggest place, or a photo when it has none.
 */
data class PlaceCountry(
    val name: String,
    val cities: List<PlaceCity>,
    val count: Int = cities.sumOf { it.count },
    /** A photo of the biggest place (or any photo of the country), for the thumbnail. */
    val coverAssetId: String = cities.first().coverAssetId,
    val lat: Double = cities.first().lat,
    val lon: Double = cities.first().lon,
    val stateCounts: Map<String, Int> = emptyMap(),
)

/** The countries of [markers] with their places among [cities], the one with the most photos first. */
fun countriesOf(markers: List<MapMarker>, cities: List<PlaceCity>): List<PlaceCountry> {
    val citiesByCountry = cities.filter { it.country != null }.groupBy { it.country!! }
    return markers.filter { it.country.cleaned() != null }
        .groupBy { it.country.cleaned()!! }
        .map { (name, group) ->
            val places = citiesByCountry[name].orEmpty().sortedByDescending { it.count }
            val first = group.first()
            PlaceCountry(
                name = name,
                cities = places,
                count = group.size,
                coverAssetId = places.firstOrNull()?.coverAssetId ?: first.id,
                lat = places.firstOrNull()?.lat ?: first.lat,
                lon = places.firstOrNull()?.lon ?: first.lon,
                stateCounts = group.mapNotNull { it.state.cleaned() }.groupingBy { it }.eachCount(),
            )
        }
        .sortedByDescending { it.count }
}

/** The places of one region (state) of a country. */
data class MapRegion(
    val state: String,
    val country: String?,
    val cities: List<PlaceCity>,
    /** All its photos, those without a place name too (they are part of what its search finds). */
    val count: Int = cities.sumOf { it.count },
) {

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

/** A country with more places than this lists its regions first, so the row of cards stays short enough for a remote. */
const val REGIONS_FROM_PLACES = 25

/** What a country lists: its regions and the places without a region, or (a smaller country) just its places. */
data class CountryContents(val regions: List<MapRegion>, val places: List<PlaceCity>)

fun PlaceCountry.contents(): CountryContents {
    val regions = if (cities.size > REGIONS_FROM_PLACES) {
        regionsOf(cities).map { it.copy(count = maxOf(it.count, stateCounts[it.state] ?: 0)) }.sortedByDescending { it.count }
    } else {
        emptyList()
    }
    return if (regions.size >= 2) CountryContents(regions, cities.filter { it.state == null }) else CountryContents(emptyList(), cities)
}

/**
 * Where the photos were taken: one point per photo (photos without a place name too, they were taken somewhere), with
 * the country of each as an index into [countries] (-1: none).
 */
class MapFootprint(val lat: FloatArray, val lon: FloatArray, val country: IntArray, val countries: List<String>) {
    val size: Int get() = lat.size

    companion object {
        fun of(markers: List<MapMarker>): MapFootprint {
            val names = LinkedHashMap<String, Int>()
            val country = IntArray(markers.size)
            markers.forEachIndexed { i, marker ->
                val name = marker.country?.trim()?.ifEmpty { null }
                country[i] = if (name == null) -1 else names.getOrPut(name) { names.size }
            }
            return MapFootprint(
                FloatArray(markers.size) { markers[it].lat.toFloat() },
                FloatArray(markers.size) { markers[it].lon.toFloat() },
                country,
                names.keys.toList(),
            )
        }
    }
}

/** Photos per dot of the map: all of them, and those of the highlighted country (all of them when nothing is highlighted). */
class FootprintCells(val cols: Int, val rows: Int, val all: IntArray, val bright: IntArray) {
    val max: Int get() = all.maxOrNull() ?: 0
}

/** Counts the photos of [footprint] per dot of a [cols] x [rows] grid over [view]; [highlight] is a country name or null. */
fun footprintCells(footprint: MapFootprint, view: MapViewport, cols: Int, rows: Int, highlight: String?): FootprintCells {
    val all = IntArray(cols * rows)
    val bright = IntArray(cols * rows)
    val highlighted = highlight?.let { footprint.countries.indexOf(it) }
    for (i in 0 until footprint.size) {
        val col = floor((footprint.lon[i] - view.lonMin) / view.width * cols).toInt()
        val row = floor((view.latTop - footprint.lat[i]) / view.height * rows).toInt()
        if (col !in 0 until cols || row !in 0 until rows) continue
        val cell = row * cols + col
        all[cell]++
        if (highlighted == null || footprint.country[i] == highlighted) bright[cell]++
    }
    return FootprintCells(cols, rows, all, bright)
}
