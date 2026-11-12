package app.roadmemory.car.voice

import kotlinx.coroutines.flow.SharedFlow

/** 말을 받아 적는 곳. PLEOS_STT = 차의 음성 인식(Pleos SpeechToText SDK), DEMO = 마이크가 없는 기기의 시연(말하기 예시를 눌러요). */
enum class VoiceKind { PLEOS_STT, DEMO }

/** 음성 인식이 실패한 까닭. 사람에게는 [korean]으로 말해요. */
enum class VoiceFailure(val korean: String) {
    MIC("마이크를 쓸 수 없어요. 마이크가 켜져 있는지 확인해 주세요"),
    PERMISSION("마이크 사용을 허락해 주세요"),
    NETWORK("네트워크가 불안정해요. 아래 말하기 예시를 눌러 볼까요?"),
    TIMEOUT("말씀을 듣지 못했어요. 다시 말씀해 주세요"),
    LANGUAGE("이 언어는 아직 알아듣지 못해요"),
    ENGINE("음성 인식이 잠깐 멈췄어요. 다시 눌러 주세요"),
    UNAVAILABLE("음성 인식을 쓸 수 없어요. 아래 말하기 예시를 눌러 주세요"),
    UNKNOWN("잘 못 알아들었어요. 다시 말씀해 주세요"),
}

sealed interface VoiceEvent {
    /** 듣기 시작. 이때부터 말해도 돼요. */
    data object Ready : VoiceEvent

    /** 목소리 크기 0..1(막대 하나로만 보여 줘요). */
    data class Level(val value: Float) : VoiceEvent

    /** 들은 말. completed = false는 아직 말하는 중의 중간 결과, true는 끝난 결과예요. */
    data class Heard(val text: String, val completed: Boolean) : VoiceEvent

    data class Failed(val reason: VoiceFailure) : VoiceEvent
}

interface VoiceInput {
    val kind: VoiceKind
    val events: SharedFlow<VoiceEvent>

    /** 듣기를 시작해요(이미 듣는 중이면 무시). */
    fun start()

    /** 듣기를 멈춰요. */
    fun stop()

    fun release()
}
