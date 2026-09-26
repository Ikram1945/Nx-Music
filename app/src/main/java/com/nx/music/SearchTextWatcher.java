package com.nx.music;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageView;

/**
 * Pencarian live pada daftar lagu di {@link SongListActivity}. Top-level agar
 * tidak menghasilkan nest mate (kompatibel dengan d8 CodeAssist).
 */
public class SearchTextWatcher implements TextWatcher {

    private final SongAdapter adapter;
    private final ImageView btnClearSearch;

    public SearchTextWatcher(SongAdapter adapter, ImageView btnClearSearch) {
        this.adapter = adapter;
        this.btnClearSearch = btnClearSearch;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        String query = s == null ? "" : s.toString();
        if (adapter != null) {
            adapter.filter(query);
            btnClearSearch.setVisibility(query.isEmpty()
                    ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void afterTextChanged(Editable s) {
    }
}
