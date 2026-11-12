package app.roadmemory.car.data

/**
 * ION 앱의 가상 사용자(오너가 "문유 moonyou"로 정했어요, 2026-11-12). 실제 차에서는 이 이름이 차 계정(마이현대 프로필)에서 와야 하는데,
 * 차 SDK에 사용자 이름을 주는 항목은 확인하지 못했어요 [미확인]. 그래서 값은 이 한 곳에만 있고, 바꾸려면 [NAME] · [ID]만 고치면 돼요(호칭 [CALL]도 함께).
 * 운전석에 앉은 사람과 여행을 함께한 사람 가운데 한 명은 언제나 이 사용자예요(운전자 본인).
 */
object DemoUser {
    const val NAME = "문유"
    const val ID = "moonyou"
    const val CALL = "문유님"

    /** 자리 화면의 한 줄: 운전석에 사람이 있으면 그 사람이 사용자라서 이름으로 말해요. */
    fun riders(count: Int, driverSeated: Boolean): String = when {
        count <= 0 -> "탄 사람이 없어요"
        !driverSeated -> "${count}명이 탔어요"
        count == 1 -> "$CALL 혼자 탔어요"
        else -> "$CALL 외 ${count - 1}명이 탔어요"
    }

    /** 여행 세부의 "함께 탄 사람" 아래 줄. 기록의 사람 수는 가장 많이 탔을 때의 수라 "최대"를 붙여요. 모르면(0 이하) null. */
    fun withTrip(people: Int): String? = when {
        people <= 0 -> null
        people == 1 -> "$CALL 혼자"
        else -> "$CALL 외 최대 ${people - 1}명"
    }
}
