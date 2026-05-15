package com.example.ja2ch

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast

class MirrorOverlayService: Service(){
    companion object {
        const val ACTION_SHOW = "ja2ch.action.SHOW_MIRROR"
        const val ACTION_HIDE = "ja2ch.action.HIDE_MIRROR"
        const val ACTION_APPLY_STYLE = "ja2ch.action.APPLY_STYLE"
        const val ACTION_CHANGE_MODEL = "ja2ch.action.CHANGE_MODEL"
        const val ACTION_CHANGE_SELECT_SIZE = "ja2ch.action.ACTION_CHANGE_SELECT_SIZE"
        const val ACTION_CHANGE_SMODE_ON = "ja2ch.action.ACTION_CHANGE_SMODE_ON"

        const val ACTION_CHANGE_SMODE_OFF = "ja2ch.action.ACTION_CHANGE_SMODE_OFF"



        const val EXTRA_TEXT_SIZE = "extra_text_size"
        const val EXTRA_MODEL_NAME = "extra_model_name"
        const val EXTRA_Regeion = "extra_EXTRA_Regeion"


    }


    private var currentTextSize = 16f
    lateinit var windowManager: WindowManager
    private var rootView: FrameLayout? = null
    private var imageView: TranslateImageView? = null

    lateinit var ocrEngine: ImageTranslateEngine
    private var selectionMode = false
    var Region: Rect? = null


    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        imageView = TranslateImageView(this)
        val savedBackend = getSharedPreferences(ImageTranslateEngine.PREFS_CLOUD_CONFIG, MODE_PRIVATE)
            .getString(
                ImageTranslateEngine.PREF_SELECTED_BACKEND,
                ImageTranslateEngine.BACKEND_GOOGLE
            ) ?: ImageTranslateEngine.BACKEND_GOOGLE
        Log.d("TranslateBackend", "初始化翻译后端$savedBackend")
        ocrEngine = ImageTranslateEngine(savedBackend, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            ACTION_SHOW -> showmirror(Region)
            ACTION_HIDE -> hidemirror()
            ACTION_APPLY_STYLE -> changestyle(intent)
            ACTION_CHANGE_MODEL -> changemodel(intent)
            ACTION_CHANGE_SELECT_SIZE -> changeSelect(intent)
            ACTION_CHANGE_SMODE_ON -> {
                selectionMode = true
                Log.d("debug","已经设定 cls")
            }
            ACTION_CHANGE_SMODE_OFF -> {
                selectionMode = false

            }
        }
        return START_STICKY
    }

    private fun changeSelect(intent: Intent) {
        var regeion = intent.getParcelableExtra<RectF>(EXTRA_Regeion)
        if (regeion!= null){
            Region = Rect(
                regeion.left.toInt(),
                regeion.top.toInt(),
                regeion.right.toInt(),
                regeion.bottom.toInt()
            )
            Toast.makeText(this, "已选择区域${Region?.left}, ${Region?.top}", Toast.LENGTH_SHORT).show()
        }
        selectionMode = false
        hidemirror()
    }
    private fun changemodel(intent: Intent) {
        val modelName = intent.getStringExtra(EXTRA_MODEL_NAME) ?: ImageTranslateEngine.BACKEND_GOOGLE
        Log.d("233", "更改模型$modelName")
        getSharedPreferences(ImageTranslateEngine.PREFS_CLOUD_CONFIG, MODE_PRIVATE)
            .edit()
            .putString(ImageTranslateEngine.PREF_SELECTED_BACKEND, modelName)
            .apply()
        ocrEngine = ImageTranslateEngine(modelName, this)
    }
    private fun showmirror(myRegion: Rect?) {
        val bitmap = CaptureFrameStore.get() ?: return
            val container = FrameLayout(this).apply {
                setBackgroundColor(Color.argb(180, 0, 0, 0))
                setOnClickListener {
                  hidemirror()
                }
            }
            var preview = TranslateImageView(this).apply {
                set_img(bitmap)
                applyStyle(currentTextSize)
            }
            imageView = preview
            if (selectionMode) {
                preview.selectionMode = selectionMode
            }
            else {
                ocrEngine.RunOcrAndTranslate(bitmap, myRegion, ::Onsucc)
            }
            val imgparam = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            container.addView(preview, imgparam)
            val overlayParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            rootView = container
            imageView = preview
            windowManager.addView(container, overlayParams)

    }

    fun changestyle(intent: Intent) {
        currentTextSize = intent.getFloatExtra(EXTRA_TEXT_SIZE, 16f)
        Log.e("fontsize", "$currentTextSize")
        imageView?.applyStyle(currentTextSize)

    }
    private fun hidemirror() {
        rootView?.let{windowManager.removeView(it)}
        imageView = null
        rootView = null
    }

    fun Onsucc(bitmap: Bitmap, items: List<OcrItem>) {
        imageView?.set_img_and_item(bitmap, items)
    }

    override fun onDestroy() {
        hidemirror()
        super.onDestroy()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
}
