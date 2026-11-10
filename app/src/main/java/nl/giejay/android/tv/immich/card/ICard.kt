package nl.giejay.android.tv.immich.card

interface ICard {
    val title: String
    val description: String?
    val id: String
    val thumbnailUrl: String?
    val backgroundUrl: String?
    val selected: Boolean
    /** A folder card shows [coverUrl] (a photo of the folder, if any) with a folder tab and a [badge] (the photo count). */
    val isFolder: Boolean get() = false
    val coverUrl: String? get() = null
    val badge: String? get() = null
    /** More than 1: the card is the primary asset of a stack of this many photos. */
    val stackCount: Int get() = 0
    /** The photo has a motion video (live photo). */
    val isLivePhoto: Boolean get() = false
}