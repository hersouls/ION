package app.roadmemory.car.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.roadmemory.car.data.DemoUser
import app.roadmemory.car.family.FamilyLink
import app.roadmemory.car.family.FamilyRead
import app.roadmemory.car.family.FamilyText
import kotlinx.coroutines.launch

/**
 * 가족 위치 → 경로 안내(가족 · 자리 화면의 아래 칸). 서 있을 때만 열리는 화면 안에 있어요.
 *  · 운전자가 "가족 위치 보기"를 누를 때 한 번 읽어요. 따라다니며 다시 묻지 않고, 이 화면을 닫으면 읽은 값이 사라져요.
 *  · "경로 안내"를 눌러야 내비에 길을 보내요. ION이 먼저 권하지 않아요.
 *  · 위치를 읽어 오는 쪽이 이 빌드에 없으면([FamilyLink.source]가 null) 아무것도 그리지 않아요(차가 주지 않는 값을 만들어 보이지 않아요).
 */
@Composable
internal fun FamilyLocations(routeActive: Boolean) {
    val source = FamilyLink.source ?: return
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf(false) }
    var read by remember { mutableStateOf<FamilyRead?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    Spacer(Modifier.height(28.dp))
    SectionTitle("${DemoUser.CALL}의 가족 위치")
    when (val r = read) {
        null -> Unit
        FamilyRead.Nobody -> Text("위치를 보여 주는 가족이 없어요", color = p.textSecondary, style = Ivi.bodyLarge)
        is FamilyRead.Failed -> Text("위치를 읽지 못했어요 · ${r.why}", color = p.textSecondary, style = Ivi.bodyLarge)
        is FamilyRead.Ready -> r.people.forEachIndexed { i, person ->
            if (i > 0) RowLine()
            ValueRow(
                title = person.alias,
                hint = FamilyText.hint(person) + if (routeActive) " · 안내 중인 길이 바뀌어요" else "",
                value = "",
                actionLabel = if (routeActive) "새 길로 안내" else "경로 안내",
                onAction = {
                    val router = FamilyLink.router
                    note = if (router == null) "이 빌드는 내비에 길을 보낼 수 없어요" else "내비에 길을 보내는 중이에요"
                    router?.guide(person) { res -> note = FamilyText.routeNote(person.alias, res) }
                },
            )
        }
    }
    note?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, color = p.textPrimary, style = Ivi.bodyLarge)
    }
    Spacer(Modifier.height(14.dp))
    BigButton(
        text = when { reading -> "읽는 중이에요"; read == null -> "가족 위치 보기"; else -> "다시 읽기" },
        primary = read == null,
        onClick = {
            if (!reading) {
                reading = true; note = null; read = null
                scope.launch { read = source.read(); reading = false }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    NoteLine("가족이 위치를 보여 주기로 한 동안만, 누르는 그때 한 번 읽어요. 이 화면을 닫으면 읽은 값이 지워져요.")
}
