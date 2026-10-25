package com.eddyizm.tempus.ui.player

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/** A small spring squeeze on press, replacing ripples on the player's large glyph buttons. */
object PressFeedback {
    private const val PRESSED_SCALE = 0.86f

    @JvmStatic
    @SuppressLint("ClickableViewAccessibility")
    fun attach(view: View) {
        fun spring() = SpringForce(1f).setDampingRatio(0.5f).setStiffness(700f)
        val scaleX = SpringAnimation(view, DynamicAnimation.SCALE_X).setSpring(spring())
        val scaleY = SpringAnimation(view, DynamicAnimation.SCALE_Y).setSpring(spring())
        view.background = null
        view.setOnTouchListener { v, event ->
            val target = when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> PRESSED_SCALE
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> 1f
                else -> null
            }
            if (target != null) {
                if (PlayerMotion.motionEnabled && v.isEnabled) {
                    scaleX.animateToFinalPosition(target)
                    scaleY.animateToFinalPosition(target)
                } else {
                    v.scaleX = 1f
                    v.scaleY = 1f
                }
            }
            false
        }
    }
}
