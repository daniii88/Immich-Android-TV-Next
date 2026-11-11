package nl.giejay.android.tv.immich.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class WorldDotsTest {
    @Test
    fun `bits are read row by row, most significant bit first`() {
        val cells = WorldDots.decode(byteArrayOf(0b1010_0000.toByte(), 0b0100_0000), cols = 4, rows = 4)
        assertThat(cells.toList().take(8)).containsExactly(true, false, true, false, false, false, false, false).inOrder()
        assertThat(cells[9]).isTrue()
        assertThat(cells.count { it }).isEqualTo(3)
    }

    @Test
    fun `missing bytes count as sea`() {
        assertThat(WorldDots.decode(byteArrayOf(), cols = 2, rows = 2).toList()).containsExactly(false, false, false, false)
    }

    @Test
    fun `positions map onto the unit square`() {
        assertThat(WorldDots.x(-180.0)).isEqualTo(0f)
        assertThat(WorldDots.x(180.0)).isEqualTo(1f)
        assertThat(WorldDots.x(0.0)).isWithin(1e-6f).of(0.5f)
        assertThat(WorldDots.y(WorldDots.LAT_TOP)).isEqualTo(0f)
        assertThat(WorldDots.y(WorldDots.LAT_BOTTOM)).isEqualTo(1f)
        assertThat(WorldDots.y(-85.0)).isEqualTo(1f) // beyond the drawn part: at the edge
    }

    @Test
    fun `the bundled mask has the expected size and puts well known places on land`() {
        val bytes = File("src/main/res/raw/world_land_dots.bin").readBytes()
        assertThat(bytes.size).isEqualTo((WorldDots.COLS * WorldDots.ROWS + 7) / 8)
        val land = WorldDots.decode(bytes)
        fun onLand(lat: Double, lon: Double): Boolean {
            val col = (WorldDots.x(lon) * WorldDots.COLS).toInt().coerceAtMost(WorldDots.COLS - 1)
            val row = (WorldDots.y(lat) * WorldDots.ROWS).toInt().coerceAtMost(WorldDots.ROWS - 1)
            return land[row * WorldDots.COLS + col]
        }
        assertThat(onLand(47.0, 8.0)).isTrue() // Switzerland
        assertThat(onLand(-25.0, 135.0)).isTrue() // Australia
        assertThat(onLand(40.0, -100.0)).isTrue() // USA
        assertThat(onLand(0.0, -30.0)).isFalse() // Atlantic
        assertThat(onLand(-30.0, -120.0)).isFalse() // Pacific
    }
}
