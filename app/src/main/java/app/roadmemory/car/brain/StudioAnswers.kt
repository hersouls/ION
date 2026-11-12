package app.roadmemory.car.brain

import app.roadmemory.car.Pane
import app.roadmemory.car.data.CarSnapshot
import app.roadmemory.car.data.NavState
import app.roadmemory.car.data.SavedTrip
import app.roadmemory.car.data.Tracks
import app.roadmemory.car.data.Weather
import app.roadmemory.car.engine.CardEngine
import app.roadmemory.car.studio.Catalog
import app.roadmemory.car.studio.LibraryState
import app.roadmemory.car.ui.TripRun
import java.time.LocalDate
import java.time.LocalTime

/**
 * 기억에게 묻기(하이엔드의 규칙 버전): ION 칸의 질문에 이 기기에 있는 값(차의 신호 · 내비 · 저장한 여행 · 충전 기록 · 받은 상품)으로만 답해요.
 * 장소("제주에서") · 때("지난달 · 올해 · 작년 · 10월")로 거른 뒤 세어서 말하고, 없으면 "없어요"라고 해요. LLM 없이요.
 * 묻는 말에 안 맞으면 null을 돌려서 다음 답변자(ION-Link 질문 · 음성 명령)에게 넘겨요.
 */
class StudioAnswers(
    private val cat: Catalog, private val lib: LibraryState, private val trips: List<SavedTrip>, private val nowPlaying: String?,
    private val charges: ChargeHistory = ChargeHistory(), private val today: LocalDate = LocalDate.now(),
    /** 내비 · 날씨 · 충전 남은 분 · 차의 값(차가 준 값). 모르면 null. */
    private val nav: NavState? = null, private val weather: Weather? = null, private val chargeMinLeft: Int? = null,
    private val clock: () -> LocalTime = { LocalTime.now() }, private val car: CarSnapshot? = null, private val charging: Boolean = false,
    /** 지금 하는 충전의 분당 %(아직이면 null). */
    private val currentPace: Double? = null,
) {
    fun answer(heard: String): String? {
        val t = heard.trim().replace(" ", "").removePrefix(Conversation.LINK)
        if (t.isEmpty()) return null
        fun has(vararg k: String) = k.any { t.contains(it.replace(" ", "")) }
        fun m(vararg rx: String) = rx.any { Regex(it).containsMatchIn(t) }
        val (scoped, scopeLabel) = scope(t)
        fun yn(yes: Boolean, body: String) = (if (yes) "네, " else "아니요, ") + body
        val label = if (scopeLabel.isEmpty()) "" else "$scopeLabel "
        return when {
            // ── 인사 · 짧은 말: 사람이 하는 말에는 사람처럼
            (t.length <= 10 && (has("안녕", "하이", "헬로") || t.lowercase().trimEnd('?', '!') in setOf("ion", "이온", "이온아", "ion아"))) -> "안녕하세요! 가는 길에 필요한 것만 말할게요"
            has("고마워", "고맙", "감사", "땡큐", "수고", "잘했어", "최고야", "멋져") || t == "굿" -> "별말씀을요. 필요할 때 불러 주세요"
            has("잘자", "잘가", "안녕히") -> "조심히 가세요. 다음에 또 불러 주세요"
            has("너누구", "넌누구", "누구세요", "정체가") -> "차 안의 ION이에요. 차가 알려 주는 값과 이 차에 담아 둔 것으로만 말해요"
            has("뭐할수있", "뭐해줄수", "도와줘", "할수있는게", "뭘할수", "뭐할줄", "도움말", "물어볼수", "뭐물어", "사용법") -> "배터리 · 도착 예상 · 충전 기록 · 지난 여행을 물어보거나, 다음 정류장 안내 · 곡 틀기 · 테마 추천 · 화면 열기를 시킬 수 있어요. 아래 말을 눌러도 돼요"
            has("몇시야", "몇시", "지금시간", "시간알려") || t == "시간" -> clock().let { "지금 ${it.hour}시 ${it.minute}분이에요" }
            has("비와", "비오", "눈와", "비내") -> weather?.let { yn(it.label.contains("비") || it.label.contains("눈"), "밖은 ${it.label}인 것 같아요 · 와이퍼 · 등 같은 차의 신호로 짐작한 거예요") } ?: "아직 모르겠어요. 날씨는 와이퍼 · 등 같은 차의 신호로만 짐작해요"
            has("날씨어때", "날씨뭐", "날씨는") || t == "날씨" -> weather?.let { "밖은 ${it.label}인 것 같아요. 와이퍼 · 등 같은 차의 신호로 짐작한 거예요" } ?: "날씨는 와이퍼 · 등 같은 차의 신호로만 짐작해요. 지금은 아직 모르겠어요"
            // ── 차의 값
            m("외기온|바깥온도|밖은몇도|밖몇도|밖에몇도|바깥몇도") && !has("기능", "데이터") -> car?.outsideTempC?.let { "외기온은 ${CardEngine.tempText(it, car.tempUnit)}예요" } ?: "외기온을 이 차에서 아직 읽지 못했어요"
            m("실내온도|안은몇도|차안온도|실내몇도") -> car?.cabinTempC?.let { "실내 온도는 ${CardEngine.tempText(it, car.tempUnit)}예요" } ?: "실내 온도를 아직 읽지 못했어요"
            m("몇키로갈|몇km갈|주행가능|갈수있는거리|얼마나갈수|주행거리|항속거리|항속|더갈수|갈수있어") && !m("도착|충전없이") -> car?.rangeKm?.takeIf { it > 0f }?.let { "지금 ${Math.round(it)}km 갈 수 있어요" + (car.soc?.let { s -> " · 배터리 $s%" } ?: "") } ?: "갈 수 있는 거리를 아직 읽지 못했어요"
            m("배터리(얼마|몇|잔량|상태|남은)|지금배터리|배터리는|^잔량|몇퍼센트|몇프로|^배터리$") && !m("도착") -> car?.soc?.let { "배터리 $it%" + (car.rangeKm?.takeIf { r -> r > 0f }?.let { r -> " · ${Math.round(r)}km 갈 수 있어요" } ?: "") + (if (charging) chargeMinLeft?.let { m -> " · 충전 중, 80%까지 약 ${m}분" } ?: " · 충전 중" else "") } ?: "배터리를 아직 읽지 못했어요"
            // ── 비교(하이엔드 제안 5): 있는 값으로만 둘을 나란히 — 이번 달 vs 지난달 · 올해 vs 작년 · 이번 달 vs 달마다 평소 · 지금 충전 vs 평소 속도
            m("(여기|이충전소|이곳|지금충전|이번충전).{0,6}(빨라|느려|평소|어때|속도)|충전.{0,4}평소보다|평소보다.{0,4}(빠|느)") -> {
                val u = charges.usualPace
                val now = currentPace
                when {
                    !charging -> "지금은 충전하고 있지 않아요" + (u?.let { " · 평소에는 분당 ${"%.1f".format(it)}%씩 올랐어요" } ?: "")
                    now == null -> "아직 속도를 재는 중이에요 · 2% 넘게 오르면 평소와 견줘 말해요" + (u?.let { " · 평소는 분당 ${"%.1f".format(it)}%" } ?: "")
                    u == null -> "지금 분당 ${"%.1f".format(now)}%씩 올라요 · 평소 속도는 세 번 이상 재야 견줄 수 있어요(지금까지 ${charges.measured.size}번)"
                    else -> "지금 분당 ${"%.1f".format(now)}% · 평소 ${"%.1f".format(u)}% · " + when { now < u * 0.7 -> "평소보다 느려요"; now > u * 1.3 -> "평소보다 빨라요"; else -> "평소만큼이에요" }
                }
            }
            m("(지난달|저번달|전달|작년|평소)(보다|에비해|이랑|과|와|하고)(비교|견줘|어때|많|적|멀|자주|$)?") && m("보다|비해|비교|견줘|어때|많이|적게|멀리|자주") -> compare(t)
            // ── 충전 중: 지금 충전하는지 · 남은 시간
            m("충전중이야|충전중\\?|충전하고있어|충전되고있|충전다됐|충전됐어|충전끝났") -> when {
                !charging -> yn(false, "지금은 충전하고 있지 않아요" + (car?.soc?.let { " · 배터리 $it%" } ?: ""))
                m("다됐|끝났|됐어") -> yn(false, "아직 충전 중이에요" + (car?.soc?.let { " · 배터리 $it%" } ?: "") + (chargeMinLeft?.let { " · 80%까지 약 ${it}분" } ?: ""))
                else -> yn(true, "충전 중이에요" + (car?.soc?.let { " · 배터리 $it%" } ?: "") + (chargeMinLeft?.let { " · 80%까지 약 ${it}분" } ?: ""))
            }
            m("충전.{0,3}(언제끝|몇분|얼마나남|끝나|끝나려면)|80퍼|80%까지|충전(얼마나)?걸|완충") -> when {
                !charging -> "지금은 충전하고 있지 않아요" + (charges.usualPace?.let { " · 평소에는 분당 ${"%.1f".format(it)}%씩 올랐어요" } ?: "")
                chargeMinLeft != null -> "80%까지 약 ${chargeMinLeft}분 남았어요(배터리가 오르는 속도를 재서 어림한 값이에요)"
                else -> "충전 속도를 재는 중이라 아직 몰라요. 2% 넘게 오르면 말해요"
            }
            t in setOf("충전기록", "충전이력", "충전내역", "충전기록알려줘", "충전이력알려줘") -> {
                val n = trips.sumOf { it.charges }
                val pace = charges.usualPace?.let { " · 잰 속도는 평소 분당 ${"%.1f".format(it)}%" } ?: ""
                val slow = charges.measured.minByOrNull { it.pctPerMin!! }?.let { " · 가장 느렸던 곳은 ${it.place}" } ?: ""
                if (trips.isEmpty() && charges.measured.isEmpty()) "충전 기록이 아직 없어요. 충전하면 이 기기에 남아요" else "저장한 여행 ${trips.size}건에서 충전 ${n}번$pace$slow"
            }
            m("충전.{0,2}(속도|빠른|빨라|느려|느린편|평균)|어디가.{0,3}느렸|제일느린|평소충전|오래걸린|분당몇|분당") -> {
                val u = charges.usualPace
                if (u == null) "충전 속도는 세 번 이상 재야 말해요. 지금까지 ${charges.measured.size}번 쟀어요"
                else "평소 분당 ${"%.1f".format(u)}%씩 올랐어요(${charges.measured.size}번 잰 값)" + (charges.measured.minByOrNull { it.pctPerMin!! }?.let { " · 가장 느렸던 곳은 ${it.place}" } ?: "")
            }
            // ── 내비
            m("도착.{0,3}(배터리|몇퍼|몇%)|도착배터리|도착하면충전|충전없이|도착할때배터리") -> when {
                nav?.routeActive != true -> "지금 안내 중인 길이 없어요. 내비에서 목적지를 정하면 도착 예상 배터리를 말해요"
                nav.estimatedSocAtDest != null -> {
                    val need = nav.chargeStops.isNotEmpty() || nav.estimatedSocAtDest <= CardEngine.LOW_SOC_AT_DEST
                    val body = "${nav.destinationName ?: "목적지"}에 도착하면 배터리가 ${nav.estimatedSocAtDest}%쯤이에요" +
                        (nav.chargeStops.firstOrNull()?.let { " · 내비가 ${Korean.eul(it.name)} 경로에 넣었어요" } ?: if (need) " · 가는 길에 충전이 필요해요" else " · 충전 없이 가요")
                    when { m("필요해|필요하|들러야") -> yn(need, body); m("충전없이|안하고갈|없이갈") -> yn(!need, body); else -> body }
                }
                else -> "내비가 아직 도착 예상 배터리를 주지 않았어요"
            }
            m("언제도착|얼마나남았|몇분남았|도착까지|남은거리|몇분걸|도착시간|^도착예상$|도착예상시간") && !m("충전") -> when {
                nav?.routeActive != true -> "지금 안내 중인 길이 없어요"
                else -> listOfNotNull(nav.remainTimeS?.let { "${Math.max(1, it / 60)}분" }, nav.remainDistanceM?.let { "${"%.1f".format(it / 1000.0)}km" }).takeIf { it.isNotEmpty() }?.let { "${nav.destinationName ?: "목적지"}까지 ${it.joinToString(" · ")} 남았어요" + (nav.estimatedSocAtDest?.let { s -> " · 도착 배터리 $s%쯤" } ?: "") } ?: "내비가 아직 남은 시간을 주지 않았어요"
            }
            m("목적지어디|어디로가고|어디가는중|어디로가는|목적지가|^목적지$|지금경로|지금길|어디로가는중") && !m("정류장") -> when {
                nav?.routeActive == true -> "${Korean.euro(nav.destinationName ?: "목적지")} 가는 길이에요" + (nav.remainTimeS?.let { " · ${Math.max(1, it / 60)}분 남았어요" } ?: "")
                else -> "지금 안내 중인 길이 없어요"
            }
            // ── 마이 트립 진행
            m("어디까지왔|다음정류장|다음은어디|진행상황|뭐남았|남은정류장|어디까지|^내일정$|진행중인일정|몇번째정류장|일정진행|정류장몇") -> {
                val run = TripRun.current(lib) ?: return "가는 중인 일정이 없어요. 마이 트립에서 정류장을 누르면 안내를 시작해요"
                val trip = cat.trips.firstOrNull { it.id == run.tripId } ?: return "가는 중인 일정을 찾지 못했어요"
                val next = trip.stops.getOrNull(run.stopIndex + 1)
                val left = trip.stops.size - run.stopIndex - 1
                // 내비가 도착했다고 알려 줬으면 "도착했어요", 아니면 "가는 중이에요"
                val here = trip.stops[run.stopIndex].name
                val where = if (run.arrived) "${here}에 도착했어요" else "${Korean.euro(here)} 가는 중이에요"
                "${trip.title} ${run.stopIndex + 1}/${trip.stops.size} 정류장, $where" + (next?.let { " · 다음은 ${it.name}, 남은 정류장 ${left}곳" } ?: if (run.arrived) " · 여행을 마쳤어요" else " · 마지막 정류장이에요")
            }
            m("건너뛰|스킵") -> "정류장 건너뛰기는 마이 트립 화면에서 다음 정류장을 눌러 주세요. 말로는 바꾸지 않아요"
            // ── 충전 횟수
            m("충전.{0,3}(몇번|횟수)|몇번충전|충전했어|충전했지|충전했나") -> if (scoped.isEmpty()) "${label}저장한 여행이 없어서 셀 게 없어요" else "${label.ifEmpty { "저장한 " }}여행 ${scoped.size}건에서 충전 ${scoped.sumOf { it.charges }}번이에요"
            // ── 여행 기록
            m("가장멀리|제일멀리|가장긴|제일긴|길었던") -> scoped.maxByOrNull { it.km }?.let { "${label}가장 멀리 간 건 ${it.destName ?: it.month} ${it.km}km예요" } ?: "${label}저장한 여행이 없어요"
            m("제일많이간|가장많이간|자주가는|자주간|많이간곳") -> {
                val counts = scoped.mapNotNull { it.destName }.groupingBy { it }.eachCount()
                val top = counts.maxByOrNull { it.value }
                when {
                    top == null -> "${label}이름이 있는 목적지 기록이 없어요"
                    top.value <= 1 -> "${label}두 번 이상 간 곳은 아직 없어요. 다녀온 곳은 ${counts.keys.take(4).joinToString(" · ")}예요"
                    else -> "${label}가장 자주 간 곳은 ${top.key}, ${top.value}번이에요"
                }
            }
            m("마지막여행|최근여행|마지막으로간|최근에간|최근여행지|마지막으로갔") -> scoped.maxByOrNull { it.at }?.let { "${label}마지막 여행은 ${it.month} ${it.destName ?: ""} ${it.km}km예요".replace("  ", " ") } ?: "${label}저장한 여행이 아직 없어요"
            m("첫여행|처음여행|첫번째여행|처음간") -> scoped.minByOrNull { it.at }?.let { "${label}첫 여행은 ${it.month} ${it.destName ?: ""} ${it.km}km예요".replace("  ", " ") } ?: "${label}저장한 여행이 아직 없어요"
            m("어디갔어|어디갔었|어디다녀|어디어디갔|어디갔다") && !m("간적|가봤") -> {
                val names = scoped.sortedByDescending { it.at }.mapNotNull { it.destName }.distinct()
                if (names.isEmpty()) "${label}저장한 여행이 없어요" else "${label}다녀온 곳은 ${if (names.size > 5) names.take(5).joinToString(" · ") + " 외 ${names.size - 5}곳이에요" else Korean.ieyo(names.joinToString(" · "))} · 여행 ${scoped.size}건"
            }
            m("누구랑|몇명이|같이간|가족이랑|누구와|식구랑|애들이랑|몇명탔|평균몇명|많이탄") -> {
                if (scoped.isEmpty()) "${label}저장한 여행이 없어요"
                else { val avg = scoped.map { it.people }.average(); "누가 탔는지는 기록하지 않아요. ${label}여행 ${scoped.size}건에 평균 ${"%.1f".format(avg)}명이 탔고, 가장 많이 탄 날은 ${scoped.maxOf { it.people }}명이에요" }
            }
            m("여행.{0,3}(몇번|몇건)|몇번갔|얼마나달렸|몇km|몇키로|몇킬로|총거리") || (m("여행$|여행은$|여행기록$") && scopeLabel.isNotEmpty()) -> {
                val pl = placeIn(t)
                if (scoped.isEmpty()) "${label}저장한 여행이 없어요. 도착하면 이 기기에 기록해요"
                else if (pl != null && m("몇번")) "${pl}에 ${scoped.size}번 갔어요 · " + scoped.sortedByDescending { it.at }.take(3).joinToString(" · ") { "${it.month} ${it.destName ?: ""} ${it.km}km".replace("  ", " ") }
                else "${label}여행 ${scoped.size}건 · 합쳐서 ${scoped.sumOf { it.km }}km예요" + (scoped.maxByOrNull { it.km }?.let { " · 가장 길었던 건 ${it.destName ?: it.month} ${it.km}km" } ?: "")
            }
            m("간적있|가봤|갔었|갔어\\?$|언제갔|언제갔어|갔던") -> {
                val q = t.replace("언제", "").replace("마지막으로", "")
                val place = placeIn(q) ?: Regex("([가-힣A-Za-z0-9]{2,})(에|에는|엔|은|는)?(간적|가봤|갔었|갔어|갔던)").find(q)?.groupValues?.get(1)
                if (place == null || Regex("몇번|여행|어디|언제|누구").containsMatchIn(place)) null else {
                    val n = trips.filter { it.destName?.contains(place) == true }
                    val last = n.maxByOrNull { it.at }
                    val tail = last?.destName?.takeIf { it != place }?.let { " $it" } ?: ""
                    when {
                        n.isEmpty() -> if (m("언제")) "${place}에 간 기록이 없어서 언제인지 말할 수 없어요" else yn(false, "${place}에 간 기록은 없어요")
                        m("언제") -> "${last!!.month}에 갔어요 ·$tail ${last.km}km".replace(" ·  ", " · ") + (if (n.size > 1) " · 모두 ${n.size}번" else "")
                        else -> yn(true, "${place}에 ${n.size}번 갔어요 · 마지막은 ${last!!.month}$tail")
                    }
                }
            }
            m("그날|작년오늘|년전오늘|오늘같은날|오늘뭐했|이맘때|년전|날짜에뭐했") -> {
                val all = Memories.thatDay(trips, today)
                val asked = Regex("(\\d)년전").find(t)?.groupValues?.get(1)?.toInt() ?: if (m("작년")) 1 else null
                val exact = asked?.let { y -> all.firstOrNull { it.yearsAgo == y } }
                when {
                    exact != null -> Memories.sentence(exact)
                    asked != null && all.isNotEmpty() -> "${if (asked == 1) "작년" else "${asked}년 전"} 이맘때 기록은 없어요 · " + Memories.sentence(all.first())
                    asked != null -> "${if (asked == 1) "작년" else "${asked}년 전"} 이맘때 여행 기록은 없어요"
                    all.isNotEmpty() -> Memories.sentence(all.first())
                    else -> "다른 해 오늘 즈음의 여행 기록은 없어요"
                }
            }
            // ── 곡 · 테마 · 아트 · 앨범 · 받은 것
            m("뭐틀|무슨곡|지금곡|트는곡|무슨노래|이곡뭐|이노래|무슨음악") -> nowPlaying?.let { "지금 트는 곡은 ${Korean.ieyo(it)}" } ?: "지금 트는 곡이 없어요"
            m("테마뭐|무슨테마|어떤테마|테마는|테마쓰고|현재테마|지금테마") -> cat.theme(lib.appliedId(Pane.SVC_THEME))?.let { "지금 테마는 ${Korean.ieyo(it.title)} · ${it.creator}" } ?: "적용한 테마가 없어요. 테마 ${cat.themes.size}개 중에 골라요"
            m("걸어둔작품|걸어둔아트|아트뭐걸|무슨아트|어떤작품|지금작품|작품뭐야|현재작품|지금아트") -> cat.art(lib.appliedId(Pane.SVC_ART))?.let { "걸어 둔 작품은 ${Korean.ieyo(it.title)} · ${it.artist}" } ?: "걸어 둔 작품이 없어요. 디지털 아트에서 내려받아 걸어요"
            m("사진몇장|사진있어|내사진|앨범에뭐") -> if (lib.has("sync_${Pane.SVC_ALBUM}")) (if (m("있어")) "네, " else "") + "마이현대에서 가져온 사진이 ${cat.album.size}장 있어요" else (if (m("있어")) "아니요, " else "") + "아직 가져온 사진이 없어요. 디지털 앨범에서 동기화해요"
            m("내려받은|뭐받았|다운받은|내콘텐츠|받은거|받은테마|받은아트|받은상품|산거|구매한") -> {
                val th = lib.countIn(cat.themes.map { it.id }); val ar = lib.countIn(cat.arts.map { it.id }); val tr = lib.countIn(cat.trips.map { it.id })
                val synced = listOfNotNull(
                    if (lib.has("sync_${Pane.SVC_ALBUM}")) "사진 ${cat.album.size}장" else null,
                    if (lib.has("sync_${Pane.SVC_MUSIC}")) "곡 ${Tracks.all.size}곡" else null,
                    if (lib.has("sync_${Pane.SVC_CALENDAR}")) "일정 ${cat.calendar.size}개" else null,
                )
                val got = if (th + ar + tr == 0) "내려받은 상품은 없어요" else "테마 ${th}개 · 아트 ${ar}점 · 담은 일정 ${tr}개예요"
                if (synced.isEmpty()) got else "$got · 마이현대에서 가져온 것은 ${synced.joinToString(" · ")}"
            }
            // ── 캘린더
            m("약속뭐|다음약속|약속있|오늘일정|오늘약속|내일일정|내일약속|일정알려|일정뭐있|무슨일정|약속몇|일정몇개|다음일정|^약속$|가까운약속|이번주(일정|약속)|다음주(일정|약속)|약속.{0,8}(늦|언제|남았|얼마나)|(늦을까|늦겠|늦어\\??$)") -> {
                if (!lib.has("sync_${Pane.SVC_CALENDAR}")) (if (m("있어")) "아니요, " else "") + "가져온 일정이 없어요. 캘린더 동기화에서 가져와요"
                else {
                    val now = java.time.LocalDateTime.of(today, clock())
                    val agenda = Agenda.build(cat.calendar, today)
                    val near = Agenda.next(agenda, now) ?: agenda.firstOrNull()
                    val nearText = near?.let { a -> "가까운 약속은 ${Agenda.line(a)}" + (Agenda.countdown(a, now)?.let { " · $it" } ?: "") + (if (a.located) " · 안내할 수 있어요" else "") }
                    fun on(b: AgendaBucket) = agenda.filter { Agenda.bucket(it, today) == b }
                    fun say(label: String, list: List<Appt>) =
                        if (list.isEmpty()) "$label 약속은 없어요 · $nearText" else "$label 약속은 ${list.size}개예요 · ${list.joinToString(" / ") { Agenda.line(it) }}"
                    when {
                        near == null -> "가져온 일정이 비어 있어요"
                        m("늦") -> agenda.firstOrNull { !Agenda.passed(it, now) && nav != null && Agenda.guiding(it, nav) }?.let { a -> Agenda.arrival(a, nav!!, now)?.let { "${a.event.title} · ${it.headline} · ${it.detail}" } }
                            ?: "약속 장소로 가는 길이 내비에 없어서 도착 시각을 아직 몰라요 · $nearText"
                        m("오늘") && on(AgendaBucket.TODAY).isEmpty() -> "오늘로 적힌 약속은 없어요 · $nearText"
                        m("오늘") -> say("오늘", on(AgendaBucket.TODAY))
                        m("내일") -> say("내일", on(AgendaBucket.TOMORROW))
                        m("이번주") -> say("이번 주", on(AgendaBucket.TODAY) + on(AgendaBucket.TOMORROW) + on(AgendaBucket.THIS_WEEK).filter { !it.date!!.isBefore(today) })
                        m("다음주") -> say("다음 주", agenda.filter { a -> a.date?.let { d -> CalendarTime.monday(d) == CalendarTime.monday(today).plusDays(7) } == true })
                        m("언제|남았|얼마나") -> "${Agenda.line(near)}" + (Agenda.countdown(near, now)?.let { " · $it" } ?: "")
                        m("몇개") -> "가져온 약속은 ${cat.calendar.size}개예요 · $nearText"
                        else -> (if (m("있어")) "네, " else "") + "$nearText · 모두 ${cat.calendar.size}개예요"
                    }
                }
            }
            else -> null
        }
    }

    /** 둘을 나란히: 이번 달 vs 지난달 · 올해 vs 작년 · 이번 달 vs 달마다 평소. 잣대는 묻는 말의 충전(번) · 거리(km) · 사람(명), 없으면 여행(건). */
    private fun compare(t: String): String {
        val ym = { d: LocalDate -> "%04d.%02d".format(d.year, d.monthValue) }
        val metric = when { t.contains("충전") -> "충전"; Regex("km|키로|킬로|거리|멀리|달렸").containsMatchIn(t) -> "거리"; Regex("명|사람|식구").containsMatchIn(t) -> "사람"; else -> "여행" }
        fun value(list: List<SavedTrip>): Pair<Int, String> = when (metric) {
            "충전" -> list.sumOf { it.charges } to "충전 ${list.sumOf { it.charges }}번"
            "거리" -> list.sumOf { it.km } to "${list.sumOf { it.km }}km"
            "사람" -> list.sumOf { it.people } to "${list.sumOf { it.people }}명"
            else -> list.size to "여행 ${list.size}건"
        }
        val unit = when (metric) { "충전" -> "번"; "거리" -> "km"; "사람" -> "명"; else -> "건" }
        fun diff(a: Int, b: Int) = when { a == b -> "같아요"; a > b -> "${a - b}$unit 많아요"; else -> "${b - a}$unit 적어요" }
        return when {
            t.contains("작년") -> {
                val a = trips.filter { it.month.startsWith("${today.year}.") }; val b = trips.filter { it.month.startsWith("${today.year - 1}.") }
                if (a.isEmpty() && b.isEmpty()) "올해도 작년도 저장한 여행이 없어요"
                else "올해 ${value(a).second} · 작년 ${value(b).second} · ${diff(value(a).first, value(b).first)}"
            }
            t.contains("평소") -> {
                val a = trips.filter { it.month == ym(today) }
                val months = trips.map { it.month }.distinct().filter { it != ym(today) }
                if (months.isEmpty()) "견줄 다른 달의 기록이 아직 없어요 · 이번 달 ${value(a).second}"
                else {
                    val avg = months.map { mth -> value(trips.filter { it.month == mth }).first }.average()
                    val cur = value(a).first
                    val word = when { avg == 0.0 && cur == 0 -> "같아요"; cur > avg * 1.2 -> "평소보다 많아요"; cur < avg * 0.8 -> "평소보다 적어요"; else -> "평소만큼이에요" }
                    "이번 달 ${value(a).second} · 달마다 평소 ${"%.1f".format(avg)}$unit(${months.size}달) · $word"
                }
            }
            else -> {
                val a = trips.filter { it.month == ym(today) }; val b = trips.filter { it.month == ym(today.minusMonths(1)) }
                if (a.isEmpty() && b.isEmpty()) "이번 달도 지난달도 저장한 여행이 없어요"
                else "이번 달 ${value(a).second} · 지난달 ${value(b).second} · ${diff(value(a).first, value(b).first)}"
            }
        }
    }

    /** 묻는 말의 장소 · 때로 여행을 걸러요. 장소는 저장한 목적지 이름에 들어 있는 말, 때는 이번 달 · 지난달 · 올해 · 작년 · N월이에요. */
    private fun scope(t: String): Pair<List<SavedTrip>, String> {
        var out = trips; val labels = mutableListOf<String>()
        val ym = { d: LocalDate -> "%04d.%02d".format(d.year, d.monthValue) }
        when {
            t.contains("이번달") -> { out = out.filter { it.month == ym(today) }; labels += "이번 달" }
            t.contains("지난달") -> { out = out.filter { it.month == ym(today.minusMonths(1)) }; labels += "지난달" }
            t.contains("올해") -> { out = out.filter { it.month.startsWith("${today.year}.") }; labels += "올해" }
            t.contains("작년") && !t.contains("작년오늘") -> { out = out.filter { it.month.startsWith("${today.year - 1}.") }; labels += "작년" }
            else -> Regex("(\\d{1,2})월").find(t)?.groupValues?.get(1)?.toIntOrNull()?.let { m -> out = out.filter { it.month.endsWith(".%02d".format(m)) }; labels += "${m}월" }
        }
        placeIn(t)?.let { p -> out = out.filter { it.destName?.contains(p) == true }; labels += "${p}에서" }
        return out to labels.joinToString(" ")
    }

    /** "제주에서 · 강릉에 · 경포대로" 처럼 장소 조사 앞의 말이 저장한 목적지 이름에 들어 있으면 그 장소. */
    private fun placeIn(t: String): String? {
        val names = trips.mapNotNull { it.destName }.distinct()
        val words = Regex("([가-힣A-Za-z0-9]{2,})(에서|에|으로|로|까지)").findAll(t).map { it.groupValues[1] }.toList()
        for (w in words) for (len in w.length downTo 2) { val tail = w.takeLast(len); if (names.any { it.replace(" ", "").contains(tail) }) return tail }
        names.firstOrNull { n -> n.length >= 2 && t.contains(n.replace(" ", "")) }?.let { return it }
        return names.flatMap { it.split(" ") }.filter { it.length >= 2 }.sortedByDescending { it.length }.firstOrNull { t.contains(it) }
    }

    companion object {
        val EXAMPLES = listOf("충전 몇 번 했어?", "내 일정 어디까지 왔어?", "지금 무슨 테마야?", "가장 멀리 간 곳은?", "도착하면 배터리 얼마야?")
    }
}
