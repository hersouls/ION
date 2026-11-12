package app.roadmemory.car.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roadmemory.car.Pane
import app.roadmemory.car.UiState
import app.roadmemory.car.data.GearPos
import app.roadmemory.car.data.SourceKind
import app.roadmemory.car.engine.CardEngine
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.ontology.Ontology
import app.roadmemory.car.ontology.Verdict
import app.roadmemory.car.studio.StudioLibrary
import app.roadmemory.car.studio.StudioStore

/** 값을 아직 못 읽었으면 "—". 차가 주지 않는 값을 만들어 보여 주지 않아요. */
internal const val NONE = "—"

// ───────── ION 홈: 지금의 차 · ION이 챙기는 일 · 서비스 ─────────

/**
 * 서 있을 때(정차 · 충전) 오른쪽 큰 칸의 처음 화면이에요. 위에서부터
 *  ① 지금: 차가 실제로 알려 주는 값(배터리 · 갈 수 있는 거리 · 외기온 · 실내 온도 · 기어/충전)
 *  ② ION이 챙기고 있어요: 지금 말할 것(안전 · 충전 · 운행). 없으면 조용해요.
 *  ③ 서비스: ION App이 제공하는 여덟 가지(시그니처 송 · 디스플레이 테마 · 디지털 아트 · 마이 트립 · 디지털 앨범 · 디지털 뮤직 · 캘린더 동기화 · 위치 동기화). 지금 이어져 있는지만 말해요.
 *  ④ 화면: 충전 · 가족 · 점검 · 여행 기록 · 데이터 · 설정
 * 값이 없으면 "—"로 두고, 이 차에서 못 읽는 값이면 그렇다고 말해요.
 */
@Composable
internal fun IonHome(ui: UiState, ontology: Ontology?, onOpen: (String) -> Unit, onPlay: (List<app.roadmemory.car.data.Tracks.Track>) -> Unit = {}, rec: RecordingActions = RecordingActions()) {
    val p = LocalPalette.current
    val wide = LocalWide.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    StudioLibrary.ensure(ctx)
    val catalog = remember(app.roadmemory.car.studio.StudioLink.rev) { StudioStore.catalog(ctx) }
    val library = StudioLibrary.state
    val look = LocalLook.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 36.dp, vertical = 32.dp)) {
        // 테마의 바탕 사진 위에 바로 놓이는 글이라 화면 바탕색을 뒤에 깔아요(테마가 없으면 창 바탕과 같은 색이라 보이지 않아요)
        Text("ION 홈", color = p.textPrimary, style = Ivi.titleLarge, modifier = Modifier.onBackdrop(p.backgroundSecondary, 16.dp))
        Spacer(Modifier.height(4.dp))
        Text(connectionLine(ui), color = p.textTertiary, style = Ivi.bodyMedium, modifier = Modifier.onBackdrop(p.backgroundSecondary))
        Spacer(Modifier.height(24.dp))

        // 기록하는 중이거나 이어서 할지 묻는 중일 때만 맨 위에 보여요(기록 시작은 ION이 권해요나 여행 기록 화면에서)
        if (ui.recording != null) RecordingCard(ui, rec)

        SectionTitle("지금")
        val m = metrics(ui)
        // 배터리가 가장 중요해서 한 줄을 다 쓰고, 나머지 값은 한 줄에 나란히 놓아요
        BatteryHero(m.first(), ui.car.soc)
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            m.drop(1).forEach { Metric(it, Modifier.weight(1f)) }
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("ION이 챙기고 있어요")
        val feed = IonFeedItems.build(ui)
        if (feed.isEmpty()) {
            Text("지금은 챙길 일이 없어요. 필요한 순간에만 말할게요.", color = p.textSecondary, style = Ivi.bodyLarge)
        } else {
            feed.forEach { FeedRow(it) }
        }

        // ION이 권해요: 규칙으로 고른 제안 한 장(서 있을 때만)
        Spacer(Modifier.height(24.dp))
        AdviceSection(ui, onOpen, onPlay, onRecord = rec.start)

        // 서비스: ION Studio에서 오는 네 가지 · 마이현대에서 오는 네 가지. 이어져 있는지만 말해요(이어지지 않은 내용은 지어내지 않아요).
        Spacer(Modifier.height(24.dp))
        SectionTitle("서비스")
        TileGrid(
            Services.all.map { s -> ServiceTile(Services.target(s), s.title, Services.status(ui, s, catalog, library).tile, s.icon, art = look?.cards?.get(s.pane)) },
            wide, onOpen,
        )

        // 화면: 차가 실제로 주는 값을 보여 주는 앱의 화면들
        Spacer(Modifier.height(8.dp))
        SectionTitle("화면")
        TileGrid(
            listOf(
                ServiceTile(Pane.CHARGE, "충전", chargeLine(ui), MwIcons.Charge, art = look?.cards?.get(Pane.CHARGE)),
                ServiceTile(Pane.FAMILY, "가족 · 자리", familyLine(ui), MwIcons.Family, warn = CardEngine.unbelted(ui.car) != null, art = look?.cards?.get(Pane.FAMILY)),
                ServiceTile(Pane.CHECK, "점검", checkLine(ui), MwIcons.Check, warn = openCount(ui) > 0, art = look?.cards?.get(Pane.CHECK)),
                ServiceTile(Pane.TRIPS, "여행 기록", if (ui.trips.isEmpty()) "아직 없어요" else "${ui.trips.size}건", MwIcons.Trips, art = look?.cards?.get(Pane.TRIPS)),
                ServiceTile(Pane.DATA, "ION이 닿는 데이터", ontology?.let { o -> o.counts().let { "만들 수 있는 일 ${it[Verdict.CAN]}/${o.functions.size}" } } ?: "—", MwIcons.Data, art = look?.cards?.get(Pane.DATA)),
                ServiceTile(Pane.SETTINGS, "설정", "음성 안내 · 충전 화면 · 화면 톤", MwIcons.Settings, art = look?.cards?.get(Pane.SETTINGS)),
            ),
            wide, onOpen,
        )
    }
}

