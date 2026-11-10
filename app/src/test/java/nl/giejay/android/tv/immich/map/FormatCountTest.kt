package nl.giejay.android.tv.immich.map

import com.google.common.truth.Truth.assertThat
import nl.giejay.android.tv.immich.ui.map.formatCount
import org.junit.Test

class FormatCountTest {
    @Test
    fun `counts are shortened to fit the marker`() {
        assertThat(formatCount(7)).isEqualTo("7")
        assertThat(formatCount(353)).isEqualTo("353")
        assertThat(formatCount(999)).isEqualTo("999")
        assertThat(formatCount(1000)).isEqualTo("1.0k")
        assertThat(formatCount(1234)).isEqualTo("1.2k")
        assertThat(formatCount(9999)).isEqualTo("9.9k")
        assertThat(formatCount(15_700)).isEqualTo("15k")
    }
}
