package com.wordflow.app.tts

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

private const val TAG = "TtsManager"

/** TTS 状态枚举 */
enum class TtsStatus {
    INITIALIZING,
    READY,
    LANGUAGE_NOT_SUPPORTED,
    LANGUAGE_MISSING_DATA,
    INITIALIZATION_FAILED,
    PLAYBACK_ERROR
}

data class TtsEngineInfo(val name: String, val label: String)

/** 系统 TTS 封装：支持手动指定引擎与诊断 */
class TtsManager(private val context: Context) {

    private var tts: TextToSpeech? = null

    private val _status = MutableStateFlow(TtsStatus.INITIALIZING)
    val status: StateFlow<TtsStatus> = _status.asStateFlow()

    private val _errorCode = MutableStateFlow<Int?>(null)
    val errorCode: StateFlow<Int?> = _errorCode.asStateFlow()

    private val _currentEngine = MutableStateFlow<String?>(null)
    val currentEngine: StateFlow<String?> = _currentEngine.asStateFlow()

    private val _availableEngines = MutableStateFlow<List<TtsEngineInfo>>(emptyList())
    val availableEngines: StateFlow<List<TtsEngineInfo>> = _availableEngines.asStateFlow()

    @Volatile
    private var ready = false

    /** 初始化或重置 TTS 引擎 */
    fun initTts(enginePackage: String? = null) {
        ready = false
        _status.value = TtsStatus.INITIALIZING
        
        tts?.shutdown()
        
        Log.d(TAG, "Initializing TTS with engine: ${enginePackage ?: "default"}...")
        tts = TextToSpeech(context.applicationContext, { status ->
            Log.d(TAG, "OnInitListener status: $status")
            val engine = tts?.defaultEngine
            _currentEngine.value = engine
            
            val engines = tts?.engines ?: emptyList()
            Log.d(TAG, "All detected TTS engines: ${engines.map { it.name }}")

            // 物理探测谷歌 TTS 是否安装（防止系统漏报）
            val isGoogleInstalled = isPackageInstalled("com.google.android.tts")
            Log.d(TAG, "Is Google TTS installed (via PM): $isGoogleInstalled")

            val engineInfos = engines.map { TtsEngineInfo(it.name, it.label) }.toMutableList()
            if (isGoogleInstalled && engineInfos.none { it.name == "com.google.android.tts" }) {
                engineInfos.add(TtsEngineInfo("com.google.android.tts", "Google TTS (强制探测)"))
            }
            _availableEngines.value = engineInfos

            if (status == TextToSpeech.SUCCESS) {
                _errorCode.value = null
                setupLanguageAndListener()
            } else {
                Log.e(TAG, "TTS Initialization failed with status: $status")
                _errorCode.value = status
                _status.value = TtsStatus.INITIALIZATION_FAILED
            }
        }, enginePackage)
    }

    private fun setupLanguageAndListener() {
        val tts = tts ?: return
        
        tts.setPitch(1.0f)
        tts.setSpeechRate(1.0f)

        try {
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set audio attributes: ${e.message}")
        }

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { 
                Log.d(TAG, "Playback started (onStart): $utteranceId") 
            }
            override fun onDone(utteranceId: String?) { 
                Log.d(TAG, "Playback done (onDone): $utteranceId") 
            }
            @Deprecated("Deprecated") override fun onError(utteranceId: String?) { 
                Log.e(TAG, "Playback error (onError): $utteranceId")
                _status.value = TtsStatus.PLAYBACK_ERROR 
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "Playback error: $utteranceId, code: $errorCode")
                _errorCode.value = errorCode
                _status.value = TtsStatus.PLAYBACK_ERROR
            }
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                Log.d(TAG, "Playback progress: char $start to $end")
            }
        })

        var result = tts.setLanguage(Locale.US)
        Log.d(TAG, "setLanguage(Locale.US) result: $result")
        
        if (isLanguageError(result)) {
            result = tts.setLanguage(Locale.ENGLISH)
            Log.d(TAG, "setLanguage(Locale.ENGLISH) result: $result")
        }
        
        if (isLanguageError(result)) {
            result = tts.setLanguage(Locale.getDefault())
            Log.d(TAG, "setLanguage(Locale.getDefault()) result: $result")
        }

        when {
            result == TextToSpeech.LANG_MISSING_DATA -> {
                Log.e(TAG, "Language data is MISSING")
                _status.value = TtsStatus.LANGUAGE_MISSING_DATA
                ready = false
            }
            isLanguageError(result) -> {
                Log.e(TAG, "Language NOT SUPPORTED")
                _status.value = TtsStatus.LANGUAGE_NOT_SUPPORTED
                ready = false
            }
            else -> {
                Log.i(TAG, "TTS is ready")
                _status.value = TtsStatus.READY
                ready = true
                tts.playSilentUtterance(100, TextToSpeech.QUEUE_FLUSH, "warmup")
            }
        }
    }

    private fun isLanguageError(result: Int) = 
        result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED

    fun speak(text: String) {
        if (!ready || text.isBlank()) {
            Log.w(TAG, "Speak requested but not ready or text blank. ready=$ready")
            return
        }

        // 诊断：检查系统音量
        val volume = getMusicVolume()
        Log.d(TAG, "Current STREAM_MUSIC volume: $volume")

        Log.d(TAG, "Speaking: $text")

        // 强制禁用网络合成，防止墙导致静默超时
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            // 强制使用离线数据（如果引擎支持）
            putString(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS, "false")
        }

        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "wf-${text.hashCode()}")
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "speak() returned ERROR")
            _status.value = TtsStatus.PLAYBACK_ERROR
        }
    }

    fun installTts(context: Context) {
        try {
            val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent("com.android.settings.TTS_SETTINGS")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e2: Exception) {}
        }
    }

    /** 播放测试音：用于验证物理通路是否畅通 */
    fun playTestBeep(streamType: Int) {
        try {
            val volume = am?.getStreamVolume(streamType) ?: -1
            Log.d(TAG, "!!! DIAGNOSTIC !!! Testing stream $streamType, current volume: $volume")
            
            // 播放长一点的音，并调高音量（如果是在诊断模式下）
            val toneGen = ToneGenerator(streamType, 100)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 500) // 500ms
            
            Log.d(TAG, "Test beep sent to system. isMusicActive: ${am?.isMusicActive}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play test beep: ${e.message}")
        }
    }

    private val am: AudioManager? by lazy { 
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager 
    }

    fun getMusicVolume(): Int = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            Log.d(TAG, "Package $packageName is PHYSICALLY INSTALLED")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Package $packageName NOT FOUND (or hidden by OS)")
            false
        }
    }

    fun stop() { tts?.stop() }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }
}