/** 칸을 한 줄에 3개(넓은 창) 또는 2개씩 놓아요. */
@Composable
private fun TileGrid(tiles: List<ServiceTile>, wide: Boolean, onOpen: (String) -> Unit) {
    val perRow = if (wide) 3 else 2
    tiles.chunked(perRow).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            row.forEach { TileCard(it, Modifier.weight(1f)) { onOpen(it.pane) } }
            repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    val p = LocalPalette.current
    Text(text, color = p.textTertiary, style = Ivi.labelMedium, modifier = Modifier.onBackdrop(p.backgroundSecondary, 10.dp))
    Spacer(Modifier.height(10.dp))
}

// ───────── 지금: 값 ─────────

internal data class MetricUi(val label: String, val value: String, val sub: String? = null, val warn: Boolean = false)

private fun connectionLine(ui: UiState): String = when {
    ui.sourceKind == null -> "차와 연결하고 있어요"
    ui.sourceKind == SourceKind.NONE -> "이 기기는 차에 연결돼 있지 않아요. 차에서 쓰면 실제 값이 보여요"
    else -> when (ui.mode) { Mode.CHARGING -> "충전하는 중이에요"; Mode.DRIVING -> "달리는 중이에요"; Mode.PARKED -> "서 있어요" }
}

internal fun metrics(ui: UiState): List<MetricUi> {
    val c = ui.car
    val unit = c.tempUnit
    fun temp(v: Float?) = v?.let { if (unit == app.roadmemory.car.data.TempUnit.FAHRENHEIT) CardEngine.tempText(it, unit) else "${Math.round(it)}℃" } ?: NONE
    return listOf(
        MetricUi("배터리", c.soc?.let { "$it%" } ?: NONE, c.rangeKm?.takeIf { it > 0f }?.let { "갈 수 있는 거리 ${Math.round(it)}km" }),
        MetricUi("외기온", temp(c.outsideTempC), if (c.outsideTempC == null) "이 차에서 읽지 못했어요" else null),
        MetricUi("실내 온도", temp(c.cabinTempC)),
        MetricUi(
            "상태", when (c.gear) { GearPos.PARK -> "주차(P)"; GearPos.DRIVE -> "주행(D)"; GearPos.REVERSE -> "후진(R)"; GearPos.NEUTRAL -> "중립(N)"; else -> NONE },
            when { ui.ux.known && ui.ux.requiresDo -> "주행 규제가 켜져 있어요"; c.chargePortOpen == true && c.plugConnected == true -> "충전 중 · 플러그 연결"; c.chargePortOpen == true -> "충전구가 열려 있어요"; c.plugConnected == true -> "플러그가 꽂혀 있어요"; else -> null },
        ),
    )
}

