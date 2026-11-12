package app.roadmemory.car.ui

import app.roadmemory.car.data.ToneSettings
import app.roadmemory.car.data.Weather
import app.roadmemory.car.studio.Theme

/**
 * 비 오면 비 테마로: 서 있을 때 비를 짐작하면(와이퍼 · 전조등 · 김서림 신호, [app.roadmemory.car.engine.EnvEngine]) 이 기기에 내려받아 둔 테마 가운데
 * 비 오는 날에 어울리는 것으로 화면이 바뀌었다가, 비가 그치면 사람이 적용해 둔 테마(없으면 기본 화면)로 돌아와요.
 *
 * 지키는 조건
 *  1. 사람이 적용한 테마(StudioLibrary의 적용 값)는 건드리지 않아요. 비 테마는 그 위에 잠깐 덮는 것이고, 비가 그치면 저절로 걷혀요.
 *  2. 내려받아 둔 비 테마만 써요. 없으면 아무것도 바꾸지 않아요(받으라고 재촉하지도 않아요).
 *  3. 주행 중에는 바꾸지 않아요(화면 톤과 같은 규칙): 서 있을 때 바뀌어요 → [next].
 *  4. 날씨를 모르면(null) 바꾸지 않아요. 설정에서 끌 수 있어요([ToneSettings.rainTheme]).
 */
object RainTheme {
    /** 비 오는 날의 테마: 내려받아 둔 테마 가운데 태그에 "비"(또는 "장마")가 있는 첫 번째. 그림(look)이 있는 것만요. */
    fun pick(themes: List<Theme>, downloaded: Set<String>): Theme? =
        themes.firstOrNull { it.id in downloaded && it.look != null && it.tags.any { t -> t == "비" || t == "장마" } }

    /** 지금 날씨가 바라는 것: 비가 오고 설정이 켜져 있으면 true. 비 테마가 있는지는 [pick]이 따로 말해요. */
    fun wanted(settings: ToneSettings, weather: Weather?): Boolean = settings.rainTheme && weather == Weather.RAIN

    /** 주행 중에는 지금 것을 지키고, 서 있을 때만 바라는 대로 바꿔요. */
    fun next(current: Boolean, wanted: Boolean, driving: Boolean): Boolean = if (driving) current else wanted

    /** 화면이 입을 테마 id: 비 테마를 덮어야 하고 그 테마가 있으면 그것, 아니면 사람이 적용한 것. */
    fun resolve(rain: Boolean, rainTheme: Theme?, appliedId: String?): String? = if (rain && rainTheme != null) rainTheme.id else appliedId
}
