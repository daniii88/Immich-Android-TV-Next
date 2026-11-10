package nl.giejay.android.tv.immich.map

import com.google.common.truth.Truth.assertThat
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
}
