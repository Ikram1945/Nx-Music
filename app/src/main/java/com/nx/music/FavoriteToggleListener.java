package com.nx.music;

/**
 * Dipanggil saat status favorit sebuah lagu berubah, agar pemilik list
 * (mis. {@link SongListActivity}) dapat merefresh item / memfilter ulang.
 * Top-level agar tidak menghasilkan nest mate (kompatibel dengan d8 CodeAssist).
 */
public interface FavoriteToggleListener {
    /** @param song lagu yang status favoritnya berubah. */
    void onFavoriteToggled(Song song);
}
