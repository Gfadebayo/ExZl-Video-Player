package com.exzell.exzlvideoplayer

import android.animation.*
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import com.exzell.exzlvideoplayer.adapter.VideoFileAdapter
import java.util.*

class MediaItemAnimator(ctx: Context): DefaultItemAnimator(){

    private val mRemoveAnimation = AnimationUtils.loadAnimation(ctx, R.anim.item_remove_animator)
    private val mAddAnimation = AnimationUtils.loadAnimation(ctx, R.anim.item_add_animation)
    private val mChangeAnimation = AnimationUtils.loadAnimation(ctx, R.anim.item_change_animator)

    private val mAddViewHolders = ArrayList<VideoFileAdapter.ViewHolder>()
    private val mRemoveViewHolders = ArrayList<VideoFileAdapter.ViewHolder>()
    private val mChangeViewHolders = ArrayList<VideoFileAdapter.ViewHolder>()

    init {
        addDuration = mAddAnimation.duration
        removeDuration = mRemoveAnimation.duration
        changeDuration = mChangeAnimation.duration
    }

    override fun runPendingAnimations() {

        mRemoveViewHolders.forEach {
            animateRemoveImpl(it)
        }

        mChangeViewHolders.forEach {
            animateChangeImpl(it)
        }

        mAddViewHolders.forEach {
            animateAddImpl(it)
        }
    }

    override fun animateAdd(holder: RecyclerView.ViewHolder?): Boolean {
        if(BuildConfig.DEBUG) Log.w("Animator", "Add called")

        mAddViewHolders.add(holder as VideoFileAdapter.ViewHolder)
        return true
    }

    override fun animateRemove(holder: RecyclerView.ViewHolder?): Boolean {
        if(BuildConfig.DEBUG) Log.w("Animator", "Remove called")

        mRemoveViewHolders.add(holder as VideoFileAdapter.ViewHolder)
        return true
    }

    private fun animateAddImpl(holder: VideoFileAdapter.ViewHolder){
        val target = holder.itemView

        target.startAnimation(mAddAnimation.apply {
            setAnimationListener(object: Animation.AnimationListener{
                override fun onAnimationRepeat(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) { dispatchAddFinished(holder)
                dispatchFinishedWhenDone()
                    mAddViewHolders.remove(holder)
                }

                override fun onAnimationStart(animation: Animation?) {
                    dispatchAddStarting(holder)

                }
            })
        })
    }

    private fun animateRemoveImpl(holder: VideoFileAdapter.ViewHolder){
        val target = holder.itemView

        target.startAnimation(mRemoveAnimation.apply {
            setAnimationListener(object: Animation.AnimationListener{
                override fun onAnimationRepeat(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {
                    dispatchRemoveFinished(holder)
                    dispatchFinishedWhenDone()
                    mRemoveViewHolders.remove(holder)
                }

                override fun onAnimationStart(animation: Animation?) {
                    dispatchRemoveStarting(holder)
                }
            })
        })
    }

    private fun animateChangeImpl(holder: VideoFileAdapter.ViewHolder){
        val target = holder.mImageView
        val trans = target.translationX

        target.startAnimation(mChangeAnimation.apply {
            setAnimationListener(object: Animation.AnimationListener{
                override fun onAnimationRepeat(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {

                    mChangeViewHolders.remove(holder)
                    dispatchAnimationFinished(holder)
                    dispatchFinishedWhenDone()
                }

                override fun onAnimationStart(animation: Animation?) {
                    dispatchAnimationStarted(holder)
                }
            })
        })
    }

    override fun canReuseUpdatedViewHolder(viewHolder: RecyclerView.ViewHolder, payloads: MutableList<Any>): Boolean {
        return true
    }

    override fun animateChange(oldHolder: RecyclerView.ViewHolder, newHolder: RecyclerView.ViewHolder, preInfo: ItemHolderInfo, postInfo: ItemHolderInfo): Boolean {
        if(BuildConfig.DEBUG) Log.w("Animator", "Change called")

        if(preInfo.changeFlags == RecyclerView.ItemAnimator.FLAG_INVALIDATED) return false

        mChangeViewHolders.add(oldHolder as VideoFileAdapter.ViewHolder)
        return true
    }

//    override fun recordPreLayoutInformation(state: RecyclerView.State, viewHolder: RecyclerView.ViewHolder, changeFlags: Int, payloads: MutableList<Any>): ItemHolderInfo {
//
//    }
    override fun isRunning(): Boolean {
        return mAddViewHolders.size > 0
                || mRemoveViewHolders.size > 0
                || mChangeViewHolders.size > 0
    }

    fun dispatchFinishedWhenDone() {
        if(!isRunning) dispatchAnimationsFinished()
    }
}