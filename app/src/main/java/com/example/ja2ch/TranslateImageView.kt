package com.example.ja2ch

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
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
    var selectionMode = false
    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var selecting = false
    private val selectionPaint = Paint().apply {
        color = Color.argb(70, 35, 92, 99)
        style = Paint.Style.FILL
    }

    private val selectionStrokePaint = Paint().apply {
        color = Color.rgb(35, 92, 99)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    val textPaint = Paint()
    init {
        textPaint.color = Color.BLACK
        textPaint.textSize = 48f
        textPaint.isAntiAlias = true
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (!::bitmap.isInitialized || !selectionMode){
            return super.onTouchEvent(event)
        }
        when (event!!.action){
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                endX = event.x
                endY = event.y
                selecting = true
                invalidate()

            }

            MotionEvent.ACTION_MOVE -> {
                endX = event.x
                endY = event.y
                selecting = true
                invalidate()
            }

            MotionEvent.ACTION_UP -> {
                endX = event.x
                endY = event.y
                selecting = true
                invalidate()
                val subRect = RectF(
                    minOf(startX, endX),
                    minOf(startY, endY),
                    maxOf(startX, endX),
                    maxOf(startY, endY),
                )
                selecting = false
                selectionMode = false
                invalidate()
                context.startService(Intent(context, MirrorOverlayService::class.java).apply {
                    action = MirrorOverlayService.ACTION_CHANGE_SELECT_SIZE
                    putExtra(MirrorOverlayService.EXTRA_Regeion, subRect)
                })
            }
        }
        return true
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
        if (selecting) {
            val rect = RectF(
                minOf(startX, endX),
                minOf(startY, endY),
                maxOf(startX, endX),
                maxOf(startY, endY)
            )
            canvas.drawRect(rect, selectionPaint)
            canvas.drawRect(rect, selectionStrokePaint)
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