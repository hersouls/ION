package app.roadmemory.car

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.roadmemory.car.data.AppPrefs
import app.roadmemory.car.data.AppPrefsStore
import app.roadmemory.car.data.CardVoice
import app.roadmemory.car.data.CarSnapshot
import app.roadmemory.car.data.CarSource
import app.roadmemory.car.data.DriverCabin
import app.roadmemory.car.data.EnvSignals
import app.roadmemory.car.data.UxLimit
import app.roadmemory.car.data.UxRules
import app.roadmemory.car.data.EnvState
import app.roadmemory.car.data.NavState
import app.roadmemory.car.data.NoCarSource
import app.roadmemory.car.data.PleosCarSource
import app.roadmemory.car.data.SavedTrip
import app.roadmemory.car.data.SourceKind
import app.roadmemory.car.data.ToneSettings
import app.roadmemory.car.data.ToneStore
import app.roadmemory.car.data.TrackPlayer
import app.roadmemory.car.data.Tracks
import app.roadmemory.car.data.TripBook
import app.roadmemory.car.data.TripStore
import app.roadmemory.car.data.Recording
import app.roadmemory.car.data.RecordingLogic
import app.roadmemory.car.data.RecordingStore
import app.roadmemory.car.data.Weather
import app.roadmemory.car.engine.CardContext
import app.roadmemory.car.engine.CardEngine
import app.roadmemory.car.engine.CardKind
import app.roadmemory.car.engine.ChargePace
import app.roadmemory.car.brain.Brevity
import app.roadmemory.car.brain.ChatContext
import app.roadmemory.car.brain.ContextNames
import app.roadmemory.car.brain.ContextResolver
import app.roadmemory.car.brain.Conversation
import app.roadmemory.car.brain.HeardLog
import app.roadmemory.car.brain.Korean
import app.roadmemory.car.studio.StudioLibrary
import app.roadmemory.car.engine.EnvEngine
import app.roadmemory.car.engine.GlanceCard
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.engine.ModeEngine
import app.roadmemory.car.engine.Taste
import app.roadmemory.car.engine.TasteEngine
import app.roadmemory.car.gleo.GleoBridge
import app.roadmemory.car.ion.IonAssistant
import app.roadmemory.car.ontology.Ontology
import app.roadmemory.car.ontology.OntologyAnswers
import app.roadmemory.car.pack.Fired
import app.roadmemory.car.pack.InstalledPack
import app.roadmemory.car.pack.MomentEngine
import app.roadmemory.car.pack.MomentInput
import app.roadmemory.car.pack.MomentNote
import app.roadmemory.car.pack.MomentText
import app.roadmemory.car.pack.MomentValues
import app.roadmemory.car.pack.PackInfo
import app.roadmemory.car.pack.PackPlayer
import app.roadmemory.car.pack.PackStore
import app.roadmemory.car.ui.RainTheme
import app.roadmemory.car.ui.Tone
import app.roadmemory.car.ui.Tones
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** 서 있을 때 ION 홈 오른쪽 칸에 열리는 화면들의 이름. null이면 ION 홈이에요. */
object Pane {
    const val TRIPS = "trips"
    const val CHARGE = "charge"
    const val FAMILY = "family"
    const val CHECK = "check"
    const val DATA = "data"
    const val SETTINGS = "settings"
    const val TONE = "tone"
    /** 시그니처 송 팩을 고르는 화면(설정에서 열어요). */
    const val PACK = "pack"
    /** 충전 중에도 ION 홈을 보겠다는 뜻(pane이 null이면 충전 화면이 자동으로 나와요). */
    const val HOME = "home"
    /** ION Studio와 바로 잇는 화면(설정에서 열어요). */
    const val LINK = "link"
    /** ION 홈의 서비스 칸에서 여는 서비스 화면(연결 상태). 시그니처 송은 [PACK]이 열려요. 위치 동기화는 [SVC_LOCATION] 자기 화면이 열리고, 시험 빌드의 가족 위치(우회 경로)는 [FAMILY] 아래 칸에도 있어요. */
    const val SVC_THEME = "svc_theme"
    const val SVC_ART = "svc_art"
    const val SVC_TRIP = "svc_trip"
    const val SVC_ALBUM = "svc_album"
    const val SVC_MUSIC = "svc_music"
    const val SVC_CALENDAR = "svc_calendar"
    const val SVC_LOCATION = "svc_location"
    val services = setOf(SVC_THEME, SVC_ART, SVC_TRIP, SVC_ALBUM, SVC_MUSIC, SVC_CALENDAR, SVC_LOCATION)
    val all = setOf(TRIPS, CHARGE, FAMILY, CHECK, DATA, SETTINGS, TONE, PACK, HOME, LINK) + services
}

private const val TAG = "RoadMemory"
/** 순간의 글이 화면에 머무는 시간 · 주행 중 목소리 토막의 최대 길이 · 카드 안내를 기다리는 최대 시간. */
private const val NOTE_MS = 8_000L
private const val DRIVING_CLIP_MS = 6_000L
private const val QUIET_WAIT_MS = 8_000L
/** 시그니처 송의 목소리가 먼저 말하지 않는 안전 카드. */
private val SAFETY_CARDS = setOf(CardKind.SEATBELT_CHECK, CardKind.REAR_ALERT)

/** 도착한 직후 보여 주는 이번 여행 요약. */
/**
 * 도착 화면이 보여 주는 이번 여행. 도착 화면은 저장하기 전의 이 값으로 그려서([preview]) 여행 기록 세부 화면과 같은 그림 · 견줌을 써요.
 * 목적지는 내비가 정한 것만(위치는 읽지 않아요), 출발 시각은 앱이 주행을 처음 본 때예요.
 */
data class TripSummary(
    val dateLabel: String, val km: Int, val people: Int, val tracks: Int, val charges: Int,
    val destName: String? = null, val lat: Double? = null, val lon: Double? = null,
    val startedAt: Long? = null, val arrivedAt: Long = 0L, val trackTitles: List<String> = emptyList(),
) {
    /** 저장될 기록과 같은 모양(번호 0). [arrivedAt]이 저장하는 기록의 도착 시각이라, 저장한 뒤에는 같은 [SavedTrip.at]으로 찾아요. */
    fun preview(): SavedTrip = SavedTrip(
        id = 0, at = arrivedAt, month = java.time.Instant.ofEpochMilli(arrivedAt).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy.MM")),
        destName = destName, lat = lat, lon = lon, km = km, people = people, tracks = trackTitles, charges = charges, startedAt = startedAt,
    )
}

