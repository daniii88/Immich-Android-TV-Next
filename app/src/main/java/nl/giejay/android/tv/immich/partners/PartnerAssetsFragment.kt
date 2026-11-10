package nl.giejay.android.tv.immich.partners

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import arrow.core.Either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.album.AlbumDetailsFragmentDirections
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.TimeBucketSummary
import nl.giejay.android.tv.immich.api.model.TimelineAsset
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.shared.fragment.VerticalCardGridFragment
import nl.giejay.android.tv.immich.shared.util.Utils.pmap
import nl.giejay.android.tv.immich.shared.util.buildSliderConfiguration
import nl.giejay.android.tv.immich.timeline.toSliderItemViewHolder
import nl.giejay.mediaslider.util.LoadMore
import nl.giejay.mediaslider.util.LoadMoreResult
import nl.giejay.mediaslider.viewmodel.MediaSliderViewModel

/** A partner's library, newest first; one page is one month, loaded while scrolling. */
class PartnerAssetsFragment : VerticalCardGridFragment<TimelineAsset>() {
    private lateinit var partnerId: String
    private lateinit var partnerName: String
    private var months: List<TimeBucketSummary> = emptyList()
    private val sliderViewModel: MediaSliderViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val args = PartnerAssetsFragmentArgs.fromBundle(requireArguments())
        partnerId = args.partnerId
        partnerName = args.partnerName
        super.onCreate(savedInstanceState)
    }

    override fun sortItems(items: List<TimelineAsset>): List<TimelineAsset> = items.sortedByDescending { it.fileCreatedAt }

    override suspend fun loadItems(apiClient: ApiClient, page: Int, pageCount: Int): Either<String, List<TimelineAsset>> {
        if (page == startPage) {
            when (val result = apiClient.getPartnerTimeBuckets(partnerId)) {
                is Either.Left -> return result
                is Either.Right -> months = result.value
            }
        }
        val month = months.getOrNull(page - startPage) ?: return Either.Right(emptyList())
        return apiClient.getPartnerTimeBucket(partnerId, month.timeBucket)
    }

    // one page is one month, so a short page does not mean the end
    override fun allPagesLoaded(items: List<TimelineAsset>): Boolean = currentPage - startPage + 1 >= months.size

    override fun setTitle(response: List<TimelineAsset>) {
        title = partnerName
    }

    override fun createCard(a: TimelineAsset): Card =
        Card("", null, a.id, ApiUtil.getThumbnailUrl(a.id, "thumbnail"), ApiUtil.getThumbnailUrl(a.id, "preview"))

    override fun getBackgroundPicture(it: TimelineAsset): String? = ApiUtil.getThumbnailUrl(it.id, "preview")

    override fun onItemSelected(card: Card, indexOf: Int) {
        // nothing to do
    }

    override fun onItemClicked(card: Card) {
        progressBar?.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.Default) {
            val items = assets.pmap { it.toSliderItemViewHolder() }
            val loadMore: LoadMore = suspend {
                val more = loadMoreAssets()
                setDataOnMain(more)
                LoadMoreResult(more.pmap { it.toSliderItemViewHolder() }, more.isNotEmpty() && !allPagesLoaded)
            }
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                progressBar?.visibility = View.GONE
                sliderViewModel.configuration = buildSliderConfiguration(
                    items,
                    startIndex = items.indexOfFirst { it.ids().contains(card.id) }.coerceAtLeast(0),
                    loadMore = loadMore,
                )
                findNavController().navigate(AlbumDetailsFragmentDirections.actionToPhotoSlider())
            }
        }
    }
}
