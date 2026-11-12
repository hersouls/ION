package app.roadmemory.car.ui

import app.roadmemory.car.Pane
import app.roadmemory.car.UiState
import app.roadmemory.car.family.FamilyLink
import app.roadmemory.car.studio.Catalog
import app.roadmemory.car.studio.LibraryState

/**
 * ION App이 제공하는 서비스 여덟 가지(기획서 ION Service · prd.moonwave.kr).
 * ION Studio에서 올라온 것과 마이현대에 등록한 것이 ION App에서 임베딩되고 ION-Link로 이어져 차 안의 서비스가 돼요.
 * 블루링크 스토어는 서비스가 아니라, ION Studio의 상품과 공유한 여행 일정이 오가는 길이에요. 기억과 설정도 서비스가 아니라 앱의 기능이에요.
 *
 * 이 앱은 차가 실제로 주는 값만 보여 줘요. 서비스 칸은 "지금 이어져 있는지"를 말할 뿐이고,
 * 이어지지 않은 서비스의 내용(테마 · 아트 · 여행 일정 · 앨범 · 곡 · 일정 · 가족 위치)을 지어내서 보여 주지 않아요.
 * 접수 시연에서 보이는 서비스의 내용은 앱에 넣어 둔 값이고, 화면에는 "시연"이라는 글자를 쓰지 않아요(오너 지시 2026-11-11)
 * (위치 동기화는 마이현대가 가족 위치를 동기화해 준다고 가정한 시연 값이에요).
 */
internal enum class ServiceOrigin(val label: String) {
    STUDIO("ION Studio"),
    MYHYUNDAI("마이현대"),
}

/** 단계가 어디에 있는지: 접수 시연의 앱(시연 값 포함) · 가족의 실제 위치를 읽는 시험 빌드 · 기획서의 제안. */
internal enum class StepWhere(val label: String) { DEMO("이 앱"), TEST("시험 빌드"), PROPOSAL("제안") }

/** 서비스 workflow의 한 단계(기획서 ION Service의 서비스별 workflow 표와 같아요). */
internal data class Step(val label: String, val where: StepWhere)

internal data class ServiceDef(
    /** 이 서비스를 여는 화면의 이름([Pane]). */
    val pane: String,
    val title: String,
    val origin: ServiceOrigin,
    /** 어디와 이어지는지(스토어를 거치는 서비스는 스토어까지). */
    val via: String,
    /** 차 안에서 하는 일. */
    val does: String,
    /** 이 일을 하는 앱 기능(ION App의 기능 표와 같은 이름). */
    val uses: String,
    val icon: MwIconData,
    /** 처음부터 끝까지 사용자가 지나가는 길. */
    val steps: List<Step>,
    /** 하이엔드(ION Brain · ION-Link)에서 ION이 더하는 일(제안). */
    val highEnd: String,
)

/**
 * 서비스 한 칸이 말하는 지금 상태. [line]은 화면 안의 문장, [tile]은 칸에 한 줄로 들어가는 짧은 말이에요.
 * [ready]가 false면 이어지지 않았거나 쓸 것이 아직 없다는 뜻이에요.
 */
internal data class ServiceStatus(val line: String, val ready: Boolean, val tile: String = line)

internal object Services {
    private val D = StepWhere.DEMO
    private val T = StepWhere.TEST
    private val P = StepWhere.PROPOSAL

