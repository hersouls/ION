package app.roadmemory.car.gleo

import android.content.Context
import android.content.Intent
import android.util.Log
import app.roadmemory.car.DriveController
import app.roadmemory.car.Pane
import app.roadmemory.car.engine.CardKind
import app.roadmemory.car.engine.Mode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gleo Interface(Pleos 음성 AI의 함수 호출). 규약은 docs/PLEOS.md §6.
 * - Gleo가 Intent를 보내요: action = 함수 이름, `ACTION_PARAM` extra = 매개변수 JSON 문자열(키별 extra로 오는 경우도 받아요).
 * - 앱은 7초 안에 브로드캐스트 `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE`로 답해요.
 *   message는 영어 완전한 한 문장, status는 success | asking | fail.
 */
object GleoBridge {
    const val ACTION_RESPONSE = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
    const val SHOW_ROAD_MEMORY = "SHOW_ROAD_MEMORY"
    const val OPEN_CHARGING_LOUNGE = "OPEN_CHARGING_LOUNGE"
    const val SUGGEST_CHARGING_STOP = "SUGGEST_CHARGING_STOP"
    val FUNCTIONS = setOf(SHOW_ROAD_MEMORY, OPEN_CHARGING_LOUNGE, SUGGEST_CHARGING_STOP)

    private const val TAG = "RoadMemory.Gleo"

    @Volatile var controller: DriveController? = null

    data class Reply(val functionName: String, val status: String, val message: String, val data: JSONArray? = null, val mode: String? = null,
                     /** 같은 답을 화면에 보여 줄 한국어 한 문장이에요. Gleo에는 보내지 않아요(규약은 영어 message). */
                     val ko: String? = null) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("functionName", functionName)
            put("status", status)
            put("message", message)
            data?.let { put("data", it.toString()) }   // 규약: data는 JSON 배열을 담은 문자열
            mode?.let { put("mode", it) }
        }
    }

    /** Gleo의 Intent를 처리하고 응답을 Gleo에 방송해요. 처리하지 않는 action이면 false. */
    fun handle(context: Context, intent: Intent?): Boolean {
        val action = intent?.action ?: return false
        if (action !in FUNCTIONS) return false
        val params = paramsOf(intent)
        val reply = runCatching { execute(action, params) }
            .getOrElse { Reply(action, "fail", "An unexpected internal error occurred.", ko = "잠깐 문제가 생겼어요") }
        runCatching { controller?.ion?.onGleo(action, reply) }       // ION 창에도 보여 줘요(Gleo가 불렀다는 것)
        respond(context, reply)
        return true
    }

    internal fun execute(action: String, params: JSONObject, ui: app.roadmemory.car.UiState? = controller?.ui?.value, c: DriveController? = controller): Reply {
        if (c == null || ui == null) return Reply(action, "fail", "The app is not ready yet.", ko = "앱이 아직 준비되지 않았어요")
        return when (action) {
            SHOW_ROAD_MEMORY -> when (ui.mode) {
                Mode.DRIVING -> {
                    val card = ui.card
                    if (card != null && card.kind == CardKind.ROAD_MEMORY) {
                        c.onPrimary(card)
                        Reply(action, "success", "Started the songs you heard on this road last time.",
                            JSONArray().put(JSONObject().put("card", card.title)), ko = "그때 이 길에서 들은 곡을 틀었어요")
                    } else {
                        Reply(action, "success", "There is no saved trip on this road yet.", ko = "이 길을 다녀온 기록은 아직 없어요")
                    }
                }
                else -> {
                    c.openPane(Pane.TRIPS)
                    Reply(action, "success", "Opened the saved trips.",
                        JSONArray().put(JSONObject().put("place", params.optString("place", ""))), ko = "여행 기록을 열었어요")
                }
            }
            OPEN_CHARGING_LOUNGE -> when (ui.mode) {
                Mode.CHARGING -> Reply(action, "success", "The charging screen is already open.", ko = "충전 화면이 이미 열려 있어요")
                Mode.PARKED -> { c.openPane(Pane.CHARGE); Reply(action, "success", "Opened the charging screen while the car is parked.", ko = "충전 화면을 열었어요") }
                Mode.DRIVING -> Reply(action, "fail", "The charging screen is only available while parked or charging.", ko = "충전 화면은 서 있거나 충전할 때만 열려요")
            }
            SUGGEST_CHARGING_STOP -> {
                val est = ui.nav.estimatedSocAtDest
                when {
                    !ui.nav.routeActive -> Reply(action, "fail", "There is no active route.", ko = "안내 중인 경로가 없어요")
                    est == null -> Reply(action, "success", "The navigation has not told the battery at the destination yet.", ko = "도착 예상 배터리를 아직 몰라요")
                    else -> {
                        // ION은 경로를 바꾸지 않아요. 내비가 충전 계획을 세우고, ION은 내비가 알려 준 값만 전해요.
                        val stop = ui.nav.chargeStops.firstOrNull()
                        val ko = "도착하면 배터리가 ${est}%예요." + (stop?.let { " 내비가 ${app.roadmemory.car.brain.Korean.eul(it.name)} 경로에 넣었어요." } ?: if (est <= app.roadmemory.car.engine.CardEngine.LOW_SOC_AT_DEST) " 충전이 필요할 수 있어요. 내비에서 충전소를 확인해 주세요." else " 충전소는 필요 없어요.")
                        Reply(action, "success", "The battery at the destination is expected to be $est percent.",
                            JSONArray().put(JSONObject().put("estimatedBatteryAtDestination", est)), ko = ko)
                    }
                }
            }
            else -> Reply(action, "fail", "This function is not supported.", ko = "이 기능은 아직 쓸 수 없어요")
        }
    }

    fun respond(context: Context, reply: Reply) {
        val json = reply.toJson().toString()
        Log.d(TAG, "callback $json")
        runCatching {
            context.sendBroadcast(Intent(ACTION_RESPONSE).apply {
                putExtra("packageName", context.packageName)
                putExtra("response", json)
            })
        }.onFailure { Log.e(TAG, "callback failed: $it") }
    }

    /** `ACTION_PARAM`(JSON 문자열)과 키별 extra를 모두 받아요. 문서 예제는 바깥 따옴표가 한 겹 더 붙어 있어요. */
    internal fun paramsOf(intent: Intent): JSONObject {
        val out = JSONObject()
        val extras = intent.extras
        extras?.keySet()?.forEach { k ->
            if (k == "ACTION_PARAM") {
                parseLoose(extras.getString(k))?.let { j -> j.keys().forEach { key -> out.put(key, j.get(key)) } }
            } else {
                runCatching { out.put(k, extras.get(k)) }
            }
        }
        return out
    }

    internal fun parseLoose(raw: String?): JSONObject? {
        if (raw.isNullOrBlank()) return null
        runCatching { return JSONObject(raw) }
        val unquoted = raw.trim().removeSurrounding("\"").replace("\\\"", "\"")
        return runCatching { JSONObject(unquoted) }.getOrNull()
    }
}
