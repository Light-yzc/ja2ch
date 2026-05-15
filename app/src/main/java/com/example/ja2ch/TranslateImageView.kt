package com.example.ja2ch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.View
import kotlin.collections.mutableListOf

class TranslateImageView(context: Context, attrs: AttributeSet? = null): View(context, attrs) {
    lateinit var bitmap: Bitmap
    var items = mutableListOf<OcrItem>()
    var imageRect = RectF()
    val box_paint = Paint().apply {
        color = Color.WHITE
        alpha = 200
    }
    val textPaint = Paint()
    init {
        textPaint.color = Color.BLACK
        textPaint.textSize = 48f
        textPaint.isAntiAlias = true
    }

    fun set_img(bitmap: Bitmap) {
        this.bitmap = bitmap
        invalidate()
    }

    fun applyStyle(textSize: Float) {
        textPaint.textSize = textSize * resources.displayMetrics.scaledDensity
        invalidate()
    }

    fun  set_img_and_item(bitmap: Bitmap, item: List<OcrItem>) {
        this.bitmap = bitmap
        this.items = item.toMutableList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!::bitmap.isInitialized) {
            return
        }
        computeImageRect(bitmap)
        canvas.drawBitmap(bitmap, null, imageRect, null)
        val scale = imageRect.width() / bitmap.width
        for (item in items) {
            val srcbox = item.box
            Log.d("mytag","${item.source_t}")
            val dstbox = RectF(
                imageRect.left + srcbox.left * scale,
                imageRect.top + srcbox.top * scale,
                imageRect.left + srcbox.right * scale,
                imageRect.top + srcbox.bottom * scale,
                )
            canvas.drawRect(dstbox, box_paint)
            val fontMetrics = textPaint.fontMetrics
//            val textY = imageRect.top + fontMetrics.ascent
            if (item.trans_t != null) {
                canvas.drawText(
                    item.trans_t!!,
                    imageRect.left + srcbox.left * scale,
                    imageRect.top + srcbox.top * scale - fontMetrics.ascent,
                    textPaint
                )
            }
        }
    }

    private fun computeImageRect(bmp: Bitmap) {
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        val scale = minOf(
            viewW / bmp.width,
            viewH / bmp.height
        )

        val drawW = bmp.width * scale
        val drawH = bmp.height * scale

        val left = (viewW - drawW) / 2f
        val top = (viewH - drawH) / 2f

        imageRect.set(
            left,
            top,
            left + drawW,
            top + drawH
        )



    }
}