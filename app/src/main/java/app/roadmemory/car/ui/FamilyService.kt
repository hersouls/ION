package app.roadmemory.car.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.roadmemory.car.Pane
import app.roadmemory.car.UiState
import app.roadmemory.car.brain.FamilyInsight
import app.roadmemory.car.family.DemoFamilySource
import app.roadmemory.car.family.FamilyLink
import app.roadmemory.car.family.FamilyLocationSource
import app.roadmemory.car.family.FamilyPlace
import app.roadmemory.car.family.FamilyRead
import app.roadmemory.car.family.FamilyText
import app.roadmemory.car.family.NaviLauncher
import app.roadmemory.car.family.RouteLauncher
import app.roadmemory.car.studio.StudioLibrary
import app.roadmemory.car.studio.StudioStore
import kotlinx.coroutines.launch

/**
 * 위치 동기화(마이현대에서 오는 서비스): 마이현대가 가족의 위치를 동기화해 준다고 가정한 서비스 화면. 서 있을 때만 열려요.
 *  · 누르는 그때 한 번 가져오고(따라다니며 다시 묻지 않아요), 이 화면을 닫으면 가져온 위치가 사라져요. 위치 보여 주기를 꺼 둔 가족은 읽지 않아요.
 *  · 사람마다 별칭 · 자리 · 위치를 받은 때를 보여 주고, ION이 쓴 값에서만 한마디를 이어요([FamilyInsight]): 오래된 위치 · 가족 일정의 장소 · 이동 중, 가는 중에는 내비가 준 남은 거리 · 도착 때 배터리.
 *  · "경로 안내"를 눌러야 내비에 길을 보내요. ION이 먼저 경로를 바꾸지 않아요.
 *  · 시험 빌드에서는 허용한 가족의 실제 위치([FamilyLink.source])를, 제출 빌드에서는 시연 값([DemoFamilySource])을 같은 화면에서 읽어요. 화면에는 "시연"이라는 글자를 쓰지 않아요(오너 지시 2026-11-11).
 */
@Composable
internal fun FamilyServicePane(ui: UiState, def: ServiceDef, onHome: () -> Unit) {
    val ctx = LocalContext.current
    StudioLibrary.ensure(ctx)
    val cat = remember(app.roadmemory.car.studio.StudioLink.rev) { StudioStore.catalog(ctx) }
    val lib = StudioLibrary.state
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val real = FamilyLink.source != null
    val source: FamilyLocationSource = remember { FamilyLink.source ?: DemoFamilySource(cat) }
    val router: RouteLauncher = remember { FamilyLink.router ?: NaviLauncher(ctx.applicationContext) }
    var reading by remember { mutableStateOf(false) }
    var read by remember { mutableStateOf<FamilyRead?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    // 캘린더를 마이현대에서 동기화해 가져왔을 때만 가족 일정의 장소를 이어요(가져오지 않은 것으로 말하지 않아요)
    val calendar = cat.calendar.takeIf { lib.has("sync_${Pane.SVC_CALENDAR}") }

    fun fetch() {
        if (reading) return
        reading = true; note = null; read = null
        scope.launch { read = source.read(); reading = false }
    }

    fun guide(person: FamilyPlace) {
        note = "내비에 ${person.alias}${FamilyText.iGa(person.alias)} 있는 곳으로 길을 보내는 중이에요"
        router.guide(person) { res -> note = FamilyText.routeNote(person.alias, res) }
    }

    val (headline, sub) = when (val r = read) {
        null -> "가족 위치" to
            (if (real) "허용한 가족의 위치를 누르는 그때 한 번 읽어요(시험 빌드 · 실제 위치)" else "마이현대에서 가족이 보여 주는 위치를 누르는 그때 한 번 가져와요")
        FamilyRead.Nobody -> "위치를 보여 주는 가족이 없어요" to "모두 꺼 두었거나 위치를 아직 받지 못했어요"
        is FamilyRead.Failed -> "위치를 가져오지 못했어요" to r.why
        is FamilyRead.Ready -> "${r.people.size}명이 위치를 보여 주고 있어요" to
            ((if (r.notSharing > 0) "${r.notSharing}명은 위치 보여 주기를 꺼 두어서 읽지 않았어요" else "위치를 보여 주기로 한 가족만 읽어요"))
    }

    PaneFrame(def.title, onHome) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                MwIcon(def.icon, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(headline, color = p.textPrimary, style = Ivi.titleSmall)
                    Text(sub, color = p.textSecondary, style = Ivi.bodyMedium)
                }
                if (read != null) PillButton("지우기", { read = null; note = null })
                PillButton(
                    when { reading -> "가져오는 중이에요"; read == null -> "가족 위치 가져오기"; else -> "다시 가져오기" },
                    { fetch() }, filled = read == null,
                )
            }
        }
        note?.let { Card { Text(it, color = p.textPrimary, style = Ivi.bodyLarge) } }

        (read as? FamilyRead.Ready)?.people?.forEach { person ->
            val guiding = FamilyInsight.guiding(person, ui.nav)
            Card {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    MwIcon(MwIcons.Location, size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(person.alias + if (guiding) " · 가는 중" else "", color = p.textPrimary, style = Ivi.titleSmall)
                        Text(FamilyText.hint(person) + if (ui.nav.routeActive && !guiding) " · 안내 중인 길이 바뀌어요" else "", color = p.textSecondary, style = Ivi.bodyMedium)
                    }
                    PillButton(
                        when { guiding -> "다시 안내"; ui.nav.routeActive -> "새 길로 안내"; else -> "경로 안내" },
                        { guide(person) }, filled = !guiding,
                    )
                }
                FamilyInsight.lines(person, ui.nav, calendar).forEach { line ->
                    Spacer(Modifier.height(12.dp))
                    Text(line.text, color = p.textPrimary, style = Ivi.bodyMediumStrong)
                    Text(line.reason, color = p.textTertiary, style = Ivi.labelMedium)
                }
            }
        }

        NoteLine(
            if (real) "허용한 가족의 실제 위치예요(시험 빌드, 우회 경로). 누르는 그때 한 번 읽고 이 화면을 닫으면 지워요. 좌표는 화면에 보이지 않고, ION은 먼저 경로를 바꾸지 않아요."
            else "마이현대가 가족의 위치를 동기화해 준다고 가정했고, 별칭 · 자리 · 좌표는 지어낸 값이에요(장소만 지도의 실제 장소). 실제로는 가족이 마이현대에서 켜 둔 동안의 위치예요. 누르는 그때 한 번 가져오고 이 화면을 닫으면 지워요. 꺼 둔 가족은 읽지 않고, ION은 먼저 경로를 바꾸지 않아요. 가는 동안의 남은 거리 · 도착 때 배터리는 내비가 주는 값이에요.",
        )
    }
}