    val all: List<ServiceDef> = listOf(
        ServiceDef(Pane.PACK, "시그니처 송", ServiceOrigin.STUDIO, "ION Studio(창작자가 올려요) · 스토어에서 사요",
            "산 목소리 묶음을 적용하면, 차의 순간마다 ION이 화면에 글을 쓰고 산 목소리를 들려줘요", "조회 · 검색, 적용, 차의 때 읽기", MwIcons.Signature,
            listOf(Step("스토어에서 골라 사요", P), Step("묶음을 내려받아요(앱에 들어 있어요)", D), Step("설정에서 하나를 적용해요", D), Step("순간마다 ION이 글과 목소리로 알려요", D), Step("끄거나 다른 것으로 바꿔요", D)),
            "ION이 자주 가는 길 · 시간 · 날씨에 맞는 묶음을 골라 권하고, 듣지 않는 순간은 조용해져요"),
        ServiceDef(Pane.SVC_THEME, "디스플레이 테마", ServiceOrigin.STUDIO, "ION Studio(창작자가 올려요) · 스토어에서 사요",
            "고른 테마를 낮과 밤 화면에 적용해요", "조회 · 검색, 적용", MwIcons.Theme,
            listOf(Step("스토어에서 골라요", D), Step("내려받아요", D), Step("적용해요", D), Step("낮과 밤에 맞춰 보여요", D), Step("바꾸거나 지워요", D)),
            "ION이 차종 · 화면 톤 · 취향을 읽어 어울리는 테마를 권해요(창작자 · 상품 종류 · 차종 · 쓰고 있는 구성을 ION-Link로 이어요)"),
        ServiceDef(Pane.SVC_ART, "디지털 아트", ServiceOrigin.STUDIO, "ION Studio(파트너 아티스트가 올려요) · 스토어에서 골라요",
            "고른 작품을 내려받아 ION App에서 써 봐요", "조회 · 검색, 적용", MwIcons.Gallery,
            listOf(Step("스토어에서 골라요", D), Step("내려받아요", D), Step("걸어요(ION 칸 · 충전 쉼터에 보여요)", D), Step("열어서 크게 봐요", D), Step("내리거나 지워요", D)),
            "내 구성과 지금 분위기에 맞는 작품을 ION이 골라 보여 줘요"),
        ServiceDef(Pane.SVC_TRIP, "마이 트립", ServiceOrigin.STUDIO, "ION Studio(API로 일정을 올려요) · 스토어(공유 · 내려받기)",
            "ION Studio에 올린 여행 일정이 앱에 나타나요. 목적지로 정해 내비를 켜고, 다녀온 뒤에 기록해요", "담기, 길 안내 · 기록", MwIcons.Trips,
            listOf(Step("ION Studio에 일정을 올려요", P), Step("앱에 받아요(내 일정에 담아요)", D), Step("정류장을 눌러 내비로 안내해요(경유지 둘까지 함께)", D), Step("도착하면 다음 정류장으로 이어 안내해요", D), Step("다녀온 뒤 이동거리 · 장소를 일정에 기록해요", P), Step("공유하기를 눌러야 스토어(무료)에 올라가요", P), Step("다른 사람은 내려받아 고쳐 써요", P)),
            "ION이 정류장 사이의 충전 · 휴식을 계산해 충전소를 끼워 넣어 일정을 짜 줘요(다음 여행 짜기)"),
        ServiceDef(Pane.SVC_ALBUM, "디지털 앨범", ServiceOrigin.MYHYUNDAI, "마이현대(내 사진 · 동영상을 등록해요)",
            "사람 · 장소 · 시간을 이어, 충전하는 사이에 그날의 사진을 보여 줘요", "담기, 조회 · 검색", MwIcons.Gallery,
            listOf(Step("마이현대에 사진 · 동영상을 등록해요", P), Step("앱으로 동기화해서 가져와요", D), Step("서 있을 때 크게 넘겨 봐요", D), Step("충전하는 사이 충전 화면에서 돌아가요", D), Step("ION-Link로 사람 · 장소 · 시간을 이어 그날의 사진을 골라요", P), Step("지워요", D)),
            "같은 길을 달릴 때 그날의 사진을 한 장 권하고, 사진마다 누가 함께였는지 이어서 말해요"),
        ServiceDef(Pane.SVC_MUSIC, "디지털 뮤직", ServiceOrigin.MYHYUNDAI, "마이현대(내 곡을 등록해요)",
            "분위기를 이어, 길과 시간에 어울리는 곡을 골라요. 등록한 곡을 듣기만 해요", "담기, 조회 · 검색", MwIcons.Music,
            listOf(Step("마이현대에 내 곡을 등록해요(내가 쓸 권리가 있는 곡만)", P), Step("앱으로 동기화해서 가져와요", D), Step("골라 틀거나 모두 이어서 틀어요 · 멈춰요", D), Step("ION-Link로 분위기를 이어 곡을 골라요", P), Step("지워요", D)),
            "분위기 · 시간 · 날씨에 어울리는 곡을 ION이 골라 틀어요"),
        ServiceDef(Pane.SVC_CALENDAR, "캘린더 동기화", ServiceOrigin.MYHYUNDAI, "마이현대(휴대폰 캘린더를 동기화해요)",
            "장소 · 시간 · 동승자를 이어, 약속에 늦지 않게 충전할 곳을 제안해요", "담기, 조회 · 검색", MwIcons.Calendar,
            listOf(Step("마이현대에서 캘린더 동기화를 켜요", P), Step("앱으로 일정을 가져와요", D), Step("다음 약속과 남은 시간 · 두 주 달력으로 봐요", D), Step("약속 장소로 내비 안내를 눌러요", D), Step("가는 중이면 도착을 약속 시각과 견줘요", D), Step("ION-Link로 장소 · 시간 · 동승자를 이어 충전할 곳을 끼워요", P), Step("지워요", D)),
            "약속의 장소 · 시간 · 동승자를 이어, 늦지 않게 충전할 곳을 제안해요"),
        ServiceDef(Pane.SVC_LOCATION, "위치 동기화", ServiceOrigin.MYHYUNDAI, "마이현대(가족이 위치를 보여 줘요)",
            "운전자가 누른 그때 한 번 가족 위치를 가져와, 가족이 있는 곳으로 경로 안내를 해요", "길 안내 · 기록", MwIcons.Location,
            listOf(
                Step("가족이 마이현대에서 위치 보여 주기를 켜요(사람마다 · 기간마다)", P),
                Step("운전자가 누를 때 한 번 가져와요(꺼 둔 가족은 읽지 않아요)", D),
                Step("별칭 · 자리 · 받은 때를 보여 주고, ION이 약속 장소 · 오래된 위치를 이어 말해요", D),
                Step("경로 안내를 누르면 내비가 그곳으로 안내를 시작해요", D),
                Step("가는 동안 내비가 준 남은 거리 · 도착 때 배터리를 이어 보여 줘요", D),
                Step("화면을 닫으면 가져온 위치를 지워요", D),
            ),
            "마이현대가 가족이 가는 길 · 도착 시각까지 주면, 만나는 곳까지 충전이 충분한지 먼저 알려요"),
    )

