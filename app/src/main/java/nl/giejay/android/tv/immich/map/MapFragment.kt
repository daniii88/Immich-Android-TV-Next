package nl.giejay.android.tv.immich.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
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
import nl.giejay.android.tv.immich.ui.map.MapVectors
import nl.giejay.android.tv.immich.ui.map.buildMapVectors
import nl.giejay.android.tv.immich.ui.map.MapZoomState
import timber.log.Timber

/** The places of the library on a world map; a place opens the photos taken there (like the Places page). */
class MapFragment : Fragment() {
    private var state by mutableStateOf<MapUiState>(MapUiState.Loading)
    private val zoom = MapZoomState()

    // Back zooms out first; at the world view it is not for the map (the home screen takes it)
    private val zoomBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            zoom.zoomOut()
            isEnabled = zoom.isZoomed
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Timber.i("Loading ${javaClass.simpleName}")
        return immichComposeView(requireContext()) {
            val landDots = remember {
                resources.openRawResource(R.raw.world_land_dots).use { WorldDots.decode(it.readBytes()) }
            }
            LaunchedEffect(zoom.changes) { zoomBack.isEnabled = zoom.isZoomed }
            // the coastlines are built off the main thread (30 thousand points); until then the map shows dots only
            val vectors by produceState<MapVectors?>(null) {
                value = withContext(Dispatchers.Default) {
                    val land = resources.openRawResource(R.raw.world_land_50m).use { WorldVectors.parse(it.readBytes()) }
                    val borders = resources.openRawResource(R.raw.world_borders_50m).use { WorldVectors.parse(it.readBytes()) }
                    buildMapVectors(land, borders)
                }
            }
            MapScreen(state, landDots, vectors, zoom, onOpenCity = ::openCity)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, zoomBack)
        viewLifecycleOwner.lifecycleScope.launch {
            // the server sends every marker (hundreds of thousands in a big library): load and group them off the main
            // thread, and keep only the places
            state = withContext(Dispatchers.IO) {
                ApiClient.getClient(ApiClientConfig.fromPrefs()).getMapMarkers().fold(
                    { MapUiState.Failed(it) },
                    { markers ->
                        val cities = groupCities(markers)
                        if (cities.isEmpty()) MapUiState.Empty else MapUiState.Ready(cities)
                    },
                )
            }
        }
    }

    private fun openCity(place: PlaceCity) {
        findNavController().navigate(
            HomeFragmentDirections.actionHomeFragmentToFilteredAssetsFragment(PlacesFragment.FILTER_CITY, place.city, place.city, place.country),
        )
    }
}
