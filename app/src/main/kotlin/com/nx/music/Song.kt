package com.nx.music

/**
 * Model lagu lokal. Kesetaraan berbasis path (lokasi file unik per lagu),
 * agar playlist yang di-refresh dari MediaStore tetap bisa mencocokkan
 * lagu yang sedang diputar.
 */
class Song(
    @JvmField var title: String?,
    @JvmField var artist: String?,
    @JvmField var path: String?,
    @JvmField var duration: Long,
) {
    override fun toString(): String = "$title\n$artist"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Song) return false
        return if (path != null) path == other.path else other.path == null
    }

    override fun hashCode(): Int = path?.hashCode() ?: 0
}