package app.roadmemory.car.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.roadmemory.car.data.CarPart
import app.roadmemory.car.data.CarSnapshot
import app.roadmemory.car.data.SeatPos
import app.roadmemory.car.engine.CardEngine

/*
 * 카드마다 왼쪽에 놓는 그림(제안서의 시안 4 · 12 · 15 · 17 · 19 · 20). 색은 모두 Pleos 색 토큰(팔레트)이고, 열림 · 부족처럼 주의가 필요한 곳만
 * 상태색(informative_negative)을 써요. 그림은 차가 알려 주는 값으로만 그려요(값을 모르는 곳은 그리지 않아요).
 */

private val Dashed = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))

@Composable
private fun ArtBox(modifier: Modifier, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Box(modifier.clip(RoundedCornerShape(32.dp)).background(p.surfaceHigh).padding(28.dp)) { content() }
}

/** 위에서 본 차(시안 17 · 19): 문 네 개, 트렁크, 후드가 열렸는지와 앉은 자리, 실내 온도 또는 워셔액. 열린 곳은 바깥으로 벌어진 선으로 그려요. */
@Composable
fun CarTopArt(car: CarSnapshot, modifier: Modifier = Modifier, thermometer: Boolean = false, washer: Boolean = false) {
    val p = LocalPalette.current
    ArtBox(modifier) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.weight(1f).fillMaxWidth()) {
                    val bodyW = size.height * 0.36f
                    val bodyH = size.height * 0.92f
                    val x0 = (size.width - bodyW) / 2f
                    val y0 = (size.height - bodyH) / 2f
                    val line = 5f
                    drawRoundRect(p.backgroundPrimary, Offset(x0, y0), Size(bodyW, bodyH), androidx.compose.ui.geometry.CornerRadius(bodyW * 0.32f))
                    drawRoundRect(p.surfaceAccent, Offset(x0, y0), Size(bodyW, bodyH), androidx.compose.ui.geometry.CornerRadius(bodyW * 0.32f), style = Stroke(line))
                    // 앞유리와 뒷유리
                    drawLine(p.surfaceAccent, Offset(x0 + bodyW * 0.12f, y0 + bodyH * 0.20f), Offset(x0 + bodyW * 0.88f, y0 + bodyH * 0.20f), line)
                    drawLine(p.surfaceAccent, Offset(x0 + bodyW * 0.12f, y0 + bodyH * 0.80f), Offset(x0 + bodyW * 0.88f, y0 + bodyH * 0.80f), line)
                    // 앉은 자리(점 하나가 한 사람)
                    val seats = listOf(
                        SeatPos.DRIVER to Offset(0.28f, 0.40f), SeatPos.PASSENGER to Offset(0.72f, 0.40f),
                        SeatPos.REAR_LEFT to Offset(0.28f, 0.64f), SeatPos.REAR_RIGHT to Offset(0.72f, 0.64f),
                    )
                    seats.forEach { (pos, at) ->
                        val c = Offset(x0 + bodyW * at.x, y0 + bodyH * at.y)
                        drawRoundRect(p.surfaceAccent, Offset(c.x - bodyW * 0.15f, c.y - bodyH * 0.055f), Size(bodyW * 0.30f, bodyH * 0.11f), androidx.compose.ui.geometry.CornerRadius(14f), style = Stroke(3f))
                        if (car.occupied(pos)) drawCircle(p.buttonFilled, bodyW * 0.085f, c)
                    }
                    // 문: 닫힘은 옆면의 얇은 선, 열림은 앞쪽 경첩에서 바깥으로 벌어진 굵은 선
                    fun door(part: CarPart, left: Boolean, yFrom: Float, yTo: Float) {
                        val x = if (left) x0 else x0 + bodyW
                        val a = Offset(x, y0 + bodyH * yFrom)
                        val b = Offset(x, y0 + bodyH * yTo)
                        if (car.isOpen(part)) {
                            val len = bodyH * (yTo - yFrom)
                            val dir = if (left) -1f else 1f
                            drawLine(p.informativeNegative, a, Offset(a.x + dir * len * 0.80f, a.y + len * 0.60f), line * 1.8f, StrokeCap.Round)
                        } else {
                            drawLine(p.textTertiary, a, b, line)
                        }
                    }
                    door(CarPart.DRIVER_DOOR, true, 0.30f, 0.52f); door(CarPart.PASSENGER_DOOR, false, 0.30f, 0.52f)
                    door(CarPart.REAR_LEFT_DOOR, true, 0.54f, 0.76f); door(CarPart.REAR_RIGHT_DOOR, false, 0.54f, 0.76f)
                    // 후드(앞)와 트렁크(뒤): 열리면 가장자리에서 바깥으로 접힌 선
                    fun flap(open: Boolean, atTop: Boolean) {
                        val y = if (atTop) y0 + bodyH * 0.02f else y0 + bodyH * 0.98f
                        val a = Offset(x0 + bodyW * 0.25f, y); val b = Offset(x0 + bodyW * 0.75f, y)
                        if (open) {
                            val dy = if (atTop) -bodyH * 0.09f else bodyH * 0.09f
                            drawLine(p.informativeNegative, a, Offset(a.x, a.y + dy), line * 1.8f, StrokeCap.Round)
                            drawLine(p.informativeNegative, b, Offset(b.x, b.y + dy), line * 1.8f, StrokeCap.Round)
                        } else drawLine(p.textTertiary, a, b, line)
                    }
                    flap(car.isOpen(CarPart.HOOD), true); flap(car.isOpen(CarPart.TRUNK), false)
                }
                if (washer) {
                    Spacer(Modifier.height(16.dp))
                    val low = car.washerLow == true
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("워셔액", color = p.textSecondary, style = Ivi.labelMedium)
                        Box(Modifier.width(220.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(p.surfaceAccent)) {
                            Box(Modifier.fillMaxHeight().width(if (low) 26.dp else 220.dp).background(if (low) p.informativeNegative else p.buttonFilled))
                        }
                        Text(if (low) "부족해요" else "넉넉해요", color = if (low) p.textPrimary else p.textTertiary, style = Ivi.labelMedium)
                    }
                }
            }
            if (thermometer) Thermometer(car)
        }
    }
}

