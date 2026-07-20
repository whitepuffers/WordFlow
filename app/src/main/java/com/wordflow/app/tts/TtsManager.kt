package com.wordflow.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** 系统 TTS 封装：懒初始化，未就绪时静默忽略播放请求 */
class TtsManager(context: Context) {

    private var tts: TextToSpeech? = null

    @Volatile
    private var ready = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                ready = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wf-${text.hashCode()}")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
