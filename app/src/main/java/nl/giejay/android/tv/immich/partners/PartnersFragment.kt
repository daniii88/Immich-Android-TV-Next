package nl.giejay.android.tv.immich.partners

import androidx.navigation.fragment.findNavController
import arrow.core.Either
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Partner
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.fragment.VerticalCardGridFragment

/** The users who share their library with this user; opening one shows that library. */
class PartnersFragment : VerticalCardGridFragment<Partner>() {
    override fun sortItems(items: List<Partner>): List<Partner> = items.sortedBy { it.name.lowercase() }

    override suspend fun loadItems(apiClient: ApiClient, page: Int, pageCount: Int): Either<String, List<Partner>> {
        // no pagination
        if (page != startPage) return Either.Right(emptyList())
        return apiClient.listPartners()
    }

    override fun setTitle(response: List<Partner>) {
        title = getString(R.string.partners)
    }

    override fun createCard(a: Partner): Card {
        val image = ApiUtil.getUserProfileImage(a.id)
        return Card(a.name, a.email, a.id, image, image)
    }

    override fun getBackgroundPicture(it: Partner): String? = null

    override fun onItemSelected(card: Card, indexOf: Int) {
        // nothing to do
    }

    override fun onItemClicked(card: Card) {
        findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToPartnerAssetsFragment(card.id, card.title))
    }
}
