package nl.giejay.android.tv.immich.map

import com.google.common.truth.Truth.assertThat
import nl.giejay.android.tv.immich.ui.map.dotPixels
import nl.giejay.android.tv.immich.ui.map.dotStep
import org.junit.Test

class DotStepTest {
    @Test
    fun `dots keep their spacing in wide views and get finer when zoomed in`() {
        assertThat(dotStep(5f, 360.0)).isEqualTo(5f)
        assertThat(dotStep(5f, 150.0)).isEqualTo(5f)
        assertThat(dotStep(5f, 105.0)).isWithin(1e-4f).of(4f) // half way: 80 %
        assertThat(dotStep(5f, 60.0)).isWithin(1e-4f).of(3f) // 60 %
        assertThat(dotStep(5f, 10.0)).isWithin(1e-4f).of(3f) // and no finer
    }

    @Test
    fun `dots sit on whole pixels, at least 3 apart, squares of 2 or 3 pixels`() {
        assertThat(dotPixels(5.3f)).isEqualTo(5 to 3)
        assertThat(dotPixels(4.4f)).isEqualTo(4 to 2)
        assertThat(dotPixels(3.2f)).isEqualTo(3 to 2)
        assertThat(dotPixels(1.0f)).isEqualTo(3 to 2) // never finer than 3 pixels
    }
}
