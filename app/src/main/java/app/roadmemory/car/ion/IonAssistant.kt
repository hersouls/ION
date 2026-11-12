package app.roadmemory.car.ion

import android.content.Context
import app.roadmemory.car.gleo.GleoBridge
import app.roadmemory.car.voice.Command
import app.roadmemory.car.voice.DemoVoice
import app.roadmemory.car.voice.PleosVoice
import app.roadmemory.car.voice.VoiceCommands
import app.roadmemory.car.voice.VoiceEvent
import app.roadmemory.car.voice.VoiceFailure
import app.roadmemory.car.voice.VoiceInput
import app.roadmemory.car.voice.VoiceKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/** ION의 상태. mark = 왼쪽 메뉴에 그리는 ION 마크(웹 기획서의 마크와 같은 이름). */
enum class IonState(val label: String, val mark: String) {
    IDLE("불러 주세요", "idle"),
    LISTENING("듣는 중", "listening"),
    THINKING("생각 중", "thinking"),
    DONE("확인했어요", "confirming"),
    ASKING("확인해 주세요", "suggesting"),
    NOTICE("안내", "alerting"),
    UNCLEAR("다시 말씀해 주세요", "unclear"),
}

/** 누가 말을 걸었는지. YOU = 사람이 마이크로, GLEO = 차의 음성 AI가 앱의 함수를 불렀어요. */
enum class IonFrom { YOU, GLEO }

data class IonUi(
    val state: IonState = IonState.IDLE,
    val panelOpen: Boolean = false,
    val heard: String = "",
    val reply: String? = null,
    /** 목소리 크기 0..1 */
    val level: Float = 0f,
    val voice: VoiceKind? = null,
    val from: IonFrom = IonFrom.YOU,
    /** 답 뒤에 이어 붙는 말(누르면 그대로 물어요). */
    val followUps: List<String> = emptyList(),
)

/**
 * 왼쪽 ION 메뉴의 두뇌. 마이크로 들은 말(또는 Gleo가 부른 함수)을 한 가지 일로 바꾸고 결과를 말해요.
 * 말 → 글은 차의 음성 인식(Pleos SpeechToText), 글 → 일은 [VoiceCommands]이고, 일은 Gleo가 부르는 함수([GleoBridge.execute])와 같은 길이에요.
 * 로컬 LLM이 아니에요: 시연은 규칙으로 돌아요(하이엔드의 ION은 설계 제안이에요).
 */
