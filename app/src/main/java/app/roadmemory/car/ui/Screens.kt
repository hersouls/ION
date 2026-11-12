package app.roadmemory.car.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.roadmemory.car.data.DemoUser
import app.roadmemory.car.brain.TripInsight
import app.roadmemory.car.data.TripBook
import app.roadmemory.car.data.SavedTrip
import app.roadmemory.car.DriveController
import app.roadmemory.car.TripSummary
import app.roadmemory.car.UiState
import app.roadmemory.car.data.AppPrefs
import app.roadmemory.car.data.SeatPos
import app.roadmemory.car.data.SourceKind
import app.roadmemory.car.data.ToneMode
import app.roadmemory.car.data.ToneSettings
import app.roadmemory.car.engine.CardKind
import app.roadmemory.car.engine.GlanceCard
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.Pane
import app.roadmemory.car.ontology.Ontology
import app.roadmemory.car.engine.CardEngine
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue


/**
 * 서 있을 때(정차 · 충전)의 작업 화면을 쓰는지: 앱 창 전체가 왼쪽 ION 칸 + 오른쪽 큰 칸(ION 홈 · 서비스 화면)으로 나뉘어요.
 * 달리는 중에는 목록 · 서비스 화면을 열지 않아요(주행 규제: 콘텐츠 제한). 서 있을 때의 안전 카드와 도착 요약은 한 화면을 다 써요.
 */
internal fun usesWorkspace(ui: UiState): Boolean {
    if (ui.sourceKind == null || ui.mode == Mode.DRIVING) return false
    if (ui.pane != null) return true
    return when (ui.mode) {
        Mode.CHARGING -> true
        // 도착 요약(SaveScreen)은 1km 넘게 달렸을 때만 떠요. 1km가 안 되는 도착은 요약이 없으니 작업 화면(ION 홈)으로 돌아와요
        Mode.PARKED -> ui.card?.kind != CardKind.REAR_ALERT && ui.card?.kind != CardKind.VEHICLE_CHECK && (ui.trip?.km ?: 0) < 1
        else -> false
    }
}

