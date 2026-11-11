package nl.giejay.android.tv.immich.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WorldVectorsTest {
    @Test
    fun `parts are read as degrees`() {
        val bytes = ByteBuffer.allocate(2 + 2 * 4 + 2 + 4).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(2).putShort(850).putShort(4730).putShort(-1234).putShort(-5600)
            .putShort(1).putShort(0).putShort(0)
            .array()
        val parts = WorldVectors.parse(bytes)
        assertThat(parts).hasSize(2)
        assertThat(parts[0].toList()).containsExactly(8.5f, 47.3f, -12.34f, -56.0f).inOrder()
        assertThat(parts[1].toList()).containsExactly(0f, 0f).inOrder()
    }

    @Test
    fun `a cut off file keeps the whole parts`() {
        val bytes = ByteBuffer.allocate(2 + 4 + 2 + 2).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(1).putShort(100).putShort(200)
            .putShort(5).putShort(1) // announces 5 points, has less than one
            .array()
        assertThat(WorldVectors.parse(bytes)).hasSize(1)
    }

    private fun land(): List<FloatArray> = WorldVectors.parse(File("src/main/res/raw/world_land_50m.bin").readBytes())

    /** Even-odd, like the map fills it: inside an odd number of rings is land. */
    private fun onLand(rings: List<FloatArray>, lon: Double, lat: Double): Boolean {
        var inside = false
        for (ring in rings) {
            var crossings = false
            var j = ring.size / 2 - 1
            for (i in 0 until ring.size / 2) {
                val xi = ring[i * 2]; val yi = ring[i * 2 + 1]; val xj = ring[j * 2]; val yj = ring[j * 2 + 1]
                if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) crossings = !crossings
                j = i
            }
            if (crossings) inside = !inside
        }
        return inside
    }

    @Test
    fun `the bundled coastlines put well known places on land and sea`() {
        val rings = land()
        assertThat(rings.size).isGreaterThan(500)
        assertThat(rings.sumOf { it.size / 2 }).isLessThan(60_000) // stays small
        assertThat(onLand(rings, 10.0, 51.0)).isTrue() // Germany
        assertThat(onLand(rings, 135.0, -25.0)).isTrue() // Australia
        assertThat(onLand(rings, 10.0, 25.0)).isTrue() // Sahara
        assertThat(onLand(rings, -100.0, 40.0)).isTrue() // USA
        assertThat(onLand(rings, 15.0, 35.0)).isFalse() // Mediterranean
        assertThat(onLand(rings, -30.0, 0.0)).isFalse() // Atlantic
        assertThat(onLand(rings, -150.0, 0.0)).isFalse() // Pacific
    }

    @Test
    fun `the bundled borders are lines inside the world`() {
        val borders = WorldVectors.parse(File("src/main/res/raw/world_borders_50m.bin").readBytes())
        assertThat(borders.size).isGreaterThan(200)
        assertThat(borders.all { part -> part.size >= 4 && part.indices.all { i -> if (i % 2 == 0) part[i] in -180f..180f else part[i] in -90f..90f } }).isTrue()
    }
}
