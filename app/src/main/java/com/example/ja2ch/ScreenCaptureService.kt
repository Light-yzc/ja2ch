package com.example.ja2ch

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.MotionEvent
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {
    companion object {
        const val ACTION_START_SESSION = "ja2ch.action.START_SESSION"
        const val ACTION_CAPTURE_ONCE = "ja2ch.action.CAPTURE_ONCE"
        const val ACTION_STOP_SESSION = "ja2ch.action.STOP_SESSION"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private const val CHANNEL_ID = "capture"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var isSessionRunning = false
            private set
    }

    private lateinit var projectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            clearSession(stopProjection = false)
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        projectionManager = getSystemService(MediaProjectionManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SESSION -> startSession(intent)
            ACTION_CAPTURE_ONCE -> captureOnce()
            ACTION_STOP_SESSION -> {
                clearSession(stopProjection = true)
                stopSelf()
            }

            MirrorOverlayService.ACTION_CHANGE_SMODE_ON -> {
                startService(Intent(this, MirrorOverlayService::class.java).apply {
                    action = MirrorOverlayService.ACTION_CHANGE_SMODE_ON
                })
                Log.d("debug","已经发送 on（1）")
            }

            MirrorOverlayService.ACTION_CHANGE_SMODE_OFF -> {
                startService(Intent(this, MirrorOverlayService::class.java).apply {
                    action = MirrorOverlayService.ACTION_CHANGE_SMODE_OFF
                })
            }
        }
        return START_NOT_STICKY
    }

    private fun startSession(intent: Intent) {
        if (isSessionRunning) {
            return
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData = readResultData(intent) ?: return

        startMediaProjectionForeground()

        val metrics = resources.displayMetrics
        val newImageReader = ImageReader.newInstance(
            metrics.widthPixels,
            metrics.heightPixels,
            PixelFormat.RGBA_8888,
            2
        )

        val projection = projectionManager.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            newImageReader.close()
            stopSelf()
            return
        }

        projection.registerCallback(projectionCallback, null)

        val display = projection.createVirtualDisplay(
            "ja2ch_capture",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            newImageReader.surface,
            null,
            null
        )

        if (display == null) {
            projection.unregisterCallback(projectionCallback)
            projection.stop()
            newImageReader.close()
            stopSelf()
            return
        }

        mediaProjection = projection
        imageReader = newImageReader
        virtualDisplay = display
        isSessionRunning = true
    }

    private fun captureOnce() {
        if (!isSessionRunning) {
            return
        }
        val image = imageReader?.acquireLatestImage() ?: return
        val bitmap = imageToBitmap(image)
        CaptureFrameStore.set(bitmap)
        startService(Intent(this, MirrorOverlayService::class.java).apply {
            action = MirrorOverlayService.ACTION_SHOW
        })
        image.close()

        // TODO: feed bitmap into OCR/translation flow.
//        bitmap.recycle()
    }

    private fun startMediaProjectionForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("截屏")
            .setContentText("截屏功能已经开启")
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen Capture",
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun clearSession(stopProjection: Boolean) {
        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.close()
        imageReader = null

        mediaProjection?.let { projection ->
            projection.unregisterCallback(projectionCallback)
            if (stopProjection) {
                projection.stop()
            }
        }
        mediaProjection = null
        isSessionRunning = false
    }

    private fun readResultData(intent: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val width = image.width
        val height = image.height
        val rowPadding = plane.rowStride - plane.pixelStride * width
        val bitmap = Bitmap.createBitmap(
            width + rowPadding / plane.pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(plane.buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, width, height)
    }

    override fun onDestroy() {
        clearSession(stopProjection = true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
