package com.example.ja2ch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import com.arm.aichat.InferenceEngine
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import android.provider.OpenableColumns
import com.arm.aichat.AiChat
import kotlinx.coroutines.Job

class ImageTranslateEngine(
    private val engineBG: String,
    context: Context,
    modelUri: Uri?
) {
    lateinit var llamaEngine: InferenceEngine
    var llamagenjob : Job? = null

    companion object {
        private const val MAX_CHUNK_CHARS = 220

        const val BACKEND_GOOGLE = "Google"
        const val BACKEND_THIRD_PARTY_API = "ThirdPartyApi"
        const val BACKEND_LLAMA = "llama"
        const val PREFS_CLOUD_CONFIG = "cloud_config"
        const val PREF_API_URL = "api_url"
        const val PREF_API_KEY = "api_key"
        const val PREF_API_MODEL = "api_model"
        const val PREF_SELECTED_BACKEND = "selected_backend"
        const val LLAMA_URL_MODEL = "llama_uri"
        const val DEFAULT_API_URL = "https://api.openai.com/v1/chat/completions"
        const val DEFAULT_API_MODEL = "gpt-4o-mini"
        const val PREF_PROMPT = "api_prompt"
        private const val SYSTEM_PROMPT = "你是游戏/视觉小说界面翻译助手。忠实翻译，不要解释，不要罗马音化人名，不要擅自扩写。URL、英文品牌、数字和已经是中文的文本保持原样。只修正常见且明显的 OCR 错字。"
    }
    @Volatile
    private var currentCall: Call? = null
    private val appContext = context.applicationContext
    private val ocrEngine = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private var translator: Translator? = null

    init {
        if (engineBG == BACKEND_GOOGLE) {
            translator = Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.JAPANESE)
                    .setTargetLanguage(TranslateLanguage.CHINESE)
                    .build()
            )
        }
        else if (engineBG == BACKEND_LLAMA) {
            llamaEngine = AiChat.getInferenceEngine(appContext)
            val engineScope = CoroutineScope(
                SupervisorJob() + Dispatchers.Main.immediate
            )
            engineScope.launch {
                try {
                    loadLlamaModel(modelUri)
                    Log.d("Llama", "模型加载完成")
                    Appevents.llamacallback?.invoke("模型已经加载完毕，开启悬浮窗开始推理")
                } catch (e: Exception) {
                    Log.e("Llama", "模型加载失败", e)
                }
            }
        }
    }


    suspend fun loadLlamaModel(uri: Uri?) {
        val modelFile = copyModelToPrivateDir(uri!!)
        withContext(Dispatchers.IO) {
            Log.d("LlamaModelLoader", "Loading model: ${modelFile.absolutePath}")
            Log.d("LlamaModelLoader", "Model size: ${modelFile.length()}")
            llamaEngine.loadModel(modelFile.absolutePath)
//            RunHYtrans("1222")
        }
    }

    private suspend fun copyModelToPrivateDir(uri: Uri): File {
        return withContext(Dispatchers.IO) {
            val modelDir = File(appContext.filesDir, "models")

            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }

            val dstFile = File(modelDir, "model.gguf")

            val sourceSize = getUriFileSize(uri)

            if (
                dstFile.exists() &&
                sourceSize > 0L &&
                dstFile.length() == sourceSize
            ) {
                Log.d("LlamaModelLoader", "Model already exists, skip copy")
                Log.d("LlamaModelLoader", "Existing model: ${dstFile.absolutePath}")
                Log.d("LlamaModelLoader", "Existing size: ${dstFile.length()}")

                return@withContext dstFile
            }

            appContext.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) {
                    "Cannot open model uri: $uri"
                }

                dstFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 1024 * 1024)
                }
            }

            Log.d("LlamaModelLoader", "Copied model to: ${dstFile.absolutePath}")
            Log.d("LlamaModelLoader", "Copied model size: ${dstFile.length()}")

            dstFile
        }
    }
    private fun getUriFileSize(uri: Uri): Long {
        val projection = arrayOf(OpenableColumns.SIZE)

        appContext.contentResolver.query(
            uri,
            projection,
            null,
            null,
            null
        ).use { cursor ->
            if (cursor == null) {
                return -1L
            }

            if (!cursor.moveToFirst()) {
                return -1L
            }

            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

            if (sizeIndex == -1) {
                return -1L
            }

            return cursor.getLong(sizeIndex)
        }
    }
    suspend fun RunHYtrans(items: MutableList<OcrItem>, bitmap: Bitmap, onSucc: (Bitmap, List<OcrItem>) -> Unit) {
//        val input_list: MutableList<String>;
//        var curlen = 0
//        var curString = buildString {
//
//        } todu:chunk string
        val input = items.mapIndexed { index, item ->
            "${index.toString().padStart(3, '0')}\t${item.source_t}"
        }.joinToString("\n")
        val prompt = buildString {
            appendLine("<｜hy_User｜>")
            appendLine("翻译成简体中文")
            appendLine("输出规则：")
            appendLine("每行开头的三位数字编号必须保留")
            appendLine("编号后面只输出对应中文译文")
            appendLine("不要解释")
            appendLine("不要合并行，不要重新排序。")
            appendLine(input)
            appendLine()
            append("<｜hy_Assistant｜>")
        }
        val result = StringBuilder()
        llamaEngine.sendUserPrompt(prompt).collect { token ->
            Log.d("llama", token)
            result.append(token)
            applyLlamaPartialResult(result.toString(), items)
            withContext(Dispatchers.Main) {
                onSucc(bitmap, items)
            }
        }
    }

    private fun applyLlamaPartialResult(
        output: String,
        items: MutableList<OcrItem>
    ) {
        val lineRegex = Regex("""(?m)^\s*(\d{3})\s+(.+?)\s*$""")

        output.lines().forEach { line ->
            val match = lineRegex.find(line) ?: return@forEach

            val index = match.groupValues[1].toIntOrNull() ?: return@forEach
            val translated = match.groupValues[2].trim()

            if (index in items.indices && translated.isNotEmpty()) {
                items[index].trans_t = translated
            }
        }
    }
    fun RunOcrAndTranslate(bitmap: Bitmap, Region: Rect? = null,onSucc: (Bitmap, List<OcrItem>, ) -> Unit) {
        Log.d("TranslateBackend", "RunOcrAndTranslate backend=$engineBG")
        val TmpBitmap: Bitmap
        if (Region != null) {
            TmpBitmap = Bitmap.createBitmap(bitmap, Region.left, Region.top, Region.width(), Region.height())
            Log.d("debug", "开始进行有限 orc 翻译")
        } else {
            TmpBitmap = bitmap
        }
        val inputImg = InputImage.fromBitmap(TmpBitmap, 0)

        ocrEngine.process(inputImg)
            .addOnSuccessListener { text ->
                val items = extra_ocr_item(text, Region)
                if (items.isEmpty()) {
                    onSucc(bitmap, items)
                    return@addOnSuccessListener
                }

                when (engineBG) {
                    BACKEND_GOOGLE -> translateWithGoogle(items, bitmap, onSucc)
                    BACKEND_THIRD_PARTY_API -> translateWithThirdPartyApi(items, bitmap, onSucc)
                    BACKEND_LLAMA -> {
                        val myScope = CoroutineScope(Dispatchers.Main + Job())
                        llamagenjob = myScope.launch{RunHYtrans(items, bitmap, onSucc)}
                    }
                    else -> onSucc(bitmap, items)
                }
            }
            .addOnFailureListener {
                onSucc(bitmap, emptyList())
            }
    }


    fun cancelCurrentRequest() {
        currentCall?.cancel()
        currentCall = null
        llamagenjob?.cancel()
        llamagenjob = null
    }
    private fun translateWithGoogle(
        items: MutableList<OcrItem>,
        bitmap: Bitmap,
        onSucc: (Bitmap, List<OcrItem>) -> Unit
    ) {
        val currentTranslator = translator ?: run {
            onSucc(bitmap, items)
            return
        }

        var finishedCount = 0
        val totalCount = items.size
        for (item in items) {
            currentTranslator.translate(item.source_t)
                .addOnSuccessListener { result ->
                    finishedCount++
                    item.trans_t = result
                    if (finishedCount == totalCount) onSucc(bitmap, items)
                }
                .addOnFailureListener {
                    finishedCount++
                    item.trans_t = item.source_t
                    if (finishedCount == totalCount) onSucc(bitmap, items)
                }
        }
    }

    private fun translateWithThirdPartyApi(
        items: MutableList<OcrItem>,
        bitmap: Bitmap,
        onSucc: (Bitmap, List<OcrItem>) -> Unit
    ) {
        serviceScope.launch {
            try {
                val translatedTexts = translateBatchWithThirdPartyApi(
                    texts = items.map { it.source_t },
                    onPartial = { partialTexts ->
                        items.forEachIndexed { index, item ->
                            partialTexts[index]?.let { item.trans_t = it }
                        }
                        withContext(Dispatchers.Main) {
                            onSucc(bitmap, items)
                        }
                    }
                )
                items.forEachIndexed { index, item ->
                    item.trans_t = translatedTexts[index]
                }
            } catch (e: Exception) {
                Log.e("ThirdPartyApi", "批量翻译失败", e)
                items.forEach { item -> item.trans_t = item.source_t }
            }

            withContext(Dispatchers.Main) {
                onSucc(bitmap, items)
            }
        }
    }

    private suspend fun translateBatchWithThirdPartyApi(
        texts: List<String>,
        onPartial: suspend (List<String?>) -> Unit
    ): List<String> {
        if (texts.isEmpty()) return emptyList()

        val prefs = appContext.getSharedPreferences(PREFS_CLOUD_CONFIG, Context.MODE_PRIVATE)
        val apiUrl = prefs.getString(PREF_API_URL, DEFAULT_API_URL)?.trim().orEmpty()
        val apiKey = prefs.getString(PREF_API_KEY, "")?.trim().orEmpty()
        val model = prefs.getString(PREF_API_MODEL, DEFAULT_API_MODEL)?.trim().orEmpty()
        val prefuserprompt = prefs.getString(PREF_PROMPT,"").orEmpty()
        if (apiUrl.isEmpty() || apiKey.isEmpty() || model.isEmpty()) {
            Log.w("ThirdPartyApi", "第三方 API 配置不完整")
            return texts
        }

        val joinedText = texts
            .mapIndexed { index, text -> "${batchMarker(index)}\n$text" }
            .joinToString(separator = "\n")
        val userPrompt = buildBatchPrompt(prefuserprompt, joinedText, texts.size)
        val translatedText = requestThirdPartyText(
            apiUrl = apiUrl,
            apiKey = apiKey,
            model = model,
            userPrompt = userPrompt,
            onPartial = { partialText ->
                onPartial(parsePartialBatchTranslation(partialText, texts.size))
            }
        )
        val translatedTexts = splitBatchTranslation(translatedText, texts)

        return translatedTexts
    }

    private fun buildBatchPrompt(prefuserprompt: String, joinedText: String, segmentCount: Int): String {
        val default_prompt = "把下面 ${segmentCount} 个日文 OCR 片段翻译成简体中文。\n"
        return """
             ${
            prefuserprompt.ifEmpty {
                default_prompt
            }
        }
            必须遵守：
            1. 每个片段前面的 <SEG_数字> 标记必须原样保留，例如 <SEG_000>。
            2. 每个标记后面只输出对应片段的中文译文。
            3. 不要添加编号、解释、代码块或额外文字。
            4. 不要合并片段，不要删除空格外的内容结构。
            5. 可以根据上下文修正明显 OCR 错字，但不要把日文人名翻成英文罗马音。
            6. 已经是中文、英文、URL、时间、数字的片段直接原样返回。

            文本：
            $joinedText
        """.trimIndent()
    }

    private suspend fun requestThirdPartyText(
        apiUrl: String,
        apiKey: String,
        model: String,
        userPrompt: String,
        onPartial: suspend (String) -> Unit
    ): String {
        val requestUrl = normalizeTranslationUrl(apiUrl)
        val maxTokens = estimateMaxTokens(userPrompt)
        val isResponsesApi = requestUrl.contains("/responses")
        val bodyJson = if (isResponsesApi) {
            JSONObject()
                .put("model", model)
                .put("max_output_tokens", maxTokens)
                .put("input", "$SYSTEM_PROMPT\n\n$userPrompt")
        } else {
            JSONObject()
                .put("model", model)
                .put("temperature", 0)
                .put("max_tokens", maxTokens)
                .put("stream", true)
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                        .put(JSONObject().put("role", "user").put("content", "/no_think\n$userPrompt"))
                )
        }

        if (shouldDisableThinking(requestUrl, model)) {
            bodyJson.put("enable_thinking", false)
        }

        val request = Request.Builder()
            .url(requestUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
            val call = httpClient.newCall(request)
            currentCall = call

            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val raw = response.body?.string().orEmpty()
                        error("HTTP ${response.code}: $raw")
                    }

                    return if (isResponsesApi) {
                        val raw = response.body?.string().orEmpty()
                        parseResponsesText(raw)
                    } else {
                        readChatCompletionsStream(response.body?.source(), onPartial)
                    }
                }
            } finally {
                if (currentCall == call) currentCall = null
            }

    }

    private suspend fun readChatCompletionsStream(
        source: okio.BufferedSource?,
        onPartial: suspend (String) -> Unit
    ): String {
        if (source == null) return ""

        val fullText = StringBuilder()
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue

            val data = line.removePrefix("data:").trim()
            if (data == "[DONE]") break
            if (data.isBlank()) continue

            val delta = parseChatStreamDelta(data)
            if (delta.isNotEmpty()) {
                fullText.append(delta)
                onPartial(fullText.toString())
            }
        }

        return fullText.toString().trim()
    }

    private fun parseChatStreamDelta(data: String): String {
        val choices = JSONObject(data).optJSONArray("choices") ?: return ""
        if (choices.length() == 0) return ""

        val delta = choices.getJSONObject(0).optJSONObject("delta") ?: return ""
        return delta.optString("content", "")
    }

    private fun normalizeTranslationUrl(apiUrl: String): String {
        val cleanUrl = apiUrl.trim().trimEnd('/')
        return when {
            cleanUrl.endsWith("/chat/completions") -> cleanUrl
            cleanUrl.endsWith("/responses") -> cleanUrl
            cleanUrl.endsWith("/models") -> cleanUrl.removeSuffix("/models") + "/chat/completions"
            cleanUrl.endsWith("/v1") -> "$cleanUrl/chat/completions"
            cleanUrl.contains("/v1/") -> cleanUrl
            else -> "$cleanUrl/v1/chat/completions"
        }
    }

    private fun shouldDisableThinking(apiUrl: String, model: String): Boolean {
        val lowerUrl = apiUrl.lowercase()
        val lowerModel = model.lowercase()
        return lowerUrl.contains("siliconflow") ||
            lowerModel.contains("qwen") ||
            lowerModel.contains("deepseek-v3") ||
            lowerModel.contains("glm-4") ||
            lowerModel.contains("glm-5") ||
            lowerModel.contains("hunyuan")
    }

    private fun parsePartialBatchTranslation(text: String, expectedCount: Int): List<String?> {
        val translationsByIndex = extractBatchTranslations(text)
        return List(expectedCount) { index -> translationsByIndex[index] }
    }

    private fun splitBatchTranslation(text: String, sourceTexts: List<String>): List<String> {
        val translationsByIndex = extractBatchTranslations(text)

        if (translationsByIndex.size != sourceTexts.size) {
            Log.w(
                "ThirdPartyApi",
                "批量翻译标记缺失: expected=${sourceTexts.size}, actual=${translationsByIndex.size}, raw=$text"
            )
        }

        return sourceTexts.mapIndexed { index, source ->
            translationsByIndex[index] ?: source
        }
    }

    private fun extractBatchTranslations(text: String): Map<Int, String> {
        val translationsByIndex = mutableMapOf<Int, String>()
        val markerRegex = Regex("<SEG_(\\d{3})>")
        val matches = markerRegex.findAll(text).toList()

        for (i in matches.indices) {
            val index = matches[i].groupValues[1].toIntOrNull() ?: continue
            val start = matches[i].range.last + 1
            val end = matches.getOrNull(i + 1)?.range?.first ?: text.length
            val translated = text.substring(start, end)
                .trim()
                .trim('`')

            if (translated.isNotBlank()) {
                translationsByIndex[index] = translated
            }
        }

        return translationsByIndex
    }

    private fun batchMarker(index: Int): String {
        return "<SEG_${index.toString().padStart(3, '0')}>"
    }

    private fun estimateMaxTokens(prompt: String): Int {
        return (prompt.length * 2).coerceIn(512, 4096)
    }

    private fun parseResponsesText(raw: String): String {
        val output = JSONObject(raw).optJSONArray("output") ?: return ""
        val result = StringBuilder()

        for (i in 0 until output.length()) {
            val content = output.getJSONObject(i).optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val item = content.getJSONObject(j)
                if (item.optString("type") == "output_text") {
                    result.append(item.optString("text"))
                }
            }
        }

        return result.toString().trim()
    }
}
