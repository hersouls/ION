package app.roadmemory.car.data

import android.content.Context
import app.roadmemory.car.engine.Mode
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * 기록하는 중인 여행(오너 2026-11-11 "기록하기 누르면 … 네비게이션 목적지 참고 · 시동 다시 키면 이어서 진행할까요? · 안내하고 종료하는 여행기록으로 저장").
 *
 *  · 시작: "기록하기"를 누르면 그때의 내비 목적지(이름 · 좌표)와 타고 있는 사람 수로 시작해요. **차의 현재 위치는 읽지 않아요**(FusedLocation은 미확인이라 앱에 넣지 않았어요).
 *  · 쌓기: 달리는 동안 거리 · 탄 사람(가장 많았을 때) · 틀어 준 곡 · 충전 횟수가 저절로 쌓이고, 내비가 목적지를 새로 정하면 따라가요.
 *  · 나눠 달리기: 중간에 멈췄다 다시 출발해도(구간이 바뀌어도) 거리가 합쳐져요. 달린 거리는 [kmDone](끝난 구간) + [kmSeg](지금 구간)이에요.
 *  · 시동을 끄면: 이 기기에 그대로 남아요([RecordingStore]). 다시 켜면 [paused]로 읽혀서 "이어서 기록할까요?"라고 물어요.
 *  · 끝: 내비 안내가 끝나 도착(기어 P, 경로 없음)하면 저절로 여행 기록 한 건으로 저장돼요. 직접 끝낼 수도 있어요. 1km도 안 달렸으면 남기지 않아요.
 */
data class Recording(
    val startedAt: Long,
    val destName: String? = null,
    val destLat: Double? = null,
    val destLon: Double? = null,
    /** 끝난 구간들의 거리(km). */
    val kmDone: Int = 0,
    /** 지금 구간의 거리(km). 구간이 끝나고 다음 구간이 시작될 때 [kmDone]에 더해져요. */
    val kmSeg: Int = 0,
    val people: Int = 0,
    val tracks: List<String> = emptyList(),
    val charges: Int = 0,
    /** 몇 번에 나눠 달렸는지(처음 1, 이어서 할 때마다 +1). */
    val legs: Int = 1,
    /** 앱이 다시 켜져서 이어서 할지 묻는 중이에요. 운전자가 답하기 전에 출발하면 이어서 하는 걸로 봐요. */
    val paused: Boolean = false,
    /** 처음 출발한 때(기어가 P에서 벗어난 때). 기록하기를 누른 때와 달라요. 아직 출발하지 않았으면 null. */
    val departedAt: Long? = null,
) {
    val km: Int get() = kmDone + kmSeg

    /** 내비가 안내 중인 목적지로 바꿔요(목적지가 비어 있으면 그대로 둬요). */
    fun withNav(nav: NavState): Recording =
        if (nav.routeActive && (!nav.destinationName.isNullOrBlank() || nav.destLat != null))
            copy(destName = nav.destinationName?.takeIf { it.isNotBlank() } ?: destName, destLat = nav.destLat ?: destLat, destLon = nav.destLon ?: destLon)
        else this

    fun withTrack(title: String): Recording = if (title in tracks) this else copy(tracks = tracks + title)

    /** 여행 기록 한 건으로. 1km도 안 달렸으면 null이에요. */
    fun toTrip(at: Long, month: String): SavedTrip? =
        if (km < 1) null else SavedTrip(0, at, month, destName, destLat, destLon, km, people, tracks, charges, startedAt = departedAt ?: startedAt)

    fun encode(): String = JSONObject().apply {
        put("startedAt", startedAt); put("destName", destName ?: JSONObject.NULL)
        put("destLat", destLat ?: JSONObject.NULL); put("destLon", destLon ?: JSONObject.NULL)
        put("kmDone", kmDone); put("kmSeg", kmSeg); put("people", people); put("charges", charges); put("legs", legs)
        put("tracks", JSONArray(tracks)); put("departedAt", departedAt ?: JSONObject.NULL)
    }.toString()

    companion object {
        /** "기록하기"를 누른 때의 내비 · 차 상태로 시작해요. [segKm]은 달리는 중에 누른 경우의 이번 주행 거리예요. */
        fun begin(now: Long, nav: NavState, people: Int, segKm: Int = 0): Recording =
            Recording(startedAt = now, people = people, kmSeg = segKm.coerceAtLeast(0)).withNav(nav)

        fun decode(raw: String?): Recording? = runCatching {
            val o = JSONObject(raw ?: return null)
            fun s(k: String) = if (o.isNull(k)) null else o.getString(k)
            fun d(k: String) = if (o.isNull(k)) null else o.getDouble(k)
            Recording(
                startedAt = o.getLong("startedAt"), destName = s("destName"), destLat = d("destLat"), destLon = d("destLon"),
                kmDone = o.optInt("kmDone"), kmSeg = o.optInt("kmSeg"), people = o.optInt("people"), charges = o.optInt("charges"),
                legs = o.optInt("legs", 1).coerceAtLeast(1), departedAt = if (o.isNull("departedAt")) null else o.optLong("departedAt"),
                tracks = o.optJSONArray("tracks")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
            )
        }.getOrNull()

        /** 앱이 다시 켜졌을 때: 지난 구간까지의 거리를 합치고 이어서 할지 묻는 상태로 읽어요. */
        fun afterRestart(r: Recording): Recording = r.copy(kmDone = r.kmDone + r.kmSeg, kmSeg = 0, paused = true)
    }
}

