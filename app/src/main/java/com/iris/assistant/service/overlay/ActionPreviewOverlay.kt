package com.iris.assistant.service.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.iris.assistant.domain.tools.ToolResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ActionPreviewOverlay(
    private val context: Context
) {
    suspend fun show(
        actionLabel: String,
        x: Int? = null,
        y: Int? = null,
        previewMs: Long = 3000L
    ): ToolResult = suspendCancellableCoroutine { continuation ->
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics().also { windowManager.defaultDisplay.getMetrics(it) }

        val highlightX = x
        val highlightY = y

        val overlayView = OverlayView(
            context, actionLabel, highlightX, highlightY,
            previewMs, metrics.widthPixels, metrics.heightPixels,
            onComplete = { result ->
                if (continuation.isActive) continuation.resume(result)
            }
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            if (continuation.isActive) continuation.resume(ToolResult.Cancelled)
            return@suspendCancellableCoroutine
        }

        continuation.invokeOnCancellation {
            try { windowManager.removeView(overlayView) } catch (_: Exception) {}
        }
    }

    private class OverlayView(
        context: Context,
        private val actionLabel: String,
        private val highlightX: Int?,
        private val highlightY: Int?,
        private val previewMs: Long,
        private val screenW: Int,
        private val screenH: Int,
        private val onComplete: (ToolResult) -> Unit
    ) : View(context) {

        private val bgPaint = Paint().apply {
            color = 0xE6000000.toInt()
        }

        private val spotlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private val highlightBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.WHITE
        }

        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 48f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        private val countdownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 120f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        private val cancelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 255, 255, 255)
            style = Paint.Style.FILL
        }

        private val cancelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 42f
            textAlign = Paint.Align.CENTER
        }

        private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(80, 255, 255, 255)
        }

        private var countdownSec = (previewMs / 1000f).coerceAtLeast(1f)
        private var phase = Phase.COUNTDOWN
        private var rippleRadius = 0f
        private var rippleAlpha = 80

        private val maxRippleRadius = Math.max(screenW, screenH) * 1.5f

        private val cancelRect = RectF()
        private val cancelWidth = 300f
        private val cancelHeight = 80f
        private var countdownAnimator: ValueAnimator? = null
        private var rippleAnimator: ValueAnimator? = null

        private var cx = 0f
        private var cy = 0f

        init {
            cx = (highlightX?.toFloat() ?: (screenW / 2f))
            cy = (highlightY?.toFloat() ?: (screenH / 2f))

            val spotlightRadius = 160f
            val gradient = RadialGradient(
                cx, cy, spotlightRadius,
                intArrayOf(Color.TRANSPARENT, 0x33000000.toInt(), bgPaint.color),
                floatArrayOf(0.0f, 0.5f, 1.0f),
                Shader.TileMode.CLAMP
            )
            spotlightPaint.shader = gradient

            if (previewMs > 0) {
                startCountdown()
            } else {
                startRipple()
            }
        }

        private fun startCountdown() {
            countdownAnimator = ValueAnimator.ofFloat(previewMs / 1000f, 0f).apply {
                duration = previewMs
                addUpdateListener { anim ->
                    countdownSec = anim.animatedValue as Float
                    invalidate()
                }
                addListener(onEnd = {
                    countdownSec = 0f
                    invalidate()
                    startRipple()
                })
                start()
            }
        }

        private fun startRipple() {
            phase = Phase.RIPPLE
            rippleAnimator = ValueAnimator.ofFloat(50f, maxRippleRadius).apply {
                duration = 500L
                addUpdateListener { anim ->
                    rippleRadius = anim.animatedValue as Float
                    rippleAlpha = (80 * (1f - anim.animatedFraction)).toInt().coerceIn(0, 80)
                    ripplePaint.alpha = rippleAlpha
                    invalidate()
                }
                addListener(onEnd = {
                    onComplete(ToolResult.Success(actionLabel))
                })
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            canvas.apply {
                // Full dark background
                drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), bgPaint)

                if (phase == Phase.COUNTDOWN) {
                    // Spotlight gradient at highlight point
                    if (highlightX != null && highlightY != null) {
                        val radius = 80f + (1f - countdownSec / (previewMs / 1000f)) * 100f
                        val grad = RadialGradient(
                            cx, cy, radius,
                            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, 0x33000000.toInt(), bgPaint.color),
                            floatArrayOf(0.0f, 0.3f, 0.7f, 1.0f),
                            Shader.TileMode.CLAMP
                        )
                        spotlightPaint.shader = grad
                        drawCircle(cx, cy, radius, spotlightPaint)

                        // Highlight border
                        drawCircle(cx, cy, radius * 0.3f, highlightBorderPaint)
                    }

                    // Countdown
                    val displaySec = if (countdownSec >= 1f) countdownSec.toInt().toString() else ""
                    if (displaySec.isNotEmpty()) {
                        drawText(displaySec, screenW / 2f, screenH / 2f, countdownPaint)
                    }

                    // Action label
                    drawText(actionLabel, screenW / 2f, screenH / 2f + 160f, labelPaint)

                    // Cancel button
                    val cancelLeft = screenW / 2f - cancelWidth / 2f
                    val cancelTop = screenH * 0.85f
                    cancelRect.set(cancelLeft, cancelTop, cancelLeft + cancelWidth, cancelTop + cancelHeight)
                    drawRoundRect(cancelRect, 40f, 40f, cancelPaint)
                    drawText("İptal", screenW / 2f, cancelTop + cancelHeight / 2f + 15f, cancelTextPaint)
                }

                // Ripple animation
                if (phase == Phase.RIPPLE) {
                    drawCircle(cx, cy, rippleRadius, ripplePaint)
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN && phase == Phase.COUNTDOWN) {
                val ex = event.x
                val ey = event.y
                if (cancelRect.contains(ex, ey)) {
                    countdownAnimator?.cancel()
                    onComplete(ToolResult.Cancelled)
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            countdownAnimator?.cancel()
            rippleAnimator?.cancel()
        }

        private enum class Phase { COUNTDOWN, RIPPLE }
    }
}

private inline fun ValueAnimator.addListener(
    crossinline onEnd: () -> Unit = {}
): android.animation.AnimatorListenerAdapter {
    val listener = object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) = onEnd()
    }
    addListener(listener)
    return listener
}