@Composable
fun RoadMemoryApp(vm: DriveController) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ion by vm.ion.ui.collectAsStateWithLifecycle()
    // 앱을 켠 뒤 정차 중이면 마지막으로 보던 화면을 되살려요. 처음이면 ION 홈이에요. 실행마다 한 번만이에요.
    LaunchedEffect(ui.sourceKind, ui.mode) {
        if (!IntroGate.restored && ui.sourceKind != null) {
            IntroGate.restored = true
            if (ui.mode == Mode.PARKED) vm.restoreLastPane()
        }
    }
    val workspace = usesWorkspace(ui)
    // ION 디스플레이가 상황에 맞춰 그리는 장면(주행 = 굴러가는 타이어, 충전 = 배터리, 가족 = 자리, 점검 = 차, 여행 기록 = 길)
    val seats = SeatPos.entries.map { ui.car.occupied(it) }
    val ionScene = IonScene.pick(
        connected = ui.sourceKind != null, mode = ui.mode, pane = ui.pane, card = ui.card?.kind,
        soc = ui.car.soc, seats = seats, arrived = ui.trip != null,
    )
    // 장면과 같은 말을 하는 값(차가 준 것만): 가족 = 타고 있는 사람, 여행 기록 = 저장된 여행. 나머지는 배터리예요.
    val batteryInfo = ui.car.soc?.let { DisplayInfo(if (ui.mode == Mode.CHARGING) "충전 중" else "배터리", "$it%", it / 100f) }
    val sceneInfo = when {
        ionScene.kind == SceneKind.SEATS && ui.car.seats.isNotEmpty() -> DisplayInfo("타고 있어요", "${seats.count { it }}명")
        ionScene.kind == SceneKind.ROAD && ui.trips.isNotEmpty() -> DisplayInfo("저장된 여행", "${ui.trips.size}번")
        else -> batteryInfo
    }
    CarFrame(ui.tone, ui.rainSkin) {
        // 인트로 막이 덮고 있는 동안은 ION 디스플레이가 시동 장면을 미뤄요(막이 닫히면 보이게 시작해요)
        var introOpen by remember { mutableStateOf(!IntroGate.played) }
        // 대화 화면의 첫마디와 칩은 지금 상황(차 · 일정 · 기록)으로 만들어요
        val chatCtx = androidx.compose.ui.platform.LocalContext.current
        app.roadmemory.car.studio.StudioLibrary.ensure(chatCtx)
        val chatCat = remember(app.roadmemory.car.studio.StudioLink.rev) { app.roadmemory.car.studio.StudioStore.catalog(chatCtx) }
        val chatHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
        val chatGreeting = app.roadmemory.car.brain.Conversation.greeting(ui, chatHour, chatCat, app.roadmemory.car.studio.StudioLibrary.state)
        val chatChipsNow = app.roadmemory.car.brain.Conversation.chipPool(ui, chatCat, app.roadmemory.car.studio.StudioLibrary.state)
        CompositionLocalProvider(LocalIntroOpen provides introOpen, LocalIonScene provides ionScene) {
        IonShell(
            ion = ion,
            driving = ui.mode == Mode.DRIVING,
            onMic = vm.ion::onMic,
            onToday = { vm.openHome(); vm.ion.close() },
            onMemories = { vm.openPane(Pane.TRIPS) },
            onSettings = { vm.openPane(Pane.SETTINGS) },
            onExample = vm.ion::submit,
            onClose = vm.ion::close,
            workspace = workspace,
            info = sceneInfo,
            moment = ui.moment,
            greeting = chatGreeting,
            chips = chatChipsNow,
        ) {
            when {
                ui.sourceKind == null -> Centered("차와 연결하고 있어요")
                workspace -> Workspace { PaneRouter(ui, vm) }
                // 서 있을 때의 안전 카드(하차 알림 · 주행 후 점검)는 한 화면을 다 써요
                ui.mode == Mode.PARKED && (ui.card?.kind == CardKind.REAR_ALERT || ui.card?.kind == CardKind.VEHICLE_CHECK) ->
                    ParkedCardScreen(ui, ui.card!!, vm::onPrimary, vm::onSecondary)
                ui.mode == Mode.PARKED && (ui.trip?.km ?: 0) >= 1 -> SaveScreen(ui.trip!!, ui.trips, ui.moment, onTrips = { vm.dismissTrip(); vm.openPane(Pane.TRIPS) }, onClose = vm::dismissTrip)
                else -> DriveScreen(ui, vm::onPrimary, vm::onSecondary, vm::stopPlaying)
            }
        }
        }
        // 앱을 켠 뒤 한 번: ION 마크가 켜지고 인사하고 열리는 인트로 막(ui/Hero.kt). 차와 연결되는 동안은 연결 중 화면을 대신해요.
        if (introOpen) {
            IntroCurtain(ready = ui.sourceKind != null, driving = ui.mode == Mode.DRIVING) { introOpen = false; IntroGate.played = true }
        }
    }
}

