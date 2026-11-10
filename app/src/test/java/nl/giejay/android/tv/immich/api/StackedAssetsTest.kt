package nl.giejay.android.tv.immich.api

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.Stack
import nl.giejay.android.tv.immich.api.model.toTimelineAssets
import org.junit.Test

class StackedAssetsTest {
    private fun request(albumIds: List<String>) = buildListAssetsSearchRequest(
        page = 1, pageCount = 30, albumIds = albumIds, order = "desc",
        type = null, personIds = emptyList(), endDate = null, fromDate = null
    )

    @Test
    fun `a stack shows as one entry outside of albums`() {
        assertThat(request(emptyList()).withStacked).isTrue()
    }

    @Test
    fun `an album keeps listing every member of a stack`() {
        assertThat(request(listOf("album")).withStacked).isNull()
    }

    @Test
    fun `stack and live photo fields are read from the asset json`() {
        val asset = Gson().fromJson(
            """{"id":"a","type":"IMAGE","stack":{"id":"s","assetCount":3,"primaryAssetId":"a"},"livePhotoVideoId":"v"}""",
            Asset::class.java
        )
        assertThat(asset.stack?.assetCount).isEqualTo(3)
        assertThat(asset.stack?.id).isEqualTo("s")
        assertThat(asset.livePhotoVideoId).isEqualTo("v")
    }

    @Test
    fun `assets without them have neither`() {
        val asset = Gson().fromJson("""{"id":"a","type":"IMAGE"}""", Asset::class.java)
        assertThat(asset.stack).isNull()
        assertThat(asset.livePhotoVideoId).isNull()
    }

    @Test
    fun `a stack response lists the primary asset and the others`() {
        val stack = Gson().fromJson(
            """{"id":"s","primaryAssetId":"b","assets":[{"id":"a","type":"IMAGE"},{"id":"b","type":"IMAGE"}]}""",
            Stack::class.java
        )
        assertThat(stack.primaryAssetId).isEqualTo("b")
        assertThat(stack.assets.map { it.id }).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `timeline buckets carry the stack and the live photo video per asset`() {
        val response = Gson().fromJson(
            """{"id":["a","b","c"],"ownerId":["u","u","u"],"ratio":[1.0,1.0,1.0],"isFavorite":[false,false,false],
               "visibility":["timeline","timeline","timeline"],"isTrashed":[false,false,false],"isImage":[true,true,true],
               "thumbhash":[null,null,null],"fileCreatedAt":["2025-09-01T10:00:00.000Z","2025-09-01T10:00:00.000Z","2025-09-01T10:00:00.000Z"],
               "localOffsetHours":[0,0,0],"duration":[null,null,null],
               "stack":[null,["s1","3"],null],"livePhotoVideoId":[null,null,"v1"]}""",
            nl.giejay.android.tv.immich.api.model.TimeBucketAssetsResponse::class.java
        ).let { it.toTimelineAssets() }
        assertThat(response.map { it.stackCount }).containsExactly(0, 3, 0).inOrder()
        assertThat(response[1].stackId).isEqualTo("s1")
        assertThat(response[0].stackId).isNull()
        assertThat(response[2].livePhotoVideoId).isEqualTo("v1")
    }

    @Test
    fun `a bucket without stack arrays (no withStacked) has no stacks`() {
        val response = Gson().fromJson(
            """{"id":["a"],"ownerId":["u"],"ratio":[1.0],"isFavorite":[false],"visibility":["timeline"],"isTrashed":[false],
               "isImage":[true],"thumbhash":[null],"fileCreatedAt":["2025-09-01T10:00:00.000Z"],"localOffsetHours":[0],"duration":[null]}""",
            nl.giejay.android.tv.immich.api.model.TimeBucketAssetsResponse::class.java
        )
        val asset = response.toTimelineAssets().single()
        assertThat(asset.stackCount).isEqualTo(0)
        assertThat(asset.livePhotoVideoId).isNull()
    }
}
