package nl.giejay.android.tv.immich.map

import com.google.common.truth.Truth.assertThat
import nl.giejay.android.tv.immich.api.model.MapMarker
import org.junit.Test

class MapPlacesTest {
    private fun marker(id: String, lat: Double, lon: Double, city: String?, country: String? = "X") = MapMarker(id, lat, lon, city, null, country)

    @Test
    fun `photos of one city become one place at the mean position`() {
        val places = groupCities(listOf(marker("a", 46.0, 7.0, "Bern"), marker("b", 48.0, 9.0, "Bern"), marker("c", 10.0, 10.0, "Lima")))
        assertThat(places.map { it.city }).containsExactly("Bern", "Lima").inOrder()
        val bern = places.first()
        assertThat(bern.count).isEqualTo(2)
        assertThat(bern.lat).isWithin(1e-9).of(47.0)
        assertThat(bern.lon).isWithin(1e-9).of(8.0)
        assertThat(bern.coverAssetId).isEqualTo("a")
    }

    @Test
    fun `photos without a city cannot be opened as a place and are left out`() {
        val places = groupCities(listOf(marker("a", 1.0, 1.0, null), marker("b", 1.0, 1.0, "  "), marker("c", 2.0, 2.0, "Oslo")))
        assertThat(places.map { it.city }).containsExactly("Oslo")
    }

    @Test
    fun `the same city name in two countries stays two places`() {
        val places = groupCities(listOf(marker("a", 1.0, 1.0, "Paris", "France"), marker("b", 30.0, -95.0, "Paris", "USA")))
        assertThat(places).hasSize(2)
    }

    @Test
    fun `close cities share a bubble, the biggest first, at the photo weighted position`() {
        val cities = groupCities(
            listOf(marker("a", 47.0, 7.0, "Bern"), marker("b", 47.0, 7.0, "Bern"), marker("c", 47.0, 7.0, "Bern"), marker("d", 47.5, 8.5, "Zurich"))
        )
        val clusters = clusterCities(cities)
        assertThat(clusters).hasSize(1)
        val cluster = clusters.single()
        assertThat(cluster.count).isEqualTo(4)
        assertThat(cluster.label).isEqualTo("Bern +1")
        assertThat(cluster.lat).isWithin(1e-9).of((47.0 * 3 + 47.5) / 4)
    }

    @Test
    fun `neighbours on either side of a cell border still share a bubble`() {
        // 6.9 and 8.1 degrees east lie in different cells of a 4 degree grid but are 130 km apart
        val clusters = clusterCities(groupCities(listOf(marker("a", 47.0, 6.9, "Bern"), marker("b", 47.0, 8.1, "Luzern"))))
        assertThat(clusters).hasSize(1)
    }

    @Test
    fun `cities across the date line are close`() {
        val clusters = clusterCities(groupCities(listOf(marker("a", -17.0, 179.5, "Suva"), marker("b", -17.0, -179.5, "Taveuni"))))
        assertThat(clusters).hasSize(1)
    }

    @Test
    fun `far apart cities stay separate bubbles, the bigger bubble first`() {
        val clusters = clusterCities(
            groupCities(listOf(marker("a", 47.0, 7.0, "Bern"), marker("b", -33.9, 151.2, "Sydney"), marker("c", -33.9, 151.2, "Sydney")))
        )
        assertThat(clusters.map { it.label }).containsExactly("Sydney", "Bern").inOrder()
    }
}
