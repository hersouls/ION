package app.roadmemory.car.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import org.json.JSONObject

/**
 * 디버그 전용: 가족 위치를 읽어 올 곳과 별칭 표를 이 기기의 앱 저장소에 넣어요(app/scripts/family_setup.sh가 보내요).
 *   설정:  adb shell am broadcast -a app.roadmemory.car.DEBUG_FAMILY -p app.roadmemory.car --es b64 <base64 of {"base_url":…,"alias":[["실명","별칭"],…],"self":"운전자의 실명"}>
 *   지우기: adb shell am broadcast -a app.roadmemory.car.DEBUG_FAMILY -p app.roadmemory.car --ez clear true
 * 한글이 adb 명령줄에서 깨지지 않게 base64로 받아요. 로그에는 이름 · 주소 · 좌표를 남기지 않고 개수만 남겨요.
 */
class FamilyDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (intent.getBooleanExtra("clear", false)) {
            FamilyConfig.clear(app)
            FamilyInit.connect(app)
            Log.d(TAG, "family link cleared")
            return
        }
        val raw = intent.getStringExtra("b64") ?: run { Log.d(TAG, "family link: b64 missing"); return }
        try {
            val o = JSONObject(String(Base64.decode(raw, Base64.DEFAULT), Charsets.UTF_8))
            val url = o.getString("base_url")
            require(url.startsWith("https://")) { "https only" }
            val alias = FamilyConfig.parseAlias(o.getJSONArray("alias").toString())
            FamilyConfig(url, alias, o.optString("self").takeIf { it.isNotBlank() }?.let { alias[it] }).save(app)
            FamilyInit.connect(app)
            Log.d(TAG, "family link set: ${alias.size} people")
        } catch (e: Exception) {
            Log.w(TAG, "family link failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private companion object { const val TAG = "RoadMemory.Debug" }
}
