package com.example.ja2ch

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Spinner
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
        lateinit var orc_btn: Button
        lateinit var float_btn: Button
        lateinit var statusT: TextView
        lateinit var Img_view: TranslateImageView
        lateinit var translator: Translator
        lateinit var Seeker: SeekBar
        lateinit var text2: TextView
        lateinit var spinner: Spinner
        lateinit var apiStatusT: TextView
        private var currentBackend = ImageTranslateEngine.BACKEND_GOOGLE
        private var suppressBackendSelect = false
        private val backendItems = listOf(
            "Google ML Kit" to ImageTranslateEngine.BACKEND_GOOGLE,
            "第三方 API" to ImageTranslateEngine.BACKEND_THIRD_PARTY_API
        )
        private val apiHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        var ocr = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        val pick_img = registerForActivityResult(ActivityResultContracts.GetContent() ) {
            uri ->
            if (uri == null) {
                statusT.text = "无选择图片"
                return@registerForActivityResult
            }
            var my_bitmap = load_bitmap_fromuri(uri)
            Img_view.set_img(my_bitmap)
            statusT.text = "选到图片${uri}"
            run_ocr(my_bitmap)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        orc_btn = findViewById(R.id.button)
        statusT = findViewById(R.id.textView)
        Img_view = findViewById(R.id.img_view)
        float_btn = findViewById(R.id.button2)
        Seeker = findViewById(R.id.seekBar)
        text2 = findViewById(R.id.textView2)
        spinner = findViewById(R.id.spinner)
        apiStatusT = findViewById(R.id.textView3)
        currentBackend = loadSelectedBackend()
        updateBackendStatus()

        val itemLabels = backendItems.map { it.first }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            itemLabels
        )
        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )
        spinner.adapter = adapter
        spinner.setSelection(backendItems.indexOfFirst { it.second == currentBackend }.coerceAtLeast(0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                if (suppressBackendSelect) return

                val backend = backendItems[p2].second
                Log.d("modelname", backend)
                if (backend == ImageTranslateEngine.BACKEND_THIRD_PARTY_API) {
                    val previousBackend = currentBackend
                    showApiConfigDialog(
                        onSaved = {
                            currentBackend = backend
                            sendBackend(backend)
                            updateBackendStatus()
                        },
                        onDismissWithoutSave = {
                            selectBackendSilently(previousBackend)
                            updateBackendStatus()
                        }
                    )
                    return
                }

                currentBackend = backend
                sendBackend(backend)
                updateBackendStatus()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // 一般不用处理
            }
        }
