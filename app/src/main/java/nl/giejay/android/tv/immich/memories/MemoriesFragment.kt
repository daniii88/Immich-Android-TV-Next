package nl.giejay.android.tv.immich.memories

import android.text.format.DateUtils
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import arrow.core.Either
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.album.AlbumDetailsFragmentDirections
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Memory
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.shared.fragment.VerticalCardGridFragment
import nl.giejay.android.tv.immich.shared.util.buildSliderConfiguration
import nl.giejay.android.tv.immich.shared.util.toSliderItems
import nl.giejay.android.tv.immich.timeline.MemoriesRowAdapter
import nl.giejay.mediaslider.viewmodel.MediaSliderViewModel

/** All memories of the server (newest first), not only today's "N years ago" row of the timeline; opens one in the viewer. */
class MemoriesFragment : VerticalCardGridFragment<Memory>() {
    private val sliderViewModel: MediaSliderViewModel by activityViewModels()

    override fun sortItems(items: List<Memory>): List<Memory> = items.sortedByDescending { it.memoryAt }

    override suspend fun loadItems(apiClient: ApiClient, page: Int, pageCount: Int): Either<String, List<Memory>> {
        // the server returns all memories at once, there is no paging
        if (page != startPage) return Either.Right(emptyList())
        return apiClient.getAllMemories().map { list -> list.filter { it.assets.isNotEmpty() } }
    }

    override fun setTitle(response: List<Memory>) {
        title = getString(R.string.memories)
    }

    override fun createCard(a: Memory): Card {
        val cover = ApiUtil.getThumbnailUrl(a.assets.firstOrNull()?.id, "thumbnail")
        return Card(
            title = MemoriesRowAdapter.yearsAgoLabel(requireContext(), a),
            description = DateUtils.formatDateTime(requireContext(), a.memoryAt.time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH),
            id = a.id,
            thumbnailUrl = cover,
            backgroundUrl = cover,
        )
    }

    override fun getBackgroundPicture(it: Memory): String? = ApiUtil.getThumbnailUrl(it.assets.firstOrNull()?.id, "thumbnail")

    override fun onItemSelected(card: Card, indexOf: Int) {
        // nothing to do
    }

    override fun onItemClicked(card: Card) {
        val memory = assets.firstOrNull { it.id == card.id } ?: return
        sliderViewModel.configuration = buildSliderConfiguration(
            // memory payloads omit EXIF/dimensions, so pairing portraits is unreliable (same as in the timeline)
            memory.assets.toSliderItems(keepOrder = true, mergePortrait = false)
        )
        findNavController().navigate(AlbumDetailsFragmentDirections.actionToPhotoSlider())
    }
}
