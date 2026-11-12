package app.roadmemory.car.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roadmemory.car.UiState
import app.roadmemory.car.brain.Agenda
import app.roadmemory.car.brain.AgendaDay
import app.roadmemory.car.brain.Appt
import app.roadmemory.car.brain.Korean
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.navi.GuideResult
import app.roadmemory.car.navi.NaviLink
import app.roadmemory.car.navi.Place
import app.roadmemory.car.studio.StudioLibrary
import app.roadmemory.car.studio.StudioStore
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay

// ───────── 캘린더 동기화: 다음 약속 → 두 주 달력 띠 → 오늘 · 내일 · 이번 주 · 다음 주. 규칙은 brain/Agenda.kt ─────────

/**
 * 캘린더 동기화 = 휴대폰 캘린더의 일정을 차에서 보고, 약속 장소로 내비 안내를 보내는 서비스예요.
 *  · 맨 위 "다음 약속": 지나지 않은 가장 가까운 약속과 남은 시간. 내비가 그곳으로 안내하고 있으면 도착 예상을 약속 시각과 견줘요.
 *  · 두 주 달력 띠: 약속이 있는 날에 점이 찍혀요. 날을 누르면 그날 약속만 보여요(다시 누르면 모두).
 *  · 약속마다 이어 보기: 같은 곳에 다녀온 여행 · 마이 트립의 정류장 · 앞 약속에서 여기까지의 거리와 충전 여부.
 * 남은 시간은 30초마다 다시 세요. 달리는 중에는 안내 단추가 없어요(길을 바꾸지 않아요).
 */
@Composable
internal fun CalendarWorkflowPane(ui: UiState, def: ServiceDef, onHome: () -> Unit) {
    val ctx = LocalContext.current
    val cat = remember(app.roadmemory.car.studio.StudioLink.rev) { StudioStore.catalog(ctx) }
    val lib = StudioLibrary.state
    val p = LocalPalette.current
    var note by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalDateTime.now() } }
    val today = now.toLocalDate()
    val agenda = remember(today) { Agenda.build(cat.calendar, today) }
    var picked by remember { mutableStateOf<LocalDate?>(null) }
    val canGuide = ui.mode != Mode.DRIVING

    fun guide(a: Appt) {
        val e = a.event
        if (e.lat == null || e.lng == null) return
        note = "내비에 ${Korean.euro(e.place)} 길을 보내는 중이에요"
        NaviLink.guide(ctx, Place(e.place, e.lat, e.lng)) { r ->
            note = when (r) { GuideResult.Sent -> "내비가 ${Korean.euro(e.place)} 안내를 시작해요. 도착 예상은 여기 다음 약속에 이어서 보여요"; is GuideResult.Failed -> r.why.label }
        }
    }

    SyncPane(def, onHome, emptyList(), "일정") {
        if (agenda.isEmpty()) {
            Card { EmptyNote(MwIcons.Calendar, "가져온 일정이 비어 있어요", "휴대폰 캘린더에 일정을 적으면 다음 동기화 때 여기에 보여요.") }
            return@SyncPane
        }
        note?.let { Card { Text(it, color = p.textPrimary, style = Ivi.bodyLarge) } }

        val next = Agenda.next(agenda, now)
        next?.let { a ->
            SectionTitle("다음 약속")
            Card { NextAppt(a, agenda, ui, now, canGuide, links = Agenda.links(a, cat, lib, ui.trips)) { guide(a) } }
        }

        SectionTitle("두 주")
        Card { AgendaStrip(Agenda.strip(agenda, today), picked) { d -> picked = if (picked == d) null else d } }

        val shown = picked?.let { d -> agenda.filter { it.date == d } } ?: agenda
        picked?.let { d ->
            if (shown.isEmpty()) Card { Text("${d.monthValue}.${d.dayOfMonth}(${Agenda.dow(d)})에는 약속이 없어요", color = p.textSecondary, style = Ivi.bodyLarge) }
        }
        Agenda.grouped(shown, today).forEach { (bucket, list) ->
            SectionTitle("${bucket.label} · ${list.size}개")
            Card {
                list.forEachIndexed { i, a ->
                    // 다음 약속의 이어 보기는 위 카드에 있어서 여기서는 되풀이하지 않아요
                    if (i > 0) { Spacer(Modifier.height(16.dp)); RowLine(); Spacer(Modifier.height(16.dp)) }
                    ApptRow(a, now, canGuide, extra = if (a === next) emptyList() else listOfNotNull(Agenda.leg(agenda, a, ui.car.rangeKm)) + Agenda.links(a, cat, lib, ui.trips)) { guide(a) }
                }
            }
        }
        NoteLine("휴대폰 캘린더의 일정이에요. 장소를 아는 약속은 \"안내\"를 누르면 차의 내비가 길을 안내해요. 약속 사이 거리는 직선 거리에 길 굽이를 더해 어림한 값이에요.")
    }
}