data class UiState(
    /** null = 아직 연결 중, PLEOS = 차, NONE = 차에 연결되지 않은 기기 */
    val sourceKind: SourceKind? = null,
    val mode: Mode = Mode.PARKED,
    /** 주행 규제(CarUxRestrictions). 모르면 known=false. 주행 모드는 이 값이 먼저이고 그다음이 기어예요. */
    val ux: UxLimit = UxLimit(),
    val card: GlanceCard? = null,
    val car: CarSnapshot = CarSnapshot(),
    val nav: NavState = NavState(),
    val nowPlaying: String? = null,
    /** 충전 속도를 실제로 잰 뒤의 80%까지 남은 시간(분). 아직 못 쟀으면 null. */
    val chargeMinLeft: Int? = null,
    val trip: TripSummary? = null,
    /** 이 기기에 저장된 여행(최근 것이 앞). */
    val trips: List<SavedTrip> = emptyList(),
    /** 이번 주행을 시작하고 지난 분(모르면 null). */
    val driveMinutes: Int? = null,
    /** 서 있을 때 오른쪽 칸에 열려 있는 화면([Pane]). null = ION 홈. */
    val pane: String? = null,
    val toneSettings: ToneSettings = ToneSettings(),
    val appPrefs: AppPrefs = AppPrefs(),
    val weather: Weather? = null,
    val env: EnvState = EnvState(),
    val tone: Tone = Tone.STANDARD,
    /** 비가 와서 화면이 비 테마를 덮고 있는지(서 있을 때만 바뀌어요). 어떤 테마인지는 CarFrame이 내려받은 것에서 골라요. */
    val rainSkin: Boolean = false,
    val taste: Taste = Taste.NEUTRAL,
    /** 시그니처 송: 잠깐 보이는 순간(ION의 문장 + 목소리 자막), 이 기기에 있는 팩, 적용한 팩. */
    val moment: MomentNote? = null,
    val packs: List<PackInfo> = emptyList(),
    val pack: PackInfo? = null,
    /** 기록하는 중인 여행("기록하기"를 누른 뒤). paused면 앱이 다시 켜져서 이어서 할지 묻는 중이에요. */
    val recording: Recording? = null,
) {
    /** 날씨가 바라는 톤과 지금 톤이 달라요(주행 중이라 다음 정차 때 바뀌어요). */
    val tonePending: Boolean get() = Tones.pick(toneSettings, weather, taste) != tone
}

/** 차 신호 → 모드 → 카드 한 장 · ION 홈. 화면은 이 상태만 그려요. 가상 값은 없어요: 차가 주지 않는 값은 null로 두고 화면이 모른다고 말해요. */
@OptIn(ExperimentalCoroutinesApi::class)
class DriveController(app: Application) : AndroidViewModel(app) {
    /** 왼쪽 ION: 마이크로 들은 말과 Gleo가 부른 함수를 같은 함수 실행 길로 처리해요. */
    val ion = IonAssistant(
        viewModelScope, run = { action, params -> GleoBridge.execute(action, params) }, goToday = { openHome() },
        ask = { text -> (studioAnswers().answer(resolve(text)) ?: currentAnswers()?.answer(resolve(text)))?.let { Brevity.forDriving(it, ui.value.mode == Mode.DRIVING, ui.value.ux) } },
        act = { text -> chatAct(resolve(text))?.let { Brevity.forDriving(it, ui.value.mode == Mode.DRIVING, ui.value.ux) } },
        follow = { heard, reply ->
            val h = resolve(heard)
            HeardLog.add(getApplication(), heard, h, true, reply)
            chatCtx = ContextResolver.update(chatCtx, h, reply, contextNames())
            Conversation.followUps(h, reply, ui.value, StudioLibrary.state)
        },
        onReply = { heard, reply, understood -> if (!understood) HeardLog.add(getApplication(), heard, resolve(heard), false, reply) },
    )

    /** 설정 › ION이 들은 말. */
    fun setHeardLog(on: Boolean) = HeardLog.setOn(getApplication(), on)
    fun exportHeardLog() = HeardLog.export(getApplication())
    fun clearHeardLog() = HeardLog.clear(getApplication())

    /** 한 턴 기억(거기 · 그때 · 그거 · "그럼 지난달은?"). 저장하지 않아요. */
    private var chatCtx = ChatContext()
    private fun resolve(text: String) = ContextResolver.rewrite(text, chatCtx)
    private fun contextNames(): ContextNames {
        val ctx = getApplication<Application>()
        val cat = app.roadmemory.car.studio.StudioStore.catalog(ctx)
        return ContextNames(
            dests = ui.value.trips.mapNotNull { it.destName }.distinct(), tracks = Tracks.all.map { it.title }, themes = cat.themes.map { it.title },
            stops = cat.trips.flatMap { it.stops }.map { it.name }.distinct(), places = cat.calendar.map { it.place },
        )
    }

    /** APK 안의 데이터 ION-Link(assets/ontology.json). 파일이 깨졌으면 null(질문 기능과 데이터 화면만 빠져요). */
    val ontology: Ontology? by lazy { runCatching { Ontology.load(app) }.getOrNull() }
    private var answersFor: String? = null
    private var answers: OntologyAnswers? = null

