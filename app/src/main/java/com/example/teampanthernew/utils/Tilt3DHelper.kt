package com.example.teampanthernew.utils

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * 3D Parallax, Tilt & Levitation Animation Utilities
 * Inspired by modern Next.js / Three.js / Framer-Motion web physics
 */
object Tilt3DHelper {

    /**
     * Attaches interactive 3D Tilt and Parallax effect to a View.
     * When touched/dragged, the view tilts in 3D perspective towards the touch position.
     * On release, it springs back with an overshoot elastic effect.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun attach3DTilt(
        targetView: View,
        maxTiltDegrees: Float = 14f,
        scaleOnTouch: Float = 1.02f,
        glareView: View? = null
    ) {
        // Calibrate 3D camera distance based on screen density for realistic cinematic perspective
        val density = targetView.resources.displayMetrics.density
        targetView.cameraDistance = 10000 * density

        targetView.setOnTouchListener { view, event ->
            val width = view.width.toFloat()
            val height = view.height.toFloat()

            if (width <= 0 || height <= 0) return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    // Normalized coordinates from -1.0 to 1.0 relative to center
                    val normX = ((event.x / width) - 0.5f) * 2f
                    val normY = ((event.y / height) - 0.5f) * 2f

                    val clampedX = normX.coerceIn(-1.2f, 1.2f)
                    val clampedY = normY.coerceIn(-1.2f, 1.2f)

                    // 3D rotation angles
                    val targetRotY = clampedX * maxTiltDegrees
                    val targetRotX = -clampedY * maxTiltDegrees

                    view.animate()
                        .rotationX(targetRotX)
                        .rotationY(targetRotY)
                        .scaleX(scaleOnTouch)
                        .scaleY(scaleOnTouch)
                        .translationZ(16 * density)
                        .setDuration(120)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()

                    // Optional dynamic light glare movement across the surface
                    glareView?.let { glare ->
                        glare.alpha = (Math.abs(normX) + Math.abs(normY)).coerceIn(0.1f, 0.45f)
                        glare.translationX = -normX * 40f * density
                        glare.translationY = -normY * 40f * density
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Spring back with natural elastic overshoot
                    view.animate()
                        .rotationX(0f)
                        .rotationY(0f)
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .translationZ(0f)
                        .setDuration(450)
                        .setInterpolator(OvershootInterpolator(1.4f))
                        .start()

                    glareView?.animate()
                        ?.alpha(0f)
                        ?.translationX(0f)
                        ?.translationY(0f)
                        ?.setDuration(350)
                        ?.start()
                }
            }
            false
        }
    }

    /**
     * Applies a continuous ambient 3D floating/levitation animation
     * making objects appear suspended in a 3D gravitational field.
     */
    fun start3DFloating(
        view: View,
        floatDistanceDp: Float = 10f,
        tiltDegrees: Float = 5f,
        durationMs: Long = 2800
    ) {
        val density = view.resources.displayMetrics.density
        view.cameraDistance = 8000 * density
        val floatPx = floatDistanceDp * density

        // 1. Vertical Levitation
        val animY = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -floatPx, floatPx).apply {
            duration = durationMs
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        // 2. Subtle 3D Y-Axis Swivel
        val animRotY = ObjectAnimator.ofFloat(view, View.ROTATION_Y, -tiltDegrees, tiltDegrees).apply {
            duration = (durationMs * 1.35).toLong()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        // 3. Subtle 3D X-Axis Nod
        val animRotX = ObjectAnimator.ofFloat(view, View.ROTATION_X, tiltDegrees * 0.6f, -tiltDegrees * 0.6f).apply {
            duration = (durationMs * 1.15).toLong()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        AnimatorSet().apply {
            playTogether(animY, animRotY, animRotX)
            start()
        }
    }

    /**
     * Applies Next.js style 3D tactile button press physics (push depth + spring rebound).
     */
    @SuppressLint("ClickableViewAccessibility")
    fun attach3DPressEffect(
        view: View,
        pressedScale: Float = 0.95f,
        depthDip: Float = 4f
    ) {
        val density = view.resources.displayMetrics.density
        view.cameraDistance = 6000 * density

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(pressedScale)
                        .scaleY(pressedScale)
                        .translationZ(-depthDip * density)
                        .setDuration(90)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .translationZ(0f)
                        .setDuration(280)
                        .setInterpolator(OvershootInterpolator(2.2f))
                        .start()
                }
            }
            false
        }
    }
}


