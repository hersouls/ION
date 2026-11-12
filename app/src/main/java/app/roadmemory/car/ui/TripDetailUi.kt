package app.roadmemory.car.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roadmemory.car.data.DemoUser
import app.roadmemory.car.brain.Korean
import app.roadmemory.car.brain.TripInsight
import app.roadmemory.car.data.SavedTrip
import app.roadmemory.car.data.Tracks
import app.roadmemory.car.data.TripBook
import app.roadmemory.car.navi.GuideResult
import app.roadmemory.car.navi.NaviLink
import app.roadmemory.car.navi.Place

/**
 * 여행 기록 한 건의 세부 화면(오너 2026-11-11 "여행 기록 눌렀을때 세부적인 내용을 볼 수 있게 하이엔드급으로").
 * 위에서부터: ① 어디를 언제 다녀왔는지 ② 지도(도착지 · 다녀온 곳)와 출발에서 도착까지 여정 그림 ③ 거리 · 걸린 시간 · 탄 사람 · 충전 ④ 다른 기록과 견준 말(순위 · 평균 · 같은 곳 · 오늘 즈음)
 * ⑤ 함께한 곡(곡마다 틀기) ⑥ 다음에 할 일(이곳으로 다시 안내 · 지우기).
 * 말은 모두 저장된 값과 저장된 다른 여행에서 센 값이에요([TripInsight]). 저장하지 않은 값(중간 경유지 · 충전한 곳 · 속도)은 말하지 않고, 값이 없으면 "—"예요.
 */