/** 오른쪽 큰 칸의 내용: 열린 서비스 화면, 없으면 ION 홈(충전 중이고 "충전 화면 자동"이 켜져 있으면 충전 화면). */
@Composable
private fun PaneRouter(ui: UiState, vm: DriveController) {
    val home = { vm.openHome() }
    val effective = ui.pane ?: if (ui.mode == Mode.CHARGING && ui.appPrefs.autoLounge) Pane.CHARGE else null
    when (effective) {
        Pane.CHARGE -> ChargePane(ui, home, onPlay = vm::playTracks, onStop = vm::stopPlaying)
        Pane.FAMILY -> FamilyPane(ui, home)
        Pane.CHECK -> CheckPane(ui, vm.ontology, home)
        Pane.TRIPS -> TripsPane(ui, home, onPlay = { vm.playTracks(app.roadmemory.car.data.Tracks.all) }, onClear = vm::clearSavedTrips, rec = vm.recordingActions(), onDelete = vm::deleteSavedTrip, onPlayTracks = vm::playTracks)
        Pane.DATA -> DataPane(vm.ontology, home, ui.pack)
        Pane.SETTINGS -> SettingsPane(ui, onChange = vm::setAppPrefs, onTone = { vm.openPane(Pane.TONE) }, onPack = { vm.openPane(Pane.PACK) }, onLink = { vm.openPane(Pane.LINK) }, onReset = { vm.setAppPrefs(AppPrefs()) }, onHome = home, onHeard = vm::setHeardLog, onHeardExport = { vm.exportHeardLog() }, onHeardClear = vm::clearHeardLog)
        Pane.TONE -> ToneSettingsScreen(ui, onChange = vm::setToneSettings, onBack = { vm.openPane(Pane.SETTINGS) })
        Pane.PACK -> PackPane(ui, vm.ontology, onApply = vm::applyPack, onBack = home, onPreview = vm::previewPack, onRescan = vm::rescanPacks)
        Pane.LINK -> LinkPane(onBack = home)
        in Pane.services -> Services.byPane(effective)?.let { ServicePane(ui, it, home, onPlay = vm::playTracks, onStop = vm::stopPlaying, onSkip = vm::skipTrack) } ?: IonHome(ui, vm.ontology, onOpen = vm::openPane, onPlay = vm::playTracks, rec = vm.recordingActions())
        else -> IonHome(ui, vm.ontology, onOpen = vm::openPane, onPlay = vm::playTracks, rec = vm.recordingActions())
    }
}

// ───────── 주행 ─────────

/**
 * 달리는 중: 왼쪽에는 차가 실제로 알려 주는 운행 값(배터리 · 갈 수 있는 거리 · 도착 · 이번 주행), 오른쪽에는 카드 한 장(없으면 조용해요).
 * 카드가 안전벨트 · 뒷좌석이면 왼쪽이 자리 그림이 돼요.
 */
