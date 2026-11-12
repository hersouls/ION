package app.roadmemory.car.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.roadmemory.car.data.CarSnapshot
import app.roadmemory.car.data.SeatPos
import kotlin.math.roundToInt

/** 충전 링: 지금 %까지 채워지고(informative_active) 목표(%)에 눈금이 있어요. 목표는 충전 계획이 있을 때만 의미가 있어요. */
@Composable
fun RingArt(percent: Int, target: Int?, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Canvas(modifier) {
        val stroke = size.minDimension * 0.07f
        // 80% 눈금이 링 밖으로 stroke만큼 나와서, 그 몫을 비워 두고 그려요(옆 칸에 닿지 않게)
        val arcSize = Size(size.minDimension - stroke * 3, size.minDimension - stroke * 3)
        val topLeft = Offset((size.width - arcSize.width) / 2f, (size.height - arcSize.height) / 2f)
        drawArc(p.surfaceAccent, 135f, 270f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        // 채운 만큼은 회색 단계의 진한 색(배터리 막대와 같아요). 충전 중인지는 글로 말해요
        drawArc(p.buttonFilled, 135f, 270f * (percent.coerceIn(0, 100) / 100f), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        if (target != null) {
            val ang = Math.toRadians(135.0 + 270.0 * target / 100.0)
            val r = arcSize.width / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            val a = Offset(c.x + ((r - stroke) * Math.cos(ang)).toFloat(), c.y + ((r - stroke) * Math.sin(ang)).toFloat())
            val b = Offset(c.x + ((r + stroke) * Math.cos(ang)).toFloat(), c.y + ((r + stroke) * Math.sin(ang)).toFloat())
            drawLine(p.textPrimary, a, b, strokeWidth = stroke * 0.35f, cap = StrokeCap.Round)
        }
    }
}

/** 위에서 본 좌석 지도. 차가 알려 주는 네 자리(착좌 · 벨트)만 그려요. */
@Composable
fun SeatMap(car: CarSnapshot, modifier: Modifier = Modifier, checkBelts: Boolean = false) {
    // 칸 폭에 맞춰 자리 둘이 반씩 나눠 가져요(고정 폭이면 좁은 칸에서 한쪽이 찌그러져요)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        SeatRow(car, SeatPos.DRIVER, SeatPos.PASSENGER, checkBelts)
        Spacer(Modifier.height(24.dp))
        SeatRow(car, SeatPos.REAR_LEFT, SeatPos.REAR_RIGHT, checkBelts)
    }
}

@Composable
private fun SeatRow(car: CarSnapshot, left: SeatPos, right: SeatPos, checkBelts: Boolean) {
    Row(Modifier.fillMaxWidth().widthIn(max = 524.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        SeatChip(left, car, checkBelts, Modifier.weight(1f)); SeatChip(right, car, checkBelts, Modifier.weight(1f))
    }
}

/** checkBelts(시안 16 안전벨트 확인): 앉아 있는데 벨트가 채워지지 않은 자리는 점선 테두리와 "벨트 확인 필요"로 말해요. */
@Composable
private fun SeatChip(pos: SeatPos, car: CarSnapshot, checkBelts: Boolean, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val on = car.occupied(pos)
    val needsCheck = checkBelts && on && car.seats[pos]?.belted == false
    val dashed = if (needsCheck) Modifier.drawBehind {
        drawRoundRect(
            color = p.informativeNegative, cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
            style = Stroke(width = 4.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(24f, 16f))),
        )
    } else Modifier
    Box(
        modifier
            .heightIn(min = 140.dp)
            .background(if (on && !needsCheck) p.buttonFilled else p.surfaceHigh, RoundedCornerShape(24.dp))
            .then(dashed),
        contentAlignment = Alignment.Center,
    ) {
        val strong = on && !needsCheck
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(pos.label, color = if (strong) p.textOnFilled else if (needsCheck) p.textPrimary else p.textTertiary, style = Ivi.bodySmallStrong, textAlign = TextAlign.Center)
            Text(
                when {
                    needsCheck -> "벨트 확인 필요"
                    on -> if (car.seats[pos]?.belted == true) "착좌 · 벨트 체결" else "착좌"
                    else -> "비어 있어요"
                },
                color = if (strong) p.textOnFilled else if (needsCheck) p.textPrimary else p.textTertiary, style = Ivi.labelMedium,
            )
        }
    }
}