@Composable
internal fun TripDetail(
    t: SavedTrip, all: List<SavedTrip>,
    onPlay: (List<Tracks.Track>) -> Unit, onDelete: (Long) -> Unit,
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val d = remember(t, all) { TripInsight.detail(t, all) }
    var note by remember(t.id) { mutableStateOf<String?>(null) }
    var confirm by remember(t.id) { mutableStateOf(false) }

    // ① 어디를 언제
    Card {
        Text(d.agoLine, color = p.textTertiary, style = Ivi.labelMedium)
        Text(d.title, color = p.textPrimary, style = Ivi.titleLarge)
        Text(d.whenLine, color = p.textSecondary, style = Ivi.bodyMedium)
        d.thatDayLine?.let { Text(it, color = p.textPrimary, style = Ivi.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
    }

    // ② 지도(도착지와 다녀온 곳)와 출발에서 도착까지의 여정 한 줄(충전 · 시각 · 함께 탄 사람)
    Box(Modifier.fillMaxWidth().padding(bottom = 16.dp)) { TripJourneyArt(t, d, all, Modifier.fillMaxWidth().height(360.dp)) }

    // ③ 거리 · 걸린 시간 · 출발과 도착 시각 · 함께 탄 사람 · 충전은 위 그림이 말해요(2026-11-12, 숫자 칸을 따로 두면 같은 값이 두 번 나와서 뺐어요)

    // ④ 다른 기록과 견줘 보면
    Card {
        Text("다른 기록과 견줘 보면", color = p.textTertiary, style = Ivi.labelMedium)
        Spacer(Modifier.height(6.dp))
        Text(d.rankLine, color = p.textPrimary, style = Ivi.bodyLargeStrong)
        d.avgLine?.let { Text(it, color = p.textSecondary, style = Ivi.bodyMedium) }
        d.sameWayLine?.let { Text(it, color = p.textSecondary, style = Ivi.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
        if (d.sameWay.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            d.sameWay.forEach { o ->
                Text(
                    listOfNotNull(o.month, "${o.km}km", TripBook.peopleWord(o.people).takeIf { it.isNotEmpty() }?.let { "$it 탔어요" }).joinToString(" · "),
                    color = p.textSecondary, style = Ivi.bodyMedium,
                )
            }
        }
    }

    // ⑤ 함께한 곡
    Card {
        Text("함께한 곡", color = p.textTertiary, style = Ivi.labelMedium)
        if (t.tracks.isEmpty()) {
            Text("이 여행에는 ION이 틀어 준 곡이 없어요", color = p.textSecondary, style = Ivi.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        } else {
            Spacer(Modifier.height(6.dp))
            t.tracks.forEach { title ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MwIcon(MwIcons.Music, size = 28.dp)
                    Text(title, color = p.textPrimary, style = Ivi.bodyLargeStrong, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Tracks.byTitle(title)?.let { tr -> PillButton("틀기", { onPlay(listOf(tr)) }) }
                }
            }
            val playable = t.tracks.mapNotNull { Tracks.byTitle(it) }
            if (playable.size > 1) { Spacer(Modifier.height(8.dp)); PillButton("그때 곡 모두 틀기", { onPlay(playable) }, filled = true) }
        }
    }

    // ⑤-1 이 여행을 다녀온 달의 사진(디지털 앨범을 가져왔을 때만). 사진의 위치는 읽지 않아서 "그달의 사진"이라고만 말해요.
    TripMonthPhotos(t)

    // ⑥ 다음에 할 일
    Card {
        Text("다음에 할 일", color = p.textTertiary, style = Ivi.labelMedium)
        Spacer(Modifier.height(8.dp))
        note?.let { Text(it, color = p.textPrimary, style = Ivi.bodyMedium, modifier = Modifier.padding(bottom = 8.dp)) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val name = t.destName
            if (!name.isNullOrBlank() && t.lat != null && t.lon != null) {
                PillButton("이곳으로 다시 안내", {
                    note = "내비에 보내는 중이에요"
                    NaviLink.guide(ctx, Place(name, t.lat, t.lon)) { r -> note = if (r is GuideResult.Failed) r.why.label else "내비가 ${Korean.euro(name)} 안내를 시작해요" }
                }, filled = true)
            }
            PillButton(if (confirm) "한 번 더 누르면 지워요" else "이 기록 지우기", { if (confirm) onDelete(t.id) else confirm = true })
        }
        if (t.destName.isNullOrBlank() || t.lat == null || t.lon == null) {
            Text("이 여행은 내비 목적지를 몰라서 다시 안내할 수 없어요", color = p.textTertiary, style = Ivi.labelMedium, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** 여행을 다녀온 달에 찍은 앨범 사진(여섯 장까지 보이고, 누르면 그달 사진을 크게 넘겨 봐요). 가져온 앨범이 없거나 그달 사진이 없으면 아무것도 그리지 않아요. */
@Composable
private fun TripMonthPhotos(t: SavedTrip) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    app.roadmemory.car.studio.StudioLibrary.ensure(ctx)
    if (!app.roadmemory.car.studio.StudioLibrary.state.has("sync_${app.roadmemory.car.Pane.SVC_ALBUM}")) return
    val photos = remember(t.id) { app.roadmemory.car.brain.AlbumShelf.ofMonth(app.roadmemory.car.studio.StudioStore.catalog(ctx).album, t) }
    if (photos.isEmpty()) return
    var open by remember(t.id) { mutableStateOf<Int?>(null) }
    Card {
        Text("${t.month}에 찍은 사진 · ${photos.size}장", color = p.textTertiary, style = Ivi.labelMedium)
        Spacer(Modifier.height(10.dp))
        photos.take(6).chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { ph ->
                    StudioImage(
                        ph.thumb,
                        Modifier.weight(1f).aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp))
                            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "${ph.title} 크게 보기") { open = photos.indexOf(ph) },
                        ph.title,
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text("디지털 앨범에서 이 여행과 같은 달에 찍은 사진이에요", color = p.textSecondary, style = Ivi.bodyMedium)
    }
    open?.let { i -> PhotoLightbox(photos.map { it.image to it.title }, i, notes = photos.map { a -> if (a.region.isNotBlank()) "${a.month} · ${a.region}" else a.month }) { open = null } }
}
