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
    fun `the same city name in two regions of one country stays two places, each in its region`() {
        val places = groupCities(
            listOf(
                marker("a", 45.5, -122.7, "Portland", "USA", "Oregon"), marker("b", 45.5, -122.7, "Portland", "USA", "Oregon"),
                marker("c", 43.7, -70.3, "Portland", "USA", "Maine"), marker("d", 52.0, 13.0, "Berlin", "Germany", null),
            )
        )
        assertThat(places.filter { it.city == "Portland" }.map { it.state to it.count }).containsExactly("Oregon" to 2, "Maine" to 1).inOrder()
        assertThat(places.filter { it.city == "Portland" }.map { it.key }.toSet()).hasSize(2)
        assertThat(regionsOf(places).map { it.state to it.count }).containsExactly("Oregon" to 2, "Maine" to 1).inOrder()
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
        val markers = listOf(
            marker("p1", 48.9, 2.3, "Paris", "France"), marker("p2", 48.9, 2.3, "Paris", "France"), marker("p3", 48.9, 2.3, "Paris", "France"),
            marker("b1", 52.5, 13.4, "Berlin", "Germany"), marker("b2", 52.5, 13.4, "Berlin", "Germany"),
            marker("h1", 53.5, 10.0, "Hamburg", "Germany"), marker("h2", 53.5, 10.0, "Hamburg", "Germany"), marker("h3", 53.5, 10.0, "Hamburg", "Germany"),
            marker("n1", 0.0, 0.0, "Nowhere", null),
        )
        val countries = countriesOf(markers, groupCities(markers))
        assertThat(countries.map { it.name }).containsExactly("Germany", "France").inOrder()
        assertThat(countries.first().count).isEqualTo(5)
        assertThat(countries.first().cities.map { it.city }).containsExactly("Hamburg", "Berlin").inOrder()
        assertThat(countries.first().coverAssetId).isEqualTo("h1")
        assertThat(countries.first().lat).isWithin(1e-9).of(53.5)
    }

    @Test
    fun `photos with a country but no place name count for their country and region, and make a country of their own`() {
        val markers = listOf(
            marker("a", 64.1, -21.9, null, "Iceland"), marker("b", 64.2, -21.8, " ", "Iceland"),
            marker("c", 48.1, 11.5, "Muenchen", "Germany", "Bayern"), marker("d", 47.6, 12.0, null, "Germany", "Bayern"),
        )
        val countries = countriesOf(markers, groupCities(markers))
        val iceland = countries.first { it.name == "Iceland" }
        assertThat(iceland.cities).isEmpty()
        assertThat(iceland.count).isEqualTo(2)
        assertThat(iceland.coverAssetId).isEqualTo("a")
        assertThat(iceland.pin().lat).isWithin(1e-9).of(64.1)
        assertThat(iceland.contents().places).isEmpty()
        val germany = countries.first { it.name == "Germany" }
        assertThat(germany.count).isEqualTo(2)
        assertThat(germany.regionPhotos.mapValues { it.value.count }).containsExactly("Bayern", 2)
    }

    @Test
    fun `a region of a big country counts its photos without a place name too`() {
        val cities = (0..REGIONS_FROM_PLACES).map { place("Ort $it", "Germany", if (it % 2 == 0) "Bayern" else "Hessen", 1) }
        val country = PlaceCountry("Germany", cities, count = cities.size + 10, regionPhotos = mapOf("Bayern" to RegionPhotos(22, "x", 0.0, 0.0), "Hessen" to RegionPhotos(13, "y", 0.0, 0.0)))
        assertThat(country.contents().regions.map { it.state to it.count }).containsExactly("Bayern" to 22, "Hessen" to 13).inOrder()
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
    fun `a region of a big country where no photo has a place name still gets a card`() {
        val cities = (0..REGIONS_FROM_PLACES).map { place("Ort $it", "Germany", if (it % 2 == 0) "Bayern" else "Hessen", 1) }
        val country = PlaceCountry(
            "Germany", cities, count = cities.size + 4,
            regionPhotos = mapOf("Bayern" to RegionPhotos(13, "b", 0.0, 0.0), "Hessen" to RegionPhotos(13, "h", 0.0, 0.0), "Saarland" to RegionPhotos(4, "s", 49.2, 7.0)),
        )
        val saarland = country.contents().regions.single { it.state == "Saarland" }
        assertThat(saarland.cities).isEmpty()
        assertThat(saarland.count).isEqualTo(4)
        assertThat(saarland.coverAssetId).isEqualTo("s")
        assertThat(saarland.pin().lat).isWithin(1e-9).of(49.2)
        assertThat(country.contents().regions.map { it.state }).containsExactly("Bayern", "Hessen", "Saarland").inOrder()
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
