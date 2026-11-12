package app.roadmemory.car.family

import android.content.Context
import app.roadmemory.car.navi.GuideFail
import app.roadmemory.car.navi.GuideResult
import app.roadmemory.car.navi.NaviLink
import app.roadmemory.car.navi.Place
import app.roadmemory.car.studio.Catalog

/**
 * 위치 동기화의 접수 시연 쪽: 마이현대가 가족의 위치를 동기화해 준다고 가정한 시연 값(assets/studio/catalog.json의 family)을 돌려줘요.
 * 네트워크 코드도 주소도 없어요. 위치를 보여 주기로 한 가족([sharing])만 읽고, 꺼 둔 가족은 수만 알려요(이름 · 위치는 읽지 않아요).
 * 마이현대가 실제로 주면 이 자리에 마이현대를 읽는 구현을 끼우고, 화면 · 내비 호출은 그대로예요.
 */
class DemoFamilySource(private val catalog: Catalog) : FamilyLocationSource {
    override suspend fun read(): FamilyRead {
        val on = catalog.family.filter { it.sharing }
        if (on.isEmpty()) return FamilyRead.Nobody
        return FamilyRead.Ready(
            on.map { FamilyPlace(it.alias, it.lat, it.lng, it.place, it.moving, it.ageSec) },
            notSharing = catalog.family.size - on.size,
        )
    }
}

/** 차의 내비에 길을 보내는 쪽(제출 빌드): 마이 트립 · 캘린더와 같은 [NaviLink]예요. 운전자가 "경로 안내"를 누른 뒤에만 불러요. */
class NaviLauncher(private val ctx: Context) : RouteLauncher {
    override fun guide(to: FamilyPlace, done: (RouteResult) -> Unit) {
        NaviLink.guide(ctx, Place(FamilyText.destName(to), to.lat, to.lng)) { r ->
            done(
                when (r) {
                    GuideResult.Sent -> RouteResult.Sent
                    is GuideResult.Failed -> RouteResult.Failed(
                        when (r.why) {
                            GuideFail.NO_PERMISSION -> RouteFail.NO_PERMISSION
                            GuideFail.NO_NAVI -> RouteFail.NO_NAVI
                            GuideFail.TOO_FAR -> RouteFail.TOO_FAR
                            GuideFail.TOO_CLOSE -> RouteFail.TOO_CLOSE
                            GuideFail.LOW_BATTERY -> RouteFail.LOW_BATTERY
                            GuideFail.OTHER -> RouteFail.OTHER
                        },
                    )
                },
            )
        }
    }
}