    fun byPane(pane: String?): ServiceDef? = all.firstOrNull { it.pane == pane }

    /** 칸을 눌렀을 때 여는 화면. 이미 동작하는 화면이 있으면 그걸 열어요(시그니처 송 → 팩 화면). 위치 동기화는 자기 서비스 화면을 열어요(시험 빌드도 같아요). */
    fun target(s: ServiceDef): String = s.pane

    /** 마이현대에서 동기화해 가져온 것의 표시(서비스 단위). */
    fun syncId(s: ServiceDef) = "sync_${s.pane}"

    /** 지금 어디까지 했는지: 단계 번호(0부터)는 "지금 할 일"이에요. 그 앞은 한 것이에요. */
    fun progress(s: ServiceDef, cat: Catalog?, lib: LibraryState): Int = when (s.pane) {
        Pane.SVC_THEME -> when {
            lib.appliedId(s.pane) != null -> 3
            cat != null && lib.countIn(cat.themes.map { it.id }) > 0 -> 2
            else -> 0
        }
        Pane.SVC_ART -> when {
            lib.appliedId(s.pane) != null -> 3
            cat != null && lib.countIn(cat.arts.map { it.id }) > 0 -> 2
            else -> 0
        }
        Pane.SVC_TRIP -> when {
            lib.appliedId(s.pane) != null -> 3
            cat != null && lib.countIn(cat.trips.map { it.id }) > 0 -> 2
            else -> 1
        }
        Pane.SVC_ALBUM, Pane.SVC_MUSIC, Pane.SVC_CALENDAR -> if (lib.has(syncId(s))) 2 else 1
        Pane.SVC_LOCATION -> 1   // 가족이 켠 것으로 보고(가정), 지금 할 일은 "누를 때 한 번 가져오기"예요. 가져온 위치는 화면을 닫으면 지워서 남기지 않아요
        else -> 0
    }

