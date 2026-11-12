package app.roadmemory.car.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.roadmemory.car.DriveController
import app.roadmemory.car.UiState
import app.roadmemory.car.data.TripBook

/** 여행 기록하기의 세 동작. [end]는 끝낸 결과를 한 줄로 돌려줘요. */
internal data class RecordingActions(
    val start: () -> Unit = {},
    val resume: () -> Unit = {},
    val end: ((String) -> Unit) -> Unit = {},
)

internal fun DriveController.recordingActions() = RecordingActions(::startRecording, ::resumeRecording) { done -> endRecording(done) }

/**
 * 여행 기록하기 카드. 세 가지 모습이에요.
 *  · 기록 안 하는 중: 내비 목적지를 보여 주고 "기록하기". 달린 거리 · 탄 사람 · 들은 곡 · 충전은 달리는 동안 저절로 쌓이고, 도착하면 저절로 저장돼요.
 *  · 기록하는 중: 지금까지 쌓인 것과 "기록 끝내기".
 *  · 이어서 물어요(시동을 껐다 켠 뒤): 지난 기록의 목적지 · 거리와 내비 안내가 이어져 있는지, "이어서 기록" · "여기서 끝내기".
 * 차의 현재 위치는 읽지 않아요(FusedLocation은 미확인). 목적지는 내비가 준 값만 써요.
 */
@Composable
internal fun RecordingCard(ui: UiState, act: RecordingActions, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val r = ui.recording
    var note by remember { mutableStateOf<String?>(null) }
    Card(modifier) {
        when {
            r == null -> {
                val dest = ui.nav.destinationName?.takeIf { ui.nav.routeActive && it.isNotBlank() }
                Text("여행 기록하기", color = p.textTertiary, style = Ivi.labelMedium)
                Text(if (dest != null) "내비 목적지 · $dest" else "내비에 목적지가 없어요", color = p.textPrimary, style = Ivi.bodyLargeStrong)
                Text(
                    "거리 · 탄 사람 · 들은 곡 · 충전이 저절로 쌓이고, 도착하면 여행 기록으로 저장돼요." +
                        if (dest == null) " 목적지가 없으면 처음 정차(기어 P)할 때 끝나요." else "",
                    color = p.textSecondary, style = Ivi.bodyMedium,
                )
                note?.let { Text(it, color = p.textPrimary, style = Ivi.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
                Spacer(Modifier.height(14.dp))
                PillButton("기록하기", { note = null; act.start() }, filled = true)
            }
            r.paused -> {
                Text("이어서 기록할까요?", color = p.textTertiary, style = Ivi.labelMedium)
                Text("${r.destName ?: "목적지 없이"} · ${r.km}km 달렸어요", color = p.textPrimary, style = Ivi.bodyLargeStrong)
                Text(
                    "시동을 다시 켰어요 · " + (if (ui.nav.routeActive) "내비 안내가 이어져 있어요" else "내비 안내는 끝났어요") + (if (r.legs > 1) " · ${r.legs}번에 나눠 달렸어요" else ""),
                    color = p.textSecondary, style = Ivi.bodyMedium,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillButton("이어서 기록", act.resume, filled = true)
                    PillButton("여기서 끝내기", { act.end { note = it } })
                }
            }
            else -> {
                Text("기록하는 중", color = p.textTertiary, style = Ivi.labelMedium)
                Text(r.destName ?: "목적지 없이", color = p.textPrimary, style = Ivi.bodyLargeStrong)
                Text(
                    listOfNotNull(
                        "${r.km}km",
                        TripBook.peopleWord(r.people).takeIf { it.isNotEmpty() }?.let { "$it 탔어요" },
                        if (r.charges > 0) "충전 ${r.charges}번" else null,
                        if (r.tracks.isNotEmpty()) "곡 ${r.tracks.size}개" else null,
                        if (r.legs > 1) "${r.legs}번에 나눠 달렸어요" else null,
                    ).joinToString(" · "),
                    color = p.textSecondary, style = Ivi.bodyMedium,
                )
                Text("내비 안내가 끝나 도착하면 저절로 저장돼요. 시동을 껐다 켜도 이어서 할지 물어요.", color = p.textTertiary, style = Ivi.labelMedium, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(14.dp))
                PillButton("기록 끝내기", { act.end { note = it } })
            }
        }
    }
}
