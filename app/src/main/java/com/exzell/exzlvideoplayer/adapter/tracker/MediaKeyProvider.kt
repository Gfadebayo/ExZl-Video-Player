package com.exzell.exzlvideoplayer.adapter.tracker


import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.widget.RecyclerView

import com.exzell.exzlvideoplayer.adapter.VideoFileAdapter

import androidx.recyclerview.selection.ItemKeyProvider.*

class MediaKeyProvider(private val mRecyclerView: RecyclerView) : ItemKeyProvider<Long>(SCOPE_MAPPED) {

    override fun getKey(position: Int): Long? {
        val itemId = mRecyclerView.adapter!!.getItemId(position)
        return if (itemId == RecyclerView.NO_ID) RecyclerView.NO_ID else itemId
    }

    override fun getPosition(key: Long): Int {

        val vh = mRecyclerView.findViewHolderForItemId(key) as? VideoFileAdapter.ViewHolder
        return vh?.absoluteAdapterPosition ?: RecyclerView.NO_POSITION
    }
}
