package app.roadmemory.car.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.roadmemory.car.Pane
import app.roadmemory.car.UiState
import app.roadmemory.car.brain.Advice
import app.roadmemory.car.brain.Advisor
import app.roadmemory.car.brain.Quiet
import app.roadmemory.car.data.Tracks
import app.roadmemory.car.navi.GuideResult
import app.roadmemory.car.navi.NaviLink
import app.roadmemory.car.navi.Place
import app.roadmemory.car.studio.StudioLibrary
import app.roadmemory.car.studio.StudioStore
import java.util.Calendar

/**
 * "ION이 권해요": 규칙으로 고른 제안 한 장(하이엔드 일곱 가지의 고르기 · 이유 보기 · 조용함 배우기를 LLM 없이). 한 번에 한 장만, 서 있을 때만이에요.
 * "이유"를 누르면 어떤 값으로 골랐는지와 지금까지의 수락 · 거절 수가 펼쳐져요. "나중에"는 거절로 세고, 두 번 거절한 종류는 7일 쉬어요.
 */
@Composable
internal fun AdviceSection(ui: UiState, onOpen: (String) -> Unit, onPlay: (List<Tracks.Track>) -> Unit, onRecord: () -> Unit = {}) {
    val ctx = LocalContext.current
    StudioLibrary.ensure(ctx); Quiet.ensure(ctx); app.roadmemory.car.brain.ChargeLog.ensure(ctx)
    val cat = remember(app.roadmemory.car.studio.StudioLink.rev) { StudioStore.catalog(ctx) }
    val lib = StudioLibrary.state
    val quiet = Quiet.state
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val advice = Advisor.top(ui, cat, lib, quiet, hour, app.roadmemory.car.brain.ChargeLog.history) ?: return
    val p = LocalPalette.current
    var why by remember(advice.kind) { mutableStateOf(false) }
    var note by remember(advice.kind) { mutableStateOf<String?>(null) }

    SectionTitle("ION이 권해요")
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp).background(p.backgroundPrimary, RoundedCornerShape(24.dp)).padding(horizontal = 22.dp, vertical = 18.dp)) {
        Text(advice.title, color = p.textPrimary, style = Ivi.bodyLargeStrong)
        Text(advice.sub, color = p.textSecondary, style = Ivi.bodyMedium)
        note?.let { Text(it, color = p.textPrimary, style = Ivi.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(advice.actionLabel, {
                Quiet.accept(ctx, advice.kind)
                when (val a = advice.action) {
                    is Advice.Action.Open -> onOpen(a.pane)
                    Advice.Action.Record -> { onRecord(); note = "기록을 시작했어요" }
                    is Advice.Action.Play -> onPlay(a.titles.mapNotNull { Tracks.byTitle(it) }.ifEmpty { Tracks.all })
                    is Advice.Action.GuidePlace -> { note = "내비에 보내는 중이에요"; NaviLink.guide(ctx, Place(a.name, a.lat, a.lng)) { r -> note = if (r is GuideResult.Failed) r.why.label else "내비가 ${app.roadmemory.car.brain.Korean.euro(a.name)} 안내를 시작해요" } }
                    is Advice.Action.GuideTrip -> {
                        val t = cat.trips.firstOrNull { it.id == a.tripId } ?: return@PillButton
                        val plan = TripGuide.plan(t, a.stopIndex) ?: return@PillButton
                        val s = t.stops[a.stopIndex]
                        note = "내비에 보내는 중이에요"
                        NaviLink.guide(ctx, plan.dest, plan.through) { r -> note = if (r is GuideResult.Failed) r.why.label else "내비가 ${app.roadmemory.car.brain.Korean.euro(s.name)} 안내를 시작해요" }
                        StudioLibrary.apply(ctx, Pane.SVC_TRIP, TripRun.encode(TripProgress(t.id, a.stopIndex, plan.end)))
                    }
                }
            }, filled = true)
            PillButton("나중에", { Quiet.dismiss(ctx, advice.kind) })
            Spacer(Modifier.weight(1f))
            Text(if (why) "이유 닫기" else "이유", color = p.textTertiary, style = Ivi.bodySmallStrong, modifier = Modifier.clickable(role = Role.Button) { why = !why }.padding(8.dp))
        }
        if (why) {
            Spacer(Modifier.height(8.dp))
            advice.reasons.forEach { Text("· $it", color = p.textSecondary, style = Ivi.bodyMedium) }
            Text("· ${quiet.summary}. 두 번 거절한 제안은 7일 쉬어요", color = p.textTertiary, style = Ivi.labelMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
