package nl.giejay.android.tv.immich.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiClientConfig
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.places.PlacesFragment
import nl.giejay.android.tv.immich.ui.immichComposeView
import nl.giejay.android.tv.immich.ui.map.MapScreen
import nl.giejay.android.tv.immich.ui.map.MapUiState
import timber.log.Timber

/** The places of the library on a world map; a place opens the photos taken there (like the Places page). */
class MapFragment : Fragment() {
    private var state by mutableStateOf<MapUiState>(MapUiState.Loading)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Timber.i("Loading ${javaClass.simpleName}")
        return immichComposeView(requireContext()) {
            val landDots = remember {
                resources.openRawResource(R.raw.world_land_dots).use { WorldDots.decode(it.readBytes()) }
            }
            MapScreen(state, landDots, onOpenCity = ::openCity)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { ApiClient.getClient(ApiClientConfig.fromPrefs()).getMapMarkers() }
            state = result.fold(
                { MapUiState.Failed(it) },
                { markers ->
                    val clusters = clusterCities(groupCities(markers))
                    if (clusters.isEmpty()) MapUiState.Empty else MapUiState.Ready(clusters)
                },
            )
        }
    }

    private fun openCity(place: PlaceCity) {
        findNavController().navigate(
            HomeFragmentDirections.actionHomeFragmentToFilteredAssetsFragment(PlacesFragment.FILTER_CITY, place.city, place.city),
        )
    }
}
