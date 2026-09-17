package io.github.shici.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Only already installed offline voices; never silently opens a voice download flow. */
class Pronunciation(context: Context, private val feedback: (String) -> Unit) : AutoCloseable {
    private var ready = false
    private val speech: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    fun speak(word: String) {
        if (!ready) { feedback("语音引擎暂不可用，请稍后再试。"); return }
        val voice = speech.voices.orEmpty().filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }
            .sortedBy { if (it.locale.country == "GB") 0 else if (it.locale.country == "US") 1 else 2 }.firstOrNull()
        if (voice == null) { feedback("手机尚未安装离线英语语音，可在系统的文字转语音设置中添加。"); return }
        speech.voice = voice
        speech.setSpeechRate(0.85f)
        if (speech.speak(word, TextToSpeech.QUEUE_FLUSH, null, "word") == TextToSpeech.ERROR) feedback("这次发音未能播放。")
    }

    override fun close() { speech.stop(); speech.shutdown() }
}
