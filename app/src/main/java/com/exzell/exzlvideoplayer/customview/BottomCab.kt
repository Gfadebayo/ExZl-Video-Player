package com.exzell.exzlvideoplayer.customview

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.MenuRes
import android.view.ActionMode
import androidx.appcompat.widget.ActionMenuView
import com.exzell.exzlvideoplayer.R
import com.google.android.material.card.MaterialCardView

class BottomCab @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    val mActionView: ActionMenuView

    init {
        inflate(context, R.layout.bottom_cab, this)

        mActionView = findViewById(R.id.bottom_cab_action)
    }

    fun show(actionMode: ActionMode, @MenuRes menu: Int, listener: (MenuItem) -> Boolean){
        if(mActionView.menu.size() == 0){
            actionMode.menuInflater.inflate(menu, mActionView.menu)
            mActionView.setOnMenuItemClickListener { listener.invoke(it) }
        }

        with(ObjectAnimator.ofFloat(this, "alpha", 1f)) {
            setDuration(1000)
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator?) {}

                override fun onAnimationEnd(animation: Animator?) {
//                    alpha = 1f
                }

                override fun onAnimationCancel(animation: Animator?) {}

                override fun onAnimationStart(animation: Animator?) {
                    visibility = View.VISIBLE
                }

            })

            start()
        }

    }

    fun destroy(){
        mActionView.menu.clear()
        mActionView.setOnMenuItemClickListener(null)
    }

    fun hide() {
        with(ObjectAnimator.ofFloat(this, "alpha", 0f)) {
            setDuration(1000)
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator?) {}

                override fun onAnimationEnd(animation: Animator?) {
//                    alpha = 0f
                    visibility = View.GONE
                }

                override fun onAnimationCancel(animation: Animator?) {}

                override fun onAnimationStart(animation: Animator?) {}
            })

            start()
        }
    }
}