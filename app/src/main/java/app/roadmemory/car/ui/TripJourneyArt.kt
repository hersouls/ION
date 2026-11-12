package app.roadmemory.car.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.roadmemory.car.brain.TripInsight
import app.roadmemory.car.data.DemoUser
import app.roadmemory.car.data.KoreaMap
import app.roadmemory.car.data.SavedTrip
import app.roadmemory.car.data.TripBook

/**
 * 여행 기록 세부 화면 맨 위 그림(오너 2026-11-11 "상단에 경로 이미지를 다시 만들어줘. 너무 허접함").
 *  · 왼쪽 지도: 대한민국 개략도에 이번 도착지를 핀으로(저장된 내비 목적지 좌표), 다녀온 다른 곳을 회색 점으로. 같은 곳은 조금 크게. 도착지를 모르면 핀이 없어요.
 *  · 오른쪽 여정([Journey]): 큰 거리와 칸 셋, 출발에서 도착지까지의 리본(충전 배지 · 거리 눈금 · 시각), 저장한 여행들의 거리 막대.
 * 출발지는 읽지 않아서 지도에 경로선은 긋지 않고, 모르는 값은 "—"로 둬요.
 */
@Composable
internal fun TripJourneyArt(t: SavedTrip, d: TripInsight.Detail, all: List<SavedTrip>, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val pinned = t.lat != null && t.lon != null && KoreaMap.project(t.lat, t.lon) != null
    Row(
        modifier.clip(RoundedCornerShape(32.dp)).background(p.backgroundPrimary).padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Column(Modifier.width(270.dp).fillMaxHeight()) {
            KoreaPanel(t, all, Modifier.weight(1f).fillMaxWidth())
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pinned) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(p.buttonFilled))
                    Text("이번 도착지", color = p.textSecondary, style = Ivi.labelSmall)
                    Box(Modifier.padding(start = 6.dp).size(9.dp).clip(CircleShape).background(p.textTertiary.copy(alpha = 0.6f)))
                    Text("다녀온 곳", color = p.textSecondary, style = Ivi.labelSmall)
                } else {
                    Text(if (t.lat != null && t.lon != null) "도착지가 지도 밖이에요" else "도착지를 몰라서 핀이 없어요", color = p.textTertiary, style = Ivi.labelSmall)
                }
            }
        }
        Journey(t, d, all, Modifier.weight(1f).fillMaxHeight())
    }
}

/** 상자 안에서 지도가 차지하는 자리(가로 · 세로 중 먼저 차는 쪽에 맞춰 가운데에). */
private class MapRect(val ox: Float, val oy: Float, val w: Float, val h: Float) {
    fun at(lat: Double, lon: Double): Offset {
        val (nx, ny) = KoreaMap.project(lat.coerceIn(KoreaMap.LAT_MIN, KoreaMap.LAT_MAX), lon.coerceIn(KoreaMap.LON_MIN, KoreaMap.LON_MAX))!!
        return Offset(ox + nx * w, oy + ny * h)
    }
    companion object {
        fun fit(width: Float, height: Float, pad: Float): MapRect {
            val availW = width - pad * 2; val availH = height - pad * 2
            val h = minOf(availH, (availW / KoreaMap.aspect).toFloat()); val w = (h * KoreaMap.aspect).toFloat()
            return MapRect((width - w) / 2f, (height - h) / 2f, w, h)
        }
    }
}

