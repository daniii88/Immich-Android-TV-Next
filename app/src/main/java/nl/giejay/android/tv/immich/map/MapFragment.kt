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
import nl.giejay.android.tv.immich.ui.map.MapData
import nl.giejay.android.tv.immich.ui.map.MapLevelState
import nl.giejay.android.tv.immich.ui.map.MapScreen
import nl.giejay.android.tv.immich.ui.map.MapUiState
import nl.giejay.android.tv.immich.ui.map.MapVectors
import nl.giejay.android.tv.immich.ui.map.buildMapVectors
import timber.log.Timber

/** Where the photos of the library were taken, on a world map; countries lead to regions and places and their photos. */
class MapFragment : Fragment() {
    private var state by mutableStateOf<MapUiState>(MapUiState.Loading)
    private val levels = MapLevelState()

    // Back goes up a level first; at the world it is not for the map (the home screen takes it)
    private val levelBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            levels.back()
            isEnabled = levels.isNested
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Timber.i("Loading ${javaClass.simpleName}")
        return immichComposeView(requireContext()) {
            val landDots = remember {
                resources.openRawResource(R.raw.world_land_dots).use { WorldDots.decode(it.readBytes()) }
            }
            LaunchedEffect(levels.changes) { levelBack.isEnabled = levels.isNested }
            // the coastlines are built off the main thread (30 thousand points); until then the map shows dots only
            val vectors by produceState<MapVectors?>(null) {
                value = withContext(Dispatchers.Default) {
                    val land = resources.openRawResource(R.raw.world_land_50m).use { WorldVectors.parse(it.readBytes()) }
                    val borders = resources.openRawResource(R.raw.world_borders_50m).use { WorldVectors.parse(it.readBytes()) }
                    buildMapVectors(land, borders)
                }
            }
            MapScreen(state, landDots, vectors, levels, onOpenCity = ::openCity, onOpenRegion = ::openRegion, onOpenCountry = ::openCountry)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, levelBack)
        viewLifecycleOwner.lifecycleScope.launch {
            // the server sends every marker (hundreds of thousands in a big library): load and group them off the main
            // thread; the places for the cards, and one point per photo for the lit dots
            state = withContext(Dispatchers.IO) {
                ApiClient.getClient(ApiClientConfig.fromPrefs()).getMapMarkers().fold(
                    { MapUiState.Failed(it) },
                    { markers ->
                        val cities = groupCities(markers)
                        if (markers.isEmpty()) MapUiState.Empty
                        else MapUiState.Ready(MapData(cities, countriesOf(markers, cities), MapFootprint.of(markers)))
                    },
                )
            }
        }
    }

    private fun openCountry(country: PlaceCountry) {
        findNavController().navigate(
            HomeFragmentDirections.actionHomeFragmentToFilteredAssetsFragment(PlacesFragment.FILTER_COUNTRY, country.name, country.name, null),
        )
    }

    private fun openRegion(region: MapRegion) {
        findNavController().navigate(
            HomeFragmentDirections.actionHomeFragmentToFilteredAssetsFragment(PlacesFragment.FILTER_STATE, region.state, region.state, region.country),
        )
    }

    private fun openCity(place: PlaceCity) {
        findNavController().navigate(
            HomeFragmentDirections.actionHomeFragmentToFilteredAssetsFragment(
                PlacesFragment.FILTER_CITY, place.city, place.city, place.country, place.state, filterExactPlace = true,
            ),
        )
    }
}
