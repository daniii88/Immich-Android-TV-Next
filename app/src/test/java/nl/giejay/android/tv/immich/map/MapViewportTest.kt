package nl.giejay.android.tv.immich.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MapViewportTest {
    private fun city(name: String, lat: Double, lon: Double, count: Int = 1) = PlaceCity(name, "X", count, lat, lon, name)

    @Test
    fun `the world view maps the corners onto the unit square`() {
        val world = MapViewport.WORLD
        assertThat(world.x(-180.0)).isEqualTo(0f)
        assertThat(world.x(180.0)).isEqualTo(1f)
        assertThat(world.y(WorldDots.LAT_TOP)).isEqualTo(0f)
        assertThat(world.y(WorldDots.LAT_BOTTOM)).isEqualTo(1f)
    }

    @Test
    fun `a zoomed view keeps the proportions of the map and stays inside the world`() {
        val view = viewportFor(listOf(city("A", 47.0, 7.0), city("B", 48.8, 2.3), city("C", 51.5, -0.1)))
        assertThat(view.width / view.height).isWithin(1e-6).of(WorldDots.ASPECT.toDouble())
        assertThat(view.lonMin).isAtLeast(-180.0)
        assertThat(view.lonMax).isAtMost(180.0)
        assertThat(view.latTop).isAtMost(WorldDots.LAT_TOP)
        assertThat(view.latBottom).isAtLeast(WorldDots.LAT_BOTTOM)
        // all three cities are in view
        listOf(47.0 to 7.0, 48.8 to 2.3, 51.5 to -0.1).forEach { (lat, lon) -> assertThat(view.contains(lat, lon)).isTrue() }
    }

    @Test
    fun `a single city is not zoomed beyond the minimum`() {
        val view = viewportFor(listOf(city("A", 35.0, 139.0)))
        assertThat(view.width).isEqualTo(MapViewport.MIN_WIDTH_DEGREES)
    }

    @Test
    fun `a view near the date line or the poles is moved into the world`() {
        val view = viewportFor(listOf(city("A", 83.0, 179.0)))
        assertThat(view.lonMax).isAtMost(180.0)
        assertThat(view.latTop).isAtMost(WorldDots.LAT_TOP)
    }

    @Test
    fun `the northernmost place leaves room above it for its photo, and every place stays in view`() {
        val cities = listOf(city("Kiel", 54.3, 10.1), city("Muenchen", 48.1, 11.6), city("Aachen", 50.8, 6.1), city("Goerlitz", 51.2, 15.0))
        val view = viewportFor(cities, topRoom = 0.3)
        assertThat(view.y(54.3).toDouble()).isAtLeast(0.3 - 1e-6)
        cities.forEach { assertThat(view.contains(it.lat, it.lon)).isTrue() }
        assertThat(view.y(48.1)).isAtMost(1f)
        // without the room the view is centred on the places
        assertThat(viewportFor(cities).y(54.3)).isLessThan(0.3f)
    }

    @Test
    fun `places spread from north to south make the view grow for the room above them`() {
        val cities = listOf(city("A", 60.0, 10.0), city("B", 40.0, 10.0))
        val view = viewportFor(cities, topRoom = 0.3)
        assertThat(view.y(60.0).toDouble()).isAtLeast(0.3 - 1e-6)
        assertThat(view.y(40.0).toDouble()).isAtMost(0.92 + 1e-6)
    }

    @Test
    fun `places given as points are framed like cities`() {
        val cities = listOf(city("Kiel", 54.3, 10.1), city("Muenchen", 48.1, 11.6))
        assertThat(viewportFor(cities.map { it.lat to it.lon }, 0.3)).isEqualTo(viewportFor(cities, 0.3))
        // an extra point far away (a region without named places) widens the view so its pin fits
        val wider = viewportFor(cities.map { it.lat to it.lon } + (47.5 to 19.0), 0.3)
        assertThat(wider.contains(47.5, 19.0)).isTrue()
    }
}