/** 실내 온도계(시안 17). 값은 차의 표시 단위로 말해요. */
@Composable
private fun Thermometer(car: CarSnapshot) {
    val p = LocalPalette.current
    val c = car.cabinTempC
    val fill = ((c ?: 0f).coerceIn(0f, 50f)) / 50f
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(18.dp).height(220.dp).clip(RoundedCornerShape(9.dp)).background(p.surfaceAccent), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxWidth().height((220 * fill).dp).background(if ((c ?: 0f) >= 28f) p.informativeNegative else p.buttonFilled))
        }
        Text(c?.let { CardEngine.tempText(it, car.tempUnit) } ?: "모름", color = p.textPrimary, style = Ivi.bodySmallStrong, textAlign = TextAlign.Center)
        Text("실내", color = p.textTertiary, style = Ivi.labelSmall)
    }
}

/** 이번 여행 한 줄(시안 4): 출발에서 도착까지 경로 하나에, 충전한 자리와 함께 탄 사람(점)이 있어요. */
@Composable
fun TripRouteArt(charges: Int, people: Int, minutesCharged: Int?, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    ArtBox(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth; val h = maxHeight
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height * 0.50f
                val route = Path().apply {
                    moveTo(size.width * 0.08f, y + size.height * 0.08f)
                    cubicTo(size.width * 0.30f, y - size.height * 0.22f, size.width * 0.58f, y + size.height * 0.20f, size.width * 0.92f, y - size.height * 0.08f)
                }
                drawPath(route, p.surfaceAccent, style = Stroke(16f, cap = StrokeCap.Round))
                drawPath(route, p.buttonFilled, style = Stroke(6f, cap = StrokeCap.Round))
                drawCircle(p.buttonFilled, 18f, Offset(size.width * 0.08f, y + size.height * 0.08f))
                drawCircle(p.textTertiary, 18f, Offset(size.width * 0.92f, y - size.height * 0.08f))
                // 충전한 자리(초록 점)
                repeat(charges.coerceAtMost(3)) { i ->
                    drawCircle(p.informativeActive, 22f, Offset(size.width * (0.46f + 0.12f * i), y + size.height * (0.02f - 0.03f * i)))
                }
            }
            Text("출발", color = p.textPrimary, style = Ivi.bodySmallStrong, modifier = Modifier.align(Alignment.TopStart).offset(x = w * 0.03f, y = h * 0.66f))
            // 도착 점의 왼쪽 위에 놓아요(넓은 칸에서도 점과 겹치지 않게 점 위치에서 dp로 띄워요)
            Text("도착", color = p.textPrimary, style = Ivi.bodySmallStrong, modifier = Modifier.align(Alignment.TopStart).offset(x = w * 0.92f - 48.dp, y = h * 0.42f - 40.dp))
            if (charges > 0) Text(
                "충전 ${charges}번" + (minutesCharged?.let { " · ${it}분" } ?: ""),
                color = p.textSecondary, style = Ivi.labelMedium, modifier = Modifier.align(Alignment.TopStart).offset(x = w * 0.38f, y = h * 0.64f),
            )
            Row(Modifier.align(Alignment.BottomStart), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(people.coerceIn(0, 6)) { Box(Modifier.width(20.dp).height(20.dp).clip(RoundedCornerShape(10.dp)).background(p.buttonFilled)) }
                Text("함께 탄 사람 ${people}명", color = p.textSecondary, style = Ivi.labelMedium, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