@Composable
private fun Metric(m: MetricUi, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier.heightIn(min = 112.dp).background(p.backgroundPrimary, RoundedCornerShape(24.dp)).padding(18.dp), verticalArrangement = Arrangement.Center) {
        Text(m.label, color = p.textTertiary, style = Ivi.labelMedium)
        Text(m.value, color = if (m.warn) p.informativeNegative else p.textPrimary, style = Ivi.titleMedium)
        m.sub?.let { Text(it, color = p.textSecondary, style = Ivi.labelMedium) }
    }
}

/** 배터리: 큰 숫자 + 갈 수 있는 거리 + 막대. 값을 못 읽었으면 "—"만 두고 막대는 그리지 않아요. */
@Composable
private fun BatteryHero(m: MetricUi, soc: Int?) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).background(p.backgroundPrimary, RoundedCornerShape(28.dp)).padding(horizontal = 28.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f)) {
                Text(m.label, color = p.textTertiary, style = Ivi.labelMedium)
                Text(m.value, color = p.textPrimary, style = Ivi.headlineSmall)
            }
            m.sub?.let { Text(it, color = p.textSecondary, style = Ivi.bodyLarge, modifier = Modifier.padding(bottom = 6.dp)) }
        }
        if (soc != null) {
            Spacer(Modifier.height(16.dp))
            BatteryBar(soc)
        }
    }
}

// ───────── ION이 챙기는 일 ─────────

internal enum class FeedLevel { INFO, WARN }
internal data class FeedItem(val id: String, val title: String, val detail: String?, val level: FeedLevel = FeedLevel.INFO)

/** 지금 ION이 말할 것. 모든 문장은 차가 실제로 준 값에서만 나와요(모르는 값은 말하지 않아요). */
internal object IonFeedItems {
    fun build(ui: UiState): List<FeedItem> = buildList {
        val c = ui.car
        CardEngine.unbelted(c)?.let { add(FeedItem("belt", "${it.label} 안전벨트를 확인해 주세요", "앉아 있는데 아직 채워지지 않았어요", FeedLevel.WARN)) }
        ui.card?.takeIf { ui.mode == Mode.PARKED }?.let { add(FeedItem(it.id, it.title, it.sub, FeedLevel.WARN)) }
        val est = ui.nav.estimatedSocAtDest
        if (ui.nav.routeActive) {
            val dest = ui.nav.destinationName?.takeIf { it.isNotBlank() } ?: "목적지"
            val eta = ui.nav.remainTimeS?.let { "${Math.max(1, it / 60)}분" }
            add(FeedItem("route", "$dest · ${eta ?: "도착 시각을 몰라요"}", est?.let { "도착하면 배터리가 $it%예요" }))
            if (est != null && est <= CardEngine.LOW_SOC_AT_DEST) {
                val stop = ui.nav.chargeStops.firstOrNull()
                add(FeedItem("charge", "충전이 필요할 수 있어요", stop?.let { "내비가 ${app.roadmemory.car.brain.Korean.eul(it.name)} 경로에 넣었어요" } ?: "내비에서 충전소를 확인해 주세요", FeedLevel.WARN))
            }
        }
        if (ui.mode == Mode.CHARGING) {
            val pace = ui.chargeMinLeft?.let { "80%까지 약 ${it}분 남았어요" } ?: "충전 속도를 재는 중이에요"
            add(FeedItem("charging", "충전하는 중이에요", pace))
        }
        ui.driveMinutes?.takeIf { it >= CardEngine.REST_AFTER_MIN }?.let { add(FeedItem("rest", "${CardEngine.restWord(it)}째 달렸어요", "쉬어 가는 건 어때요?")) }
        c.outsideTempC?.takeIf { it <= CardEngine.COLD_C }?.let { add(FeedItem("cold", "바깥이 ${CardEngine.tempText(it, c.tempUnit)}예요", "추운 날에는 갈 수 있는 거리가 줄 수 있어요")) }
        val issues = CardEngine.vehicleIssues(c)
        if (issues.isNotEmpty() && ui.card?.kind != app.roadmemory.car.engine.CardKind.VEHICLE_CHECK) add(FeedItem("check", "살펴볼 곳이 있어요", issues.joinToString(" · "), FeedLevel.WARN))
    }.distinctBy { it.id }
}

