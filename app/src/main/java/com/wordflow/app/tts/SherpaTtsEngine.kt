package com.wordflow.app.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 基于 Sherpa-ONNX 的离线 TTS 引擎。
 */
class SherpaTtsEngine(private val context: Context) {

    private val tag = "SherpaTtsEngine"
    private var tts: OfflineTts? = null
    private var initialized = false

    suspend fun init() = withContext(Dispatchers.IO) {
        if (initialized) return@withContext
        try {
            val baseDir = File(context.filesDir, "sherpa-onnx")
            if (!baseDir.exists()) baseDir.mkdirs()

            // 拷贝必要文件
            val modelFile = copyAssetToFile("sherpa-onnx/model.onnx", File(baseDir, "model.onnx"))
            val tokensFile = copyAssetToFile("sherpa-onnx/tokens.txt", File(baseDir, "tokens.txt"))
            val dataDir = copyAssetFolder("sherpa-onnx/espeak-ng-data", File(baseDir, "espeak-ng-data"))

            val vitsConfig = OfflineTtsVitsModelConfig(
                model = modelFile.absolutePath,
                lexicon = "",
                tokens = tokensFile.absolutePath,
                dataDir = dataDir.absolutePath,
                noiseScale = 0.667f,
                noiseScaleW = 0.8f,
                lengthScale = 1.0f,
            )

            val modelConfig = OfflineTtsModelConfig(
                vits = vitsConfig,
                numThreads = 1,
                debug = false
            )

            val config = OfflineTtsConfig(
                model = modelConfig,
                ruleFsts = "",
                maxNumSentences = 1
            )

            // 当使用绝对路径加载 SD 卡或内部存储中的模型时，必须将 AssetManager 设为 null
            tts = OfflineTts(null, config)
            initialized = true
            Log.i(tag, "Sherpa-ONNX initialized successfully.")
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize Sherpa-ONNX: ${e.message}", e)
        }
    }

    private fun copyAssetToFile(assetPath: String, outFile: File): File {
        if (outFile.exists()) return outFile
        context.assets.open(assetPath).use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        return outFile
    }

    private fun copyAssetFolder(assetFolder: String, outDir: File): File {
        if (outDir.exists() && (outDir.list()?.isNotEmpty() == true)) return outDir
        if (!outDir.exists()) outDir.mkdirs()
        
        val assets = context.assets.list(assetFolder) ?: return outDir
        for (asset in assets) {
            val childAssetPath = "$assetFolder/$asset"
            val childOutFile = File(outDir, asset)
            if (context.assets.list(childAssetPath)?.isNotEmpty() == true) {
                copyAssetFolder(childAssetPath, childOutFile)
            } else {
                copyAssetToFile(childAssetPath, childOutFile)
            }
        }
        return outDir
    }

    fun speak(text: String) {
        val ttsInstance = tts ?: return
        try {
            val audio = ttsInstance.generate(text, 0, 1.0f)
            playAudio(audio.samples, audio.sampleRate)
        } catch (e: Exception) {
            Log.e(tag, "Error generating or playing audio: ${e.message}")
        }
    }

    private fun playAudio(samples: FloatArray, sampleRate: Int) {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(bufferSize, samples.size * 4))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        audioTrack.play()

        // 简单的自动释放逻辑：根据采样率计算播放时间并延时释放
        val durationMs = (samples.size.toFloat() / sampleRate * 1000).toLong()
        Thread {
            try {
                Thread.sleep(durationMs + 500)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                Log.e(tag, "Error releasing AudioTrack: ${e.message}")
            }
        }.start()
    }

    fun release() {
        tts?.release()
        tts = null
        initialized = false
    }
}
