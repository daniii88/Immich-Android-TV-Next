package nl.giejay.android.tv.immich.api.model

/** One photo with a location (GET map/markers); [city], [state] and [country] come from the server's reverse geocoding. */
data class MapMarker(
    val id: String,
    val lat: Double,
    val lon: Double,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null
)
