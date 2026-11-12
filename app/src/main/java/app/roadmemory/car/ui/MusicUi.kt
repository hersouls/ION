package app.roadmemory.car.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roadmemory.car.UiState
import app.roadmemory.car.brain.MusicShelf
import app.roadmemory.car.data.Playback
import app.roadmemory.car.data.Tracks
import java.time.LocalDate
import java.util.Calendar
import kotlinx.coroutines.delay

// ───────── 디지털 뮤직: 지금 트는 곡 → 지금 어울리는 곡 → 분위기 묶음 → 곡 목록. 규칙은 brain/Shelves.kt의 MusicShelf ─────────

/**
 * 디지털 뮤직 = 마이현대에 등록한 내 곡을 차에서 듣는 서비스예요.
 *  · 지금 트는 곡: 지난 시간 막대 · 곡의 분위기 · 이어질 곡 · 이전 · 다음 · 멈추기.
 *  · 지금 어울리는 곡: 날씨 · 시간 · 달 · 충전 중인지에 맞는 곡을 많이 맞는 순서로 고르고 까닭을 달아요. 맞는 곡이 없으면 이 칸이 없어요.
 *  · 분위기 묶음: 달릴 때 · 쉬어 갈 때 · 비 · 밤 · 저녁 · 가을. 누르면 그 곡들만 보이고 "이 묶음 틀기"로 이어 틀어요.
 *  · 곡마다 분위기와, 저장한 여행에서 튼 적이 있으면 "여행 N번에 함께했어요"가 붙어요.
 */
@Composable
internal fun MusicWorkflowPane(ui: UiState, def: ServiceDef, onHome: () -> Unit, onPlay: (List<Tracks.Track>) -> Unit, onStop: () -> Unit, onSkip: (Int) -> Unit = {}) {
    val p = LocalPalette.current
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val month = remember { LocalDate.now().monthValue }
    var group by remember { mutableStateOf<String?>(null) }
    SyncPane(def, onHome, emptyList(), "곡") {
        NowPlayingCard(ui, onPlay, onStop, onSkip)

        val now = MusicShelf.forNow(ui, hour, month)
        if (now.isNotEmpty()) {
            SectionTitle("지금 어울리는 곡 · ${MusicShelf.nowLabel(ui, hour, month)}")
            Card {
                now.take(3).forEachIndexed { i, pick ->
                    if (i > 0) { Spacer(Modifier.height(14.dp)); RowLine(); Spacer(Modifier.height(14.dp)) }
                    TrackRow(pick.track, "${pick.hits.joinToString(" · ")}에 맞아요", ui, onPlay, onStop)
                }
                if (now.size > 1) {
                    Spacer(Modifier.height(18.dp))
                    BigButton("어울리는 ${now.size}곡 이어서 틀기", true, { onPlay(now.map { it.track }) }, Modifier.fillMaxWidth())
                }
            }
        }

        SectionTitle("분위기로 고르기")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("모든 곡 ${Tracks.all.size}", { group = null }, filled = group == null)
            MusicShelf.groups.forEach { (label, _) -> PillButton("$label ${MusicShelf.group(label).size}", { group = if (group == label) null else label }, filled = group == label) }
        }
        val shown = group?.let { MusicShelf.group(it) } ?: Tracks.all
        Card {
            shown.forEachIndexed { i, tr ->
                if (i > 0) { Spacer(Modifier.height(14.dp)); RowLine(); Spacer(Modifier.height(14.dp)) }
                TrackRow(tr, listOfNotNull(MusicShelf.tags(tr.title).joinToString(" · ").ifEmpty { null }, MusicShelf.tripLine(tr.title, ui.trips)).joinToString(" · "), ui, onPlay, onStop)
            }
            Spacer(Modifier.height(18.dp))
            BigButton(if (group == null) "모두 이어서 틀기" else "이 묶음 ${shown.size}곡 틀기", now.size <= 1, { onPlay(shown) }, Modifier.fillMaxWidth())
        }
        NoteLine("마이현대에 등록한 곡이에요. 이 앱에서는 듣기만 해요. 노래 ${Tracks.songs}곡과 가사 없는 연주곡 ${Tracks.instrumentals}곡이에요. 차의 미디어 키(이전 · 다음 · 멈춤)로도 조작할 수 있어요.")
    }
}

/** 지금 트는 곡: 제목 · 분위기 · 지난 시간 막대 · 이어질 곡. 조용하면 한 줄만 보여요. */
@Composable
private fun NowPlayingCard(ui: UiState, onPlay: (List<Tracks.Track>) -> Unit, onStop: () -> Unit, onSkip: (Int) -> Unit) {
    val p = LocalPalette.current
    val title = ui.nowPlaying
    if (title == null) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                MwIcon(MwIcons.Music, size = 48.dp, tint = p.textTertiary)
                Column(Modifier.weight(1f)) {
                    Text("조용해요", color = p.textTertiary, style = Ivi.labelMedium)
                    Text("곡을 고르거나 아래에서 이어 틀어요", color = p.textPrimary, style = Ivi.bodyLargeStrong)
                }
            }
        }
        return
    }
    // 지난 시간은 플레이어에게 0.5초마다 물어요(곡이 바뀌면 처음부터)
    var progress by remember(title) { mutableStateOf<Pair<Int, Int>?>(null) }
    var next by remember(title) { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(title) { while (true) { progress = Playback.probe(); next = Playback.next(); delay(500) } }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            MwIcon(MwIcons.Music, size = 56.dp)
            Column(Modifier.weight(1f)) {
                Text("지금 트는 곡", color = p.textTertiary, style = Ivi.labelMedium)
                Text(title, color = p.textPrimary, style = Ivi.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                listOfNotNull(MusicShelf.tags(title).joinToString(" · ").ifEmpty { null }, MusicShelf.tripLine(title, ui.trips)).joinToString(" · ").takeIf { it.isNotEmpty() }
                    ?.let { Text(it, color = p.textSecondary, style = Ivi.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        progress?.let { (pos, dur) ->
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).background(p.surfaceHigh, RoundedCornerShape(3.dp))) {
                Box(Modifier.fillMaxWidth((pos.toFloat() / dur).coerceIn(0f, 1f)).height(6.dp).background(p.buttonFilled, RoundedCornerShape(3.dp)))
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(MusicShelf.clock(pos), color = p.textTertiary, style = Ivi.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(MusicShelf.clock(dur), color = p.textTertiary, style = Ivi.labelMedium)
            }
        }
        Text(if (next.isEmpty()) "이 곡이 끝나면 멈춰요" else "다음 곡 · ${next.first()}" + if (next.size > 1) " 밖에 ${next.size - 1}곡" else "", color = p.textSecondary, style = Ivi.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("이전", { onSkip(-1) })
            PillButton("다음", { onSkip(1) })
            PillButton("멈추기", onStop, filled = true)
        }
    }
}

@Composable
private fun TrackRow(tr: Tracks.Track, sub: String, ui: UiState, onPlay: (List<Tracks.Track>) -> Unit, onStop: () -> Unit) {
    val p = LocalPalette.current
    val on = ui.nowPlaying == tr.title
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        MwIcon(MwIcons.Music, size = 40.dp, tint = if (on) p.textPrimary else p.textTertiary)
        Column(Modifier.weight(1f)) {
            Text(tr.title + if (on) " · 트는 중" else "", color = p.textPrimary, style = Ivi.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) Text(sub, color = p.textSecondary, style = Ivi.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (on) PillButton("멈추기", onStop) else PillButton("틀기", { onPlay(listOf(tr)) })
    }
}