@Composable
private fun KoreaPanel(t: SavedTrip, all: List<SavedTrip>, modifier: Modifier) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier.clip(RoundedCornerShape(24.dp)).background(p.surfaceHigh)) {
        val pad = with(density) { 16.dp.toPx() }
        val r = MapRect.fit(with(density) { maxWidth.toPx() }, with(density) { maxHeight.toPx() }, pad)
        Canvas(Modifier.fillMaxSize()) {
            // 위도 · 경도 격자(1도마다, 아주 옅게)
            val grid = p.textTertiary.copy(alpha = 0.12f)
            var lat = 35.0; while (lat <= 38.0) { val y = r.at(lat, KoreaMap.LON_MIN).y; drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()); lat += 1.0 }
            var lon = 126.0; while (lon <= 129.0) { val x = r.at(KoreaMap.LAT_MAX, lon).x; drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx()); lon += 1.0 }

            // 땅: 해안은 부드러운 곡선, 휴전선은 곧은 선
            val land = Path().apply {
                val c = KoreaMap.coast.map { r.at(it.first, it.second) }
                moveTo(c.first().x, c.first().y)
                for (i in 0 until c.size - 1) {
                    val mid = Offset((c[i].x + c[i + 1].x) / 2f, (c[i].y + c[i + 1].y) / 2f)
                    if (i == 0) lineTo(mid.x, mid.y) else quadraticBezierTo(c[i].x, c[i].y, mid.x, mid.y)
                }
                lineTo(c.last().x, c.last().y)
                KoreaMap.dmz.forEach { val o = r.at(it.first, it.second); lineTo(o.x, o.y) }
                close()
            }
            drawPath(land, p.backgroundPrimary)
            drawPath(land, p.textTertiary.copy(alpha = 0.6f), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))

            // 길잡이 도시(속이 빈 작은 점)
            KoreaMap.cities.forEach { (_, la, lo) -> val o = r.at(la, lo); drawCircle(p.textTertiary, 3.5.dp.toPx(), o, style = Stroke(1.4.dp.toPx())) }

            // 다녀온 다른 곳(회색 점). 이번과 같은 곳은 조금 크게
            val here = TripBook.tripsTo(all, t.destName, t.lat, t.lon).map { it.id }.toSet()
            all.forEach { o ->
                if (o.id == t.id || o.lat == null || o.lon == null || KoreaMap.project(o.lat, o.lon) == null) return@forEach
                val same = o.id in here
                drawCircle(if (same) p.buttonFilled.copy(alpha = 0.55f) else p.textTertiary.copy(alpha = 0.6f), (if (same) 7f else 5f).dp.toPx(), r.at(o.lat, o.lon))
            }

            // 이번 도착지: 번지는 둥근 빛 + 핀
            if (t.lat != null && t.lon != null && KoreaMap.project(t.lat, t.lon) != null) {
                val dest = r.at(t.lat, t.lon)
                drawCircle(p.buttonFilled.copy(alpha = 0.14f), 34.dp.toPx(), dest)
                drawCircle(p.buttonFilled.copy(alpha = 0.26f), 21.dp.toPx(), dest)
                drawCircle(p.backgroundPrimary, 11.dp.toPx(), dest)
                drawCircle(p.buttonFilled, 8.dp.toPx(), dest)
            }
        }
        // 도시 이름(부산은 오른쪽 끝이라 점의 왼쪽에)
        KoreaMap.cities.forEach { (name, la, lo) ->
            val o = r.at(la, lo)
            val left = lo > 128.0 || name == "서울"   // 부산은 오른쪽 끝, 서울은 도착 핀의 빛과 겹치지 않게 왼쪽에
            val x = with(density) { (if (left) o.x - 34.dp.toPx() else o.x + 8.dp.toPx()).toDp() }
            val y = with(density) { (o.y - 9.dp.toPx()).toDp() }
            Text(name, color = p.textTertiary, style = Ivi.labelSmall, modifier = Modifier.offset(x = x, y = y))
        }
    }
}

/**
 * 오른쪽 여정(오너 2026-11-12 "이동경로 선이 너무 허접 · 하이엔드급으로 다시"). 위치를 읽지 않아서 지어낸 경로는 그리지 않고, 저장한 값만 세 층으로 보여 줘요.
 *  ① 큰 거리와 칸 셋(걸린 시간 · 충전 · 함께 탄 사람) ② 여정 리본: 출발(번지는 빛) → 그라데이션 길 → 도착(진한 고리), 충전 배지(자리는 기록하지 않아서 고르게), 거리 눈금 · 출발과 도착 시각
 *  ③ 저장한 여행들의 거리 막대(오래된 것부터), 이번 여행만 강조색이에요.
 */
