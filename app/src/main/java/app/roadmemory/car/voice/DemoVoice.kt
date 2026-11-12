package app.roadmemory.car.voice

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 마이크가 없는 기기(폰 · 일반 에뮬레이터)의 시연. 듣기를 시작하면 바로 준비 상태가 되고,
 * 사람은 화면의 말하기 예시를 눌러 말을 대신해요(IonAssistant.submit). 차에서는 쓰지 않아요.
 */
class DemoVoice : VoiceInput {
    override val kind = VoiceKind.DEMO

    private val bus = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<VoiceEvent> = bus.asSharedFlow()

    override fun start() { bus.tryEmit(VoiceEvent.Ready) }
    override fun stop() = Unit
    override fun release() = Unit
}
