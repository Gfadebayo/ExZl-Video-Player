package com.exzell.exzlvideoplayer.fragments

import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.ViewStubCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.selection.*
import androidx.recyclerview.widget.RecyclerView
import androidx.room.InvalidationTracker
import com.exzell.exzlvideoplayer.R
import com.exzell.exzlvideoplayer.adapter.VideoFileAdapter
import com.exzell.exzlvideoplayer.adapter.tracker.MediaKeyProvider
import com.exzell.exzlvideoplayer.customview.BottomCab
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

abstract class SelectionFragment: Fragment() {

    val mTrackerId = "fragment_id"
    var mActionMode: ActionMode? = null
    val mBottomCab: BottomCab by lazy { requireActivity().findViewById<BottomCab>(R.id.bottom_cab) }
    val mActionCallback: ActionMode.Callback by lazy { actionCallback() }
    private lateinit var mTracker: SelectionTracker<Long>


    val mExFab: ExtendedFloatingActionButton by lazy { requireActivity().findViewById<ExtendedFloatingActionButton>(R.id.fab_current_file) }

    protected fun initTracker(recyclerView: RecyclerView): SelectionTracker<Long> {

        val provider = MediaKeyProvider(recyclerView)

        mTracker = SelectionTracker.Builder<Long>(mTrackerId, recyclerView, provider,
                detailsLookup(recyclerView), StorageStrategy.createLongStorage())
                .withSelectionPredicate(SelectionPredicates.createSelectAnything()).build().apply {

            addObserver(object : SelectionTracker.SelectionObserver<Long>() {
                override fun onSelectionChanged() {

                    if(hasSelection()){
                        val size = selection.size()
                        if(size == 1 && mActionMode == null) {
                            mActionMode = requireActivity().startActionMode(mActionCallback)
                        }

                        mExFab.text = size.toString()
                        mExFab.extend()
                    }else{
                        if(mActionMode != null) closeActionMode()
                    }
                }
            })
        }

        return mTracker
    }

    private fun actionCallback() = object : ActionMode.Callback {
        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean { return false }

        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                mBottomCab.show(mode!!, R.menu.menu_videos){
                    onActionItemClicked(it)
            }
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {return false}

        override fun onDestroyActionMode(mode: ActionMode?) {

            mActionMode = null
            mBottomCab.hide()
            mTracker!!.clearSelection()
            mExFab.shrink()
            mExFab.text = "0"
        }
    }

    abstract fun onActionItemClicked(item: MenuItem) : Boolean

    private fun detailsLookup(rv: RecyclerView): ItemDetailsLookup<Long>{
        return object : ItemDetailsLookup<Long>() {
            override fun getItemDetails(e: MotionEvent): ItemDetails<Long>? {
                val childV = rv.findChildViewUnder(e.x, e.y)
                val vh = childV.let{
                    rv.findContainingViewHolder(it!!) as VideoFileAdapter.ViewHolder
                }

                return vh.mDetails
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mTracker?.onSaveInstanceState(outState)
    }

    override fun onViewStateRestored(saveState: Bundle?) {
        super.onViewStateRestored(saveState)
        mTracker?.onRestoreInstanceState(saveState)
    }

    override fun onPause() {
        super.onPause()
        mActionMode?.finish()
    }

    fun closeActionMode(){
        mActionMode?.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        mBottomCab.destroy()
    }
}