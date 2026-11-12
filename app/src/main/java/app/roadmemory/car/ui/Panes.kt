package app.roadmemory.car.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import app.roadmemory.car.data.DemoUser
import app.roadmemory.car.UiState
import app.roadmemory.car.data.CarPart
import app.roadmemory.car.data.CheckupReading
import app.roadmemory.car.data.SavedTrip
import app.roadmemory.car.data.SeatPos
import app.roadmemory.car.data.TripBook
import app.roadmemory.car.engine.CardEngine
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.ontology.Ontology
import app.roadmemory.car.ontology.Role as NeedRole
import app.roadmemory.car.ontology.Verdict

/**
 * 서비스 화면의 틀: 위에는 "ION 홈" 단추(와 화면의 단추)만 있고, 아래는 스크롤돼요.
 * 서비스 이름과 테마 엠블럼은 위 줄에 그리지 않아요(오너 2026-11-11: 서비스명 · 아이콘 삭제). [title]은 호출하는 쪽이 화면을 가리키는 이름이에요.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun PaneFrame(title: String, onHome: () -> Unit, actions: @Composable (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val viewport = remember { mutableStateOf(Rect.Zero) }
    Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            actions?.invoke()
            if (actions != null) Spacer(Modifier.width(12.dp))
            Box(
                Modifier.height(IviSize.buttonSmall.dp)
                    .iviButton(RoundedCornerShape(IviSize.buttonSmall.dp / 2), p.buttonBasic, p.buttonBasicPressed, onHome)
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center,
            ) { Text("ION 홈", color = p.textPrimary, style = Ivi.bodySmallStrong) }
        }
        Spacer(Modifier.height(20.dp))
        CompositionLocalProvider(LocalPaneViewport provides viewport) {
            Column(
                Modifier.weight(1f).fillMaxWidth()
                    .onGloballyPositioned { c -> Rect(c.positionInWindow(), c.size.toSize()).let { if (it != viewport.value) viewport.value = it } }
                    .verticalScroll(rememberScrollState()),
                content = content,
            )
        }
    }
}

/** 스크롤 칸(서비스 화면의 아래쪽)이 창 안에서 차지하는 자리. 칸 끝에서 잘린 줄을 가려내는 데 써요. */
private val LocalPaneViewport = staticCompositionLocalOf<State<Rect>?> { null }

/** 칸 끝에 이만큼도 안 보이는 줄은 글 한 줄이 읽히지 않아요(줄 높이 18px + 위아래 여백). */
private val MIN_VISIBLE = 48.dp

/**
 * 스크롤 칸의 위나 아래 끝에서 [MIN_VISIBLE]도 안 보이게 잘린 줄은 스크롤해서 들어올 때까지 읽히는 글로 내놓지 않아요.
 * 잘려서 윗단 몇 px만 보이는 카드의 글이 "줄 높이 5px 글자"로 읽히는 걸 막아요(눈에 보이는 모습은 그대로예요: 다음 줄이 이어진다는 단서는 남아요).
 * 칸 안에 충분히 보이는 줄 · 칸보다 큰 줄은 그대로 읽혀요. 눌러서 여는 일(터치)은 건드리지 않아요. 줄의 바깥 여백 안쪽(padding 뒤)에 붙여요.
 */
@Composable
private fun Modifier.quietWhenCut(): Modifier {
    val viewport = LocalPaneViewport.current
    val minVisible = with(LocalDensity.current) { MIN_VISIBLE.toPx() }
    var box by remember { mutableStateOf(Rect.Zero) }
    val cut by remember(viewport, minVisible) {
        derivedStateOf {
            val v = viewport?.value ?: Rect.Zero
            if (v.isEmpty || box.isEmpty) {
                false
            } else {
                val clipped = box.top < v.top - 1f || box.bottom > v.bottom + 1f
                clipped && (minOf(box.bottom, v.bottom) - maxOf(box.top, v.top)) < minVisible
            }
        }
    }
    return onGloballyPositioned { c -> Rect(c.positionInWindow(), c.size.toSize()).let { if (it != box) box = it } }
        .then(if (cut) Modifier.clearAndSetSemantics {} else Modifier)
}

