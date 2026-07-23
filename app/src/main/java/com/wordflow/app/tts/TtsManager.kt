package com.wordflow.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** 系统 TTS 封装：增加内置 Sherpa-ONNX 备用引擎 */
class TtsManager(private val context: Context) {

    private var systemTts: TextToSpeech? = null
    private var sherpaEngine: SherpaTtsEngine? = null

    @Volatile
    private var systemReady = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val tag = "TtsManager"

    enum class Status { INITIALIZING, READY, MISSING_ENGINE, MISSING_DATA, ERROR }
    private val _status = MutableStateFlow(Status.INITIALIZING)
    val status: StateFlow<Status> = _status.asStateFlow()

    init {
        initialize()
    }

    private fun initialize() {
        Log.d(tag, "Initializing TTS services...")
        
        // 1. 同时初始化内置引擎作为备份
        sherpaEngine = SherpaTtsEngine(context)
        scope.launch {
            sherpaEngine?.init()
        }

        // 2. 初始化系统引擎
        val dummyTts = TextToSpeech(context.applicationContext) {}
        val engines = dummyTts.engines
        dummyTts.shutdown()
        
        if (engines.isEmpty()) {
            Log.w(tag, "No System TTS engines found. Will use built-in engine.")
            _status.value = Status.READY // 既然有内置引擎，这里可以标记为 READY
            return
        }

        systemTts = TextToSpeech(context.applicationContext) { initStatus ->
            if (initStatus == TextToSpeech.SUCCESS) {
                val locales = listOf(Locale.US, Locale.ENGLISH, Locale.getDefault())
                var supportedLocale: Locale? = null
                var hasMissingData = false

                for (locale in locales) {
                    val result = systemTts?.setLanguage(locale)
                    when (result) {
                        TextToSpeech.LANG_MISSING_DATA -> hasMissingData = true
                        TextToSpeech.LANG_NOT_SUPPORTED -> Unit
                        else -> {
                            supportedLocale = locale
                            break
                        }
                    }
                }

                if (supportedLocale != null) {
                    systemReady = true
                    _status.value = Status.READY
                    Log.i(tag, "System TTS ready with locale: $supportedLocale")
                } else {
                    Log.w(tag, "System TTS missing data or unsupported. Status: ${if (hasMissingData) "MISSING_DATA" else "ERROR"}")
                    _status.value = Status.READY // 仍然标记为 READY，因为内置引擎可用
                }
            } else {
                Log.w(tag, "System TTS Initialization failed. Status: $initStatus")
                _status.value = Status.READY
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        
        // 优先使用系统引擎
        if (systemReady) {
            try {
                val result = systemTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wf-${text.hashCode()}")
                if (result != TextToSpeech.ERROR) return
                Log.e(tag, "System TTS speak failed, falling back to Sherpa.")
            } catch (e: Exception) {
                Log.e(tag, "System TTS exception: ${e.message}, falling back to Sherpa.")
                systemReady = false
            }
        }

        // 使用内置引擎作为兜底
        Log.d(tag, "Speaking via Sherpa-ONNX: $text")
        scope.launch {
            sherpaEngine?.speak(text)
        }
    }

    fun stop() {
        systemTts?.stop()
        sherpaEngine?.release() // Sherpa 目前没有单独的 stop，直接 release 或复用
    }

    fun shutdown() {
        Log.d(tag, "Shutting down TTS services.")
        systemTts?.stop()
        systemTts?.shutdown()
        systemTts = null
        systemReady = false
        
        sherpaEngine?.release()
        sherpaEngine = null
    }
}
