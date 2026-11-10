package nl.giejay.android.tv.immich.api

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaceSearchRequestTest {
    @Test
    fun `a place search carries the country, so Paris in France is not Paris in the USA`() {
        val request = buildListAssetsSearchRequest(
            page = 1, pageCount = 30, albumIds = emptyList(), order = "desc", type = null, personIds = emptyList(),
            endDate = null, fromDate = null, city = "Paris", country = "France",
        )
        assertThat(request.city).isEqualTo("Paris")
        assertThat(request.country).isEqualTo("France")
    }

    @Test
    fun `without a country the search stays on the city`() {
        val request = buildListAssetsSearchRequest(
            page = 1, pageCount = 30, albumIds = emptyList(), order = "desc", type = null, personIds = emptyList(),
            endDate = null, fromDate = null, city = "Paris",
        )
        assertThat(request.country).isNull()
    }
}