@Composable
private fun DriveScreen(ui: UiState, onPrimary: (GlanceCard) -> Unit, onSecondary: (GlanceCard) -> Unit, onStop: () -> Unit) {
    val p = LocalPalette.current
    val card = ui.card
    TwoPane(
        left = {
            when (card?.kind) {
                CardKind.REAR_SEAT -> SeatMap(ui.car, Modifier.align(Alignment.Center))
                CardKind.SEATBELT_CHECK -> SeatMap(ui.car, Modifier.align(Alignment.Center), checkBelts = true)
                else -> DriveBoard(ui, Modifier.align(Alignment.Center))
            }
        },
        right = {
            ModeLabel("주행")
            if (card == null) {
                CardBox {
                    Text("조용히 달리는 중이에요", color = p.textPrimary, style = Ivi.titleMedium)
                    Text(if (ui.sourceKind == SourceKind.NONE) "이 기기는 차에 연결돼 있지 않아요" else "필요할 때만 한 장을 띄워요", color = p.textSecondary, style = Ivi.bodyLarge)
                }
            } else {
                CardBox {
                    Text(card.title, color = p.textPrimary, style = Ivi.titleMedium)
                    card.sub?.let { Text(it, color = p.textSecondary, style = Ivi.bodyLarge) }
                    Spacer(Modifier.height(28.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        BigButton(card.primary, true, { onPrimary(card) }, Modifier.weight(1f))
                        BigButton(card.secondary, false, { onSecondary(card) }, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(card.hint, color = p.textTertiary, style = Ivi.labelMedium)
            }
            ui.nowPlaying?.let {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().background(p.surfaceHigh, RoundedCornerShape(24.dp)).padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("재생 중  $it", color = p.textPrimary, style = Ivi.bodySmall, modifier = Modifier.weight(1f))
                    Text("멈춤", color = p.textPrimary, style = Ivi.bodySmallStrong,
                        modifier = Modifier.clickable(role = Role.Button, onClick = onStop).padding(12.dp))
                }
            }
            ui.moment?.let { MomentStrip(it) }
        },
    )
}

/** 운행 값 판: 배터리 링 + 한 줄씩. 값을 모르면 줄을 그리지 않아요(가짜 값 없음). */
@Composable
private fun DriveBoard(ui: UiState, modifier: Modifier) {
    val p = LocalPalette.current
    val c = ui.car
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        c.soc?.let { RingArt(it, null, Modifier.size(200.dp)) }
        Spacer(Modifier.height(20.dp))
        val lines = listOfNotNull(
            c.soc?.let { "배터리 $it%" },
            c.rangeKm?.takeIf { it > 0f }?.let { "갈 수 있는 거리 ${Math.round(it)}km" },
            if (ui.nav.routeActive) listOfNotNull(ui.nav.destinationName, ui.nav.remainTimeS?.let { "${Math.max(1, it / 60)}분 남았어요" }).joinToString(" · ").ifEmpty { null } else null,
            if (ui.nav.routeActive) ui.nav.estimatedSocAtDest?.let { "도착하면 배터리 $it%" } else null,
            c.tripKm?.takeIf { it >= 0.5f }?.let { "이번 주행 ${Math.round(it)}km" + (ui.driveMinutes?.let { m -> " · ${m}분" } ?: "") },
            c.outsideTempC?.let { "바깥 ${CardEngine.tempText(it, c.tempUnit)}" },
        )
        if (lines.isEmpty()) Text("차의 값을 기다리는 중이에요", color = p.textTertiary, style = Ivi.bodyLarge)
        lines.forEach { Text(it, color = p.textPrimary, style = Ivi.bodyLargeStrong, modifier = Modifier.padding(vertical = 4.dp)) }
    }
}

// ───────── 도착 ─────────

/**
 * 도착 화면(오너 2026-11-12 "너무 허접하잖아"): 여행 기록 세부 화면과 같은 말 · 그림으로 방금 다녀온 길을 한 장에 보여 줘요.
 *  ① 어디에 언제 도착했는지(내비 목적지를 알 때만 이름) ② 지도 핀과 출발에서 도착까지의 여정 그림([TripJourneyArt])
 *  ③ 거리 · 걸린 시간 · 함께 탄 사람 · 충전 ④ 다른 기록과 견준 한두 줄 ⑤ 기록 보기 · 닫기.
 * 1km도 안 달렸으면 이 화면을 띄우지 않아요(기록으로도 남기지 않아서요). 값은 모두 이번 주행에서 잰 것과 저장한 다른 여행에서 센 것이에요.
 */
@Composable
private fun SaveScreen(t: TripSummary, saved: List<SavedTrip>, moment: app.roadmemory.car.pack.MomentNote?, onTrips: () -> Unit, onClose: () -> Unit) {
    val p = LocalPalette.current
    val trip = remember(t) { t.preview() }
    // 저장(도착 4초 뒤)이 끝나면 목록에 같은 도착 시각의 기록이 생겨요: 두 번 세지 않게 빼고 이번 것을 넣어요
    val all = remember(trip, saved) { listOf(trip) + saved.filter { it.at != trip.at } }
    val d = remember(trip, all) { TripInsight.detail(trip, all) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 28.dp)) {
        CardBox {
            // 화면 이름(ION · 정차)은 테마 사진 위에 두면 묻혀서 카드 안 첫 줄로 옮겼어요
            Text("ION · 정차 · 방금 다녀온 길이에요", color = p.textTertiary, style = Ivi.labelMedium)
            Text(t.destName?.let { "${it}에 도착했어요" } ?: "도착했어요", color = p.textPrimary, style = Ivi.titleLarge)
            Text(d.whenLine, color = p.textSecondary, style = Ivi.bodyMedium)
            d.thatDayLine?.let { Text(it, color = p.textPrimary, style = Ivi.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
        }
        Spacer(Modifier.height(16.dp))
        // 거리 · 걸린 시간 · 출발과 도착 시각 · 함께 탄 사람 · 충전은 이 그림이 다 말해요(숫자 칸을 따로 두면 같은 값이 두 번 나와요)
        TripJourneyArt(trip, d, all, Modifier.fillMaxWidth().height(360.dp))
        Spacer(Modifier.height(16.dp))
        CardBox {
            Text("다른 기록과 견줘 보면", color = p.textTertiary, style = Ivi.labelMedium)
            Spacer(Modifier.height(6.dp))
            Text(d.rankLine, color = p.textPrimary, style = Ivi.bodyLargeStrong)
            (d.sameWayLine ?: d.avgLine)?.let { Text(it, color = p.textSecondary, style = Ivi.bodyMedium) }
            if (t.tracks > 0) Text("이번 길에 함께 들은 곡 ${t.tracks}곡 · 기록 보기에서 다시 틀 수 있어요", color = p.textSecondary, style = Ivi.bodyMedium)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BigButton("기록 보기", true, onTrips, Modifier.weight(1f))
                BigButton("닫기", false, onClose, Modifier.weight(1f))
            }
        }
        moment?.let { MomentStrip(it) }
    }
}

// ───────── 설정 ─────────

/** 켜고 끄는 값 둘과 화면 톤. 값은 바로 적용되고 이 기기에만 남아요. 단위는 차의 표시 단위를 따라요. */
@Composable
private fun SettingsPane(ui: UiState, onChange: (AppPrefs) -> Unit, onTone: () -> Unit, onPack: () -> Unit, onLink: () -> Unit = {}, onReset: () -> Unit, onHome: () -> Unit, onHeard: (Boolean) -> Unit = {}, onHeardExport: () -> Unit = {}, onHeardClear: () -> Unit = {}) {
    val p = LocalPalette.current
    val s = ui.appPrefs
    val unit = if (ui.car.tempUnit == app.roadmemory.car.data.TempUnit.FAHRENHEIT) "화씨" else "섭씨"
    PaneFrame("설정", onHome) {
        // 설정마다 한 줄: 이름은 왼쪽, 스위치 · 값 · 버튼은 오른쪽 끝(Moonwave 설정 규칙). 그룹 이름은 카드 위에 둬요.
        SectionTitle("내 정보")
        CardBox { ValueRow("사용자", "${DemoUser.ID} · 이 차의 ION을 쓰는 사람이에요", DemoUser.NAME) }
        Spacer(Modifier.height(24.dp))
        SectionTitle("알림")
        CardBox {
            SwitchRow("음성 안내", "카드의 한 문장을 소리로도 읽어요", s.voiceGuide) { onChange(s.copy(voiceGuide = it)) }
            RowLine()
            SwitchRow("충전 화면 자동", "충전을 시작하면 ION의 충전 화면을 저절로 열어요", s.autoLounge) { onChange(s.copy(autoLounge = it)) }
        }
        Spacer(Modifier.height(24.dp))
        SectionTitle("시그니처 송")
        CardBox {
            ValueRow(
                "시그니처 송", "산 목소리가 차의 순간마다 나와요" + if (ui.pack?.synthetic == true) " (합성 음성)" else "",
                ui.pack?.title ?: "꺼짐", "고르기", onPack,
            )
        }
        Spacer(Modifier.height(24.dp))
        SectionTitle("ION Studio")
        CardBox {
            val linkCtx = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.runtime.LaunchedEffect(Unit) { app.roadmemory.car.studio.StudioLink.ensure(linkCtx) }
            val link = app.roadmemory.car.studio.StudioLink.state
            ValueRow(
                "ION Studio 연결", "올라온 테마 · 아트 · 시그니처 송 · 여행 일정을 이 차로 받아요",
                if (link.linked) "이어져 있어요" else "이어져 있지 않아요", if (link.linked) "보기" else "연결", onLink,
            )
        }
        Spacer(Modifier.height(24.dp))
        SectionTitle("화면")
        CardBox {
            ValueRow("화면 톤", "서 있을 때 적용돼요", "지금 ${ui.tone.label}", "바꾸기", onTone)
            RowLine()
            ValueRow("온도 단위", "차의 표시 단위를 따라요", unit)
        }
        Spacer(Modifier.height(24.dp))
        SectionTitle("ION이 들은 말")
        CardBox {
            val hl = app.roadmemory.car.brain.HeardLog
            SwitchRow("들은 말 남기기", "이 기기에만 ${hl.count}개(못 알아들은 말 ${hl.unknownCount}개) · 모델은 없고, 다음에 규칙을 넓히는 데 써요", hl.on, onHeard)
            RowLine()
            ValueRow("내보내기", hl.lastExport?.let { "썼어요 · $it" } ?: "앱 폴더의 ${app.roadmemory.car.brain.HeardLog.EXPORT_NAME}에 써요. 저절로 나가지 않아요", "${hl.count}개", "내보내기", onHeardExport)
            RowLine()
            ValueRow("지우기", "남긴 말과 내보낸 파일을 모두 지워요", "", "지우기", onHeardClear)
        }
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.height(IviSize.buttonSmall.dp)
                .iviButton(RoundedCornerShape(IviSize.buttonSmall.dp / 2), p.buttonBasic, p.buttonBasicPressed, onReset)
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) { Text("처음 값으로", color = p.textPrimary, style = Ivi.bodySmallStrong) }
    }
}

/**
 * 가로(넓은 창): 왼쪽 그림 · 오른쪽 글. 세로(한 창): 위쪽 그림 · 아래쪽 글. 글 칸이 더 커요(다빈치 황금비 1 : 1.618).
 * 가이드: portrait에는 핵심 기능과 주요 정보, landscape에는 확장된 영역(그림 · 부가 정보)을 줘요.
 */
@Composable
internal fun TwoPane(left: @Composable BoxScope.() -> Unit, right: @Composable ColumnScope.() -> Unit) {
    if (LocalWide.current) {
        Row(Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 36.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight()) { left() }
            Spacer(Modifier.width(36.dp))
            // 오른쪽 글 칸이 더 커요(황금비 1 : 1.618). 글이 길어도 잘리지 않게 스크롤돼요(가운데 정렬은 그대로).
            Box(Modifier.weight(GOLDEN).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center, content = right)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 28.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth()) { left() }
            Spacer(Modifier.height(20.dp))
            Column(
                Modifier.weight(GOLDEN).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Top, content = right,
            )
        }
    }
}

