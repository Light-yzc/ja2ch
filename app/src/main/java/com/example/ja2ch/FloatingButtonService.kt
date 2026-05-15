package com.example.ja2ch

import android.app.Service
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button

class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingButton: Button? = null
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        show_float_btn()
    }

    fun show_float_btn() {
        var btn = Button(this)
        btn.text = "×"
        btn.textSize = 22f
        btn.setTextColor(Color.WHITE)
//        btn.typeface = Typeface.DEFAULT_BOLD
        btn.isAllCaps = false

        // 去掉 Button 默认的最小尺寸和多余 padding
        btn.minWidth = 0
        btn.minHeight = 0
        btn.minimumWidth = 0
        btn.minimumHeight = 0
        btn.setPadding(0, 0, 0, dp(2))

        // 设置圆形背景
        btn.background = createFloatButtonBackground()

        // 阴影，悬浮感
        btn.elevation = dp(8).toFloat()
        btn.setOnClickListener {
//            var intent = Intent(this, CapturePermissionActivity::class.java)
//            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//            startActivity(intent)
            if (ScreenCaptureService.isSessionRunning) {
                startService(Intent(this, ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_CAPTURE_ONCE
                })
            }
            else {
                startActivity(Intent(this, CapturePermissionActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
        val size = dp(33)

        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = dp(10)
        params.y = dp(10)

        floatingButton = btn
        windowManager.addView(btn, params)
    }

    override fun onDestroy() {
        var tmp_btn = floatingButton
        if (tmp_btn != null) {
            windowManager.removeView(tmp_btn)
        }
        super.onDestroy()

    }
    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density).toInt()
    }
    private fun createFloatButtonBackground(): Drawable {
        val normalBackground = GradientDrawable()

        normalBackground.shape = GradientDrawable.OVAL
        normalBackground.setColor(Color.argb(220, 30, 30, 30))
        normalBackground.setStroke(
            dp(1),
            Color.argb(120, 255, 255, 255)
        )

        val rippleColor = ColorStateList.valueOf(
            Color.argb(80, 255, 255, 255)
        )

        return RippleDrawable(
            rippleColor,
            normalBackground,
            null
        )
    }

    override fun onBind(p0: Intent?): IBinder? {
        return  null
    }
}
