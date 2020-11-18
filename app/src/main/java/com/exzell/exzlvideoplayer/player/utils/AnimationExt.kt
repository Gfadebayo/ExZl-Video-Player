package com.exzell.exzlvideoplayer.player.utils

import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.ObjectAnimator
import android.util.Log
import android.view.View
import android.widget.SeekBar
import androidx.annotation.AnimatorRes
import com.exzell.exzlvideoplayer.BuildConfig


fun SeekBar.animateSeekbar(endFunc: () -> Unit): ObjectAnimator =
        ObjectAnimator.ofInt(this, "progress", progress)
                .apply {
                    setDuration(1000)

                    addListener(object : Animator.AnimatorListener {
                        override fun onAnimationRepeat(animation: Animator?) {}

                        override fun onAnimationEnd(animation: Animator?) {
                            if (BuildConfig.DEBUG) Log.i("Seekbar Progress", "Animation Ended, Updating again")

                            endFunc.invoke()
                        }

                        override fun onAnimationCancel(animation: Animator?) {}

                        override fun onAnimationStart(animation: Animator?) {}

                    })
                }

fun View.animate(@AnimatorRes id: Int){
    val animation = AnimatorInflater.loadAnimator(context, id)

    animation.setTarget(this)

    animation.start()
}