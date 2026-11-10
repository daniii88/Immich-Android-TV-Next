package nl.giejay.android.tv.immich.api.model

import java.util.Date

data class Album(
    val albumName: String,
    val description: String,
    val id: String,
    val albumThumbnailAssetId: String?,
    val updatedAt: Date,
    val endDate: Date?,
    val assetCount: Int,
    val startDate: Date? = null,
    val createdAt: Date? = null,
    /** True when the album is shared with other users (or by them with this user); absent on old servers. */
    val shared: Boolean = false
)