    /** 적용한 팩이 있으면 그 팩을 더한 ION-Link로 답해요("이 팩은 언제 나와?"). 팩이 바뀌면 다시 만들어요. */
    /** ION 칸에서 말로 시키는 일(규칙 버전). 주행 중에는 화면을 여는 일을 하지 않아요. 한 일을 한 문장으로 돌려줘요. */
    private var recommendedTheme: String? = null
    private fun chatAct(text: String): String? {
        val ctx = getApplication<Application>()
        app.roadmemory.car.studio.StudioLibrary.ensure(ctx)
        val cat = app.roadmemory.car.studio.StudioStore.catalog(ctx)
        val lib = app.roadmemory.car.studio.StudioLibrary.state
        val cmd = app.roadmemory.car.brain.Conversation.command(text) ?: return null
        val parked = ui.value.mode != Mode.DRIVING
        return when (cmd) {
            app.roadmemory.car.brain.ChatCommand.NextStop -> {
                val run = app.roadmemory.car.ui.TripRun.current(lib) ?: return "가는 중인 일정이 없어요. 마이 트립에서 정류장을 누르면 시작해요"
                val t = cat.trips.firstOrNull { it.id == run.tripId } ?: return "가는 중인 일정을 찾지 못했어요"
                val i = run.stopIndex + 1
                val plan = app.roadmemory.car.ui.TripGuide.plan(t, i) ?: return "마지막 정류장이에요. 여행을 마쳤어요"
                val s = t.stops[i]
                if (!parked) return "서 있을 때 눌러 주세요. 달리는 중에는 길을 바꾸지 않아요"
                app.roadmemory.car.navi.NaviLink.guide(ctx, plan.dest, plan.through) { }
                app.roadmemory.car.studio.StudioLibrary.apply(ctx, Pane.SVC_TRIP, app.roadmemory.car.ui.TripRun.encode(app.roadmemory.car.ui.TripProgress(t.id, i, plan.end)))
                "내비에 ${app.roadmemory.car.brain.Korean.euro(s.name)} 길을 보냈어요 · ${i + 1}/${t.stops.size} 정류장"
            }
            app.roadmemory.car.brain.ChatCommand.PlayMusic -> {
                if (!lib.has("sync_${Pane.SVC_MUSIC}")) return "마이현대에서 곡을 아직 가져오지 않았어요. 디지털 뮤직에서 동기화해요"
                val pick = app.roadmemory.car.brain.TrackMood.pick(ui.value, java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY), java.time.LocalDate.now().monthValue)
                val list = pick?.let { listOf(it.first) } ?: Tracks.all
                playTracks(list)
                pick?.let { "${app.roadmemory.car.brain.Korean.eul(it.first.title)} 틀어요 · 지금 분위기(${it.second.joinToString(" · ")})에 맞춰 골랐어요" } ?: "내 곡 ${list.size}곡을 이어서 틀어요"
            }
            app.roadmemory.car.brain.ChatCommand.StopMusic -> { stopPlaying(); "곡을 멈췄어요" }
            app.roadmemory.car.brain.ChatCommand.NextTrack -> {
                if (!lib.has("sync_${Pane.SVC_MUSIC}")) return "마이현대에서 곡을 아직 가져오지 않았어요. 디지털 뮤직에서 동기화해요"
                val others = Tracks.all.filter { it.title != ui.value.nowPlaying }
                val next = others.randomOrNull() ?: return "다른 곡이 없어요"
                playTracks(listOf(next)); "${Korean.eul(next.title)} 틀어요"
            }
            is app.roadmemory.car.brain.ChatCommand.PlayTrack -> {
                if (!lib.has("sync_${Pane.SVC_MUSIC}")) return "마이현대에서 곡을 아직 가져오지 않았어요. 디지털 뮤직에서 동기화해요"
                val tr = Tracks.byTitle(cmd.title) ?: return "${Korean.eul(cmd.title)} 내 곡에서 찾지 못했어요"
                playTracks(listOf(tr)); "${Korean.eul(tr.title)} 틀어요"
            }
            is app.roadmemory.car.brain.ChatCommand.GuidePlace -> {
                val stop = cat.trips.flatMap { it.stops }.firstOrNull { it.name.replace(" ", "") == cmd.name }
                val ev = cat.calendar.firstOrNull { it.place.replace(" ", "") == cmd.name && it.lat != null && it.lng != null }
                val saved = ui.value.trips.firstOrNull { it.destName?.replace(" ", "") == cmd.name && it.lat != null && it.lon != null }
                val place = stop?.let { app.roadmemory.car.navi.Place(it.name, it.lat, it.lng) } ?: ev?.let { app.roadmemory.car.navi.Place(it.place, it.lat!!, it.lng!!) } ?: saved?.let { app.roadmemory.car.navi.Place(it.destName!!, it.lat!!, it.lon!!) }
                    ?: return "${cmd.name}의 위치를 몰라요. 마이 트립의 정류장 · 약속 장소 · 저장한 목적지만 안내할 수 있어요"
                if (!parked) return "서 있을 때 눌러 주세요. 달리는 중에는 길을 바꾸지 않아요"
                app.roadmemory.car.navi.NaviLink.guide(ctx, place) { }
                "내비에 ${Korean.euro(place.name)} 길을 보냈어요"
            }
            app.roadmemory.car.brain.ChatCommand.GuideAppointment -> {
                if (!lib.has("sync_${Pane.SVC_CALENDAR}")) return "가져온 일정이 없어요. 캘린더 동기화에서 가져와요"
                val now = java.time.LocalDateTime.now()
                val appt = app.roadmemory.car.brain.Agenda.nextLocated(app.roadmemory.car.brain.Agenda.build(cat.calendar, now.toLocalDate()), now) ?: return "안내할 장소를 아는 약속이 없어요"
                val e = appt.event
                if (!parked) return "서 있을 때 눌러 주세요. 달리는 중에는 길을 바꾸지 않아요"
                app.roadmemory.car.navi.NaviLink.guide(ctx, app.roadmemory.car.navi.Place(e.place, e.lat!!, e.lng!!)) { }
                "내비에 ${Korean.euro(e.place)} 길을 보냈어요 · ${e.title} · ${app.roadmemory.car.brain.Agenda.dateLabel(appt)}"
            }
            app.roadmemory.car.brain.ChatCommand.StartRecording -> {
                val cur = ui.value.recording
                if (cur != null) return if (cur.paused) "이어서 기록할지 묻는 중이에요. \"이어서 기록해 줘\"라고 해 보세요" else "이미 기록하는 중이에요"
                startRecording()
                val d = ui.value.nav.destinationName?.takeIf { ui.value.nav.routeActive && it.isNotBlank() }
                if (d != null) "${Korean.euro(d)} 가는 길을 기록하기 시작했어요" else "여행을 기록하기 시작했어요. 내비에 목적지가 없어서 처음 도착할 때 끝나요"
            }
            app.roadmemory.car.brain.ChatCommand.ResumeRecording -> {
                val r = ui.value.recording?.takeIf { it.paused } ?: return "이어서 할 기록이 없어요"
                resumeRecording(); "${r.destName ?: "지난"} 기록을 이어서 해요 · ${r.km}km부터"
            }
            app.roadmemory.car.brain.ChatCommand.EndRecording -> {
                if (ui.value.recording == null) return "기록하는 여행이 없어요"
                var said = ""; endRecording { said = it }; said
            }
            app.roadmemory.car.brain.ChatCommand.ThatDayMusic -> {
                val m = app.roadmemory.car.brain.Memories.thatDay(ui.value.trips, java.time.LocalDate.now()).firstOrNull { it.trip.tracks.isNotEmpty() } ?: return "그때 들은 곡 기록이 없어요"
                playTracks(m.trip.tracks.mapNotNull { Tracks.byTitle(it) })
                "${m.yearsAgo}년 전 그날 들은 곡을 틀어요"
            }
            app.roadmemory.car.brain.ChatCommand.RecommendTheme -> {
                app.roadmemory.car.brain.Quiet.ensure(ctx)
                val a = app.roadmemory.car.brain.Advisor.advise(ui.value, cat, lib, app.roadmemory.car.brain.QuietState(), java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)).firstOrNull { it.kind == "theme_pick" }
                    ?: return lib.appliedId(Pane.SVC_THEME)?.let { id -> "지금 ${app.roadmemory.car.brain.Korean.eul((cat.theme(id)?.title ?: "그 테마"))} 쓰고 있어요. 다른 걸 보려면 디스플레이 테마를 열어요" } ?: "지금 값으로는 고를 테마가 없어요"
                val title = a.title.removeSuffix(" 어때요?")
                recommendedTheme = cat.themes.firstOrNull { it.title == title }?.id
                "$title 어때요? ${a.reasons.filter { !it.startsWith("테마의 태그") }.joinToString(" · ")}"
            }
            app.roadmemory.car.brain.ChatCommand.ApplyRecommendedTheme -> {
                val id = recommendedTheme ?: return "먼저 \"테마 추천해 줘\"라고 물어 주세요"
                val t = cat.theme(id) ?: return "그 테마를 찾지 못했어요"
                if (!lib.has(id)) {
                    // ION Studio에서 받는 테마는 파일을 다 받은 뒤에 적용돼요
                    app.roadmemory.car.studio.StudioLibrary.download(ctx, id, t.files) { app.roadmemory.car.studio.StudioLibrary.apply(ctx, Pane.SVC_THEME, id) }
                    if (app.roadmemory.car.studio.StudioLibrary.state.has(id)) "${app.roadmemory.car.brain.Korean.eul(t.title)} 내려받아 적용했어요" else "${app.roadmemory.car.brain.Korean.eul(t.title)} 받고 있어요. 다 받으면 적용돼요"
                } else {
                    app.roadmemory.car.studio.StudioLibrary.apply(ctx, Pane.SVC_THEME, id)
                    "${app.roadmemory.car.brain.Korean.eul(t.title)} 적용했어요"
                }
            }
            is app.roadmemory.car.brain.ChatCommand.Open -> { if (!parked) return "서 있을 때 열 수 있어요"; openPane(cmd.pane); "${cmd.label} 화면을 열었어요" }
            is app.roadmemory.car.brain.ChatCommand.Slideshow -> {
                if (!app.roadmemory.car.studio.Slideshow.allowed(!parked)) return "서 있을 때만 볼 수 있어요. 달리는 중에는 사진을 틀지 않아요"
                if (!cmd.start) { app.roadmemory.car.studio.SlideshowControl.stop(); return "슬라이드쇼를 멈췄어요" }
                val hasAlbum = lib.has("sync_${Pane.SVC_ALBUM}") && cat.album.isNotEmpty()
                if (ui.value.mode == Mode.CHARGING) {
                    // 충전 중에는 충전 화면에서 틀어요(앨범을 안 가져왔어도 걸어 둔 아트만으로 틀 수 있어요)
                    if (!hasAlbum && lib.appliedId(Pane.SVC_ART) == null) return "앨범도 걸어 둔 아트도 아직 없어요. 디지털 앨범에서 동기화하거나 디지털 아트를 걸어 두면 슬라이드쇼를 볼 수 있어요"
                    openPane(Pane.CHARGE)
                    app.roadmemory.car.studio.SlideshowControl.start()
                    return "충전 화면에서 슬라이드쇼를 시작해요"
                }
                if (!hasAlbum) return "앨범을 아직 가져오지 않았어요. 디지털 앨범에서 동기화하면 슬라이드쇼를 볼 수 있어요"
                openPane(Pane.SVC_ALBUM)
                app.roadmemory.car.studio.SlideshowControl.start()
                "슬라이드쇼를 시작해요"
            }
        }
    }

    /** 기억에게 묻기(규칙 버전): 이 기기의 상품 · 일정 · 여행 기록으로 답해요. */
    private fun studioAnswers(): app.roadmemory.car.brain.StudioAnswers {
        val ctx = getApplication<Application>()
        app.roadmemory.car.studio.StudioLibrary.ensure(ctx)
        app.roadmemory.car.brain.ChargeLog.ensure(ctx)
        val u = ui.value
        return app.roadmemory.car.brain.StudioAnswers(app.roadmemory.car.studio.StudioStore.catalog(ctx), app.roadmemory.car.studio.StudioLibrary.state, u.trips, u.nowPlaying, app.roadmemory.car.brain.ChargeLog.history, nav = u.nav, weather = u.weather, chargeMinLeft = u.chargeMinLeft, car = u.car, charging = u.mode == Mode.CHARGING, currentPace = app.roadmemory.car.brain.ChargeLog.currentPace(u.car.soc, System.currentTimeMillis()))
    }

    private fun currentAnswers(): OntologyAnswers? {
        val o = ontology ?: return null
        val pack = installed.value.firstOrNull { it.id == activePackId.value }
        if (answers == null || answersFor != pack?.id) {
            answers = OntologyAnswers(if (pack == null) o else o.withPack(pack.id, pack.file.title, pack.file.speakerName, pack.file.voice, pack.file.moments.map { it.moment }))
            answersFor = pack?.id
        }
        return answers
    }

    private val source = MutableStateFlow<CarSource?>(null)
    private val handled = MutableStateFlow<Set<String>>(emptySet())
    private val nowPlaying = MutableStateFlow<String?>(null)
    private val tracksPlayed = MutableStateFlow(0)
    // 여행 기록하기: 시동을 꺼도 이 기기에 남아서, 앱이 다시 켜지면 이어서 할지 물어요(player 콜백이 쓰므로 먼저 선언해요)
    private val recStore = RecordingStore(app)
    private val recFlow = MutableStateFlow(recStore.load()?.let { Recording.afterRestart(it) })
    private var recSegActive = false
    /** 도착으로 닫힌 기록(4초 뒤 저장할 때 목적지 · 곡을 채우는 데 써요). */
    private var closedRec: Recording? = null
    /** 도착 요약의 거리에 더할, 앞 구간들의 거리(km). */
    private var summaryKmBase = 0
    /** 도착으로 닫힌 주행의 출발 시각(자동 기록의 출발 시각 · 4초 뒤 저장할 때 써요). */
    private var closedStartMs: Long? = null
    /** 곡이 바뀔 때마다 지금 곡을 화면에 알리고 들은 곡 수를 세요. */
    private val player = TrackPlayer(app) { t ->
        nowPlaying.value = t?.title
        if (t != null) {
            tracksPlayed.update { n -> n + 1 }; tripTracks += t.title
            recFlow.value?.takeIf { !it.paused }?.let { r -> r.withTrack(t.title).let { n -> if (n != r) { recFlow.value = n; recStore.save(n) } } }
        }
    }
    /** 이번 여행에서 앱이 틀어 준 곡의 제목(도착할 때 기록에 남겨요). */
    private val tripTracks: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    /** 가는 동안 마지막으로 본 경로(도착하면 경로가 사라지므로, 어디로 갔는지 기억해 둬요). */
    private var lastNav: NavState? = null
    /** 도착한 여행을 저장하려고 기다리는 동안 붙들고 있는 목적지와 곡(주행 거리가 마지막으로 고쳐질 때까지 몇 초 기다려요). */
    private var pendingDest: NavState? = null
    private var pendingTracks: List<String> = emptyList()
    private var saveJob: Job? = null
    private val tripStore = TripStore(app)
    private val tripsFlow = MutableStateFlow<List<SavedTrip>>(emptyList())
    private val maxPeople = MutableStateFlow(0)
    private val charges = MutableStateFlow(0)
    private val pane = MutableStateFlow<String?>(null)
    // 운전자가 설정에서 정한 화면 톤: 이 기기에만 남고, toneFlow보다 먼저 선언해야 해요(선언 순서)
    private val toneStore = ToneStore(app)
    private val toneSettings = MutableStateFlow(toneStore.load())
    private val appPrefsStore = AppPrefsStore(app)
    private val appPrefs = MutableStateFlow(appPrefsStore.load())
    private val cardVoice = CardVoice(app)
    // 시그니처 송: 이 기기에 있는 팩 · 적용한 팩 · 화면에 잠깐 보이는 순간
    private val packStore = PackStore(app)
    private val packPlayer = PackPlayer(app)
    private val installed = MutableStateFlow<List<InstalledPack>>(emptyList())
    private val activePackId = MutableStateFlow(appPrefsStore.packId())
    private val noteFlow = MutableStateFlow<MomentNote?>(null)
    private var noteJob: Job? = null
    private var soundJob: Job? = null
    private val chargePace = ChargePace()
    private var lastMode: Mode? = null
    private var tripActive = false
    private var tripStartMs: Long? = null
    private val tripFlow = MutableStateFlow<TripSummary?>(null)

    init {
        GleoBridge.controller = this
        // 이 기기에 저장된 여행(진짜 기록)을 읽어 와요. 인터넷 없이 기기 안에서만 읽어요.
        viewModelScope.launch(Dispatchers.IO) { loadTrips() }
        viewModelScope.launch {
            // 차(Android Automotive)이면 Pleos SDK 값을 쓰고, 아니면 값이 없는 기기로 보고 가상 값을 만들지 않아요.
            val isCar = getApplication<Application>().packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
            val pleos = if (!isCar) null else withContext(Dispatchers.IO) {
                withTimeoutOrNull(4_000) { PleosCarSource(getApplication()).also { it.tryStart() } }
            }
            source.value = pleos ?: NoCarSource()
            ion.attach(isCar, getApplication())   // 차이면 Pleos 음성 인식, 아니면 말하기 예시
        }
    }

    private fun loadTrips() {
        runCatching { val all = tripStore.all(); TripBook.saved = all; tripsFlow.value = all }
            .onFailure { android.util.Log.w("RoadMemory", "저장된 여행을 읽지 못했어요", it) }
    }

    private val carFlow = source.flatMapLatest { it?.car ?: flowOf(CarSnapshot()) }
    private val navFlow = source.flatMapLatest { it?.nav ?: flowOf(NavState()) }
    private val envSignals = source.flatMapLatest { it?.env ?: flowOf(EnvSignals()) }
    /** 주행 규제. 플랫폼이 내려 주는 값을 그대로 따라요(docs/PLEOS.md §7 주행 규제). */
    private val uxFlow = source.flatMapLatest { it?.ux ?: flowOf(UxLimit()) }

    /**
     * 신호가 바뀔 때 + 10초마다(비가 그친 뒤 유지 시간이 지나도록, 운행 시간이 흐르도록) 다시 셈해요.
     * 와이퍼를 세는 상태 기계라서 stateIn으로 딱 하나만 돌려요. 구독자마다 따로 돌리면 사본마다 "비"를 알아차리는 때가 달라요.
     */
    private val ticker = flow { while (true) { emit(System.currentTimeMillis()); delay(10_000) } }
    private val envFlow = combine(envSignals, ticker) { s, now -> s to now }
        .scan(EnvEngine.State() to EnvState()) { (st, _), (s, now) -> EnvEngine.step(st, s, now) }
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.Eagerly, EnvState())

    /** 차가 주는 와이퍼 · 전조등 신호에서 추정한 비 · 안개(날씨 공개 API는 없어요). 모르면 null이에요. */
    private val weatherFlow = envFlow.map { it.weather }.stateIn(viewModelScope, SharingStarted.Eagerly, null as Weather?)

    /** 운전자(운전석 구역)가 맞춰 둔 실내 온도에서 읽는 취향. 승객 구역의 설정은 읽지 않아요. */
    private val cabinSignals = source.flatMapLatest { it?.cabin ?: flowOf(DriverCabin()) }
    private val tasteFlow = cabinSignals.scan(Taste.NEUTRAL) { prev, c -> TasteEngine.step(prev, c) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Taste.NEUTRAL)

    /** 날씨(먼저)와 운전자의 취향이 바라는 톤은 서 있을 때만 적용해요. 주행 중에는 지금 톤을 붙들고 있다가 정차(P) · 충전 때 바꿔요. */
    private val toneFlow = combine(weatherFlow, tasteFlow, carFlow, toneSettings, uxFlow) { w, taste, car, settings, ux -> Tones.pick(settings, w, taste) to (ModeEngine.modeOf(car, ux) == Mode.DRIVING) }
        .scan(Tone.STANDARD) { current, (wanted, driving) -> Tones.next(current, wanted, driving) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Tone.STANDARD)

    /** 비 오면 비 테마로: 비 짐작 + 설정이 바라는 값은 서 있을 때만 적용해요(톤과 같은 규칙). 주행 중에는 지금 것을 붙들고 있다가 정차(P) · 충전 때 바꿔요. */
    private val rainSkinFlow = combine(weatherFlow, carFlow, toneSettings, uxFlow) { w, car, settings, ux -> RainTheme.wanted(settings, w) to (ModeEngine.modeOf(car, ux) == Mode.DRIVING) }
        .scan(false) { current, (wanted, driving) -> RainTheme.next(current, wanted, driving) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val ui: StateFlow<UiState> = combine(
        listOf(source, carFlow, navFlow, handled, nowPlaying, tripFlow, weatherFlow, envFlow, toneFlow, tasteFlow, toneSettings, appPrefs, pane, tripsFlow, ticker, noteFlow, installed, activePackId, recFlow, uxFlow, rainSkinFlow),
    ) { v ->
        val src = v[0] as CarSource?
        val car = v[1] as CarSnapshot
        val nav = v[2] as NavState
        @Suppress("UNCHECKED_CAST") val done = v[3] as Set<String>
        val now = v[14] as Long
        val ux = v[19] as UxLimit
        val mode = ModeEngine.modeOf(car, ux)
        track(mode, car, nav, now)
        @Suppress("UNCHECKED_CAST") val trips = v[13] as List<SavedTrip>
        val prefs = v[11] as AppPrefs
        val driveMin = tripStartMs?.takeIf { tripActive }?.let { ((now - it) / 60_000L).toInt().coerceAtLeast(0) }
        UiState(
            sourceKind = src?.kind,
            mode = mode,
            ux = ux,
            card = CardEngine.pick(mode, car, nav, done, CardContext(prefs = prefs, trips = trips, driveMinutes = driveMin, month = YearMonth.now()))
                ?.let { c -> if (ux.limitStringLength) c.copy(title = UxRules.clip(c.title, ux), sub = c.sub?.let { UxRules.clip(it, ux) }) else c },
            car = car,
            nav = nav,
            nowPlaying = v[4] as String?,
            chargeMinLeft = if (mode == Mode.CHARGING) chargePace.minutesLeft(car.soc) else null,
            trip = if (mode == Mode.PARKED) v[5] as TripSummary? else null,
            trips = trips,
            driveMinutes = driveMin,
            pane = v[12] as String?,
            toneSettings = v[10] as ToneSettings,
            appPrefs = prefs,
            weather = v[6] as Weather?,
            env = v[7] as EnvState,
            tone = v[8] as Tone,
            rainSkin = v[20] as Boolean,
            taste = v[9] as Taste,
            moment = v[15] as MomentNote?,
            packs = (v[16] as List<*>).map { PackInfo.of(it as InstalledPack) },
            pack = (v[16] as List<*>).firstOrNull { (it as InstalledPack).id == v[17] }?.let { PackInfo.of(it as InstalledPack) },
            recording = v[18] as Recording?,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    init {
        // 카드가 새로 뜨면 한 문장을 소리로도 읽어요(설정의 음성 안내가 켜져 있을 때만). 같은 카드는 한 번만.
        viewModelScope.launch {
            ui.map { s -> s.card?.takeIf { s.appPrefs.voiceGuide } }
                .distinctUntilChangedBy { it?.id }
                .collect { c -> c?.let { cardVoice.speak(it.title) } }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) { installed.value = packStore.scan() }
        // 내비가 알려 주는 도착이 오면 마이 트립의 진행을 옮겨요
        viewModelScope.launch { source.flatMapLatest { it?.arrivals ?: kotlinx.coroutines.flow.emptyFlow() }.collect { onNaviArrival(it) } }
        // 시그니처 송: 차의 값이 바뀌는 것을 보고 순간을 알아차려요(ION-Link의 mo.* 순간). 팩을 적용하지 않았으면 알아차리기만 하고 아무것도 보이거나 틀지 않아요.
        viewModelScope.launch {
            var st = MomentEngine.State()
            combine(carFlow, navFlow, envSignals, cabinSignals, ticker) { car, nav, env, cabin, now ->
                MomentInput(car, nav, env, cabin, tripStartMs?.takeIf { tripActive }?.let { ((now - it) / 60_000L).toInt().coerceAtLeast(0) }, now)
            }.collect { input ->
                val (next, fired) = MomentEngine.step(st, input)
                st = next
                if (fired.isNotEmpty()) onMoments(fired, input)
            }
        }
    }

    /** 알아차린 순간들 가운데 적용한 팩이 소리를 가진 가장 앞선 하나만 보여 주고 틀어요. 나머지는 건너뛰고 까닭을 로그에 남겨요. */
    private fun onMoments(fired: List<Fired>, input: MomentInput) {
        val pack = installed.value.firstOrNull { it.id == activePackId.value } ?: return
        val o = ontology
        val chosen = MomentEngine.choose(fired) { id -> pack.file.momentOf(id) != null && o?.offerable("mo.$id") == true }
        for (f in fired) if (f !== chosen) {
            val why = when {
                pack.file.momentOf(f.moment) == null -> "이 팩에 소리가 없어요"
                o?.offerable("mo.${f.moment}") != true -> "내놓을 수 없는 순간이에요"
                else -> "다른 순간이 먼저예요"
            }
            Log.i(TAG, "시그니처 순간 ${f.moment} (${f.mode.name.lowercase()}) 건너뜀: $why")
        }
        if (chosen != null) present(pack, chosen, input)
    }

    private fun present(pack: InstalledPack, f: Fired, input: MomentInput) {
        val m = pack.file.momentOf(f.moment) ?: return
        val values = MomentValues.of(input.car, input.nav, input.cabin, f.part, input.driveMinutes)
            .let { v -> if (f.destination != null) v.copy(destination = f.destination) else v }
        val note = MomentNote(
            f.moment, ontology?.nodes?.get("mo.${f.moment}")?.label.orEmpty(), f.mode,
            MomentText.fill(ontology?.momentInfo("mo.${f.moment}"), values), pack.file.speakerName, m.line, pack.file.title, pack.file.synthetic, System.currentTimeMillis(),
        )
        noteFlow.value = note
        noteJob?.cancel()
        noteJob = viewModelScope.launch { delay(NOTE_MS); if (noteFlow.value === note) noteFlow.value = null }
        val driving = ModeEngine.modeOf(input.car, ui.value.ux) == Mode.DRIVING
        soundJob?.cancel()
        soundJob = viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { packStore.clip(pack, m) }
            val result = when {
                file == null -> "소리=아니요(파일을 열지 못했어요)"
                !waitForQuiet() -> "소리=아니요(카드 안내가 끝나지 않았어요)"
                packPlayer.play(file, if (driving) DRIVING_CLIP_MS else null) -> "소리=예"
                else -> "소리=아니요(재생을 시작하지 못했어요)"
            }
            Log.i(TAG, "시그니처 순간 ${f.moment} (${f.mode.name.lowercase()}) 팩=${pack.id} 글=${if (note.info != null) "예" else "자막만"} $result")
        }
    }

    /** 카드 한 문장을 읽는 중이거나 안전 카드(안전벨트 · 뒷좌석)가 떠 있으면 끝날 때까지 기다려요(최대 8초). 끝나지 않으면 소리는 건너뛰고 글만 보여요. */
    private suspend fun waitForQuiet(): Boolean {
        val ok = withTimeoutOrNull(QUIET_WAIT_MS) {
            while (cardVoice.isSpeaking || ui.value.card?.kind in SAFETY_CARDS) delay(250)
            true
        }
        return ok == true
    }

    /** 팩을 적용하거나(id) 끄는(null) 일. 설정 화면과 디버그 수신기가 불러요. 이 기기에만 남아요. */
    fun applyPack(id: String?) {
        if (id != null && installed.value.none { it.id == id }) return
        activePackId.value = id
        appPrefsStore.savePackId(id)
        packPlayer.stop(); soundJob?.cancel(); noteJob?.cancel(); noteFlow.value = null
        Log.i(TAG, "시그니처 송 ${if (id == null) "끔" else "적용: $id"}")
    }

    /**
     * 시그니처 송 화면의 "들어 보기": 이 팩의 출발 소리(없으면 첫 소리) 한 토막을 틀고, 달릴 때 화면에 쓰이는 자막을 함께 보여 줘요.
     * 적용하지 않아도 들어 볼 수 있어요. 달리는 중에는 틀지 않아요.
     */
    fun previewPack(id: String) {
        if (ui.value.mode == Mode.DRIVING) return
        val pack = installed.value.firstOrNull { it.id == id } ?: return
        val m = pack.file.momentOf("start") ?: pack.file.moments.firstOrNull() ?: return
        val mode = MomentEngine.MODES[m.moment] ?: return
        val note = MomentNote(
            m.moment, ontology?.nodes?.get("mo.${m.moment}")?.label.orEmpty(), mode, null,
            pack.file.speakerName, m.line, pack.file.title, pack.file.synthetic, System.currentTimeMillis(),
        )
        noteFlow.value = note
        noteJob?.cancel()
        noteJob = viewModelScope.launch { delay(NOTE_MS); if (noteFlow.value === note) noteFlow.value = null }
        soundJob?.cancel()
        soundJob = viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { packStore.clip(pack, m) }
            val ok = file != null && packPlayer.play(file, null)
            Log.i(TAG, "시그니처 송 들어 보기 팩=$id 소리=${if (ok) "예" else "아니요"}")
        }
    }

    /**
     * 내비가 알려 준 도착(경유지 · 목적지)으로 마이 트립의 진행을 옮겨요. 가는 중인 일정이 없거나, 도착한 곳이 그 일정에서 아직 닿지 않은 정류장이 아니면
     * 건드리지 않아요(다른 길의 도착). 위치는 읽지 않고 내비가 준 이름 · 좌표만 견줘요.
     */
    private fun onNaviArrival(a: app.roadmemory.car.data.NaviArrival) {
        val ctx = getApplication<Application>()
        app.roadmemory.car.studio.StudioLibrary.ensure(ctx)
        val run = app.roadmemory.car.ui.TripRun.current(app.roadmemory.car.studio.StudioLibrary.state) ?: return
        val trip = app.roadmemory.car.studio.StudioStore.catalog(ctx).trips.firstOrNull { it.id == run.tripId } ?: return
        val dest = lastNav?.let { n -> if (n.destLat != null && n.destLon != null) app.roadmemory.car.navi.Place(n.destinationName.orEmpty(), n.destLat, n.destLon) else null }
        val next = app.roadmemory.car.ui.TripRun.onArrival(run, trip, a, dest)
        if (next == null) { Log.i(TAG, "마이 트립: 도착(${a.kind} ${a.name})은 이 일정의 다음 정류장이 아니에요"); return }
        app.roadmemory.car.studio.StudioLibrary.apply(ctx, Pane.SVC_TRIP, app.roadmemory.car.ui.TripRun.encode(next))
        Log.i(TAG, "마이 트립: ${trip.stops.getOrNull(next.stopIndex)?.name ?: next.stopIndex} 도착 (${next.stopIndex + 1}/${trip.stops.size})")
    }

    /** 디버그 빌드에서 내비의 도착 알림을 흉내 내요(에뮬레이터 내비는 도착을 보내지 않아요). */
    fun debugArrive(a: app.roadmemory.car.data.NaviArrival) { if (isDebuggable) onNaviArrival(a) }

    /** 디버그 빌드에서 순간 하나를 지금 값으로 바로 내요(콘솔로 바꾸기 어려운 순간을 시험해요). */
    fun debugFire(moment: String) {
        if (!isDebuggable) return
        val mode = MomentEngine.MODES[moment] ?: return
        val s = ui.value
        onMoments(listOf(Fired(moment, mode, destination = s.nav.destinationName)), MomentInput(s.car, s.nav, driveMinutes = s.driveMinutes, now = System.currentTimeMillis()))
    }

    /** 모드가 바뀔 때 이번 트립을 쌓고, 도착(기어 P, 경로 없음)하면 기록 한 건으로 닫아요. */
    private fun track(mode: Mode, car: CarSnapshot, nav: NavState, now: Long) {
        trackRecording(mode, car, nav)
        maxPeople.update { maxOf(it, car.occupantCount) }
        if (mode == Mode.CHARGING) {
            if (lastMode != Mode.CHARGING) chargePace.reset()
            chargePace.onSoc(now, car.soc)
        }
        if (mode == Mode.DRIVING) {
            if (pane.value != null) pane.value = null   // 달리는 동안은 오른쪽 칸 화면이 없어요
            if (!tripActive) {
                flushPendingSave(); pendingDest = null; pendingTracks = emptyList()
                tripActive = true; tripStartMs = now; tripFlow.value = null; summaryKmBase = 0
                charges.value = 0; tracksPlayed.value = 0; tripTracks.clear()
            }
        }
        if (nav.routeActive && (!nav.destinationName.isNullOrBlank() || nav.destLat != null)) lastNav = nav
        if (mode == Mode.CHARGING && lastMode != Mode.CHARGING) {
            charges.update { it + 1 }
            // 충전소 이력(하이엔드의 규칙 버전): 어디서 몇 %부터 시작했는지 남겨 두고, 끝나면 속도를 재요
            app.roadmemory.car.brain.ChargeLog.begin(getApplication(), nav.chargeStops.firstOrNull()?.name ?: lastNav?.destinationName, car.soc, now)
        }
        if (mode != Mode.CHARGING && lastMode == Mode.CHARGING) app.roadmemory.car.brain.ChargeLog.end(getApplication(), car.soc, now)
        if (mode == Mode.PARKED && tripActive && !nav.routeActive) {
            closedStartMs = tripStartMs; tripActive = false; tripStartMs = null
            // 기록하는 중이었으면 나눠 달린 거리 · 가장 많이 탄 사람 · 충전을 모두 합쳐서 한 건으로 닫아요(묻는 중이면 아직 이어 가는 중이 아니에요)
            val rec = recFlow.value?.takeIf { !it.paused }
            if (rec != null) { closedRec = rec; summaryKmBase = rec.kmDone; recFlow.value = null; recStore.save(null) }
            val dest = lastNav
            val titles = ((rec?.tracks ?: emptyList()) + tripTracks).distinct()
            val summary = TripSummary(
                dateLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd(E)", Locale.KOREAN)),
                km = Math.round(car.tripKm ?: 0f) + summaryKmBase,
                people = maxOf(maxPeople.value, rec?.people ?: 0),
                tracks = maxOf(tracksPlayed.value, titles.size),
                charges = maxOf(charges.value, rec?.charges ?: 0),
                destName = dest?.destinationName?.takeIf { it.isNotBlank() } ?: rec?.destName,
                lat = dest?.destLat ?: rec?.destLat, lon = dest?.destLon ?: rec?.destLon,
                startedAt = rec?.departedAt ?: closedStartMs, arrivedAt = now, trackTitles = titles,
            )
            appPrefsStore.addTrip(summary.km)
            scheduleSave()
            tripFlow.value = summary
            handled.value = emptySet()
            maxPeople.value = 0
        }
        if (mode == Mode.PARKED && !tripActive) {
            val shown = tripFlow.value
            val km = Math.round(car.tripKm ?: 0f) + summaryKmBase
            if (shown != null && km > shown.km) tripFlow.value = shown.copy(km = km)
        }
        lastMode = mode
    }

    fun onPrimary(card: GlanceCard) {
        handled.update { it + card.id }
        when (card.kind) {
            CardKind.ROAD_MEMORY -> {
                // 그때 이 길에서 들은 곡을 틀어요(오디오 포커스를 받아 재생해요)
                val titles = TripBook.tripsTo(ui.value.nav).flatMap { it.tracks }.distinct()
                playTracks(titles.mapNotNull { Tracks.byTitle(it) })
            }
            CardKind.REAR_SEAT -> playTracks(Tracks.all)
            CardKind.CHARGE_VIA, CardKind.SEATBELT_CHECK, CardKind.REST_SUGGEST, CardKind.COLD_NOTICE, CardKind.REAR_ALERT, CardKind.VEHICLE_CHECK -> Unit   // 알았다고만 해요(차는 제어하지 않아요)
        }
    }

    fun onSecondary(card: GlanceCard) { handled.update { it + card.id } }

    fun playTracks(tracks: List<Tracks.Track>) { player.play(tracks) }

    /** 디지털 뮤직 화면의 이전(-1) · 다음(+1). 트는 곡이 없으면 아무 일도 없어요. */
    fun skipTrack(step: Int) { if (step < 0) player.previous() else player.next() }
    fun stopPlaying() { player.stop() }

    /**
     * 도착한 여행을 이 기기에 남겨요. 도착 요약이 만들어진 뒤에도 주행 거리가 마지막으로 한 번 고쳐지므로, 4초 동안 값이 안정된 뒤에 써요.
     * 1km도 안 달린 오갈이는 남기지 않아요. 다음에 같은 곳으로 길을 정하면 "길의 기억"이 돼요.
     */
    private fun scheduleSave() {
        pendingDest = lastNav ?: pendingDest; pendingTracks = tripTracks.toList().distinct().ifEmpty { pendingTracks }; lastNav = null
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(4_000); writePending() }
    }

    /** 4초가 지나기 전에 다음 여행이 시작되면 기다리던 것을 바로 써요. */
    private fun flushPendingSave() {
        if (saveJob?.isActive == true) { saveJob?.cancel(); writePending() }
    }

    private fun writePending() {
        val s = tripFlow.value ?: return
        android.util.Log.i("RoadMemory", "도착: ${s.km}km, 사람 ${s.people}, 목적지 ${pendingDest?.destinationName}")
        if (s.km < 1) return
        val dest = pendingDest
        val rec = closedRec
        val trip = SavedTrip(
            id = 0, at = s.arrivedAt.takeIf { it > 0 } ?: System.currentTimeMillis(), month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM")),
            destName = dest?.destinationName?.takeIf { it.isNotBlank() } ?: rec?.destName, lat = dest?.destLat ?: rec?.destLat, lon = dest?.destLon ?: rec?.destLon,
            km = s.km, people = s.people, tracks = ((rec?.tracks ?: emptyList()) + pendingTracks).distinct(), charges = s.charges,
            startedAt = rec?.departedAt ?: closedStartMs,
        )
        pendingDest = null; pendingTracks = emptyList(); closedRec = null; closedStartMs = null
        viewModelScope.launch(Dispatchers.IO) { runCatching { tripStore.insert(trip); loadTrips() }.onFailure { android.util.Log.w("RoadMemory", "여행을 저장하지 못했어요", it) } }
    }

    // ── 여행 기록하기: 내비 목적지로 시작 → 달리며 쌓기 → 시동을 껐다 켜면 이어서 → 도착하면 한 건 ──

    /** 한 번 읽을 때마다 기록을 앞으로 보내요(바뀐 것만 이 기기에 남겨요). 차의 현재 위치는 읽지 않아요. */
    private fun trackRecording(mode: Mode, car: CarSnapshot, nav: NavState) {
        val r = recFlow.value ?: return
        val step = RecordingLogic.tick(r, lastMode, mode, car, nav, recSegActive, System.currentTimeMillis())
        recSegActive = step.segActive
        if (step.rec != r) { recFlow.value = step.rec; recStore.save(step.rec) }
    }

    /** "기록하기": 지금의 내비 목적지와 타고 있는 사람으로 시작해요. 이미 기록하는 중이면 그대로예요. */
    fun startRecording() {
        if (recFlow.value != null) return
        val s = ui.value
        val driving = s.mode == Mode.DRIVING
        val r = Recording.begin(System.currentTimeMillis(), s.nav, s.car.occupantCount, segKm = if (driving) Math.round(s.car.tripKm ?: 0f) else 0)
        recSegActive = driving
        recFlow.value = r; recStore.save(r)
    }

    /** 시동을 다시 켠 뒤 "이어서 기록"을 눌렀어요. */
    fun resumeRecording() {
        val r = recFlow.value?.takeIf { it.paused } ?: return
        val n = r.copy(paused = false, legs = r.legs + 1)
        recFlow.value = n; recStore.save(n)
    }

    /** "기록 끝내기" · "여기서 끝내기": 지금까지를 여행 기록으로 남겨요(1km도 안 달렸으면 남기지 않고, 그렇다고 알려요). */
    fun endRecording(onDone: (String) -> Unit = {}) {
        val r = recFlow.value ?: return
        recFlow.value = null; recStore.save(null)
        val trip = r.toTrip(System.currentTimeMillis(), LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM")))
        if (trip == null) { onDone("1km도 달리지 않아서 남기지 않았어요"); return }
        viewModelScope.launch(Dispatchers.IO) { runCatching { tripStore.insert(trip); loadTrips() }.onFailure { android.util.Log.w("RoadMemory", "여행을 저장하지 못했어요", it) } }
        onDone("${trip.destName ?: trip.month + " 여행"} ${trip.km}km를 여행 기록에 남겼어요")
    }

    /** 저장한 여행 한 건을 지워요(여행 기록 화면의 "지우기"). */
    fun deleteSavedTrip(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { tripStore.delete(id); loadTrips() }.onFailure { android.util.Log.w("RoadMemory", "여행 한 건을 지우지 못했어요", it) } }
    }

    /** 저장한 여행을 모두 지워요. */
    fun clearSavedTrips() {
        viewModelScope.launch(Dispatchers.IO) { runCatching { tripStore.clear(); TripBook.saved = emptyList(); tripsFlow.value = emptyList() }.onFailure { android.util.Log.w("RoadMemory", "저장한 여행을 지우지 못했어요", it) } }
    }

    // ── 서 있을 때의 오른쪽 칸 ──

    /** 오른쪽 칸에 화면을 열어요(null이면 ION 홈). 사람이 직접 고른 화면만 이 기기에 남겨요. 달리는 중에는 열지 않아요. */
    /** ION Studio에서 묶음을 받은 뒤: 이 기기의 시그니처 송을 다시 찾아요. */
    fun rescanPacks() { viewModelScope.launch(Dispatchers.IO) { installed.value = packStore.scan() } }

    fun openPane(id: String?) {
        if (ui.value.mode == Mode.DRIVING) return
        pane.value = id?.takeIf { it in Pane.all }
        // 시그니처 송 화면을 열 때마다 기기에 넣은 팩을 다시 찾아요(adb로 팩을 넣은 뒤 앱을 다시 켜지 않아도 돼요)
        if (pane.value == Pane.PACK) viewModelScope.launch(Dispatchers.IO) { installed.value = packStore.scan() }
        appPrefsStore.saveLastPane(pane.value ?: "menu")
    }

    /** ION 홈으로. 충전 중에는 "충전 화면 자동"이 켜져 있어도 홈을 볼 수 있게 HOME을 따로 둬요. */
    fun openHome() = openPane(if (ui.value.mode == Mode.CHARGING) Pane.HOME else null)

    /** 도착 요약을 닫아요(다음 여행이 시작될 때까지 다시 뜨지 않아요). */
    fun dismissTrip() { tripFlow.value = null }

    /** 앱을 켠 뒤 정차 중이면, 마지막으로 보던 화면을 다시 열어요(없으면 ION 홈). */
    fun restoreLastPane() {
        appPrefsStore.lastPane().takeIf { it in Pane.all }?.let { pane.value = it }
    }

    /** 운전자가 고른 화면 톤을 바로 적용하고(서 있을 때) 이 기기에 남겨요. */
    fun setToneSettings(s: ToneSettings) { toneSettings.value = s; toneStore.save(s) }

    /** 켜고 끄는 값을 바로 적용하고 이 기기에 남겨요. */
    fun setAppPrefs(p: AppPrefs) { appPrefs.value = p; appPrefsStore.save(p) }

    /** 디버그 빌드인지(개발용 도구를 보일지). */
    val isDebuggable: Boolean = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    override fun onCleared() {
        player.shutdown()
        packPlayer.stop()
        cardVoice.shutdown()
        if (GleoBridge.controller === this) GleoBridge.controller = null
        ion.release()
        (source.value as? PleosCarSource)?.stop()
        super.onCleared()
    }
}
