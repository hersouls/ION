package app.roadmemory.car.data

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 카드의 한 문장을 소리로도 읽어요(설정의 "음성 안내"가 켜져 있을 때만). Android 표준 TextToSpeech를 쓰고, 엔진이 없거나 한국어를 못 읽으면
 * 조용히 건너뛰어요(카드 글은 늘 화면에 있어요). TTS SDK(Pleos)가 열리면 이 자리를 바꿔 끼워요(docs/PLEOS.md §5).
 */
class CardVoice(context: Context) {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false

    init {
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val r = tts?.setLanguage(Locale.KOREAN)
                    ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                }
            }
        }
    }

    /** 지금 카드를 읽는 중인지. 시그니처 송의 목소리가 카드 읽기와 겹치지 않게 기다려요. */
    val isSpeaking: Boolean get() = ready && runCatching { tts?.isSpeaking == true }.getOrDefault(false)

    fun speak(text: String) {
        if (ready) runCatching { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "card") }
    }

    fun shutdown() { runCatching { tts?.stop(); tts?.shutdown() }; ready = false }
}
