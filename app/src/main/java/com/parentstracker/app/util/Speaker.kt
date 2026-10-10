package com.parentstracker.app.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Reads short phrases aloud with the phone's built-in text-to-speech (no internet or key needed).
 * Falls back to English when the phone has no voice for the chosen language.
 */
class Speaker(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<String, String>? = null

    fun speak(text: String, localeTag: String) {
        val engine = tts
        if (engine == null) {
            pending = text to localeTag
            tts = TextToSpeech(appContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (!ready) Log.w(TAG, "Text-to-speech isn't available on this phone")
                pending?.let { (t, l) -> if (ready) say(t, l) }
                pending = null
            }
            return
        }
        if (ready) say(text, localeTag) else pending = text to localeTag
    }

    private fun say(text: String, localeTag: String) {
        val engine = tts ?: return
        val result = engine.setLanguage(Locale.forLanguageTag(localeTag))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.ENGLISH)
        }
        engine.setSpeechRate(0.9f)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pt-" + text.hashCode())
    }

    fun stop() {
        tts?.stop()
    }

    private companion object {
        const val TAG = "Speaker"
    }
}
