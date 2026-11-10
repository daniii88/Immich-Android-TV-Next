package nl.giejay.android.tv.immich.shared.prefs

import com.google.common.truth.Truth.assertThat
import nl.giejay.android.tv.immich.api.model.Album
import org.junit.Test
import java.util.Date

class AlbumsFilterTest {
    private fun album(id: String) = Album(id, "", id, null, Date(0), null, 0)

    @Test
    fun `an own album that is also shared shows up once`() {
        val merged = mergeAlbums(listOf(album("a"), album("b")), listOf(album("b"), album("c")))
        assertThat(merged.map { it.id }).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `unknown stored values fall back to the default`() {
        assertThat(AlbumsFilter.valueOfSafe("nonsense", AlbumsFilter.MINE)).isEqualTo(AlbumsFilter.MINE)
        assertThat(AlbumsFilter.valueOfSafe("SHARED", AlbumsFilter.MINE)).isEqualTo(AlbumsFilter.SHARED)
    }
}
