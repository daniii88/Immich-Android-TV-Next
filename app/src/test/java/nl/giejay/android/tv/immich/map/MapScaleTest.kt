package nl.giejay.android.tv.immich.map

import com.google.common.truth.Truth.assertThat
import nl.giejay.android.tv.immich.api.model.MapMarker
import org.junit.Test
import kotlin.random.Random

/** A big library: tens of thousands of photos in one country, hundreds of places. Must stay quick and readable. */
class MapScaleTest {
    // Germany, with a few hundred "places" the way the server's geocoding names them (localities, not just cities)
    private val markers: List<MapMarker> = run {
        val random = Random(7)
        val places = List(600) { Triple("Ort $it", 47.5 + random.nextDouble() * 7.0, 6.0 + random.nextDouble() * 9.0) }
        List(30_000) { i ->
            val (name, lat, lon) = places[(random.nextDouble().let { it * it } * places.size).toInt()] // some places have many photos
            MapMarker("id$i", lat + random.nextDouble() * 0.01, lon + random.nextDouble() * 0.01, name, "Land", "Germany")
        }
    }

    @Test
    fun `thousands of photos in one country group into places and stay quick`() {
        val start = System.nanoTime()
        val cities = groupCities(markers)
        val world = clustersFor(cities, MapViewport.WORLD)
        val tookMs = (System.nanoTime() - start) / 1_000_000
        assertThat(cities.size).isAtMost(600)
        assertThat(cities.sumOf { it.count }).isEqualTo(30_000)
        assertThat(tookMs).isLessThan(3_000)
        // on the world map the whole country is a single bubble, not hundreds on top of each other
        assertThat(world.size).isAtMost(2)
        assertThat(world.sumOf { it.count }).isEqualTo(30_000)
    }

    @Test
    fun `zooming into the country falls apart into regions, then into cities, step by step`() {
        val cities = groupCities(markers)
        var view = MapViewport.WORLD
        var clusters = clustersFor(cities, view)
        var counts = mutableListOf(clusters.size)
        repeat(6) {
            val biggest = clusters.maxByOrNull { it.count }!!
            if (!canZoomInto(biggest, view)) return@repeat
            view = viewportFor(biggest.cities)
            clusters = clustersFor(cities, view)
            counts += clusters.size
        }
        // every step shows more bubbles than the one before (until the places are apart), and each stays readable
        assertThat(counts.zipWithNext().all { (a, b) -> b >= a }).isTrue()
        assertThat(counts.last()).isGreaterThan(counts.first())
        assertThat(clusters.size).isAtMost(120)
    }

    @Test
    fun `the cards of a huge bubble are the biggest places`() {
        val cities = groupCities(markers)
        val bubble = clustersFor(cities, MapViewport.WORLD).first()
        val top = bubble.cities.take(40)
        assertThat(top.map { it.count }).isInOrder(Comparator.reverseOrder<Int>())
        assertThat(orderByProximity(top)).hasSize(40)
    }
}