//        todo:change model
        orc_btn.setOnClickListener {
            pick_img.launch("image/*")
        }
        float_btn.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                get_float_premit()
                return@setOnClickListener
            }
            var float_intent = Intent(this, FloatingButtonService::class.java)
            startService(float_intent)
        }
        Seeker.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                var textSize = p1.coerceAtLeast(6).toFloat()
                sendStyle(textSize)
                text2.text = "当前字体:${textSize.toInt()}"
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
//                TODO("Not yet implemented")
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
//                TODO("Not yet implemented")
            }
        })
        set_translator()



    }

    private fun sendStyle(
        textSize: Float
    ) {
        startService(Intent(this, MirrorOverlayService::class.java).apply {
            action = MirrorOverlayService.ACTION_APPLY_STYLE
            putExtra(MirrorOverlayService.EXTRA_TEXT_SIZE, textSize)
        })
    }

    private fun sendBackend(backend: String) {
        saveSelectedBackend(backend)
        startService(Intent(this, MirrorOverlayService::class.java).apply {
            action = MirrorOverlayService.ACTION_CHANGE_MODEL
            putExtra(MirrorOverlayService.EXTRA_MODEL_NAME, backend)
        })
    }

    private fun loadSelectedBackend(): String {
        return getSharedPreferences(ImageTranslateEngine.PREFS_CLOUD_CONFIG, MODE_PRIVATE)
            .getString(
                ImageTranslateEngine.PREF_SELECTED_BACKEND,
                ImageTranslateEngine.BACKEND_GOOGLE
            ) ?: ImageTranslateEngine.BACKEND_GOOGLE
    }

    private fun saveSelectedBackend(backend: String) {
        getSharedPreferences(ImageTranslateEngine.PREFS_CLOUD_CONFIG, MODE_PRIVATE)
            .edit()
            .putString(ImageTranslateEngine.PREF_SELECTED_BACKEND, backend)
            .apply()
    }

    private fun updateBackendStatus() {
        apiStatusT.text = "当前后端: ${backendLabel(currentBackend)}"
    }

    private fun backendLabel(backend: String): String {
        return backendItems.firstOrNull { it.second == backend }?.first ?: backend
    }

    private fun selectBackendSilently(backend: String) {
        val index = backendItems.indexOfFirst { it.second == backend }.coerceAtLeast(0)
        suppressBackendSelect = true
        spinner.setSelection(index)
        spinner.post { suppressBackendSelect = false }
    }

    private fun showApiConfigDialog(
        onSaved: () -> Unit,
        onDismissWithoutSave: () -> Unit
    ) {
        val prefs = getSharedPreferences(ImageTranslateEngine.PREFS_CLOUD_CONFIG, MODE_PRIVATE)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_panel_card)
            setPadding(dp(22), dp(22), dp(22), dp(14))
        }

        val chip = TextView(this).apply {
            text = "CLOUD API"
            setTextColor(getColor(R.color.brand_primary))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setBackgroundResource(R.drawable.bg_inline_chip)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val title = TextView(this).apply {
            text = "第三方 API 配置"
            setTextColor(getColor(R.color.ink_strong))
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
        }
        val desc = TextView(this).apply {
            text = "保存后悬浮窗翻译会切换到这个云端接口。"
            setTextColor(getColor(R.color.ink_muted))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(14)
            }
        }

        val urlInput = newDialogInput(
            hint = "API URL",
            text = prefs.getString(
                ImageTranslateEngine.PREF_API_URL,
                ImageTranslateEngine.DEFAULT_API_URL
            ).orEmpty(),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        )
        val keyInput = newDialogInput(
            hint = "API Token",
            text = prefs.getString(ImageTranslateEngine.PREF_API_KEY, "").orEmpty(),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            isPassword = true
        )
        val savedModel = prefs.getString(
            ImageTranslateEngine.PREF_API_MODEL,
            ImageTranslateEngine.DEFAULT_API_MODEL
        ).orEmpty()
        val modelItems = mutableListOf(savedModel.ifEmpty { ImageTranslateEngine.DEFAULT_API_MODEL })
        val modelAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            modelItems
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val modelSpinner = newDialogSpinner(modelAdapter)
        val modelPromt = newDialogInput(
            hint = "模型提示词，默认JA->CH",
            text = prefs.getString(ImageTranslateEngine.PREF_PROMPT, "").orEmpty(),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        )
        val loadModelsButton = newDialogButton(
            text = "加载模型列表",
            backgroundRes = R.drawable.bg_button_secondary,
            textColor = getColor(R.color.brand_primary)
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
            ).apply {
                topMargin = dp(8)
            }
        }

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(18)
            }
        }
        val cancelButton = newDialogButton(
            text = "取消",
            backgroundRes = R.drawable.bg_button_secondary,
            textColor = getColor(R.color.brand_primary)
        )
        val saveButton = newDialogButton(
            text = "保存",
            backgroundRes = R.drawable.bg_button_primary,
            textColor = getColor(R.color.white)
        )
        actionRow.addView(cancelButton)
        actionRow.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        actionRow.addView(saveButton)

        container.addView(chip)
        container.addView(title)
        container.addView(desc)
        container.addView(newDialogLabel("接口地址"))
        container.addView(urlInput)
        container.addView(newDialogLabel("访问令牌"))
        container.addView(keyInput)
        container.addView(newDialogLabel("模型名称"))
        container.addView(modelSpinner)
        container.addView(loadModelsButton)
        container.addView(newDialogLabel("模型Prompt"))
        container.addView(modelPromt)
        container.addView(actionRow)
        container.isFocusableInTouchMode = true

        var saved = false
        val dialog = AlertDialog.Builder(this)
            .setView(container)
            .create()

        dialog.setOnShowListener {
            container.requestFocus()
            cancelButton.setOnClickListener {
                dialog.dismiss()
            }
            loadModelsButton.setOnClickListener {
                loadModelsIntoSpinner(
                    apiUrl = urlInput.text.toString().trim(),
                    apiKey = keyInput.text.toString().trim(),
                    modelItems = modelItems,
                    modelAdapter = modelAdapter,
                    modelSpinner = modelSpinner,
                    loadButton = loadModelsButton,
                    statusText = desc
                )
            }
            saveButton.setOnClickListener {
                saveApiConfig(
                    apiUrl = urlInput.text.toString().trim(),
                    apiKey = keyInput.text.toString().trim(),
                    model = modelSpinner.selectedItem?.toString().orEmpty(),
                    prompt = modelPromt.text.toString().trim()
                )
                saved = true
                dialog.dismiss()
                onSaved()
            }
        }
        dialog.setOnDismissListener {
            if (!saved) onDismissWithoutSave()
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
    }

    private fun newDialogLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(getColor(R.color.ink_strong))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
        }
    }

    private fun newDialogInput(
        hint: String,
        text: String,
        inputType: Int,
        isPassword: Boolean = false
    ): EditText {
        return EditText(this).apply {
            this.hint = hint
            setText(text)
            setTextColor(getColor(R.color.ink_strong))
            setHintTextColor(getColor(R.color.ink_muted))
            textSize = 15f
            minHeight = dp(56)
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_preview_frame)
            setPadding(dp(16), 0, dp(16), 0)
            setSingleLine()
            this.inputType = inputType
            if (isPassword) {
                transformationMethod = PasswordTransformationMethod.getInstance()
                setSelection(text.length)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            ).apply {
                topMargin = dp(8)
            }
        }
    }

    private fun newDialogSpinner(adapter: ArrayAdapter<String>): Spinner {
        return Spinner(this).apply {
            this.adapter = adapter
            setBackgroundResource(R.drawable.bg_preview_frame)
            minimumHeight = dp(56)
            setPadding(dp(12), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            ).apply {
                topMargin = dp(8)
            }
        }
    }

    private fun newDialogButton(
        text: String,
        backgroundRes: Int,
        textColor: Int
    ): Button {
        return Button(this).apply {
            this.text = text
            textSize = 15f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            setBackgroundResource(backgroundRes)
            minHeight = dp(48)
            minWidth = 0
            setPadding(dp(12), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(
                0,
                dp(48),
                1f
            )
        }
    }

    private fun loadModelsIntoSpinner(
        apiUrl: String,
        apiKey: String,
        modelItems: MutableList<String>,
        modelAdapter: ArrayAdapter<String>,
        modelSpinner: Spinner,
        loadButton: Button,
        statusText: TextView
    ) {
        if (apiUrl.isEmpty() || apiKey.isEmpty()) {
            Toast.makeText(this, "先填写 API URL 和 Token", Toast.LENGTH_SHORT).show()
            return
        }

        val modelsUrl = inferModelsUrl(apiUrl)
        loadButton.isEnabled = false
        loadButton.text = "加载中..."
        statusText.text = "正在请求模型列表: $modelsUrl"

        Thread {
            val result = runCatching {
                requestModelIds(modelsUrl, apiKey)
            }

            runOnUiThread {
                loadButton.isEnabled = true
                loadButton.text = "加载模型列表"

                result.onSuccess { ids ->
                    if (ids.isEmpty()) {
                        statusText.text = "没有从接口返回模型列表。"
                        Toast.makeText(this, "模型列表为空", Toast.LENGTH_SHORT).show()
                        return@onSuccess
                    }

                    val previousModel = modelSpinner.selectedItem?.toString().orEmpty()
                    modelItems.clear()
                    modelItems.addAll(ids)
                    modelAdapter.notifyDataSetChanged()
                    val index = modelItems.indexOf(previousModel).takeIf { it >= 0 } ?: 0
                    modelSpinner.setSelection(index)
                    statusText.text = "已加载 ${ids.size} 个模型。"
                }.onFailure { e ->
                    Log.e("ThirdPartyApi", "加载模型列表失败", e)
                    statusText.text = "加载模型失败: ${e.message}"
                    Toast.makeText(this, "加载模型失败", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun requestModelIds(modelsUrl: String, apiKey: String): List<String> {
        val request = Request.Builder()
            .url(modelsUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        apiHttpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("HTTP ${response.code}: $raw")
            }

            val data = JSONObject(raw).optJSONArray("data") ?: return emptyList()
            val ids = mutableListOf<String>()
            for (i in 0 until data.length()) {
                val id = data.getJSONObject(i).optString("id")
                if (id.isNotBlank()) ids.add(id)
            }
            return ids.sorted()
        }
    }

    private fun inferModelsUrl(apiUrl: String): String {
        val cleanUrl = apiUrl.trim().trimEnd('/')
        val v1Index = cleanUrl.indexOf("/v1/")
        if (v1Index >= 0) {
            return cleanUrl.substring(0, v1Index + 4) + "/models"
        }

        return cleanUrl
            .removeSuffix("/chat/completions")
            .removeSuffix("/responses")
            .removeSuffix("/completions") + "/models"
    }

    private fun saveApiConfig(apiUrl: String, apiKey: String, model: String, prompt: String) {
        getSharedPreferences(ImageTranslateEngine.PREFS_CLOUD_CONFIG, MODE_PRIVATE)
            .edit()
            .putString(ImageTranslateEngine.PREF_API_URL, apiUrl)
            .putString(ImageTranslateEngine.PREF_API_KEY, apiKey)
            .putString(ImageTranslateEngine.PREF_API_MODEL, model)
            .putString(ImageTranslateEngine.PREF_PROMPT, prompt)
            .apply()

        Toast.makeText(this, "第三方 API 配置已保存", Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    fun get_float_premit() {
        val uri: Uri = Uri.parse("package:$packageName")
        var open_setting = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            uri
        )
        startActivity(open_setting)
        Toast.makeText(this, "请打开悬浮窗权限", Toast.LENGTH_SHORT).show()
    }
    fun load_bitmap_fromuri(uri: Uri): Bitmap{
        var source = ImageDecoder.createSource(contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) {
            dedocer, _, _ ->
            dedocer.isMutableRequired = true
        }
    }


    fun run_ocr(bitmap: Bitmap) {
        val Input_img = InputImage.fromBitmap(bitmap, 0)
        ocr.process(Input_img)
            .addOnSuccessListener { text->
                val items = extra_ocr_item(text)
                statusT.text = "识别到 ${items.size} 行文字"
                Img_view.set_img_and_item(bitmap, items)
                translateItems(items, bitmap)
            }
            .addOnFailureListener { e->
                statusT.text = "OCR失败 ${e}"
            }
    }



    fun set_translator() {
        var builder =  TranslatorOptions.Builder()
        builder.setTargetLanguage(TranslateLanguage.CHINESE)
        builder.setSourceLanguage(TranslateLanguage.JAPANESE)
        val options = builder.build()
        translator = Translation.getClient(options)
        val down_builder = DownloadConditions.Builder()
//        down_builder.requireWifi()
        val contition = down_builder.build()
        statusT.text = "正在下载翻译模型，请等待"
        translator.downloadModelIfNeeded(contition)
            .addOnSuccessListener { statusT.text = "下载完成，选择图片" }
            .addOnFailureListener {  e -> statusT.text = "下载失败, ${e.message}" }
    }
    private fun translateItems(items: MutableList<OcrItem>, bitmap: Bitmap) {
        if (items.isEmpty()) {
            statusT.text = "未发现需要翻译文本"
            return
        }
        var cur_index = 0

        for (item in items) {
            translator.translate(item.source_t)
                .addOnSuccessListener { result -> item.trans_t = result
                    cur_index++
                    statusT.text = "翻译中 ${cur_index} / ${items.size}"
                    Log.d("my_count", "${cur_index},,${items.size}")
                    if (cur_index == items.size) {
                        show_r(items, bitmap)
                    }
                }
                .addOnFailureListener { r -> item.trans_t = item.source_t
                    cur_index++
                    statusT.text = "翻译中 ${cur_index} / ${items.size}"
                    show_r(items, bitmap)
                }

        }
    }

    private fun show_r(items: MutableList<OcrItem>, bitmap: Bitmap) {
        Img_view.set_img_and_item(bitmap, items)
    }

}

fun extra_ocr_item(text: Text, Region: Rect? = null): MutableList<OcrItem> {
    var items = mutableListOf<OcrItem>()
    var id = 0

    for (text_chunk in text.textBlocks) {
        for (line in text_chunk.lines) {
            var src_text = line.text.trim()
            var box = line.boundingBox

            if (src_text.isEmpty() || box == null) {
                continue
            }
            if (Region!= null) {
                box.offset(Region.left, Region.top)
            }
            items.add(OcrItem(
                id = id++,
                source_t = src_text,
                box= box
            ))

        }
    }
    return  items
}
