package app.roadmemory.car.brain

import app.roadmemory.car.Pane
import app.roadmemory.car.UiState
import app.roadmemory.car.data.Tracks
import app.roadmemory.car.data.Weather
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.engine.Taste
import app.roadmemory.car.studio.Catalog
import app.roadmemory.car.studio.LibraryState
import app.roadmemory.car.ui.TripRun
import java.time.LocalDate

/** ION이 권하는 것 한 가지. 왜 권하는지([reasons])는 쓴 값에서만 나와요(지어내지 않아요). */
data class Advice(
    val kind: String,
    val title: String,
    val sub: String,
    val actionLabel: String,
    val reasons: List<String>,
    val action: Action,
) {
    sealed interface Action {
        data class Open(val pane: String) : Action
        data class GuideTrip(val tripId: String, val stopIndex: Int) : Action
        data class GuidePlace(val name: String, val lat: Double, val lng: Double) : Action
        data class Play(val titles: List<String>) : Action
        /** 여행 기록하기를 바로 시작해요(내비 목적지로). */
        data object Record : Action
    }
}

/** 앱에 든 곡의 분위기(제목이 말하는 것). 곡을 고르는 규칙이 써요. */
internal object TrackMood {
    private val moods = mapOf(
        "Solcho Sea Loop" to setOf("바다", "맑음", "낮"), "Paju Drive Breeze" to setOf("드라이브", "낮", "맑음"), "Yeongil Breeze Loop" to setOf("바람", "맑음", "저녁"),
        "Harbor Breeze Lullaby" to setOf("충전", "밤", "비", "쉼"), "October Maple Loop" to setOf("가을", "흐림", "저녁"), "Rose & Pond Loop" to setOf("쉼", "충전", "비", "아침"),
        // 노래 16곡(곡의 스타일 · 가사가 말하는 것)
        "Suspended Air" to setOf("가을", "아침", "바람"), "Nah Got A Sea" to setOf("아침", "바다", "쉼"), "한강 위, 오늘도" to setOf("저녁", "밤", "쉼"),
        "Na-Na" to setOf("아침", "흐림", "쉼"), "Banana & Breeze" to setOf("바람", "맑음", "낮", "바다"), "Soul sweet" to setOf("낮", "맑음", "드라이브"),
        "No flight ticket" to setOf("비", "흐림", "저녁"), "Home Is Where We Go" to setOf("드라이브", "흐림"), "Sea Remains" to setOf("바다", "드라이브", "바람"),
        "Sizzle So" to setOf("밤", "저녁"), "산방산 아침해" to setOf("아침", "맑음", "바다", "드라이브"), "Maple Syrup Home" to setOf("밤", "쉼", "충전", "가을"),
        "3842 (Aiguille du Midi)" to setOf("드라이브", "낮", "맑음"), "Light In The Cold Wind" to setOf("밤", "바람", "가을"), "Hit 'em up" to setOf("드라이브", "아침"),
        "Blue Curve Walk" to setOf("바다", "낮", "쉼"),
    )
    fun of(title: String): Set<String> = moods[title] ?: emptySet()
    fun wanted(ui: UiState, hour: Int, month: Int): Set<String> = buildSet {
        if (ui.mode == Mode.CHARGING) add("충전")
        when (ui.weather) { Weather.RAIN, Weather.SNOW, Weather.FOG -> add("비"); Weather.CLOUDY -> add("흐림"); Weather.CLEAR -> add("맑음"); null -> Unit }
        add(when { hour in 5..10 -> "아침"; hour in 11..16 -> "낮"; hour in 17..20 -> "저녁"; else -> "밤" })
        if (month in 9..11) add("가을")
    }
    fun pick(ui: UiState, hour: Int, month: Int): Pair<Tracks.Track, Set<String>>? {
        val want = wanted(ui, hour, month)
        val best = Tracks.all.maxByOrNull { t -> (moods[t.title] ?: emptySet()).count { it in want } } ?: return null
        val hit = (moods[best.title] ?: emptySet()).intersect(want)
        return if (hit.isEmpty()) null else best to hit
    }
}