@Composable
private fun Centered(text: String) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = p.textTertiary, style = Ivi.titleSmall)
    }
}

@Composable
internal fun ModeLabel(text: String) {
    val p = LocalPalette.current
    Text("ION · $text", color = p.textTertiary, style = Ivi.bodySmallStrong)   // 어느 화면이든 앱 이름(ION)이 보여요
    Spacer(Modifier.height(12.dp))
}

@Composable
internal fun BigButton(text: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(
        modifier
            .height(IviSize.buttonLarge.dp)
            .iviButton(
                RoundedCornerShape(IviSize.buttonLarge.dp / 2),
                if (primary) p.buttonFilled else p.buttonBasic,
                if (primary) p.buttonFilledPressed else p.buttonBasicPressed,
                onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (primary) p.textOnFilled else p.textPrimary, style = Ivi.titleSmall)
    }
}

@Composable
internal fun CardBox(content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().background(p.backgroundPrimary, RoundedCornerShape(32.dp)).padding(36.dp),
        content = content,
    )
}

@Composable
private fun Fact(label: String, value: String) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = p.textTertiary, style = Ivi.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, color = p.textPrimary, style = Ivi.bodyLargeStrong, textAlign = TextAlign.End)
    }
}

/**
 * 화면 톤 설정. 정차 · 충전 중에만 열려요(주행 중에는 열리지 않아요). 운전자가 고르면 바로 적용되고 이 기기에 남아요.
 * 자동이면 날씨와 운전자가 맞춰 둔 실내 온도를 따르고, 둘 중 하나만 따르게 하거나 한 가지 톤으로 고정할 수도 있어요.
 */
