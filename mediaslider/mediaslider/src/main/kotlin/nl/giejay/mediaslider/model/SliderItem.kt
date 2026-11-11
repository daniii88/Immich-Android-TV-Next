package nl.giejay.mediaslider.model

import java.util.Objects

interface MetaDataProvider {
    suspend fun getValue(): String?
}

class StaticMetaDataProvider(private val value: String?) : MetaDataProvider {
    override suspend fun getValue(): String? = value
}

class SliderItem(
    var id: String,
    val url: String?,
    val type: SliderItemType,
    val orientation: Int,
    private val metaData: Map<MetaDataType, MetaDataProvider>,
    val thumbnailUrl: String?,
    val isPanorama: Boolean,
    var isFavorite: Boolean = false,
    /** A live photo: the url of its motion video (the item itself is the still image). */
    val livePhotoVideoUrl: String? = null
) {
    suspend fun get(metaDataType: MetaDataType): String? {
        return this.metaData[metaDataType]?.getValue()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as SliderItem
        return url == that.url
    }

    override fun hashCode(): Int {
        return Objects.hash(url)
    }
}