@Composable
internal fun InfoRow(label: String, value: String, strong: Boolean = false) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = p.textTertiary, style = Ivi.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, color = p.textPrimary, style = if (strong) Ivi.bodyLargeStrong else Ivi.bodyMedium)
    }
}

@Composable
internal fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(modifier.fillMaxWidth().padding(bottom = 16.dp).background(p.backgroundPrimary, RoundedCornerShape(28.dp)).padding(24.dp), content = content)
}

// ───────── 충전 ─────────

/**
 * 배터리와 충전. 충전 속도는 앱이 배터리 %가 바뀐 때를 재서 어림하고(가정한 속도를 쓰지 않아요), 충분히 재기 전에는 그렇다고 말해요.
 */
@Composable
internal fun ChargePane(ui: UiState, onHome: () -> Unit, onPlay: (List<app.roadmemory.car.data.Tracks.Track>) -> Unit = {}, onStop: () -> Unit = {}) {
    val p = LocalPalette.current
    val c = ui.car
    PaneFrame("충전", onHome) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.soc != null) RingArt(c.soc, if (ui.mode == Mode.CHARGING) 80 else null, Modifier.size(150.dp)) else Box(Modifier.size(150.dp))
                Spacer(Modifier.width(24.dp))
                Column {
                    Text(c.soc?.let { "$it%" } ?: NONE, color = p.textPrimary, style = Ivi.headlineSmall)
                    Text(
                        when {
                            ui.mode == Mode.CHARGING -> ui.chargeMinLeft?.let { "80%까지 약 ${it}분 남았어요" } ?: "충전 속도를 재는 중이에요"
                            c.plugConnected == true -> "플러그가 꽂혀 있어요"
                            else -> "충전하고 있지 않아요"
                        },
                        color = p.textSecondary, style = Ivi.bodyLarge,
                    )
                }
            }
        }
        Card {
            InfoRow("갈 수 있는 거리", c.rangeKm?.takeIf { it > 0f }?.let { "${Math.round(it)}km" } ?: NONE)
            InfoRow("충전구", c.chargePortOpen?.let { if (it) "열려 있어요" else "닫혀 있어요" } ?: NONE)
            InfoRow("충전 플러그", c.plugConnected?.let { if (it) "꽂혀 있어요" else "꽂혀 있지 않아요" } ?: NONE)
            if (ui.nav.routeActive) {
                InfoRow("목적지", ui.nav.destinationName ?: NONE)
                InfoRow("도착하면 배터리", ui.nav.estimatedSocAtDest?.let { "$it%" } ?: NONE, strong = true)
                ui.nav.chargeStops.forEach { InfoRow("내비가 넣은 충전소", it.name + (if (it.startSoc != null && it.endSoc != null) " · ${it.startSoc}% → ${it.endSoc}%" else "")) }
            }
        }
        // 충전하는 사이 슬라이드쇼(ui/ChargeSlideshow.kt): 가져온 앨범 · 걸어 둔 아트가 돌고, 전체 창으로 크게 봐요
        ChargeSlideshowCard(ui, onPlay, onStop)
        ChargeHistorySection(ui)
        NoteLine("충전 남은 시간은 차가 알려 주지 않아서, 배터리가 오르는 속도를 직접 재서 어림해요. 충전소는 내비가 정하고 ION은 경로를 바꾸지 않아요.")
    }
}

/** 충전소 이력으로 고르기(규칙 버전): 이 기기에서 잰 충전 속도와, 내비가 넣은 충전소가 지난번에 어땠는지. 잰 게 없으면 그렇다고 말해요. */
@Composable
private fun ChargeHistorySection(ui: UiState) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    app.roadmemory.car.brain.ChargeLog.ensure(ctx)
    val h = app.roadmemory.car.brain.ChargeLog.history
    val p = LocalPalette.current
    SectionTitle("충전 기록 · 이 기기에서 잰 것")
    Card {
        if (h.measured.isEmpty()) {
            Text("아직 잰 충전이 없어요. 충전이 끝날 때마다 어디서 분당 몇 %가 올랐는지 남겨요.", color = p.textSecondary, style = Ivi.bodyLarge)
        } else {
            InfoRow("잰 충전", "${h.measured.size}번")
            h.usualPace?.let { InfoRow("평소 속도", "분당 ${"%.1f".format(it)}%", strong = true) } ?: InfoRow("평소 속도", "세 번 이상 재면 말해요")
            ui.nav.chargeStops.forEach { s -> h.line(s.name)?.let { InfoRow(s.name, it) } }
            h.measured.takeLast(3).reversed().forEach { s -> InfoRow(s.place, "${s.startSoc}% → ${s.endSoc}% · ${s.minutes}분 · 분당 ${"%.1f".format(s.pctPerMin!!)}%") }
        }
    }
}

