package app.roadmemory.car.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.util.Log
import java.util.Locale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 차의 음성 인식. Pleos SpeechToText SDK(3.3.1)를 온디바이스 모드로 써요(서버 모드의 클라이언트 ID/시크릿이 필요 없어요).
 * SDK는 말을 글로만 돌려줘요. 그 글로 무엇을 할지는 [VoiceCommands]가 정하고, Gleo가 부를 때와 같은 함수로 이어져요.
 * 순서(docs/PLEOS.md §5): 만들기 → initialize → addListener → setLocale → request → stop → release.
 */
class PleosVoice(private val context: Context) : VoiceInput {
    override val kind = VoiceKind.PLEOS_STT

    private val bus = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<VoiceEvent> = bus.asSharedFlow()

    private var stt: SpeechToText? = null
    @Volatile private var ready = false
    @Volatile private var failedWith: VoiceFailure? = null
    @Volatile private var listening = false
    @Volatile private var wantStart = false
    private val meter = LevelMeter()
    private var rmsCount = 0

    private val listener = object : ResultListener {
        override fun onUpdated(stt: String, completed: Boolean) {
            Log.d(TAG, "heard completed=$completed len=${stt.length}")      // 들은 말은 로그에 남기지 않아요(글자 수만)
            bus.tryEmit(VoiceEvent.Heard(stt, completed))
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onStartedRecognition() { Log.d(TAG, "started") }
        override fun onEndedRecognition() { listening = false; Log.d(TAG, "ended") }
        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "error $errorType")
            listening = false
            bus.tryEmit(VoiceEvent.Failed(failureOf(errorType)))
        }
        override fun onReady() { Log.d(TAG, "ready"); bus.tryEmit(VoiceEvent.Ready) }
        override fun onUpdatedRms(rms: Double) {
            if (++rmsCount % 30 == 1) Log.v(TAG, "rms=$rms")                 // 단위 확인용(문서에 없어요). 30번에 한 번만
            bus.tryEmit(VoiceEvent.Level(meter.level(rms)))
        }
    }

    /** 앱이 열릴 때 한 번. 실패하면 [start]가 UNAVAILABLE을 알려 줘요(앱은 말하기 예시로 이어 가요). */
    fun prepare() {
        runCatching {
            val s = SpeechToText(context, Mode.ON_DEVICE)
            stt = s
            s.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    Log.i(TAG, "initialized (ON_DEVICE)")
                    s.addListener(listener)
                    // 국가까지 있는 로케일(ko-KR)이어야 해요. Locale.KOREAN(언어만)을 주면 온디바이스 엔진이
                    // "unsupported locale: ko-"로 ON_DEVICE_ENGINE_ERROR를 내요(Pleos 에뮬레이터 2.1.0, 2026-11-07).
                    runCatching { s.setLocale(Locale.KOREA) }.onFailure { Log.w(TAG, "setLocale: $it") }
                    ready = true
                    if (wantStart) { wantStart = false; start() }
                }
                override fun onFailure(errorType: ErrorType) {
                    Log.w(TAG, "initialize failed $errorType")
                    failedWith = failureOf(errorType)
                    wantStart = false
                    bus.tryEmit(VoiceEvent.Failed(VoiceFailure.UNAVAILABLE))
                }
            })
        }.onFailure {
            Log.w(TAG, "SDK unavailable: $it")
            failedWith = VoiceFailure.UNAVAILABLE
        }
    }

    override fun start() {
        if (failedWith != null) { bus.tryEmit(VoiceEvent.Failed(VoiceFailure.UNAVAILABLE)); return }
        if (!ready) { wantStart = true; return }                             // 초기화가 끝나면 바로 시작해요
        if (listening) return
        listening = true
        meter.reset()
        runCatching { stt?.request() }.onFailure {
            listening = false
            Log.w(TAG, "request: $it")
            bus.tryEmit(VoiceEvent.Failed(VoiceFailure.ENGINE))
        }
    }

    override fun stop() {
        wantStart = false
        if (!listening) return
        listening = false
        runCatching { stt?.stop() }.onFailure { Log.w(TAG, "stop: $it") }
    }

    /** 마이크 대신 오디오를 직접 넣어요(SDK의 sendAudio). 디버그 빌드의 SpeechFeedReceiver만 써요. 형식은 문서에 없어 16kHz 16bit 모노 PCM으로 시험해요. */
    internal fun feed(pcm: ByteArray) { runCatching { stt?.sendAudio(pcm) }.onFailure { Log.w(TAG, "sendAudio: $it") } }
    internal fun completeFeed() { runCatching { stt?.completeAudioSend() }.onFailure { Log.w(TAG, "completeAudioSend: $it") } }

    override fun release() {
        wantStart = false
        runCatching { stt?.removeListener(listener) }
        runCatching { stt?.release() }.onFailure { Log.w(TAG, "release: $it") }
        stt = null
        ready = false
        listening = false
    }

    companion object {
        private const val TAG = "RoadMemory.Voice"

        /** SDK의 오류 종류를 사람이 이해할 수 있는 여덟 갈래로 줄여요. */
        internal fun failureOf(e: ErrorType): VoiceFailure = when (e) {
            ErrorType.MIC_ERROR, ErrorType.AUDIO_ERROR -> VoiceFailure.MIC
            ErrorType.PERMISSION_DENIED, ErrorType.UNAUTHENTICATED -> VoiceFailure.PERMISSION
            ErrorType.NETWORK_ERROR, ErrorType.SERVER_INTERNAL_ERROR -> VoiceFailure.NETWORK
            ErrorType.TIMEOUT -> VoiceFailure.TIMEOUT
            ErrorType.UNAVAILABLE_LANGUAGE -> VoiceFailure.LANGUAGE
            ErrorType.ON_DEVICE_ENGINE_ERROR, ErrorType.INVALID_REQUEST_ERROR -> VoiceFailure.ENGINE
            ErrorType.NOT_SUPPORTED_API -> VoiceFailure.UNAVAILABLE
            else -> VoiceFailure.UNKNOWN
        }
    }
}

/**
 * 목소리 크기 막대. rms의 단위가 문서에 없어서(0..1인지 dB인지 알 수 없어서) 값 자체가 아니라 "조용할 때보다 얼마나 큰지"만 보여 줘요:
 * 가장 낮은 값을 기준선으로 삼고, 지금까지 가장 컸던 값을 1로 봐요. 조용하면 0이고, 단위가 무엇이든 같은 모양으로 움직여요.
 */
internal class LevelMeter {
    private var floor = Double.NaN
    private var peak = Double.NEGATIVE_INFINITY

    fun reset() { floor = Double.NaN; peak = Double.NEGATIVE_INFINITY }

    fun level(rms: Double): Float {
        if (rms.isNaN()) return 0f
        floor = if (floor.isNaN() || rms < floor) rms else floor + (rms - floor) * 0.002   // 기준선은 천천히만 올라가요
        peak = if (peak == Double.NEGATIVE_INFINITY) rms else maxOf(rms, peak - (peak - floor) * 0.005)
        val span = maxOf(peak - floor, MIN_SPAN)                                            // 잡음만 있을 때 크게 부풀리지 않아요
        return ((rms - floor) / span).coerceIn(0.0, 1.0).toFloat()
    }

    private companion object { const val MIN_SPAN = 0.2 }
}
