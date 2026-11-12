package nl.giejay.android.tv.immich.places

import android.os.Bundle
import arrow.core.Either
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.assets.GenericAssetFragment
import nl.giejay.android.tv.immich.shared.prefs.PhotosOrder
import nl.giejay.android.tv.immich.tags.TagsFragment

/** The photos of one place (city), region (state) or country, or of one tag; opened from [PlacesFragment] / [TagsFragment]. */
class FilteredAssetsFragment : GenericAssetFragment() {
    private lateinit var filterType: String
    private lateinit var filterValue: String
    private lateinit var filterTitle: String
    /** Narrows a city to its country, where two countries have a city of the same name (Paris); null: any. */
    private var filterCountry: String? = null
    /** Narrows a city to its region, where one country has two cities of the same name (Portland); null: any. */
    private var filterState: String? = null
    /** A city exactly as the map knows it: a missing region or country means "without one", not "any". */
    private var filterExactPlace = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val args = FilteredAssetsFragmentArgs.fromBundle(requireArguments())
        filterType = args.filterType
        filterValue = args.filterValue
        filterTitle = args.filterTitle
        filterCountry = args.filterCountry
        filterState = args.filterState
        filterExactPlace = args.filterExactPlace
        super.onCreate(savedInstanceState)
    }

    override suspend fun loadItems(apiClient: ApiClient, page: Int, pageCount: Int): Either<String, List<Asset>> =
        apiClient.listAssetsByFilter(
            page = page,
            pageCount = pageCount,
            contentType = currentFilter,
            order = if (currentSort == PhotosOrder.NEWEST_OLDEST) "desc" else "asc",
            city = if (filterType == PlacesFragment.FILTER_CITY) filterValue else null,
            state = when (filterType) {
                PlacesFragment.FILTER_STATE -> filterValue
                PlacesFragment.FILTER_CITY -> filterState
                else -> null
            },
            country = when (filterType) {
                PlacesFragment.FILTER_COUNTRY -> filterValue
                PlacesFragment.FILTER_CITY, PlacesFragment.FILTER_STATE -> filterCountry
                else -> null
            },
            tagIds = if (filterType == TagsFragment.FILTER_TAG) listOf(filterValue) else emptyList(),
            exactPlace = filterExactPlace && filterType == PlacesFragment.FILTER_CITY,
        ).map { it.assets }

    override fun showMediaCount(): Boolean = false

    override fun setTitle(response: List<Asset>) {
        title = filterTitle
    }
}