@Composable
private fun Journey(t: SavedTrip, d: TripInsight.Detail, all: List<SavedTrip>, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        // ① 큰 거리와 칸 셋(도착지 이름은 이 그림을 쓰는 화면의 제목에 있어요)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.weight(1f)) {
                Text("${t.km}", color = p.textPrimary, style = Ivi.titleLarge)
                Text("km", color = p.textSecondary, style = Ivi.bodyLargeStrong, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))
            }
            StatChip("걸린 시간", d.duration ?: "—")
            StatChip("충전", if (t.charges > 0) "${t.charges}번" else "없어요")
            StatChip("함께 탄 사람", if (t.people > 0) "${t.people}명" else "몰라요", DemoUser.withTrip(t.people))   // 오너가 정한 가상 사용자 이름(문유님)을 여행 세부에 보여요
        }
        // ③ 여정 리본 ④ 저장한 여행들의 거리
        Ribbon(t, d, Modifier.fillMaxWidth().height(112.dp))
        TripBars(t, all, Modifier.fillMaxWidth().height(88.dp))
    }
}

@Composable
private fun StatChip(label: String, value: String, sub: String? = null) {
    val p = LocalPalette.current
    Column(Modifier.clip(RoundedCornerShape(16.dp)).background(p.surfaceHigh).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(label, color = p.textTertiary, style = Ivi.labelSmall, maxLines = 1, softWrap = false)
        Text(value, color = p.textPrimary, style = Ivi.bodyLargeStrong, maxLines = 1, softWrap = false)
        sub?.let { Text(it, color = p.textSecondary, style = Ivi.labelSmall, maxLines = 1, softWrap = false) }
    }
}

/** 리본의 가로 자리: 끝 점 반지름만큼 안쪽에서 시작하고 끝나요(그림과 글자가 같은 자리를 써요). */
private val RIBBON_END = 24.dp
private val RIBBON_Y = 30.dp