class IonAssistant(
    private val scope: CoroutineScope,
    /** 함수를 실행하고 답을 받아요(주행 규제 · 동의 같은 규칙은 그 안에서 지켜요). */
    private val run: (action: String, params: JSONObject) -> GleoBridge.Reply,
    /** "오늘"로 돌아가요. */
    private val goToday: () -> Unit,
    /** 앱 안 ION-Link에 대한 질문("외기온을 쓰는 기능은?")이면 답을, 아니면 null. 명령 규칙보다 먼저 물어봐요(질문 표지가 있을 때만 답해요). */
    private val ask: (String) -> String? = { null },
    /** 말로 시키는 일("다음 정류장 안내해 줘" · "곡 틀어 줘" · "테마 추천해 줘")이면 한 일을 한 문장으로, 아니면 null. 질문보다 먼저 봐요. */
    private val act: (String) -> String? = { null },
    /** 답 뒤에 이어 붙일 말. */
    private val follow: (heard: String, reply: String) -> List<String> = { _, _ -> emptyList() },
    /** 사람이 한 말에 답을 낸 뒤 불러요(알아들었는지와 함께). 들은 말을 기기 안에 남기는 데 써요. */
    private val onReply: (heard: String, reply: String, understood: Boolean) -> Unit = { _, _, _ -> },
) {
    private val state = MutableStateFlow(IonUi())
    val ui: StateFlow<IonUi> = state.asStateFlow()

    private var voice: VoiceInput? = null
    /** 디버그 수신기가 오디오를 직접 넣을 때만 써요. */
    internal val voiceInput: VoiceInput? get() = voice
    private var events: Job? = null
    private var timeout: Job? = null
    private var autoClose: Job? = null

    /** 차(Automotive)이면 Pleos 음성 인식, 아니면 시연용이에요. DriveController가 소스를 정할 때 같이 불러요. */
    fun attach(isCar: Boolean, context: Context) {
        release()
        val v = if (isCar) PleosVoice(context).also { it.prepare() } else DemoVoice()
        voice = v
        state.update { it.copy(voice = v.kind) }
        events = scope.launch { v.events.collect(::onEvent) }
    }

    fun release() {
        events?.cancel(); timeout?.cancel(); autoClose?.cancel()
        voice?.release()
        voice = null
    }

    /** 말하기 버튼: 누르면 듣기, 듣는 중에 누르면 멈춰요. */
    fun onMic() {
        if (state.value.state == IonState.LISTENING) { close(); return }
        autoClose?.cancel()
        val v = voice
        if (v == null) { finish(IonState.UNCLEAR, VoiceFailure.UNAVAILABLE.korean); return }
        state.value = IonUi(state = IonState.LISTENING, panelOpen = true, voice = v.kind)
        v.start()
        timeout?.cancel()
        timeout = scope.launch {
            delay(LISTEN_LIMIT_MS)
            if (state.value.state == IonState.LISTENING) { voice?.stop(); finish(IonState.UNCLEAR, VoiceFailure.TIMEOUT.korean) }
        }
    }

    /** 말하기 예시를 눌렀거나 받아 적기가 끝났어요. 마이크가 없어도 같은 길로 가요. */
    fun submit(text: String) {
        timeout?.cancel(); autoClose?.cancel()
        voice?.stop()
        state.update { it.copy(state = IonState.THINKING, panelOpen = true, heard = text, reply = null, level = 0f, from = IonFrom.YOU) }
        act(text)?.let { finish(IonState.DONE, it, ASK_MS); return }
        ask(text)?.let { finish(IonState.DONE, it, ASK_MS); return }
        when (val cmd = VoiceCommands.route(text)) {
            null -> finish(IonState.UNCLEAR, "‘$text’는 아직 알아듣지 못해요. 아래 말 중에서 골라 볼까요?")
            Command.Close -> close()
            Command.Today -> { goToday(); finish(IonState.DONE, "오늘 화면으로 돌아왔어요") }
            is Command.Gleo -> {
                val params = JSONObject().apply { cmd.place?.let { put("place", it) } }
                val reply = runCatching { run(cmd.action, params) }
                    .getOrElse { GleoBridge.Reply(cmd.action, "fail", "An unexpected internal error occurred.", ko = "잠깐 문제가 생겼어요") }
                finish(stateOf(reply), reply.ko ?: reply.message)
            }
        }
    }

    /** 차의 음성 AI(Gleo)가 앱의 함수를 불렀어요. ION 창에 그 일과 결과를 보여 줘요. */
    fun onGleo(action: String, reply: GleoBridge.Reply) {
        timeout?.cancel(); autoClose?.cancel()
        voice?.stop()
        state.update {
            it.copy(state = stateOf(reply), panelOpen = true, heard = "Gleo가 불렀어요: ${labelOf(action)}",
                reply = reply.ko ?: reply.message, level = 0f, from = IonFrom.GLEO)
        }
        scheduleClose(SHOW_MS)
    }

    fun close() {
        timeout?.cancel(); autoClose?.cancel()
        voice?.stop()
        state.value = IonUi(voice = voice?.kind)
    }

    private fun onEvent(e: VoiceEvent) {
        val now = state.value
        when (e) {
            VoiceEvent.Ready -> Unit
            is VoiceEvent.Level -> if (now.state == IonState.LISTENING) state.update { it.copy(level = e.value) }
            is VoiceEvent.Heard -> if (now.state == IonState.LISTENING) {
                if (e.completed) submit(e.text) else state.update { it.copy(heard = e.text) }
            }
            is VoiceEvent.Failed -> if (now.state == IonState.LISTENING || now.state == IonState.THINKING) {
                timeout?.cancel()
                finish(IonState.UNCLEAR, e.reason.korean)
            }
        }
    }

    private fun finish(s: IonState, reply: String, holdMs: Long? = null) {
        val next = if (s == IonState.DONE) runCatching { follow(state.value.heard, reply) }.getOrDefault(emptyList()) else emptyList()
        if (state.value.from == IonFrom.YOU && state.value.heard.isNotBlank() && state.value.state == IonState.THINKING) runCatching { onReply(state.value.heard, reply, s != IonState.UNCLEAR) }
        state.update { it.copy(state = s, panelOpen = true, reply = reply, level = 0f, followUps = next) }
        scheduleClose(holdMs ?: if (s == IonState.UNCLEAR) HINT_MS else SHOW_MS)
    }

    private fun scheduleClose(ms: Long) {
        autoClose?.cancel()
        autoClose = scope.launch { delay(ms); close() }
    }

    companion object {
        /** 데이터에 대한 설명은 읽을 글이 길어서 더 오래 보여요. */
        const val ASK_MS = 15_000L
        /** 듣기를 시작하고 이만큼 말이 없으면 멈춰요. */
        const val LISTEN_LIMIT_MS = 10_000L
        /** 답을 보여 주는 시간. 말하기 예시를 고르라고 안내할 때는 더 길게요. */
        const val SHOW_MS = 8_000L
        const val HINT_MS = 14_000L

        internal fun stateOf(r: GleoBridge.Reply) = when (r.status) {
            "success" -> IonState.DONE
            "asking" -> IonState.ASKING
            else -> IonState.NOTICE
        }

        internal fun labelOf(action: String) = when (action) {
            GleoBridge.SHOW_ROAD_MEMORY -> "기억 보기"
            GleoBridge.OPEN_CHARGING_LOUNGE -> "충전 쉼터 열기"
            GleoBridge.SUGGEST_CHARGING_STOP -> "충전소 넣기"
            else -> action
        }
    }
}