/** 다음 약속 카드: 남은 시간 → 제목 → 때 · 장소 · 함께하는 사람 → (가는 중이면) 도착 예상 → 이어 보기 → 안내. */
@Composable
private fun NextAppt(a: Appt, agenda: List<Appt>, ui: UiState, now: LocalDateTime, canGuide: Boolean, links: List<String>, onGuide: () -> Unit) {
    val p = LocalPalette.current
    val e = a.event
    val arrival = Agenda.arrival(a, ui.nav, now)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        MwIcon(MwIcons.Calendar, size = 56.dp)
        Column(Modifier.weight(1f)) {
            Agenda.countdown(a, now)?.let { Text(it, color = p.textSecondary, style = Ivi.bodyMediumStrong) }
            Text(e.title, color = p.textPrimary, style = Ivi.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOf(Agenda.dateLabel(a), e.place, e.with).filter { it.isNotBlank() }.joinToString(" · "), color = p.textSecondary, style = Ivi.bodyMedium)
        }
    }
    val lines = listOfNotNull(Agenda.leg(agenda, a, ui.car.rangeKm)) + links
    if (arrival != null || lines.isNotEmpty()) {
        Spacer(Modifier.height(16.dp)); RowLine(); Spacer(Modifier.height(16.dp))
        arrival?.let { ar ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // 늦을 때만 표시를 달아요(색만으로 알리지 않고 말이 같이 있어요)
                if (ar.late) StateDot(alert = true)
                Text(ar.headline, color = p.textPrimary, style = Ivi.bodyLargeStrong)
            }
            Text(ar.detail, color = p.textSecondary, style = Ivi.bodyMedium)
            if (lines.isNotEmpty()) Spacer(Modifier.height(8.dp))
        }
        lines.forEach { Text(it, color = p.textSecondary, style = Ivi.bodyMedium) }
    }
    if (a.located && canGuide && arrival == null) {
        Spacer(Modifier.height(20.dp))
        BigButton("약속 장소로 안내", true, onGuide, Modifier.fillMaxWidth())
    }
}

/** 두 주 달력 띠: 요일 → 날짜 → 약속 수만큼의 점(셋까지). 오늘은 테두리 없이 굵은 글, 고른 날은 채운 바탕이에요. */
@Composable
private fun AgendaStrip(days: List<AgendaDay>, picked: LocalDate?, onPick: (LocalDate) -> Unit) {
    val p = LocalPalette.current
    days.chunked(7).forEachIndexed { row, week ->
        if (row > 0) Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            week.forEach { d ->
                val on = picked == d.date
                val fg = when { on -> p.textOnFilled; d.past -> p.textTertiary; else -> p.textPrimary }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(if (on) p.buttonFilled else if (d.today) p.surfaceHigh else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(role = Role.Button, onClickLabel = "이 날 약속만 보기") { onPick(d.date) }
                        .semantics { contentDescription = "${d.date.monthValue}월 ${d.date.dayOfMonth}일 ${Agenda.dow(d.date)}요일" + (if (d.today) " 오늘" else "") + " 약속 ${d.count}개" }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(if (d.today) "오늘" else Agenda.dow(d.date), color = if (on) p.textOnFilled else p.textTertiary, style = Ivi.labelSmall)
                    Text("${d.date.dayOfMonth}", color = fg, style = if (d.today || d.count > 0) Ivi.bodyLargeStrong else Ivi.bodyLarge)
                    Row(Modifier.height(10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        repeat(d.count.coerceAtMost(3)) { Box(Modifier.size(8.dp).clip(CircleShape).background(fg)) }
                    }
                }
            }
        }
    }
}

/** 약속 한 줄: 때(왼쪽) · 제목과 장소 · 이어 보기 · 안내 단추. 장소를 모르는 약속에는 단추가 없어요. */
@Composable
private fun ApptRow(a: Appt, now: LocalDateTime, canGuide: Boolean, extra: List<String>, onGuide: () -> Unit) {
    val p = LocalPalette.current
    val e = a.event
    val passed = Agenda.passed(a, now)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.width(190.dp)) {
            if (a.date != null) {
                Text("${a.date.monthValue}.${a.date.dayOfMonth}(${Agenda.dow(a.date)})", color = if (passed) p.textTertiary else p.textPrimary, style = Ivi.bodyMediumStrong)
                Text(a.time?.let { Agenda.timeLabel(it) } ?: "하루 종일", color = p.textSecondary, style = Ivi.labelMedium)
            } else Text(e.whenText, color = p.textSecondary, style = Ivi.labelMedium)
        }
        Column(Modifier.weight(1f)) {
            Text(e.title, color = if (passed) p.textTertiary else p.textPrimary, style = Ivi.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(Agenda.countdown(a, now), e.place.ifBlank { null }, e.with.ifBlank { null }).joinToString(" · "), color = p.textSecondary, style = Ivi.bodyMedium)
            extra.forEach { Text(it, color = p.textTertiary, style = Ivi.labelMedium) }
        }
        if (a.located && canGuide && !passed) PillButton("안내", onGuide, filled = false)
    }
}