/** 출발(번지는 빛) → 그라데이션 길 → 도착(진한 고리). 충전 배지는 자리를 기록하지 않아서 고르게 놓아요. 아래로 거리 눈금 글자, 맨 아래 출발 · 도착 시각. */
@Composable
private fun Ribbon(t: SavedTrip, d: TripInsight.Detail, modifier: Modifier) {
    val p = LocalPalette.current
    val n = t.charges.coerceIn(0, 4)
    BoxWithConstraints(modifier) {
        val w = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val y = RIBBON_Y.toPx()
            val x0 = RIBBON_END.toPx(); val x1 = size.width - RIBBON_END.toPx()
            // 길 바탕(넓고 옅게) → 출발에서 도착으로 짙어지는 길 → 위쪽의 가는 빛
            drawLine(p.surfaceAccent, Offset(x0, y), Offset(x1, y), 22.dp.toPx(), StrokeCap.Round)
            drawLine(Brush.horizontalGradient(listOf(p.buttonFilled.copy(alpha = 0.35f), p.buttonFilled), startX = x0, endX = x1), Offset(x0, y), Offset(x1, y), 10.dp.toPx(), StrokeCap.Round)
            drawLine(p.backgroundPrimary.copy(alpha = 0.45f), Offset(x0 + 6.dp.toPx(), y - 2.5.dp.toPx()), Offset(x1 - 6.dp.toPx(), y - 2.5.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
            if (t.km > 0) TripInsight.scaleTicks(t.km).forEach { k ->
                val x = x0 + (x1 - x0) * k / t.km.toFloat()
                drawLine(p.textTertiary.copy(alpha = 0.55f), Offset(x, y + 15.dp.toPx()), Offset(x, y + 21.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
            }
            for (i in 1..n) {
                val c = Offset(x0 + (x1 - x0) * i / (n + 1f), y)
                drawCircle(p.backgroundPrimary, 18.dp.toPx(), c)
                drawCircle(p.informativeActive, 15.dp.toPx(), c)
                drawBolt(c, 8.5.dp.toPx(), p.backgroundPrimary)
            }
            val s = Offset(x0, y)
            drawCircle(p.buttonFilled.copy(alpha = 0.12f), 24.dp.toPx(), s)
            drawCircle(p.buttonFilled.copy(alpha = 0.24f), 17.dp.toPx(), s)
            drawCircle(p.buttonFilled, 11.dp.toPx(), s)
            drawCircle(p.backgroundPrimary, 4.dp.toPx(), s)
            val e = Offset(x1, y)
            drawCircle(p.backgroundPrimary, 17.dp.toPx(), e)
            drawCircle(p.textPrimary, 14.dp.toPx(), e, style = Stroke(4.dp.toPx()))
            drawCircle(p.textPrimary, 5.5.dp.toPx(), e)
        }
        // 거리 눈금 글자(양 끝 점과 겹치는 눈금은 글자를 빼요)
        if (t.km > 0) {
            val ticks = TripInsight.scaleTicks(t.km)
            ticks.forEachIndexed { i, k ->
                val f = k / t.km.toFloat()
                val x = RIBBON_END + (w - RIBBON_END * 2) * f
                if (x < 60.dp || x > w - 60.dp) return@forEachIndexed
                Box(Modifier.offset(x = x - 40.dp, y = RIBBON_Y + 22.dp).width(80.dp), contentAlignment = Alignment.Center) {
                    Text(if (i == ticks.lastIndex) "${k}km" else "$k", color = p.textTertiary, style = Ivi.labelSmall, softWrap = false, maxLines = 1)
                }
            }
        }
        Row(Modifier.fillMaxWidth().align(Alignment.BottomStart), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(d.startTime?.let { "$it 출발" } ?: "출발 시각 —", color = p.textSecondary, style = Ivi.labelMedium)
            Text("${d.arriveTime} 도착", color = p.textPrimary, style = Ivi.labelMedium)
        }
    }
}

/** 저장한 여행들의 거리를 오래된 것부터 막대로. 이번 여행만 강조색이고, 가장 긴 거리를 오른쪽에 적어요(모두 저장한 값이에요). */
@Composable
private fun TripBars(t: SavedTrip, all: List<SavedTrip>, modifier: Modifier) {
    val p = LocalPalette.current
    val list = (all.filter { it.id != t.id || it.at != t.at } + t).distinctBy { it.id to it.at }.sortedBy { it.at }.takeLast(24)
    val max = list.maxOfOrNull { it.km }?.coerceAtLeast(1) ?: 1
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Text("저장한 여행 ${list.size}건의 거리 · 점이 이번 여행", color = p.textTertiary, style = Ivi.labelSmall, modifier = Modifier.weight(1f), maxLines = 1, softWrap = false)
            Text("가장 긴 ${max}km", color = p.textTertiary, style = Ivi.labelSmall, maxLines = 1, softWrap = false)
        }
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val gap = 5.dp.toPx()
            val top = 12.dp.toPx()   // 이번 여행 점이 들어갈 자리
            val bw = ((size.width - gap * (list.size - 1)) / list.size).coerceAtMost(44.dp.toPx())
            val total = bw * list.size + gap * (list.size - 1)
            var x = size.width - total   // 오른쪽(최근)에 붙여요
            drawLine(p.textTertiary.copy(alpha = 0.25f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            list.forEach { o ->
                val h = ((size.height - top) * o.km / max.toFloat()).coerceAtLeast(4.dp.toPx())
                val me = o.at == t.at && o.km == t.km
                // 이번 여행: 막대 위에 강조 점(짧은 여행도 어디인지 보이게)
                if (me) drawCircle(p.buttonFilled, 5.dp.toPx(), Offset(x + bw / 2f, size.height - h - 9.dp.toPx()))
                drawRoundRect(
                    if (me) p.buttonFilled else p.textTertiary.copy(alpha = 0.32f),
                    topLeft = Offset(x, size.height - h), size = androidx.compose.ui.geometry.Size(bw, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                )
                x += bw + gap
            }
        }
    }
}

private fun DrawScope.drawBolt(c: Offset, s: Float, color: Color) {
    val pts = listOf(0.15f to -1.0f, -0.6f to 0.15f, -0.05f to 0.15f, -0.2f to 1.0f, 0.6f to -0.2f, 0.05f to -0.2f)
    val path = Path().apply {
        pts.forEachIndexed { i, (x, y) -> val o = Offset(c.x + x * s, c.y + y * s); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
        close()
    }
    drawPath(path, color)
}
