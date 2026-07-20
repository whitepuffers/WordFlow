package com.wordflow.app.tts

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * 答对/答错的提示音（系统蜂鸣，无需音频资源文件）。
 * 调用前需判断用户是否开启了音效。
 */
object SoundPlayer {

    private var toneGenerator: ToneGenerator? = null

    @Synchronized
    private fun ensure(): ToneGenerator? {
        if (toneGenerator == null) {
            toneGenerator = runCatching {
                ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            }.getOrNull()
        }
        return toneGenerator
    }

    fun correct() {
        runCatching { ensure()?.startTone(ToneGenerator.TONE_PROP_ACK, 180) }
    }

    fun wrong() {
        runCatching { ensure()?.startTone(ToneGenerator.TONE_PROP_NACK, 260) }
    }

    fun celebrate() {
        runCatching { ensure()?.startTone(ToneGenerator.TONE_PROP_ACK, 350) }
    }

    @Synchronized
    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
