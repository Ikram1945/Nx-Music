package com.nx.music

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageView
import java.util.function.Consumer

/**
 * Pencarian live pada daftar lagu di [SongListActivity].
 *
 * [onQuery] (SAM Java Consumer<String>) menggantikan tipe Java SongAdapter.
 */
class SearchTextWatcher(
    private val btnClearSearch: ImageView,
    private val onQuery: Consumer<String>,
) : TextWatcher {

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        val query = s?.toString() ?: ""
        onQuery.accept(query)
        btnClearSearch.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun afterTextChanged(s: Editable?) {}
}