    /** 지금 상태: 이 기기에서 실제로 알 수 있는 것만 말해요. */
    fun status(ui: UiState, s: ServiceDef, cat: Catalog? = null, lib: LibraryState = LibraryState()): ServiceStatus = when (s.pane) {
        Pane.PACK -> ui.pack?.let { ServiceStatus("적용 중 · ${it.title}", true, "적용 중 · ${it.title}") }
            ?: if (ui.packs.isEmpty()) ServiceStatus("이 기기에 목소리 묶음이 없어요", false, "묶음이 없어요")
            else ServiceStatus("묶음 ${ui.packs.size}개 · 적용하지 않았어요", true, "묶음 ${ui.packs.size}개 · 적용 안 함")
        Pane.SVC_LOCATION -> {
            // 가는 중이면 그 사람을 말해요(내비 목적지 이름에 별칭이 들어 있어요)
            val sharing = cat?.family?.filter { it.sharing }.orEmpty()
            val guiding = if (ui.nav.routeActive) sharing.firstOrNull { ui.nav.destinationName?.contains(it.alias) == true } else null
            when {
                guiding != null -> ServiceStatus("${guiding.alias}에게 가는 중", true, "${guiding.alias}에게 가는 중")
                FamilyLink.source != null -> ServiceStatus("시험 빌드 · 허용한 가족의 실제 위치를 읽을 수 있어요", true, "시험 빌드 · 읽을 수 있어요")
                sharing.isNotEmpty() -> ServiceStatus("가족 ${sharing.size}명이 위치를 보여 줘요. 누르면 한 번 가져와요", true, "가족 ${sharing.size}명")
                else -> notConnected(s)
            }
        }
        Pane.SVC_THEME -> {
            val n = cat?.themes?.size ?: 0
            val applied = cat?.theme(lib.appliedId(s.pane))
            val got = cat?.let { lib.countIn(it.themes.map { t -> t.id }) } ?: 0
            when {
                applied != null -> ServiceStatus("적용 중 · ${applied.title}", true, "적용 중 · ${applied.title}")
                got > 0 -> ServiceStatus("내려받은 테마 ${got}개 · 적용하지 않았어요", true, "내려받음 ${got}개")
                n > 0 -> ServiceStatus("테마 ${n}개 · 내려받으면 적용할 수 있어요", true, "테마 ${n}개")
                else -> notConnected(s)
            }
        }
        Pane.SVC_ART -> {
            val n = cat?.arts?.size ?: 0
            val got = cat?.let { lib.countIn(it.arts.map { a -> a.id }) } ?: 0
            val hung = cat?.art(lib.appliedId(s.pane))
            when {
                hung != null -> ServiceStatus("걸어 둠 · ${hung.title}", true, "걸어 둠 · ${hung.title}")
                got > 0 -> ServiceStatus("내려받은 작품 ${got}점", true, "내려받음 ${got}점")
                n > 0 -> ServiceStatus("작품 ${n}점 · 내려받아 써 봐요", true, "아트 ${n}점")
                else -> notConnected(s)
            }
        }
        Pane.SVC_TRIP -> {
            val n = cat?.trips?.size ?: 0
            val got = cat?.let { lib.countIn(it.trips.map { t -> t.id }) } ?: 0
            val run = TripRun.current(lib)
            val runTrip = cat?.trips?.firstOrNull { it.id == run?.tripId }
            when {
                run != null && runTrip != null -> {
                    val word = if (!run.arrived) "가는 중" else if (run.stopIndex >= runTrip.stops.lastIndex) "여행을 마쳤어요" else "도착"
                    ServiceStatus("$word · ${runTrip.title} ${run.stopIndex + 1}/${runTrip.stops.size}", true, "$word ${run.stopIndex + 1}/${runTrip.stops.size} · ${runTrip.stops.getOrNull(run.stopIndex)?.name ?: ""}")
                }
                got > 0 -> ServiceStatus("내 일정에 담은 여행 ${got}개", true, "담은 일정 ${got}개")
                n > 0 -> ServiceStatus("일정 ${n}개 · 담아서 쓸 수 있어요", true, "일정 ${n}개")
                else -> notConnected(s)
            }
        }
        Pane.SVC_ALBUM -> synced(s, cat?.album?.size ?: 0, lib, "사진", "장")
        Pane.SVC_MUSIC -> ui.nowPlaying?.takeIf { lib.has(syncId(s)) }?.let { ServiceStatus("트는 중 · $it", true, "트는 중 · $it") } ?: synced(s, app.roadmemory.car.data.Tracks.all.size, lib, "내 곡", "곡")
        Pane.SVC_CALENDAR -> synced(s, cat?.calendar?.size ?: 0, lib, "일정", "개")
        else -> notConnected(s)
    }

    private fun notConnected(s: ServiceDef) = ServiceStatus("${s.origin.label}와 아직 이어지지 않았어요", false, "${s.origin.label} 연결 전")

    private fun synced(s: ServiceDef, n: Int, lib: LibraryState, noun: String, unit: String): ServiceStatus = when {
        n == 0 -> notConnected(s)
        lib.has(syncId(s)) -> ServiceStatus("마이현대에서 동기화해 가져왔어요 · $noun $n$unit", true, "$noun $n$unit 가져옴")
        else -> ServiceStatus("동기화 전 · $noun $n$unit", true, "동기화 전 · $noun $n$unit")
    }
}
