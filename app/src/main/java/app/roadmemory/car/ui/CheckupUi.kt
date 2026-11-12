package app.roadmemory.car.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.roadmemory.car.UiState
import app.roadmemory.car.data.CheckupReading
import app.roadmemory.car.data.DemoCheckup
import app.roadmemory.car.data.Wheel
import java.time.LocalDate

/**
 * 점검 화면의 아래쪽: 차 상태 · 소모품, 다음 점검 · 교환, 정비 이력. 값은 [DemoCheckup]의 지어낸 서비스센터 기록이고,
 * 차가 지금 직접 알려 주는 것(문 · 트렁크 · 후드, 워셔액이 읽히면 그 값)은 섞지 않고 위쪽에 따로 있어요.
 */
@Composable
internal fun CheckupSections(ui: UiState, today: LocalDate = LocalDate.now()) {
    val p = LocalPalette.current
    val washerLow = ui.car.washerLow == true   // 차가 읽은 값이 있으면 그대로, 못 읽었으면 기록의 "넉넉해요"

    SectionTitle("차 상태 · 소모품")
    Card {
        MeterRow("배터리 건강", "${DemoCheckup.BATTERY_HEALTH_PERCENT}%", DemoCheckup.BATTERY_HEALTH_PERCENT / 100f, "새 차 때와 견준 배터리의 상태예요")
        Divider()
        val liveTires = CheckupReading.tiresBar(ui.car)
        val tpms = CheckupReading.tireWarning(ui.car)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("타이어 공기압", color = p.textPrimary, style = Ivi.bodyLargeStrong)
                Text(
                    if (liveTires != null) "지금 차가 알려 주는 값" + if (tpms) " · 공기압 경고가 켜져 있어요" else ""
                    else if (tpms) "마지막 점검(${DemoCheckup.ago(DemoCheckup.records.first().daysAgo)}) 때 잰 값이에요 · 지금 차는 공기압 경고를 알려요"
                    else "마지막 점검(${DemoCheckup.ago(DemoCheckup.records.first().daysAgo)}) 때 잰 값 · 적정 ${DemoCheckup.PRESSURE_OK_BAR.start} ~ ${DemoCheckup.PRESSURE_OK_BAR.endInclusive} bar",
                    color = p.textTertiary, style = Ivi.labelMedium,
                )
            }
        }
        Wheel.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { w ->
                    val bar = liveTires?.get(w) ?: DemoCheckup.wheels.first { it.label == w.label }.bar
                    Row(
                        Modifier.weight(1f).background(p.surfaceHigh, RoundedCornerShape(20.dp)).padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(w.label, color = p.textTertiary, style = Ivi.labelMedium)
                            Text("$bar bar", color = p.textPrimary, style = Ivi.titleSmall)
                        }
                        // 읽은 값은 적정 · 낮음을 가르지 않아요(차마다 기준이 달라요): 공기압 경고가 켜졌을 때만 말해요
                        // 기록 값은 그때의 판정이라, 지금 차가 공기압 경고를 알리면 "적정"이라고 말하지 않아요
                        val word = when {
                            liveTires != null -> if (tpms) "경고" else ""
                            tpms -> ""
                            else -> DemoCheckup.pressureWord(DemoCheckup.wheels.first { it.label == w.label })
                        }
                        if (word.isNotEmpty()) Text(word, color = p.textSecondary, style = Ivi.bodyMedium)
                    }
                }
            }
        }
        Divider()
        DemoCheckup.consumables.forEachIndexed { i, c ->
            if (i > 0) Divider()
            MeterRow(c.name, "${c.leftPercent}% 남음", c.leftPercent / 100f, null)
        }
        Divider()
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (washerLow) StateDot(alert = true)
            Text("워셔액", color = p.textPrimary, style = Ivi.bodyLargeStrong, modifier = Modifier.weight(1f))
            Text(if (washerLow) "부족해요" else "넉넉해요", color = p.textPrimary, style = if (washerLow) Ivi.bodyLargeStrong else Ivi.bodyLarge)
        }
        Divider()
        val liveWarn = CheckupReading.warnings(ui.car)
        if (liveWarn != null && liveWarn.isNotEmpty()) {
            // 켜진 경고의 이름은 이 화면 맨 위에 있어요: 여기서는 다시 늘어놓지 않아요
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StateDot(alert = true)
                Text("경고", color = p.textPrimary, style = Ivi.bodyLargeStrong, modifier = Modifier.weight(1f))
                Text("${liveWarn.size}개가 켜져 있어요 · 맨 위에 있어요", color = p.textPrimary, style = Ivi.bodyLargeStrong)
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("경고", color = p.textPrimary, style = Ivi.bodyLargeStrong, modifier = Modifier.weight(1f))
                Text(if (liveWarn != null) "지금 남아 있는 경고가 없어요" else "마지막 점검 때 경고가 없었어요", color = p.textSecondary, style = Ivi.bodyLarge)
            }
        }
    }

    SectionTitle("다음 점검 · 교환")
    Card {
        val next = DemoCheckup.nextCheck(today)
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, next).toInt()
        Text("정기 점검 예약", color = p.textTertiary, style = Ivi.labelMedium)
        Text("${DemoCheckup.dateLabel(next)} 오후 2시", color = p.textPrimary, style = Ivi.titleMedium)
        Text("${DemoCheckup.PLACE} · ${DemoCheckup.inDays(days)}", color = p.textSecondary, style = Ivi.bodyLarge)
        Spacer(Modifier.height(8.dp))
        DemoCheckup.due.forEach { d ->
            Divider()
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(d.name, color = p.textPrimary, style = Ivi.bodyLargeStrong, modifier = Modifier.weight(1f))
                Text(DemoCheckup.inDays(d.inDays), color = p.textSecondary, style = Ivi.bodyLarge)
            }
        }
    }

    SectionTitle("정비 이력")
    Card {
        DemoCheckup.records.forEachIndexed { i, r ->
            if (i > 0) Divider()
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(r.title, color = p.textPrimary, style = Ivi.bodyLargeStrong)
                    Text("${DemoCheckup.dateLabel(today.minusDays(r.daysAgo.toLong()))} · ${DemoCheckup.PLACE} · ${r.detail}", color = p.textTertiary, style = Ivi.labelMedium)
                }
                Text(DemoCheckup.ago(r.daysAgo), color = p.textSecondary, style = Ivi.bodyMedium)
            }
        }
    }
    NoteLine(CheckupReading.sourceNote(ui.car))
}

/** 이름 · 값 · (막대) · 한 줄 설명. 막대는 채운 만큼이 진한 색이에요. */
@Composable
private fun MeterRow(label: String, value: String, fraction: Float, note: String?) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = p.textPrimary, style = Ivi.bodyLargeStrong, modifier = Modifier.weight(1f))
            Text(value, color = p.textPrimary, style = Ivi.titleSmall)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(p.surfaceAccent)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(p.buttonFilled))
        }
        note?.let { Text(it, color = p.textTertiary, style = Ivi.labelMedium, modifier = Modifier.padding(top = 6.dp)) }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalPalette.current.surfaceHigh))
}