@Composable
private fun FeedRow(item: FeedItem) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).background(p.backgroundPrimary, RoundedCornerShape(24.dp)).padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(14.dp).background(if (item.level == FeedLevel.WARN) p.informativeNegative else p.informativeActive, RoundedCornerShape(7.dp)))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = p.textPrimary, style = Ivi.bodyLargeStrong)
            item.detail?.let { Text(it, color = p.textSecondary, style = Ivi.bodyMedium) }
        }
    }
}

// ───────── 서비스 타일 ─────────

/** warn: 이상이 있는 칸만 점을 달아요(평소 상태에는 표시를 달지 않아요 — 상태 말은 state에 늘 있어요). */
/** art: 적용한 테마의 카드 그림(세트가 있을 때). 있으면 그림이 위에 놓이는 카드로 그려요. */
private data class ServiceTile(val pane: String, val title: String, val state: String, val icon: MwIconData, val warn: Boolean = false, val art: String? = null)

@Composable
private fun TileCard(tile: ServiceTile, modifier: Modifier, onClick: () -> Unit) {
    if (tile.art != null) { ArtTileCard(tile, tile.art, modifier, onClick); return }
    val p = LocalPalette.current
    Row(
        modifier.heightIn(min = 132.dp)
            .iviButton(RoundedCornerShape(28.dp), p.backgroundPrimary, p.buttonBasicPressed, onClick)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        MwIcon(tile.icon, size = 44.dp)
        Column(Modifier.weight(1f)) {
            Text(tile.title, color = p.textPrimary, style = Ivi.titleSmall)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (tile.warn) StateDot(alert = true)
                Text(tile.state, color = if (tile.warn) p.textPrimary else p.textTertiary, style = Ivi.bodyMedium)
            }
        }
    }
}

/**
 * 테마 세트가 있을 때의 카드: 테마의 그림이 위에, 아이콘 · 이름 · 지금 상태가 아래에 놓여요(실제 스토어 테마의 메뉴 카드처럼).
 * 그림에는 글자가 없고, 이름과 상태는 카드의 글이에요. 상태 점은 이상이 있는 칸에만 달려요.
 */
@Composable
private fun ArtTileCard(tile: ServiceTile, art: String, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(modifier.iviButton(RoundedCornerShape(28.dp), p.backgroundPrimary, p.buttonBasicPressed, onClick)) {
        StudioImage(art, Modifier.fillMaxWidth().aspectRatio(1.5f), null, ContentScale.Crop, plain = true)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 124.dp).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MwIcon(tile.icon, size = 36.dp)
            Column(Modifier.weight(1f)) {
                Text(tile.title, color = p.textPrimary, style = Ivi.bodyLargeStrong, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (tile.warn) StateDot(alert = true)
                    Text(tile.state, color = if (tile.warn) p.textPrimary else p.textTertiary, style = Ivi.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun chargeLine(ui: UiState): String = when {
    ui.mode == Mode.CHARGING -> "충전하는 중 · ${ui.car.soc?.let { "$it%" } ?: NONE}"
    ui.car.soc != null -> "배터리 ${ui.car.soc}%"
    else -> "배터리를 읽는 중"
}

private fun familyLine(ui: UiState): String {
    val n = ui.car.occupantCount
    return when {
        ui.car.seats.isEmpty() -> "자리를 읽는 중"
        CardEngine.unbelted(ui.car) != null -> "${n}명 · 벨트 확인 필요"
        n == 0 -> "탄 사람이 없어요"
        else -> "${n}명이 탔어요"
    }
}

private fun openCount(ui: UiState): Int = app.roadmemory.car.data.CarPart.entries.count { ui.car.isOpen(it) }

private fun checkLine(ui: UiState): String {
    val open = openCount(ui)
    return if (ui.car.open.isEmpty()) "문 · 트렁크를 읽는 중" else if (open == 0) "모두 닫혀 있어요" else "${open}곳이 열려 있어요"
}
