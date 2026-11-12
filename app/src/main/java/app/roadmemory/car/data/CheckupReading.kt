package app.roadmemory.car.data

/**
 * 점검 화면이 차가 읽은 값을 쓸지, 서비스센터 기록([DemoCheckup])을 쓸지 정해요. 읽은 값이 있으면 언제나 읽은 값이 먼저예요.
 * (경고등 · 타이어 공기압은 PRIVATE 권한을 받았다고 가정한 구현이고, 못 읽으면 기록으로 돌아가요.)
 */
object CheckupReading {
    /** 지금 켜진 경고의 이름들. 차가 읽었으면 목록(빈 목록 = 경고 없음), 못 읽었으면 null(= 기록을 써요). */
    fun warnings(car: CarSnapshot): List<String>? = car.telltales?.mapNotNull { TelltaleText.label(it.type) }

    /** 네 바퀴 공기압(bar). 네 바퀴가 모두 읽혔을 때만, 아니면 null(= 기록을 써요). */
    fun tiresBar(car: CarSnapshot): Map<Wheel, Double>? = TirePressure.allBar(car.tirePressureRaw)

    /** 타이어 공기압 경고(TPMS)가 켜져 있는지. */
    fun tireWarning(car: CarSnapshot): Boolean = car.telltales?.any { it.type == "TPMS" } == true

    /** 아래쪽 안내 한 줄: 차가 직접 알려 주는 것과 기록을 나눠 말해요. */
    fun sourceNote(car: CarSnapshot): String {
        val live = buildList {
            add("문 · 트렁크 · 후드")
            if (car.telltales != null) add("경고등")
            if (tiresBar(car) != null) add("타이어 공기압")
        }
        return "차가 지금 직접 알려 주는 값은 ${live.joinToString(" · ")}이에요. 나머지는 서비스센터가 남긴 기록이에요."
    }
}
