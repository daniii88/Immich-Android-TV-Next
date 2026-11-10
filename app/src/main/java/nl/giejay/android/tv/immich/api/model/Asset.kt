package nl.giejay.android.tv.immich.api.model

import java.util.Date

data class AssetExifInfo(
    val description: String?,
    val orientation: Int?,
    val exifImageWidth: Int?,
    val exifImageHeight: Int?,
    val city: String?,
    val country: String?,
    val dateTimeOriginal: Date?,
    val make: String?,
    val model: String?,
    // shown in the viewer's info panel; absent in the list payloads, present in GET assets/{id}
    val lensModel: String? = null,
    val fNumber: Double? = null,
    val focalLength: Double? = null,
    val iso: Int? = null,
    val exposureTime: String? = null,
    val fileSizeInByte: Long? = null,
    val state: String? = null
)

data class Tag(
    val color: String?,
    val createdAt: Date,
    val name: String,
    val value: String
)

/** The stack an asset belongs to (shown on its primary asset): [assetCount] photos taken as a series or burst. */
data class AssetStack(
    val id: String,
    val assetCount: Int,
    val primaryAssetId: String? = null
)

/** GET stacks/{id}: the primary asset and all the assets of the stack. */
data class Stack(
    val id: String,
    val primaryAssetId: String? = null,
    val assets: List<Asset> = emptyList()
)

data class Asset(
    val id: String,
    val type: String,
    val deviceAssetId: String?,
    val exifInfo: AssetExifInfo?,
    val fileCreatedAt: Date? = null,
    val fileModifiedAt: Date?,
    val albumName: String?,
    val people: List<Person>?,
    val tags: List<Tag>?,
    val originalPath: String?,
    val originalFileName: String?,
    val isFavorite: Boolean = false,
    /** Only sent for a search with `withStacked`. */
    val stack: AssetStack? = null,
    /** The video of a live photo (the photo itself is the still). */
    val livePhotoVideoId: String? = null
)
