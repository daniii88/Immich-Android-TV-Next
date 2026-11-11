package nl.giejay.android.tv.immich.ui.map

import com.google.common.truth.Truth.assertThat
import nl.giejay.android.tv.immich.map.MapViewport
import org.junit.Test

class MapViewportCoversTest {
    private val wide = MapViewport(-20.0, 40.0, 60.0, 30.0)
    private val narrow = MapViewport(0.0, 20.0, 50.0, 40.0)

    @Test fun aWiderViewCoversANarrowerOne() {
        assertThat(wide.covers(narrow)).isTrue()
        assertThat(wide.covers(wide)).isTrue()
    }

    @Test fun aNarrowerViewDoesNotCoverAWiderOne() {
        assertThat(narrow.covers(wide)).isFalse()
    }

    @Test fun aShiftedViewOfTheSameSizeDoesNot() {
        assertThat(narrow.covers(MapViewport(5.0, 25.0, 50.0, 40.0))).isFalse()
    }
}
