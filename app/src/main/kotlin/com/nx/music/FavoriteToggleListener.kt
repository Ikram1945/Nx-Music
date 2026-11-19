package com.nx.music

/**
 * Dipanggil saat status favorit sebuah lagu berubah, agar pemilik list
 * (mis. [SongListActivity]) dapat merefresh item / memfilter ulang.
 */
fun interface FavoriteToggleListener {
    /** @param song lagu yang status favoritnya berubah. */
    fun onFavoriteToggled(song: Song)
}
