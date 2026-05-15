package  com.example.ja2ch

import android.graphics.Rect
import okio.Source

data class OcrItem(
    var id: Int,
    var source_t: String,
    var trans_t: String? = null,
    var box: Rect
)