// ───────── 가족 · 자리 ─────────

@Composable
internal fun FamilyPane(ui: UiState, onHome: () -> Unit) {
    val p = LocalPalette.current
    val c = ui.car
    PaneFrame("가족 · 자리", onHome) {
        if (c.seats.isEmpty()) {
            Card { EmptyNote(MwIcons.Family, "자리를 아직 읽지 못했어요", "차가 자리 정보를 주면 누가 어디에 앉았는지 여기에 보여요.") }
        } else {
            // 자리 칸이 이미 한 겹의 상자라서 카드로 또 싸지 않아요(상자 안에 상자를 두지 않아요)
            SeatMap(c, Modifier.fillMaxWidth(), checkBelts = true)
            Spacer(Modifier.height(24.dp))
            Text(DemoUser.riders(c.occupantCount, c.occupied(SeatPos.DRIVER)), color = p.textPrimary, style = Ivi.titleSmall)
            // 평소와 다른 자리만 말해요(앉아 있는데 벨트가 안 채워진 자리)
            SeatPos.entries.filter { c.occupied(it) && c.seats[it]?.belted == false }.forEach { pos ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StateDot(alert = true)
                    Text("${pos.label} 벨트를 확인해 주세요", color = p.textPrimary, style = Ivi.bodyLarge)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        NoteLine("운전석 문이 열렸는데 뒷좌석에 누가 있으면 ION이 알려요. 차 안 카드와 소리로만 알려요(폰 알림은 마이현대가 있어야 해요).")
        FamilyLocations(routeActive = ui.nav.routeActive)
    }
}

// ───────── 점검 ─────────

@Composable
internal fun CheckPane(ui: UiState, ontology: Ontology?, onHome: () -> Unit) {
    val p = LocalPalette.current
    val c = ui.car
    PaneFrame("점검", onHome) {
        val known = CarPart.entries.filter { c.open.containsKey(it) }
        val opened = known.filter { c.isOpen(it) }
        val unread = CarPart.entries.filter { !c.open.containsKey(it) }
        // 왼쪽: 위에서 본 차(열린 문은 바깥으로 벌어진 선) · 오른쪽: 지금 어떤지 한마디
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CarTopArt(c, Modifier.width(300.dp).height(340.dp))
            Column(Modifier.weight(1f).height(340.dp), verticalArrangement = Arrangement.Center) {
                when {
                    known.isEmpty() -> Text("문 · 트렁크 · 후드를 읽는 중이에요", color = p.textSecondary, style = Ivi.bodyLarge)
                    opened.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        MwIcon(MwIcons.Ok, size = 40.dp)
                        Text(if (unread.isEmpty()) "모두 닫혀 있어요" else "읽은 곳은 모두 닫혀 있어요", color = p.textPrimary, style = Ivi.titleMedium)
                    }
                    // 평소와 다른 곳만 말해요
                    else -> opened.forEach { part ->
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            StateDot(alert = true)
                            Text("${app.roadmemory.car.brain.Korean.iga(part.label)} 열려 있어요", color = p.textPrimary, style = Ivi.titleMedium)
                        }
                    }
                }
                // 차가 알려 준 켜진 경고는 아래 목록까지 내려가지 않아도 보여요
                CheckupReading.warnings(c).orEmpty().forEach { w ->
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        StateDot(alert = true)
                        Text("경고 · $w", color = p.textPrimary, style = Ivi.bodyLargeStrong)
                    }
                }
                if (known.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("확인한 곳: ${known.joinToString(" · ") { it.label }}", color = p.textTertiary, style = Ivi.bodySmall)
                }
                if (unread.isNotEmpty() && known.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("이 차에서 읽지 못한 곳: ${unread.joinToString(" · ") { it.label }}", color = p.textTertiary, style = Ivi.bodySmall)
                }
            }
        }
        // 아래쪽은 서비스센터가 남긴 기록(지어낸 값)이라 차가 직접 준 값과 나눠서 보여요
        CheckupSections(ui)
    }
}

