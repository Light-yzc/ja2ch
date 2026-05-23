package com.example.ja2ch

import android.graphics.Bitmap

object CaptureFrameStore {
    private var latestbitmap: Bitmap? = null

    fun set(bitmap: Bitmap) {
        latestbitmap = bitmap
    }

    fun get(): Bitmap? {
        return latestbitmap
    }
}

object Appevents {
    var llamacallback: ((message: String) -> Unit)? = null
}