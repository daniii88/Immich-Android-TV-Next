package nl.giejay.android.tv.immich.shared.prefs

import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.model.Album

/** Which albums the albums page lists: the user's own, the shared ones (with or by the user), or both. */
enum class AlbumsFilter(private val titleRes: Int) : EnumWithTitle {
    MINE(R.string.albums_filter_mine),
    SHARED(R.string.albums_filter_shared),
    ALL(R.string.albums_filter_all);

    override fun getTitle(): String = ImmichApplication.appContext!!.getString(titleRes)

    companion object {
        fun valueOfSafe(name: String, default: AlbumsFilter): AlbumsFilter = entries.find { it.toString() == name } ?: default
    }
}

/**
 * The albums the server returned for `shared=true`, reduced to the ones that really are shared: servers differ in what
 * the parameter does (some ignore it and send every album), the `shared` flag of the album is reliable.
 */
fun onlySharedAlbums(albums: List<Album>): List<Album> = albums.filter { it.shared }

/** The own and the shared albums as one list: an album that is in both (an own album that is shared) shows up once. */
fun mergeAlbums(own: List<Album>, shared: List<Album>): List<Album> = (own + shared).distinctBy { it.id }
