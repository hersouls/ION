package app.roadmemory.car.engine

import app.roadmemory.car.data.EnvSignals
import app.roadmemory.car.data.EnvState
import app.roadmemory.car.data.Weather
import app.roadmemory.car.data.WiperLevel

/**
 * 차의 신호에서 비 · 안개를 추정해요. 날씨 API가 없는 차에서도 쓰려는 것이고, 모르면 모른다고 말해요(weather = null).
 *
 * 맑음 · 눈은 추정하지 않아요: 와이퍼가 안 움직인다고 맑은 건 아니고, 눈은 외기온 없이 가려낼 수 없어요.
 *
 *  비    ① 와이퍼가 60초 안에 3번 쓸면 비예요(전조등이나 김서림 제거가 함께 켜져 있으면 2번).
 *        ② 와이퍼를 수동으로 켜 두었거나(간헐 · 연속) 상태가 ON으로 이어진 채 20초가 지나도 비예요. 쓸 때마다 알림이 오지 않는 차(연속 모드는 ON이 계속돼요,
 *        Pleos 에뮬레이터는 스위치만 바꿔요)를 위한 길이에요. 자동(AUTO) 모드의 대기는 비가 아니에요.
 *        마지막으로 확인한 뒤 180초 동안 유지해요. 워셔액 한 번 쓸기(MIST)나 스위치가 꺼진 움직임은 세지 않아요 → 한 번 닦았다고 비로 보지 않아요.
 *  안개  안개등(앞 · 뒤)이 20초 넘게 켜져 있으면 안개예요. 와이퍼가 비를 말하면 비가 먼저예요.
 *
 * 순수 함수라 JVM에서 시험해요(EnvEngineTest). 시계는 now(밀리초)로 받아요.
 */
object EnvEngine {
    const val WINDOW_MS = 60_000L
    const val HOLD_MS = 180_000L
    const val FOG_MS = 20_000L
    const val SUSTAIN_MS = 20_000L

    data class State(
        val sweeps: List<Long> = emptyList(), // 와이퍼가 한 번 쓴 시각들(WINDOW_MS 안의 것만)
        val lastWiperOn: Boolean = false,
        val activeSince: Long? = null,
        val rainUntil: Long = 0,
        val fogSince: Long? = null,
    )

    fun step(prev: State, s: EnvSignals, now: Long): Pair<State, EnvState> {
        val wiperOn = s.wiperOn == true
        val counts = s.wiperLevel != WiperLevel.MIST && s.wiperLevel != WiperLevel.OFF
        val rising = wiperOn && !prev.lastWiperOn && counts
        val sweeps = (prev.sweeps + if (rising) listOf(now) else emptyList()).filter { now - it <= WINDOW_MS }
        val helped = s.lowBeam == true || s.defrost == true || s.maxDefrost == true
        val need = if (helped) 2 else 3
        val manual = s.wiperLevel == WiperLevel.INTERMITTENT || s.wiperLevel == WiperLevel.CONTINUOUS
        val activeSince = if ((wiperOn || manual) && counts) prev.activeSince ?: now else null
        val sustained = activeSince != null && now - activeSince >= SUSTAIN_MS
        val rainUntil = if (sweeps.size >= need || sustained) now + HOLD_MS else prev.rainUntil
        val raining = now < rainUntil

        val fogLamp = s.frontFog == true || s.rearFog == true
        val fogSince = if (fogLamp) prev.fogSince ?: now else null
        val foggy = fogSince != null && now - fogSince >= FOG_MS

        val reasons = buildList {
            if (raining) {
                add(
                    when {
                        sweeps.size >= need -> "와이퍼가 ${sweeps.size}번 쓸었어요"
                        sustained -> "와이퍼가 계속 켜져 있어요"
                        else -> "조금 전까지 와이퍼가 움직였어요" // 마지막 확인 뒤 유지 시간(180초) 동안
                    },
                )
                if (s.lowBeam == true) add("전조등이 켜져 있어요")
                if (s.defrost == true || s.maxDefrost == true) add("앞유리 김서림을 지우고 있어요")
            } else if (foggy) {
                add("안개등이 켜져 있어요")
            }
        }
        val weather = when {
            raining -> Weather.RAIN
            foggy -> Weather.FOG
            else -> null
        }
        return State(sweeps, wiperOn, activeSince, rainUntil, fogSince) to EnvState(weather, reasons)
    }
}
