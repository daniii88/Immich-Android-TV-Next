package nl.giejay.android.tv.immich.map

import com.google.common.truth.Truth.assertThat
import nl.giejay.android.tv.immich.api.model.MapMarker
import org.junit.Test

class MapPlacesTest {
    private fun marker(id: String, lat: Double, lon: Double, city: String?, country: String? = "X", state: String? = null) = MapMarker(id, lat, lon, city, state, country)

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
    fun `places that are excluded from the TV app do not reach the map`() {
        val markers = listOf(marker("a", 1.0, 1.0, "Oslo"), marker("b", 1.0, 1.0, "Oslo"), marker("c", 2.0, 2.0, "Rome"))
        val shown = nl.giejay.android.tv.immich.api.withoutExcludedMarkers(markers, setOf("a", "c"))
        val places = groupCities(shown)
        assertThat(places.map { it.city }).containsExactly("Oslo")
        assertThat(places.single().coverAssetId).isEqualTo("b") // the hidden photo is not the picture of the place
        assertThat(nl.giejay.android.tv.immich.api.withoutExcludedMarkers(markers, emptySet())).isEqualTo(markers)
    }

    @Test
    fun `a place takes the region most of its photos name`() {
        val places = groupCities(
            listOf(
                marker("a", 50.0, 8.0, "Mainz", "Germany", "Hessen"), marker("b", 50.0, 8.0, "Mainz", "Germany", "Rheinland-Pfalz"),
                marker("c", 50.0, 8.0, "Mainz", "Germany", "Rheinland-Pfalz"), marker("d", 52.0, 13.0, "Berlin", "Germany", null),
            )
        )
        assertThat(places.first { it.city == "Mainz" }.state).isEqualTo("Rheinland-Pfalz")
        assertThat(places.first { it.city == "Berlin" }.state).isNull()
    }

    @Test
    fun `regions group the places of a state, the one with most photos first, and leave out places without a state`() {
        val cities = groupCities(
            listOf(
                marker("a", 48.1, 11.5, "Muenchen", "Germany", "Bayern"), marker("b", 48.1, 11.5, "Muenchen", "Germany", "Bayern"),
                marker("c", 49.4, 11.1, "Nuernberg", "Germany", "Bayern"),
                marker("d", 53.5, 10.0, "Hamburg", "Germany", "Hamburg"),
                marker("e", 52.0, 13.0, "Berlin", "Germany", null),
            )
        )
        val regions = regionsOf(cities)
        assertThat(regions.map { it.state }).containsExactly("Bayern", "Hamburg").inOrder()
        assertThat(regions.first().count).isEqualTo(3)
        assertThat(regions.first().cities.map { it.city }).containsExactly("Muenchen", "Nuernberg").inOrder()
        assertThat(regions.first().coverAssetId).isEqualTo("a")
    }
    private fun place(name: String, country: String?, state: String?, count: Int) = PlaceCity(name, country, count, 0.0, 0.0, name, state)

    @Test
    fun `countries group their places, the most photographed country and place first`() {
        val countries = countriesOf(
            listOf(place("Paris", "France", null, 3), place("Berlin", "Germany", null, 2), place("Hamburg", "Germany", null, 4), place("Nowhere", null, null, 9))
        )
        assertThat(countries.map { it.name }).containsExactly("Germany", "France").inOrder()
        assertThat(countries.first().count).isEqualTo(6)
        assertThat(countries.first().cities.map { it.city }).containsExactly("Hamburg", "Berlin").inOrder()
        assertThat(countries.first().coverAssetId).isEqualTo("Hamburg")
    }

    @Test
    fun `a small country lists its places, a big one its regions and the places without a region`() {
        val small = PlaceCountry("Austria", listOf(place("Wien", "Austria", "Wien", 3), place("Graz", "Austria", "Steiermark", 1)))
        assertThat(small.contents().regions).isEmpty()
        assertThat(small.contents().places.map { it.city }).containsExactly("Wien", "Graz")

        val cities = (1..REGIONS_FROM_PLACES).map { place("Ort $it", "Germany", if (it % 2 == 0) "Bayern" else "Hessen", 1) } +
            place("Irgendwo", "Germany", null, 1)
        val big = PlaceCountry("Germany", cities).contents()
        assertThat(big.regions.map { it.state }).containsExactly("Bayern", "Hessen")
        assertThat(big.places.map { it.city }).containsExactly("Irgendwo")
    }

    @Test
    fun `a big country of one region lists its places`() {
        val cities = (0..REGIONS_FROM_PLACES).map { place("Ort $it", "Monaco", "Monaco", 1) }
        val contents = PlaceCountry("Monaco", cities).contents()
        assertThat(contents.regions).isEmpty()
        assertThat(contents.places).hasSize(cities.size)
    }

    @Test
    fun `the footprint keeps every photo, also those without a place name, with its country`() {
        val footprint = MapFootprint.of(
            listOf(marker("a", 48.1, 11.5, "Muenchen", "Germany"), marker("b", 10.0, 10.0, null, null), marker("c", 48.9, 2.3, "Paris", "France"))
        )
        assertThat(footprint.size).isEqualTo(3)
        assertThat(footprint.countries).containsExactly("Germany", "France").inOrder()
        assertThat(footprint.country.toList()).containsExactly(0, -1, 1).inOrder()
    }

    @Test
    fun `footprint cells count the photos per dot, the highlighted country apart`() {
        val footprint = MapFootprint.of(
            listOf(
                marker("a", 45.0, -135.0, "A", "Germany"), marker("b", 45.0, -135.0, "A", "Germany"),
                marker("c", 45.0, -135.0, "B", "France"), marker("d", -45.0, 135.0, "C", "France"),
            )
        )
        // a 4 x 2 grid over the world from -180 to 180 and 90 to -90: 90 degrees per column, 90 per row
        val view = MapViewport(-180.0, 180.0, 90.0, -90.0)
        val cells = footprintCells(footprint, view, 4, 2, highlight = "Germany")
        assertThat(cells.all.toList()).containsExactly(3, 0, 0, 0, 0, 0, 0, 1).inOrder()
        assertThat(cells.bright.toList()).containsExactly(2, 0, 0, 0, 0, 0, 0, 0).inOrder()
        assertThat(cells.max).isEqualTo(3)
        assertThat(footprintCells(footprint, view, 4, 2, highlight = null).bright.toList()).isEqualTo(cells.all.toList())
    }

    @Test
    fun `photos outside the view are not counted`() {
        val footprint = MapFootprint.of(listOf(marker("a", 48.1, 11.5, "Muenchen", "Germany"), marker("b", -33.9, 151.2, "Sydney", "Australia")))
        val europe = MapViewport(-10.0, 30.0, 60.0, 40.0)
        assertThat(footprintCells(footprint, europe, 10, 5, null).all.sum()).isEqualTo(1)
    }
}
