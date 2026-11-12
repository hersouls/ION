package app.roadmemory.car.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.roadmemory.car.ontology.Verdict

/*
 * 서비스 화면(ION 홈 · 충전 · 가족 · 점검 · 여행 기록 · 데이터)이 함께 쓰는 작은 그림들이에요.
 * 색은 팔레트 토큰의 회색 단계(textPrimary · surfaceAccent …)만 쓰고, 새 강조색을 만들지 않아요.
 * 주의가 필요한 곳(열림 · 벨트)만 상태색(informativeNegative)이고, 그때도 말이 함께 있어요(색만으로 알리지 않아요).
 */

/** Moonwave 아이콘 키트의 한 아이콘(MwIcons)을 24칸 기준으로 그려요. */
@Composable
internal fun MwIcon(icon: MwIconData, modifier: Modifier = Modifier, size: Dp = 40.dp, tint: Color = LocalPalette.current.textPrimary) {
    val paths = remember(icon) {
        icon.paths.map { (d, evenOdd) ->
            PathParser().parsePathString(d).toPath().apply { fillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero }
        }
    }
    Canvas(modifier.size(size)) {
        scale(this.size.minDimension / 24f, pivot = Offset.Zero) { paths.forEach { drawPath(it, tint) } }
    }
}

/** 배터리 막대: 채운 만큼이 진한 회색이에요. 값이 없으면 그리지 않아요(호출하는 쪽에서 판단). */
@Composable
internal fun BatteryBar(percent: Int, modifier: Modifier = Modifier, barHeight: Dp = 20.dp) {
    val p = LocalPalette.current
    val look = LocalLookColors.current
    Canvas(modifier.fillMaxWidth().height(barHeight)) {
        val r = size.height / 2f
        drawRoundRect(p.surfaceAccent, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
        val w = (size.width * percent.coerceIn(0, 100) / 100f).coerceAtLeast(size.height)
        if (look != null) drawRoundRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(look.barA.toInt()), Color(look.barB.toInt())), endX = w), size = Size(w, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
        else drawRoundRect(p.buttonFilled, size = Size(w, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
        // 25 · 50 · 75% 눈금 — 막대가 몇 칸쯤인지 한눈에 보이게
        listOf(0.25f, 0.5f, 0.75f).forEach { t ->
            val x = size.width * t
            drawLine(if (x <= w) p.backgroundPrimary else p.textTertiary, Offset(x, size.height * 0.28f), Offset(x, size.height * 0.72f), size.height * 0.08f, StrokeCap.Round)
        }
    }
}

/** 판정 표시(● ◐ ○)를 글자 대신 그려요. 글자 모양은 글꼴마다 달라서 크기와 위치가 흔들렸어요. */
@Composable
internal fun VerdictMark(v: Verdict, modifier: Modifier = Modifier, size: Dp = 26.dp) {
    val p = LocalPalette.current
    Canvas(modifier.size(size).semantics { contentDescription = v.label }) {
        val stroke = this.size.minDimension * 0.12f
        val r = (this.size.minDimension - stroke) / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        when (v) {
            Verdict.CAN -> drawCircle(p.textPrimary, r + stroke / 2f, c)
            Verdict.NOT -> drawCircle(p.textPrimary, r, c, style = Stroke(stroke))
            Verdict.PARTLY -> {
                drawCircle(p.textPrimary, r, c, style = Stroke(stroke))
                val half = Path().apply { addArc(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r), 90f, 180f); close() }
                drawPath(half, p.textPrimary)
            }
        }
    }
}

/**
 * 빈 화면(Moonwave 빈 화면 패턴): 그림 하나 → 무엇이 없는지 → 어떻게 채워지는지. 다음에 할 일이 없으면 버튼은 없어요.
 */
@Composable
internal fun EmptyNote(icon: MwIconData, title: String, body: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        MwIcon(icon, size = 64.dp, tint = p.textTertiary)
        Spacer(Modifier.height(16.dp))
        Text(title, color = p.textPrimary, style = Ivi.titleSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, color = p.textSecondary, style = Ivi.bodyLarge, textAlign = TextAlign.Center)
    }
}

/**
 * 카드 밖, 창 바탕 위에 바로 놓이는 글 뒤에 화면 바탕색(ivi_background_secondary 자리)을 깔아요. 디스플레이 테마를 적용하면 창 바탕이 그림이라
 * 글자 대비(4.5 : 1)가 그림에 따라 떨어져요. 테마가 없으면 창 바탕과 같은 색이라 보이지 않아요. 자리를 차지하지 않고 바깥으로 [out]만큼 넓게 그려요.
 */
internal fun Modifier.onBackdrop(color: Color, out: Dp = 12.dp): Modifier = drawBehind {
    val o = out.toPx()
    drawRoundRect(color, Offset(-o, -o / 2), Size(size.width + o * 2, size.height + o), CornerRadius(20.dp.toPx()))
}

/** 글 한 줄 앞에 작은 아이콘이 붙은 안내(읽기 쉬운 크기, 회색 단계). */
@Composable
internal fun NoteLine(text: String, icon: MwIconData = MwIcons.Info, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    androidx.compose.foundation.layout.Row(modifier.fillMaxWidth().padding(top = 4.dp).onBackdrop(p.backgroundSecondary), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MwIcon(icon, size = 28.dp, tint = p.textTertiary, modifier = Modifier.padding(top = 4.dp))
        Text(text, color = p.textSecondary, style = Ivi.bodySmall, modifier = Modifier.weight(1f))
    }
}

/** 상태 점 + 말: 이상이 있을 때만 상태색, 아니면 회색 단계(색만으로 알리지 않아요 — 점 옆에 말이 늘 있어요). */
@Composable
internal fun StateDot(alert: Boolean, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Canvas(modifier.size(14.dp)) { drawCircle(if (alert) p.informativeNegative else p.textTertiary, this.size.minDimension / 2f) }
}
