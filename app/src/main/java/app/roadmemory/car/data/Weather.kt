package app.roadmemory.car.data

/**
 * 날씨는 색 톤에만 닿아요(ui/Tone.kt). 화면에 날씨 값을 그리거나 말해 주지 않아요.
 * Pleos SDK에는 날씨 · 외기온 · 우적 센서 공개 API가 없어요. 그래서 차의 신호(와이퍼 · 전조등 · 안개등 · 김서림 제거)에서
 * 비 · 안개만 추정하고(engine/EnvEngine.kt), 맑음 · 눈은 신호가 없어서 추정하지 않아요(모르면 기본 톤).
 */
enum class Weather(val label: String) {
    CLEAR("맑음"),
    CLOUDY("흐림"),
    RAIN("비"),
    SNOW("눈"),
    FOG("안개"),
}