/** 한 번 읽을 때마다 기록을 한 걸음 앞으로 보내는 규칙(차 신호만 써요). 컨트롤러가 불러요. */
object RecordingLogic {
    data class Step(val rec: Recording, val segActive: Boolean)

    /**
     * [prevMode]는 바로 앞에 본 모드(앱이 막 켜졌으면 null). [segActive]는 car.tripKm이 이 기록의 지금 구간을 가리키는지예요:
     * 정차 중에 기록을 시작하면 tripKm에는 지난 주행의 값이 남아 있어서, 출발해서 새로 재기 시작할 때까지 읽지 않아요.
     */
    fun tick(r: Recording, prevMode: Mode?, mode: Mode, car: CarSnapshot, nav: NavState, segActive: Boolean, now: Long = 0L): Step {
        val departure = mode == Mode.DRIVING && prevMode != Mode.DRIVING
        var rec = r
        var seg = segActive
        if (rec.paused) {
            // 묻는 중에 달리기 시작하면 달리는 중에는 누를 수 없으니 이어서 하는 걸로 봐요
            if (!departure) return Step(rec, seg)
            rec = rec.copy(paused = false, legs = rec.legs + 1)
        }
        if (departure) { rec = rec.copy(kmDone = rec.kmDone + rec.kmSeg, kmSeg = 0, departedAt = rec.departedAt ?: now.takeIf { it > 0 }); seg = true }
        if (seg) car.tripKm?.let { rec = rec.copy(kmSeg = it.roundToInt().coerceAtLeast(0)) }
        if (car.occupantCount > rec.people) rec = rec.copy(people = car.occupantCount)
        if (mode == Mode.CHARGING && prevMode != Mode.CHARGING) rec = rec.copy(charges = rec.charges + 1)
        return Step(rec.withNav(nav), seg)
    }
}

/** 기록하는 중인 여행을 이 기기에 남겨요(시동을 꺼도 사라지지 않게). 서버도 계정도 쓰지 않아요. */
class RecordingStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("trip_recording", Context.MODE_PRIVATE)
    fun load(): Recording? = Recording.decode(prefs.getString("current", null))
    fun save(r: Recording?) { prefs.edit().apply { if (r == null) remove("current") else putString("current", r.encode()) }.apply() }
}
