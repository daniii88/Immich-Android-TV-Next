package nl.giejay.android.tv.immich.map

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import nl.giejay.android.tv.immich.api.model.MapMarker
import org.junit.Test
import kotlin.random.Random

/**
 * A heavy library: the server sends every marker at once (about 120 bytes of JSON each). Parsing, grouping into places
 * and the clustering of a view have to stay in the range of a second on a desktop JVM; the numbers are printed so a
 * slower TV box can be judged against them.
 */
class MapHugeLibraryTest {
    private fun json(count: Int, placeCount: Int): String {
        val random = Random(11)
        // places all over the populated world, with a few hot spots that hold most of the photos
        val places = List(placeCount) { Triple("Ort $it", -50 + random.nextDouble() * 110, -120 + random.nextDouble() * 300) }
        val sb = StringBuilder("[")
        repeat(count) { i ->
            val (name, lat, lon) = places[(random.nextDouble().let { it * it * it } * places.size).toInt()]
            if (i > 0) sb.append(',')
            sb.append("{\"id\":\"b3a1f0c2-4d5e-4f60-8a7b-").append(i.toString().padStart(12, '0'))
                .append("\",\"lat\":").append(lat + random.nextDouble() * 0.02)
                .append(",\"lon\":").append(lon + random.nextDouble() * 0.02)
                .append(",\"city\":\"").append(name).append("\",\"state\":\"Staat\",\"country\":\"Land ").append(name.hashCode() and 15).append("\"}")
        }
        return sb.append(']').toString()
    }

    private fun measure(count: Int, placeCount: Int) {
        val text = json(count, placeCount)
        val type = object : TypeToken<List<MapMarker>>() {}.type
        var t = System.nanoTime()
        val markers: List<MapMarker> = Gson().fromJson(text, type)
        val parseMs = (System.nanoTime() - t) / 1_000_000
        t = System.nanoTime()
        val cities = groupCities(markers)
        val groupMs = (System.nanoTime() - t) / 1_000_000
        t = System.nanoTime()
        val world = clustersFor(cities, MapViewport.WORLD)
        val worldMs = (System.nanoTime() - t) / 1_000_000
        t = System.nanoTime()
        val zoomView = viewportFor(world.maxByOrNull { it.count }!!.cities)
        val zoomed = clustersFor(cities, zoomView)
        val zoomMs = (System.nanoTime() - t) / 1_000_000
        println("MAPSCALE markers=$count json=${text.length / 1_000_000}MB places=${cities.size} parse=${parseMs}ms group=${groupMs}ms worldClusters=${world.size} (${worldMs}ms) zoomClusters=${zoomed.size} (${zoomMs}ms)")
        assertThat(markers).hasSize(count)
        assertThat(cities.sumOf { it.count }).isEqualTo(count) // every photo has a city here
        assertThat(world.sumOf { it.count }).isEqualTo(count)
    }

    @Test fun `100 thousand photos in 8 thousand places`() = measure(100_000, 8_000)

    @Test fun `300 thousand photos in 20 thousand places`() = measure(300_000, 20_000)
}