// ───────── 여행 기록 ─────────

/** 이 기기에 저장된 여행. 도착할 때 쌓이고(시연용 가상 여행이 처음에 들어 있어요), 같은 곳으로 길을 정하면 ION이 "이 길, N 전에 …"로 알려 줘요. 위에서 "기록하기"로 시작할 수 있어요. */
@Composable
internal fun TripsPane(
    ui: UiState, onHome: () -> Unit, onPlay: () -> Unit, onClear: () -> Unit, rec: RecordingActions = RecordingActions(), onDelete: (Long) -> Unit = {},
    onPlayTracks: (List<app.roadmemory.car.data.Tracks.Track>) -> Unit = {},
) {
    val p = LocalPalette.current
    var confirm by remember { mutableStateOf(false) }
    // 줄을 누르면 그 여행의 세부 화면이 열려요(위의 "목록"으로 돌아와요)
    var openId by remember { mutableStateOf<Long?>(null) }
    val open = openId?.let { id -> ui.trips.firstOrNull { it.id == id } }
    val actions: (@Composable () -> Unit)? = when {
        open != null -> ({ PillButton("목록", { openId = null }) })
        ui.trips.isEmpty() -> null
        else -> ({
        Box(
            Modifier.height(IviSize.buttonSmall.dp)
                .iviButton(RoundedCornerShape(IviSize.buttonSmall.dp / 2), p.buttonBasic, p.buttonBasicPressed, { if (confirm) { onClear(); confirm = false } else confirm = true })
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) { Text(if (confirm) "한 번 더 누르면 지워요" else "모두 지우기", color = p.textPrimary, style = Ivi.bodySmallStrong) }
        })
    }
    PaneFrame("여행 기록", onHome, actions = actions) {
        if (open != null) {
            TripDetail(open, ui.trips, onPlay = onPlayTracks, onDelete = { id -> openId = null; onDelete(id) })
            return@PaneFrame
        }
        RecordingCard(ui, rec)
        if (ui.trips.isEmpty()) {
            Card {
                EmptyNote(MwIcons.Trips, "여행 기록이 없어요", "출발해서 목적지에 도착하면 ION이 이번 여행을 이 기기에 기록해요. 다음에 같은 곳으로 가면 그 기록을 알려 줘요.")
            }
        } else {
            // 되짚기: 저장한 여행을 세어서 한 줄로(센 값만, 지어내지 않아요)
            Card {
                Text("되짚기", color = p.textTertiary, style = Ivi.labelMedium)
                Text("여행 ${ui.trips.size}건 · 합쳐서 ${ui.trips.sumOf { it.km }}km · 충전 ${ui.trips.sumOf { it.charges }}번", color = p.textPrimary, style = Ivi.bodyLargeStrong)
                ui.trips.maxByOrNull { it.km }?.let { Text("가장 길었던 여행 · ${it.destName ?: it.month} ${it.km}km", color = p.textSecondary, style = Ivi.bodyMedium) }
            }
            // 줄마다 번호로 묶어요: 지운 줄의 "한 번 더 누르면 지워요"가 아래 줄로 넘어가면 다음 기록이 실수로 지워져요
            ui.trips.forEach { t -> androidx.compose.runtime.key(t.id) { TripRow(t, onDelete, onOpen = { openId = t.id }) } }
            Spacer(Modifier.height(4.dp))
            BigButton("ION 곡 틀기", false, onPlay, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TripRow(t: SavedTrip, onDelete: (Long) -> Unit, onOpen: () -> Unit) {
    val p = LocalPalette.current
    var confirm by remember { mutableStateOf(false) }
    Card(Modifier.quietWhenCut().clickable(onClickLabel = "세부 보기", role = androidx.compose.ui.semantics.Role.Button, onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            MwIcon(MwIcons.Trips, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(t.destName ?: "${t.month} 여행", color = p.textPrimary, style = Ivi.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(t.month, "${t.km}km", TripBook.peopleWord(t.people).takeIf { it.isNotEmpty() }?.let { "$it 탔어요" }, if (t.charges > 0) "충전 ${t.charges}번" else null, if (t.tracks.isNotEmpty()) "곡 ${t.tracks.size}개" else null).joinToString(" · "),
                    color = p.textSecondary, style = Ivi.bodyMedium,
                )
            }
            // 한 건씩 지울 수 있어요(두 번 눌러야 지워요). 지워도 다른 기록은 그대로예요
            PillButton(if (confirm) "한 번 더 누르면 지워요" else "지우기", { if (confirm) onDelete(t.id) else confirm = true })
            Text("›", color = p.textTertiary, style = Ivi.titleLarge)   // 누르면 세부 화면이 열려요
        }
    }
}

// ───────── 서비스 ─────────

/**
 * ION 홈의 서비스 칸에서 여는 화면. 이 서비스가 무엇이고 어디와 이어지는지, 지금 이어져 있는지를 말해요.
 * 이어지지 않은 서비스의 내용은 지어내지 않아요: 접수 시연의 앱에는 그 연결이 없고, 기획서의 제안이에요.
 */
@Composable
internal fun ServicePane(ui: UiState, def: ServiceDef, onHome: () -> Unit, onPlay: (List<app.roadmemory.car.data.Tracks.Track>) -> Unit, onStop: () -> Unit, onSkip: (Int) -> Unit = {}) {
    when (def.pane) {
        app.roadmemory.car.Pane.SVC_THEME -> ThemeWorkflowPane(ui, def, onHome)
        app.roadmemory.car.Pane.SVC_ART -> ArtWorkflowPane(ui, def, onHome)
        app.roadmemory.car.Pane.SVC_TRIP -> TripWorkflowPane(ui, def, onHome)
        app.roadmemory.car.Pane.SVC_ALBUM -> AlbumWorkflowPane(ui, def, onHome)
        app.roadmemory.car.Pane.SVC_MUSIC -> MusicWorkflowPane(ui, def, onHome, onPlay, onStop, onSkip)
        app.roadmemory.car.Pane.SVC_CALENDAR -> CalendarWorkflowPane(ui, def, onHome)
        app.roadmemory.car.Pane.SVC_LOCATION -> FamilyServicePane(ui, def, onHome)
        else -> ServiceInfoPane(ui, def, onHome)
    }
}

/** 서비스 화면이 아직 없는 서비스의 바탕 화면: 어디와 이어지는지와 지금 상태만 말해요(지금 여덟 서비스는 모두 자기 화면이 있어요). */
@Composable
private fun ServiceInfoPane(ui: UiState, def: ServiceDef, onHome: () -> Unit) {
    val p = LocalPalette.current
    val st = Services.status(ui, def)
    PaneFrame(def.title, onHome) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                MwIcon(def.icon, size = 44.dp)
                Text(def.does, color = p.textPrimary, style = Ivi.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
        Card {
            ServiceFact("이어지는 곳", def.via)
            ServiceFact("지금", st.line, strong = true)
            ServiceFact("쓰는 앱 기능", def.uses)
        }
        NoteLine(
            if (st.ready) "이 기기에서 실제로 알 수 있는 상태만 보여 줘요."
            else "이 앱에는 아직 이 연결이 없어요. 연결되면 여기에서 쓸 수 있어요.",
        )
    }
}

@Composable
private fun ServiceFact(label: String, value: String, strong: Boolean = false) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, color = p.textTertiary, style = Ivi.labelMedium)
        Text(value, color = p.textPrimary, style = if (strong) Ivi.bodyLargeStrong else Ivi.bodyMedium)
    }
}

// ───────── ION이 닿는 데이터 ─────────

/**
 * APK 안의 ION-Link(assets/ontology.json)를 앱이 직접 계산한 결과를 보여 줘요: ION이 하는 일 30가지가 이 차에서 닿는 데이터로 되는지(● ◐ ○)와 그 까닭.
 * 일을 누르면 그 일이 쓰는 데이터와 지금 상태가 펼쳐져요.
 */
@Composable
internal fun DataPane(ontology: Ontology?, onHome: () -> Unit, pack: app.roadmemory.car.pack.PackInfo? = null) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf<String?>(null) }
    PaneFrame("ION이 닿는 데이터", onHome) {
        if (ontology == null) {
            Card { Text("데이터 정의 파일을 읽지 못했어요", color = p.textSecondary, style = Ivi.bodyLarge) }
            return@PaneFrame
        }
        val c = ontology.counts()
        Card(Modifier.quietWhenCut()) {
            Text("ION이 하는 일 ${ontology.functions.size}가지 중", color = p.textTertiary, style = Ivi.labelMedium)
            Spacer(Modifier.height(10.dp))
            listOf(
                Verdict.CAN to "닿는 데이터로 만들 수 있어요",
                Verdict.PARTLY to "일부만 돼요",
                Verdict.NOT to "폰 · 마이현대의 데이터가 있어야 해요",
            ).forEach { (v, text) ->
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    VerdictMark(v)
                    Spacer(Modifier.width(16.dp))
                    Text(text, color = p.textPrimary, style = Ivi.bodyLarge, modifier = Modifier.weight(1f))
                    Text("${c[v]}", color = p.textPrimary, style = Ivi.titleSmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("${ontology.basis} 기준(${ontology.generated}). 실제 차에서는 다를 수 있어요.", color = p.textTertiary, style = Ivi.labelMedium)
        }
        ontology.functions.groupBy { it.props["group"].orEmpty() }.forEach { (group, fns) ->
            Text(group, color = p.textTertiary, style = Ivi.labelMedium, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp).quietWhenCut())
            fns.forEach { f ->
                val v = ontology.verdict(f.id)
                val expanded = open == f.id
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).quietWhenCut().background(p.backgroundPrimary, RoundedCornerShape(24.dp))
                        .clickable(role = Role.Button, onClickLabel = "${f.label} 자세히") { open = if (expanded) null else f.id }
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VerdictMark(v)
                        Spacer(Modifier.width(16.dp))
                        Text(f.label, color = p.textPrimary, style = Ivi.bodyLargeStrong, modifier = Modifier.weight(1f))
                        // 평소(만들 수 있어요)에는 말을 달지 않고, 다른 판정에만 말을 달아요(표시 모양이 이미 말해요)
                        if (v != Verdict.CAN) Text(v.label, color = p.textTertiary, style = Ivi.labelMedium)
                    }
                    if (expanded) {
                        Spacer(Modifier.height(10.dp))
                        ontology.needs(f.id).filter { it.role != NeedRole.EXTRA }.forEach { n ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text(if (n.role == NeedRole.KEY) "꼭 필요" else "도움", color = p.textTertiary, style = Ivi.labelMedium, modifier = Modifier.width(88.dp))
                                Text(n.concept.label, color = p.textPrimary, style = Ivi.bodyMedium, modifier = Modifier.weight(1f))
                                Text(n.status.label, color = if (n.status.usable) p.textSecondary else p.informativeNegative, style = Ivi.labelMedium)
                            }
                        }
                    }
                }
            }
        }
        MomentsSection(ontology, pack)
        ServiceWorkflowSection()
    }
}

/** 서비스 여덟 가지가 지나가는 길과 지금 어디까지 왔는지(기획서 ION Service의 workflow 표와 같아요). 설명 자리라서 이 화면에만 있어요. */
@Composable
private fun ServiceWorkflowSection() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    app.roadmemory.car.studio.StudioLibrary.ensure(ctx)
    val cat = remember(app.roadmemory.car.studio.StudioLink.rev) { app.roadmemory.car.studio.StudioStore.catalog(ctx) }
    val lib = app.roadmemory.car.studio.StudioLibrary.state
    Spacer(Modifier.height(24.dp))
    SectionTitle("서비스 workflow · 어디까지 왔는지")
    Services.all.forEach { def ->
        Text(def.title, color = LocalPalette.current.textPrimary, style = Ivi.titleSmall, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp).onBackdrop(LocalPalette.current.backgroundSecondary))
        WorkflowCard(def, Services.progress(def, cat, lib))
    }
}