/**
 * 하이엔드의 "고르기"를 규칙으로: 차의 신호(모드 · 배터리 · 날씨 · 톤 · 취향 · 탄 사람 · 내비)와 이 기기에 있는 것(내려받은 상품 · 담은 일정 · 가져온 사진 · 곡 · 저장한 여행 · 충전 기록)으로
 * 지금 권할 것을 골라요. 카드는 한 번에 한 장이고, 거절한 종류는 조용해져요([Quiet]). 모르는 값으로는 권하지 않아요.
 */
object Advisor {
    fun advise(
        ui: UiState, cat: Catalog, lib: LibraryState, quiet: QuietState, hour: Int,
        now: Long = System.currentTimeMillis(), today: LocalDate = LocalDate.now(), charges: ChargeHistory = ChargeHistory(),
        /** 지금 시각(약속 도착을 견줄 때만 써요). */
        clock: () -> java.time.LocalTime = { java.time.LocalTime.now() },
    ): List<Advice> {
        if (ui.mode == Mode.DRIVING) return emptyList()   // 주행 중에는 권하지 않아요(카드 한 장은 CardEngine 몫)
        val out = mutableListOf<Advice>()
        val range = ui.car.rangeKm?.takeIf { it > 0f }
        val run = TripRun.current(lib)
        val runTrip = cat.trips.firstOrNull { it.id == run?.tripId }

        // 1) 가던 일정의 다음 정류장 (이어 가기)
        if (run != null && runTrip != null && !ui.nav.routeActive) {
            val next = run.stopIndex + 1
            runTrip.stops.getOrNull(next)?.let { s ->
                val here = runTrip.stops.getOrNull(run.stopIndex)?.name
                val title = if (run.arrived && here != null) "${here}에 도착했어요. 다음 정류장 ${Korean.euro(s.name)} 안내할까요?" else "다음 정류장 ${Korean.euro(s.name)} 안내할까요?"
                out += Advice("next_stop", title, "${runTrip.title} · ${next + 1}/${runTrip.stops.size}", "안내",
                    listOf("내 일정 ${Korean.eul(runTrip.title)} ${run.stopIndex + 1}번째 정류장까지 안내했어요", "지금 내비 안내가 없어요", "서 있을 때만 권해요"),
                    Advice.Action.GuideTrip(runTrip.id, next))
            }
        }
        // 2) 충전소 이력으로 고르기: 내비가 넣은 충전소가 지난번에 평소보다 느렸으면
        ui.nav.chargeStops.firstOrNull { charges.slowerThanUsual(it.name) == true }?.let { stop ->
            out += Advice("charger_history", "${Korean.eun(stop.name)} 지난번에 평소보다 느렸어요", "다음 휴게소는 어때요? 경로는 운전자가 내비에서 바꿔요", "충전 기록 보기",
                listOfNotNull(charges.line(stop.name), "평소 속도는 이 기기에서 잰 충전 ${charges.measured.size}번의 평균이에요", "ION은 경로를 바꾸지 않아요"), Advice.Action.Open(Pane.CHARGE))
        }
        // 2.5) 여행 기록하기(충전소 경고 다음): 내비가 목적지로 안내 중인데 기록하는 여행이 없으면. 목적지는 내비가 준 값만 써요(차의 현재 위치는 읽지 않아요)
        ui.nav.destinationName?.takeIf { ui.nav.routeActive && it.isNotBlank() && ui.recording == null }?.let { dest ->
            out += Advice("record_trip", "${Korean.euro(dest)} 가는 길을 기록할까요?",
                ui.nav.remainDistanceM?.let { "내비 안내 중 · 남은 거리 ${it / 1000}km" } ?: "내비 안내 중", "기록하기",
                listOf("내비가 안내 중인 목적지가 있어요: $dest", "기록하는 중인 여행이 없어요", "도착하면 저절로 여행 기록으로 저장돼요", "서 있을 때만 권해요"),
                Advice.Action.Record)
        }

        // 3) 그날의 기억: 다른 해의 오늘 즈음에 다녀온 여행
        Memories.thatDay(ui.trips, today).firstOrNull()?.let { m ->
            out += Advice("that_day", Memories.sentence(m), "${m.trip.month} · ${m.trip.km}km" + (if (m.trip.tracks.isNotEmpty()) " · 그때 들은 곡 ${m.trip.tracks.size}개" else ""), if (m.trip.tracks.isNotEmpty()) "그때 곡 틀기" else "여행 기록",
                listOf("저장한 여행 ${Memories.date(m.trip).format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd"))}의 날짜가 오늘과 ${Math.abs(m.daysOff)}일 차이예요", "이름 · 거리 · 사람 수는 저장한 값 그대로예요"),
                if (m.trip.tracks.isNotEmpty()) Advice.Action.Play(m.trip.tracks) else Advice.Action.Open(Pane.TRIPS))
        }
        // 4) 담은 일정의 충전 계획 (다음 여행 짜기)
        val planned = cat.trips.filter { lib.has(it.id) }
        if (run == null && planned.isNotEmpty() && range != null) {
            planned.map { it to TripPlan.plan(it, range) }.firstOrNull { (_, p) -> (p.chargesNeeded ?: 0) > 0 }?.let { (t, p) ->
                out += Advice("trip_charge", "${t.title}, 중간에 ${p.chargesNeeded}번 충전해요", p.summary, "일정 보기",
                    listOf("정류장 사이 거리 합 ${Math.round(p.totalKm)}km(직선 × 1.3)", "지금 갈 수 있는 거리 ${Math.round(range)}km의 80%만 믿어요", "충전소는 내비가 정하고 ION은 경로를 바꾸지 않아요"),
                    Advice.Action.Open(Pane.SVC_TRIP))
            }
        }
        // 6) 충전 중: 사진 · 곡(분위기에 맞는 곡 하나)
        if (ui.mode == Mode.CHARGING) {
            val minutes = ui.chargeMinLeft
            if (lib.has("sync_${Pane.SVC_ALBUM}") && cat.album.isNotEmpty()) out += Advice("charge_album", "충전하는 동안 사진을 넘겨 볼까요?", (minutes?.let { "80%까지 약 ${it}분 · " } ?: "") + "디지털 앨범 ${cat.album.size}장", "보기",
                listOfNotNull("충전 중이에요", minutes?.let { "충전 속도를 재서 약 ${it}분 남았어요" }, "마이현대에서 가져온 사진이 ${cat.album.size}장 있어요"), Advice.Action.Open(Pane.CHARGE))
            if (lib.has("sync_${Pane.SVC_MUSIC}") && ui.nowPlaying == null) {
                val picked = TrackMood.pick(ui, hour, today.monthValue)
                out += Advice("charge_music", picked?.let { "${it.first.title} 틀까요?" } ?: "충전하는 동안 곡을 틀까요?", picked?.let { "지금 분위기(${it.second.joinToString(" · ")})에 맞는 곡이에요" } ?: "내 곡 ${Tracks.all.size}곡 이어서", "틀기",
                    listOfNotNull("충전 중이고 지금 트는 곡이 없어요", ui.weather?.let { "날씨 ${it.label}" }, "지금 ${hour}시", picked?.let { "곡의 분위기 ${Korean.gwa(it.second.joinToString(" · "))} 맞아요" }),
                    Advice.Action.Play(picked?.let { listOf(it.first.title) } ?: Tracks.all.map { it.title }))
            }
        }
        // 7) 약속 장소 (캘린더 → 길)
        if (lib.has("sync_${Pane.SVC_CALENDAR}") && !ui.nav.routeActive && run == null) {
            val at = today.atTime(hour.coerceIn(0, 23), 0)
            Agenda.nextLocated(Agenda.build(cat.calendar, today), at)?.let { a ->
                val e = a.event
                out += Advice("calendar_go", "${e.title} · ${Korean.euro(e.place)} 안내할까요?", listOfNotNull(Agenda.dateLabel(a), Agenda.countdown(a, at)?.takeIf { a.time == null || a.date != today }, e.with.ifBlank { null }).joinToString(" · "), "안내",
                    listOf("가져온 일정 가운데 장소를 아는 가장 가까운 약속이에요", "지금 내비 안내가 없어요", "도착하면 배터리가 얼마일지는 내비가 알려 줘요"),
                    Advice.Action.GuidePlace(e.place, e.lat!!, e.lng!!))
            }
        }
        // 7-1) 약속 장소로 가는 중: 내비가 준 남은 시간으로 도착을 약속 시각과 견줘요
        if (lib.has("sync_${Pane.SVC_CALENDAR}") && ui.nav.routeActive) {
            val at = java.time.LocalDateTime.of(today, clock())
            Agenda.build(cat.calendar, today).firstOrNull { !Agenda.passed(it, at) && Agenda.guiding(it, ui.nav) }?.let { a ->
                Agenda.arrival(a, ui.nav, at)?.let { ar ->
                    out += Advice("calendar_eta", "${a.event.title} · ${ar.headline}", ar.detail, "약속 보기",
                        listOf("내비가 약속 장소(${a.event.place})로 안내하고 있어요", "남은 시간은 내비가 준 값이에요", "약속은 ${Agenda.dateLabel(a)}예요"),
                        Advice.Action.Open(Pane.SVC_CALENDAR))
                }
            }
        }
        // 5→7.5) 다음 여행 초안(캘린더의 약속 카드보다 뒤: 시나리오 D): 담은 일정이 없으면, 지금 배터리로 충전 없이 가는 일정 가운데 지난 여행과 같은 지역을 먼저
        // 충전 중에는 "충전하는 동안 사진을 넘겨 볼까요?"(6)가 먼저예요: 시나리오 C. 초안은 충전이 아닌 때 권해요
        if (run == null && planned.isEmpty() && range != null && cat.trips.isNotEmpty() && ui.mode != Mode.CHARGING) {
            val visited = ui.trips.mapNotNull { it.destName }.toSet()
            val fits = cat.trips.map { it to TripPlan.plan(it, range) }.filter { (_, p) -> p.chargesNeeded == 0 }
            val pick = fits.maxByOrNull { (t, _) -> (if (visited.any { v -> t.region.isNotBlank() && (v.contains(t.region) || t.stops.any { s -> s.address.contains(v) || v.contains(s.name) }) }) 10 else 0) - (t.stops.size.coerceAtMost(9)) }
            pick?.let { (t, p) ->
                out += Advice("next_trip", "다음 여행 초안 · ${t.title}", "${p.summary} · 정류장 ${t.stops.size}곳", "일정 보기",
                    listOfNotNull("지금 갈 수 있는 거리 ${Math.round(range)}km로 충전 없이 다녀와요", if (visited.isNotEmpty()) "지난 여행의 목적지(${visited.take(2).joinToString(" · ")})와 지역을 견줬어요" else "저장한 여행이 아직 없어서 가까운 일정을 골랐어요", "담으면 정류장마다 안내해요"),
                    Advice.Action.Open(Pane.SVC_TRIP))
            }
        }
        // 8) 테마 고르기: 날씨 · 시간 · 탄 사람 · 취향(운전석 설정 온도) · 화면 톤
        if (lib.appliedId(Pane.SVC_THEME) == null) {
            ThemeShelf.pick(ui, cat.themes, hour)?.let { (pick, why) ->
                out += Advice("theme_pick", "${pick.title} 어때요?", "${pick.creator} · 테마 · ${if (lib.has(pick.id)) "내려받아 두었어요" else "내려받아 적용해요"}", "보기", why, Advice.Action.Open(Pane.SVC_THEME))
            }
        }
        // 9) 아트 고르기 (시간대)
        if (lib.appliedId(Pane.SVC_ART) == null && ui.mode == Mode.CHARGING) {
            ArtShelf.forHour(cat.arts, hour)?.let { (a, hits) ->
                out += Advice("art_pick", "충전하는 동안 ${Korean.eul(a.title)} 걸어 볼까요?", "${a.artist} · 디지털 아트", "보기",
                    listOf("충전 중이에요", "지금 ${hour}시 · 태그 ${Korean.gwa(hits.joinToString(" · "))} 맞아요"), Advice.Action.Open(Pane.SVC_ART))
            }
        }
        return out.filter { !quiet.muted(it.kind, now) }
    }

    /** 홈에 보여 줄 한 장. */
    fun top(ui: UiState, cat: Catalog, lib: LibraryState, quiet: QuietState, hour: Int, charges: ChargeHistory = ChargeHistory()): Advice? =
        advise(ui, cat, lib, quiet, hour, charges = charges).firstOrNull()
}
