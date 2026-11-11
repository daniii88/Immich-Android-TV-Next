package nl.giejay.android.tv.immich.map

import com.google.common.truth.Truth.assertThat
import nl.giejay.android.tv.immich.api.model.MapMarker
import org.junit.Test
import kotlin.random.Random

/** A big library: tens of thousands of photos in one country, hundreds of places in a few regions. Must stay quick and readable. */
class MapScaleTest {
    private val markers: List<MapMarker> = run {
        val random = Random(7)
        val regions = listOf("Bayern", "Hessen", "Sachsen", "Berlin")
        val places = List(600) { Triple("Ort $it", 47.5 + random.nextDouble() * 7.0, 6.0 + random.nextDouble() * 9.0) }
        List(30_000) { i ->
            val index = (random.nextDouble().let { it * it } * places.size).toInt() // some places have many photos
            val (name, lat, lon) = places[index]
            MapMarker("id$i", lat + random.nextDouble() * 0.01, lon + random.nextDouble() * 0.01, name, regions[index % regions.size], "Germany")
        }
    }

    @Test
    fun `thousands of photos in one country become one country card, its regions, and lit dots, quickly`() {
        val start = System.nanoTime()
        val cities = groupCities(markers)
        val countries = countriesOf(cities)
        val contents = countries.single().contents()
        val footprint = MapFootprint.of(markers)
        val cells = footprintCells(footprint, MapViewport.WORLD, 400, 160, highlight = "Germany")
        val tookMs = (System.nanoTime() - start) / 1_000_000
        assertThat(tookMs).isLessThan(3_000)
        assertThat(countries.single().count).isEqualTo(30_000)
        // 600 places would be too many cards for a remote: the country lists its four regions instead
        assertThat(contents.regions).hasSize(4)
        assertThat(contents.regions.sumOf { it.count }).isEqualTo(30_000)
        assertThat(cells.all.sum()).isEqualTo(30_000)
        assertThat(cells.bright.sum()).isEqualTo(30_000)
    }
}
