package app.roadmemory.car.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** 한 바퀴의 공기압(bar). */
data class WheelPressure(val label: String, val bar: Double)

/** 소모품 하나: 남은 양(%). */
data class Consumable(val name: String, val leftPercent: Int)

/** 곧 해야 하는 일: 오늘로부터 며칠 뒤인지. */
data class DueItem(val name: String, val inDays: Int)

/** 서비스센터가 남긴 정비 기록 한 건: 오늘로부터 며칠 전인지. */
data class ServiceRecord(val daysAgo: Int, val title: String, val detail: String)

/**
 * 점검 화면을 채우는 **지어낸 정비 · 소모품 값**이에요(차가 준 값이 아니에요: SDK는 바퀴별 공기압 · 경고등을 주지 않아요, `docs/DATA.md`).
 * 날짜는 오늘을 기준으로 한 상대값("24일 전", "다음 화요일")이라 앱을 언제 열어도 최근 기록처럼 보여요.
 * 화면은 이 값을 "서비스센터가 남긴 기록"으로만 말하고, 차가 지금 직접 알려 주는 문 · 트렁크 · 후드와 섞어 말하지 않아요.
 * 캘린더 동기화의 "정기 점검 예약(다음 주 화요일 오후 2시 · 서비스센터)"과 같은 약속이에요([nextCheck]).
 */
object DemoCheckup {
    const val PLACE = "서비스센터"
    const val BATTERY_HEALTH_PERCENT = 97
    val PRESSURE_OK_BAR = 2.3..2.6

    val wheels = listOf(WheelPressure("앞 왼쪽", 2.5), WheelPressure("앞 오른쪽", 2.5), WheelPressure("뒤 왼쪽", 2.4), WheelPressure("뒤 오른쪽", 2.5))

    val consumables = listOf(Consumable("브레이크 패드", 68), Consumable("와이퍼 블레이드", 80))

    /** 정기 점검 말고 곧 해야 하는 일(가까운 순). */
    val due = listOf(DueItem("타이어 위치 교환", 24), DueItem("에어컨 필터 교환", 62))

    /**
     * 정비 이력(최근 것이 먼저). 다음 교환과 앞뒤가 맞아야 해요: 타이어 위치 교환은 정기 점검(205일 전) 때 했고 24일 뒤가 다음(약 7개월 주기),
     * 에어컨 필터는 131일 전에 갈았고 62일 뒤가 다음(약 6개월 주기), 공기압은 마지막 점검(24일 전)에 맞췄어요.
     */
    val records = listOf(
        ServiceRecord(24, "타이어 점검 · 공기압 보충", "네 바퀴의 공기압을 맞췄어요"),
        ServiceRecord(131, "에어컨 필터 교환", "실내 공기 필터를 새것으로 갈았어요"),
        ServiceRecord(205, "정기 점검(1년)", "타이어 위치 교환 · 브레이크 · 배터리 점검, 이상 없음"),
        ServiceRecord(380, "첫 점검", "차를 받고 처음 살핀 점검이에요, 이상 없음"),
    )

    /** 다음 정기 점검: 오늘 다음에 오는 화요일 오후 2시. */
    fun nextCheck(today: LocalDate): LocalDate = today.with(TemporalAdjusters.next(DayOfWeek.TUESDAY))

    fun pressureWord(w: WheelPressure) = when {
        w.bar < PRESSURE_OK_BAR.start -> "낮아요"
        w.bar > PRESSURE_OK_BAR.endInclusive -> "높아요"
        else -> "적정"
    }

    /** "24일 전" · "4개월 전" · "1년 전". */
    fun ago(days: Int) = when {
        days < 1 -> "오늘"
        days < 45 -> "${days}일 전"
        days < 365 -> "${days / 30}개월 전"
        else -> "${days / 365}년 전"
    }

    /** "내일" · "6일 뒤" · "약 2개월 뒤". */
    fun inDays(days: Int) = when {
        days <= 0 -> "오늘"
        days == 1 -> "내일"
        days < 45 -> "${days}일 뒤"
        else -> "약 ${days / 30}개월 뒤"
    }

    fun dateLabel(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("yyyy.MM.dd(E)", Locale.KOREAN))
}
