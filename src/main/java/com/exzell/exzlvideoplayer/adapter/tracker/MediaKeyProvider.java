package com.exzell.exzlvideoplayer.adapter.tracker;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemKeyProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.exzell.exzlvideoplayer.adapter.VideoFileAdapter;

public class MediaKeyProvider extends ItemKeyProvider {

    private RecyclerView mRecyclerView;

    public MediaKeyProvider(RecyclerView rv) {
        super(ItemKeyProvider.SCOPE_MAPPED);
        mRecyclerView = rv;
    }

    @Nullable
    @Override
    public Object getKey(int position) {
        long itemId = mRecyclerView.getAdapter().getItemId(position);
        return itemId == -1 ? RecyclerView.NO_ID : itemId;
    }

    @Override
    public int getPosition(@NonNull Object key) {

        VideoFileAdapter.ViewHolder vh = (VideoFileAdapter.ViewHolder) mRecyclerView.findViewHolderForItemId((Long) key);
        return vh == null ? RecyclerView.NO_POSITION : vh.getAdapterPosition();
    }
}
