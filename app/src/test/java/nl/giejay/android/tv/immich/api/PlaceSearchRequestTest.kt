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

    private fun json(request: nl.giejay.android.tv.immich.api.model.SearchRequest) =
        com.google.gson.JsonParser.parseString(SearchRequestJson.gson.toJson(request)).asJsonObject

    @Test
    fun `an exact place sends a missing state and country as null, so only photos without one are found`() {
        val request = buildListAssetsSearchRequest(
            page = 1, pageCount = 30, albumIds = emptyList(), order = "desc", type = null, personIds = emptyList(),
            endDate = null, fromDate = null, city = "Portland", exactPlace = true,
        )
        val body = json(request)
        assertThat(body.get("city").asString).isEqualTo("Portland")
        assertThat(body.has("state")).isTrue()
        assertThat(body.get("state").isJsonNull).isTrue()
        assertThat(body.get("country").isJsonNull).isTrue()
        // other empty fields are still left out, and the marker itself is not sent
        assertThat(body.has("takenBefore")).isFalse()
        assertThat(body.has("exactPlace")).isFalse()
    }

    @Test
    fun `an exact place with a state and country sends them, other searches leave missing ones out`() {
        val exact = json(
            buildListAssetsSearchRequest(
                page = 1, pageCount = 30, albumIds = emptyList(), order = "desc", type = null, personIds = emptyList(),
                endDate = null, fromDate = null, city = "Portland", state = "Maine", country = "USA", exactPlace = true,
            )
        )
        assertThat(exact.get("state").asString).isEqualTo("Maine")
        assertThat(exact.get("country").asString).isEqualTo("USA")
        val any = json(
            buildListAssetsSearchRequest(
                page = 1, pageCount = 30, albumIds = emptyList(), order = "desc", type = null, personIds = emptyList(),
                endDate = null, fromDate = null, city = "Portland",
            )
        )
        assertThat(any.has("state")).isFalse()
        assertThat(any.has("country")).isFalse()
    }
}
