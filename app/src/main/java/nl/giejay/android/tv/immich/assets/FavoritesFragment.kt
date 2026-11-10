package nl.giejay.android.tv.immich.assets

import arrow.core.Either
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.shared.prefs.ContentType
import nl.giejay.android.tv.immich.shared.prefs.EnumByTitlePref
import nl.giejay.android.tv.immich.shared.prefs.FILTER_CONTENT_TYPE_FOR_SPECIFIC_ALBUM
import nl.giejay.android.tv.immich.shared.prefs.PhotosOrder

/** The photos and videos marked as favorite (read only; the heart in the viewer changes them). */
class FavoritesFragment : GenericAssetFragment() {
    override fun getFilterKey(): EnumByTitlePref<ContentType> = FILTER_CONTENT_TYPE_FOR_SPECIFIC_ALBUM("favorites", "favorites")

    override suspend fun loadItems(apiClient: ApiClient, page: Int, pageCount: Int): Either<String, List<Asset>> =
        apiClient.listAssetsByFilter(
            page = page,
            pageCount = pageCount,
            contentType = ContentType.ALL,
            order = if (currentSort == PhotosOrder.NEWEST_OLDEST) "desc" else "asc",
            favorite = true,
        ).map { it.assets }

    override fun setTitle(response: List<Asset>) {
        title = getString(R.string.favorites)
    }
}
