package com.catgirldownloader.android.ui

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

/**
 * An [AppCompatImageView] that supports pinch-zoom, pan and double-tap
 * zoom using the image matrix. The base matrix fits the image centered,
 * and zoom factors are RELATIVE to that fit (1x = fit-to-screen).
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val baseMatrix = Matrix()
    private val currentMatrix = Matrix()
    private var drawableW = 0
    private var drawableH = 0
    private var baseScale = 1f
    private var isZoomed = false

    private val maxZoomFactor = 5f

    /** Reports the current zoom RELATIVE to the fit scale (1f = fit). */
    var zoomListener: ((Float) -> Unit)? = null

    init {
        scaleType = ScaleType.MATRIX
        isClickable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateBaseMatrix()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        if (drawable != null) {
            val w = drawable.intrinsicWidth
            val h = drawable.intrinsicHeight
            when {
                w > 0 && h > 0 -> {
                    drawableW = w
                    drawableH = h
                }
                drawable.bounds.width() > 0 && drawable.bounds.height() > 0 -> {
                    drawableW = drawable.bounds.width()
                    drawableH = drawable.bounds.height()
                }
                width > 0 && height > 0 -> {
                    // Unknown intrinsic size: fall back to a 1:1 fit.
                    drawableW = width
                    drawableH = height
                }
            }
        }
        updateBaseMatrix()
    }

    private fun updateBaseMatrix() {
        if (drawableW <= 0 || drawableH <= 0 || width == 0 || height == 0) return
        baseMatrix.reset()
        val scale = min(width.toFloat() / drawableW, height.toFloat() / drawableH)
        baseScale = scale
        baseMatrix.postScale(scale, scale)
        baseMatrix.postTranslate(
            (width - drawableW * scale) / 2f,
            (height - drawableH * scale) / 2f,
        )
        resetZoom()
    }

    fun resetZoom() {
        currentMatrix.set(baseMatrix)
        imageMatrix = currentMatrix
        isZoomed = false
        zoomListener?.invoke(1f)
    }

    private fun currentScale(): Float {
        val values = FloatArray(9)
        currentMatrix.getValues(values)
        return values[Matrix.MSCALE_X]
    }

    /** Multiply the current scale by [scaleFactor], keeping the focus point fixed. */
    private fun zoomBy(scaleFactor: Float, focusX: Float, focusY: Float) {
        val cur = currentScale()
        val target = (cur * scaleFactor).coerceIn(baseScale, baseScale * maxZoomFactor)
        currentMatrix.postScale(target / cur, target / cur, focusX, focusY)
        isZoomed = target > baseScale * 1.01f
        if (!isZoomed) {
            currentMatrix.set(baseMatrix)
        }
        clampTranslation()
        imageMatrix = currentMatrix
        zoomListener?.invoke(currentScale() / baseScale)
    }

    /** Zoom to [factor] relative to the fit scale (e.g. 3f = 300%). */
    private fun zoomToRelative(factor: Float, px: Float, py: Float) {
        val cur = currentScale()
        val target = (baseScale * factor).coerceIn(baseScale, baseScale * maxZoomFactor)
        currentMatrix.postScale(target / cur, target / cur, px, py)
        isZoomed = target > baseScale * 1.01f
        clampTranslation()
        imageMatrix = currentMatrix
        zoomListener?.invoke(target / baseScale)
    }

    private fun clampTranslation() {
        if (!isZoomed) return
        val values = FloatArray(9)
        currentMatrix.getValues(values)
        val scale = values[Matrix.MSCALE_X]
        val tx = values[Matrix.MTRANS_X]
        val ty = values[Matrix.MTRANS_Y]
        val imageW = drawableW * scale
        val imageH = drawableH * scale

        // When the scaled image is smaller than the view in a dimension it
        // must stay CENTERED; only when it is larger can it be panned, and the
        // translation is then clamped so the image always covers the view.
        val newTx = if (imageW <= width) {
            (width - imageW) / 2f
        } else {
            tx.coerceIn(width - imageW, 0f)
        }
        val newTy = if (imageH <= height) {
            (height - imageH) / 2f
        } else {
            ty.coerceIn(height - imageH, 0f)
        }
        currentMatrix.postTranslate(newTx - tx, newTy - ty)
    }

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoomBy(detector.scaleFactor, detector.focusX, detector.focusY)
                return true
            }
        },
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isZoomed) {
                    resetZoom()
                } else {
                    zoomToRelative(3f, e.x, e.y)
                }
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                if (!isZoomed) return false
                currentMatrix.postTranslate(-distanceX, -distanceY)
                clampTranslation()
                imageMatrix = currentMatrix
                return true
            }
        },
    )

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }
}
