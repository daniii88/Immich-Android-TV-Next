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
    fun `the cluster radius shrinks with the zoom`() {
        assertThat(MapViewport.WORLD.clusterRadius).isEqualTo(MAP_CLUSTER_RADIUS_DEGREES)
        val zoomed = viewportFor(listOf(city("A", 47.0, 7.0), city("B", 48.8, 2.3)))
        assertThat(zoomed.clusterRadius).isLessThan(MAP_CLUSTER_RADIUS_DEGREES / 5)
    }

    @Test
    fun `zooming into a bubble splits its cities, a bubble of one spot cannot be zoomed`() {
        val cities = listOf(city("Paris", 48.85, 2.35, 3), city("London", 51.5, -0.12, 2), city("Rome", 41.9, 12.5, 1))
        val world = clusterCities(cities, MapViewport.WORLD.clusterRadius)
        val europe = world.maxByOrNull { it.cities.size }!!
        if (europe.cities.size > 1) {
            assertThat(canZoomInto(europe, MapViewport.WORLD)).isTrue()
            val inside = clustersFor(cities, viewportFor(europe.cities))
            assertThat(inside.size).isGreaterThan(1)
        }
        val sameSpot = clusterCities(listOf(city("A", 10.0, 10.0), city("B", 10.001, 10.001)), 3.0).single()
        assertThat(canZoomInto(sameSpot, MapViewport.WORLD)).isFalse()
    }

    @Test
    fun `only cities in view are listed`() {
        val view = viewportFor(listOf(city("Paris", 48.85, 2.35), city("London", 51.5, -0.12)))
        val clusters = clustersFor(listOf(city("Paris", 48.85, 2.35), city("London", 51.5, -0.12), city("Sydney", -33.9, 151.2)), view)
        assertThat(clusters.flatMap { it.cities }.map { it.city }).containsExactly("Paris", "London")
    }
}