@Composable
private fun ToneSettingsScreen(ui: UiState, onChange: (ToneSettings) -> Unit, onBack: () -> Unit) {
    val p = LocalPalette.current
    val wide = LocalWide.current
    val s = ui.toneSettings
    Column(
        Modifier.fillMaxSize().padding(horizontal = if (wide) 44.dp else 32.dp, vertical = 32.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("화면 톤", color = p.textPrimary, style = Ivi.titleLarge, modifier = Modifier.weight(1f))
            BigButton("닫기", false, onBack, Modifier.width(200.dp))
        }
        val rainTheme = RainTheme.pick(app.roadmemory.car.studio.StudioStore.catalog(androidx.compose.ui.platform.LocalContext.current).themes, app.roadmemory.car.studio.StudioLibrary.state.downloaded)
        // 테마 사진 위에서도 읽혀야 해서 카드 안에 둬요
        CardBox {
            Text(
                if (ui.rainSkin && rainTheme != null) "비가 와서 ${app.roadmemory.car.brain.Korean.euro(rainTheme.title)} 바꿔 뒀어요. 비가 그치면 돌아와요."
                else "지금은 ${ui.tone.label} 톤이에요. 주행 중에는 바뀌지 않고, 서 있을 때 적용돼요.",
                color = p.textPrimary, style = Ivi.bodyLarge,
            )
        }
        CardBox {
            Text("톤", color = p.textTertiary, style = Ivi.labelMedium)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ToneMode.entries.forEach { m ->
                    BigButton(m.label, s.mode == m, { onChange(s.copy(mode = m)) }, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when (s.mode) {
                    ToneMode.AUTO -> "날씨와 내가 맞춰 둔 온도를 따라가요"
                    ToneMode.STANDARD -> "늘 기본 톤이에요"
                    ToneMode.CRISP -> "늘 선명한 톤이에요. 보조 글자와 버튼이 진해요"
                    ToneMode.CALM -> "늘 차분한 톤이에요. 바탕이 한 단계 낮아요"
                },
                color = p.textSecondary, style = Ivi.bodySmall,
            )
        }
        CardBox {
            SettingToggle(
                "비 오면 비 테마로",
                if (rainTheme != null) "서 있을 때 비를 짐작하면 ${app.roadmemory.car.brain.Korean.euro(rainTheme.title)} 바꿔요. 그치면 내가 적용한 테마로 돌아와요"
                else "내려받은 비 테마가 없어요. 디스플레이 테마에서 비 테마를 받아 두면 돼요",
                s.rainTheme,
            ) { onChange(s.copy(rainTheme = it)) }
        }
        if (s.mode == ToneMode.AUTO) {
            CardBox {
                SettingToggle("날씨에 맞추기", "와이퍼 · 안개등 · 김서림 신호로 비 · 안개를 짐작해요", s.followWeather) { onChange(s.copy(followWeather = it)) }
                RowLine()
                SettingToggle("내가 맞춘 온도에 맞추기", "운전석 설정 온도만 읽어요. 동승석 · 뒷좌석은 읽지 않아요", s.followTaste) { onChange(s.copy(followTaste = it)) }
            }
        }
        Text("색은 Pleos 색 토큰 안에서만 움직이고, 글자는 어떤 톤에서도 4.5:1을 넘어요.", color = p.textTertiary, style = Ivi.labelMedium)
    }
}

@Composable
private fun SettingToggle(title: String, hint: String, on: Boolean, onChange: (Boolean) -> Unit) = SwitchRow(title, hint, on, onChange)

/**
 * 기어 P에서 뜨는 카드 한 장(시안 17 뒷좌석 하차 알림 · 시안 19 주행 후 점검 리포트). 위에서 본 차가 왼쪽에, 카드가 오른쪽에 있어요.
 * 차 신호만으로 만든 제안이고 안전 기능 인증과는 별개예요. 알았다고 하면 이번 정차에서는 다시 말하지 않아요.
 */
@Composable
private fun ParkedCardScreen(ui: UiState, card: GlanceCard, onPrimary: (GlanceCard) -> Unit, onSecondary: (GlanceCard) -> Unit) {
    val p = LocalPalette.current
    TwoPane(
        left = {
            CarTopArt(ui.car, Modifier.fillMaxSize(), thermometer = card.kind == CardKind.REAR_ALERT, washer = card.kind == CardKind.VEHICLE_CHECK)
        },
        right = {
            ModeLabel("정차")
            CardBox {
                Text(card.title, color = p.textPrimary, style = Ivi.titleMedium)
                card.sub?.let { Text(it, color = p.textSecondary, style = Ivi.bodyLarge) }
                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BigButton(card.primary, true, { onPrimary(card) }, Modifier.weight(1f))
                    BigButton(card.secondary, false, { onSecondary(card) }, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(card.hint, color = p.textTertiary, style = Ivi.labelMedium)
            ui.moment?.let { MomentStrip(it) }
        },
    )
}
