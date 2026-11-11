package nl.giejay.android.tv.immich.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MapPinsTest {
    private val view = MapViewport(0.0, 100.0, 50.0, 0.0) // 1000 x 500 px: 10 px per degree
    private fun pin(key: String, lat: Double, lon: Double, count: Int) = MapPin(key, lat, lon, key, key, count)
    private val size = PinSize(photoPx = 50f, focusedPhotoPx = 70f, tipPx = 7f, gapPx = 5f)

    @Test
    fun `the places with the most photos get a pin first`() {
        val pins = listOf(pin("a", 10.0, 10.0, 1), pin("b", 20.0, 50.0, 9), pin("c", 40.0, 90.0, 5))
        assertThat(choosePins(pins, null, view, 1000f, 500f, size, 2).map { it.key }).containsExactly("b", "c").inOrder()
    }

    @Test
    fun `pins never lie on top of each other, the bigger place wins`() {
        // 2 degrees apart = 20 px, closer than 60 px
        val pins = listOf(pin("small", 10.0, 10.0, 1), pin("big", 10.0, 12.0, 9))
        assertThat(choosePins(pins, null, view, 1000f, 500f, size, 10).map { it.key }).containsExactly("big")
    }

    @Test
    fun `the focused place is always shown, even next to a bigger one`() {
        val pins = listOf(pin("small", 10.0, 10.0, 1), pin("big", 10.0, 12.0, 9))
        assertThat(choosePins(pins, "small", view, 1000f, 500f, size, 10).map { it.key }).containsExactly("small")
    }

    @Test
    fun `places outside the view get no pin`() {
        val pins = listOf(pin("in", 10.0, 10.0, 1), pin("out", -20.0, 150.0, 9))
        assertThat(choosePins(pins, null, view, 1000f, 500f, size, 10).map { it.key }).containsExactly("in")
    }

    @Test
    fun `photos that would overlap vertically are kept apart too`() {
        // one place 4 degrees (40 px) below the other: the upper photo stands right where the lower one's is
        val pins = listOf(pin("upper", 30.0, 50.0, 9), pin("lower", 26.0, 50.0, 1))
        assertThat(choosePins(pins, null, view, 1000f, 500f, size, 10).map { it.key }).containsExactly("upper")
    }

    @Test
    fun `a photo near the top edge hangs below its place`() {
        assertThat(hangsBelow(20f, 50f, 7f)).isTrue()
        assertThat(hangsBelow(200f, 50f, 7f)).isFalse()
    }

    @Test
    fun `the name under the focused photo keeps the photo below it away`() {
        val named = size.copy(labelPx = 30f)
        // the lower place's photo (50 px + tip above its spot) starts 3 px under the focused photo, inside its name
        val pins = listOf(pin("focused", 30.0, 50.0, 1), pin("below", 23.0, 50.0, 9))
        assertThat(choosePins(pins, "focused", view, 1000f, 500f, size, 10).map { it.key }).containsExactly("focused", "below")
        assertThat(choosePins(pins, "focused", view, 1000f, 500f, named, 10).map { it.key }).containsExactly("focused")
    }
}
