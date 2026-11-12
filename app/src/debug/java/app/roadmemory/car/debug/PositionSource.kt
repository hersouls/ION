package app.roadmemory.car.debug

import android.content.Context
import app.roadmemory.car.family.FamilyLocationSource
import app.roadmemory.car.family.FamilyRead
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 디버그 전용, 우회 경로: 마이현대가 가족 위치를 아직 주지 않아서, 제안자가 따로 쓰는 위치 서비스에서 읽어요.
 * 마이현대가 주면 이 클래스만 [FamilyLocationSource]의 다른 구현으로 바꾸면 돼요(화면 · 내비 호출은 그대로).
 *
 * 읽기(GET) 한 번이 전부예요. 올리는 것은 없고, 읽은 값을 저장하지 않아요. 주소와 허용 목록은 [FamilyConfig]에서 와요(소스에는 없어요).
 */
class PositionSource(private val ctx: Context) : FamilyLocationSource {
    override suspend fun read(): FamilyRead = withContext(Dispatchers.IO) {
        val cfg = FamilyConfig.load(ctx) ?: return@withContext FamilyRead.Failed("연결 설정이 없어요")
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(cfg.baseUrl.trimEnd('/') + "/api/live").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 6_000
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code !in 200..299) return@withContext FamilyRead.Failed("서비스가 ${code}로 답했어요")
            PositionParse.people(conn.inputStream.bufferedReader().use { it.readText() }, cfg.alias, cfg.selfAlias)
        } catch (e: Exception) {
            FamilyRead.Failed("닿지 못했어요(${e.javaClass.simpleName})")
        } finally {
            conn?.disconnect()
        }
    }
}
