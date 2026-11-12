package app.roadmemory.car.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.roadmemory.car.studio.LinkRules
import app.roadmemory.car.studio.StudioLibrary
import app.roadmemory.car.studio.StudioLink
import app.roadmemory.car.studio.StudioPack

// ───────── ION Studio와 바로 잇기: 주소와 API 키를 적어 잇고, 올라온 테마 · 아트 · 시그니처 송 · 일정을 받아요 ─────────

/** 글을 적는 한 줄: 이름이 위, 적는 칸이 아래. 서 있을 때만 열리는 화면이라 주행 중에는 글을 적지 않아요. */
@Composable
private fun LinkField(label: String, value: String, hint: String, onChange: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Ascii) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(label, color = p.textTertiary, style = Ivi.labelMedium)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().background(p.buttonBasic, RoundedCornerShape(20.dp)).padding(horizontal = 24.dp, vertical = 18.dp)) {
            if (value.isEmpty()) Text(hint, color = p.textTertiary, style = Ivi.bodyLarge)
            BasicTextField(
                value, { onChange(it.filter { ch -> !ch.isWhitespace() }) }, Modifier.fillMaxWidth().semantics { contentDescription = label },
                textStyle = Ivi.bodyLarge.copy(color = p.textPrimary), singleLine = true, cursorBrush = SolidColor(p.textPrimary),
                keyboardOptions = KeyboardOptions(autoCorrect = false, keyboardType = keyboard, imeAction = ImeAction.Done),
            )
        }
    }
}

/** 설정에서 여는 화면: ION Studio와 잇고, 새로 받고, 끊어요. */
@Composable
internal fun LinkPane(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    LaunchedEffect(Unit) { StudioLink.ensure(ctx); StudioLibrary.ensure(ctx) }
    val s = StudioLink.state
    PaneFrame("ION Studio 연결", onBack) {
        CardBox {
            Text("ION Studio에 올라온 시그니처 송 · 디스플레이 테마 · 디지털 아트와 내 여행 일정을 이 차로 받아요.", color = p.textPrimary, style = Ivi.bodyLarge)
            Spacer(Modifier.height(10.dp))
            Text("ION Studio의 「연동」에서 만든 API 키로 이어요. 이 차가 보내는 것은 키뿐이고, 차의 값이나 위치는 보내지 않아요.", color = p.textTertiary, style = Ivi.labelMedium)
        }
        Spacer(Modifier.height(20.dp))
        if (!s.linked) {
            var base by remember { mutableStateOf(LinkRules.DEFAULT_HOST) }
            var key by remember { mutableStateOf("") }
            SectionTitle("연결하기")
            CardBox {
                LinkField("ION Studio 주소", base, LinkRules.DEFAULT_HOST, { base = it }, KeyboardType.Uri)
                LinkField("API 키", key, "ion_test_로 시작하는 키", { key = it })
                Spacer(Modifier.height(14.dp))
                if (s.busy) Text("ION Studio에서 목록을 받고 있어요", color = p.textSecondary, style = Ivi.bodyMedium)
                else BigButton("연결하기", true, { StudioLink.connect(ctx, base, key) }, Modifier.fillMaxWidth())
                s.error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = p.informativeNegative, style = Ivi.bodyMedium) }
            }
            return@PaneFrame
        }
        SectionTitle("이어져 있어요")
        CardBox {
            ValueRow("ION Studio", "이 주소에서 받아요", s.base.removePrefix("https://"))
            RowLine()
            ValueRow("API 키", if (s.member.isEmpty()) "이 키로 이어져 있어요" else "${s.member} 님이 만든 키예요", s.keyHint)
            RowLine()
            ValueRow(
                "받은 목록", if (s.busy) "ION Studio에서 받고 있어요" else "목록과 표지를 다시 받아요",
                if (s.syncedAt == 0L) "아직 없어요" else LinkRules.whenLabel(s.syncedAt, System.currentTimeMillis()),
                if (s.busy) null else "새로 받기", if (s.busy) null else ({ StudioLink.sync(ctx) }),
            )
            s.error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = p.informativeNegative, style = Ivi.bodyMedium) }
        }
        Spacer(Modifier.height(24.dp))
        SectionTitle("ION Studio에서 받은 것")
        CardBox {
            ValueRow("디스플레이 테마", "디스플레이 테마에서 내려받아 적용해요", "${s.themes}개")
            RowLine()
            ValueRow("디지털 아트", "디지털 아트에서 내려받아 걸어요", "${s.arts}개")
            RowLine()
            ValueRow("시그니처 송", "시그니처 송에서 내려받아 적용해요", "${s.packs}개")
            RowLine()
            ValueRow("여행 일정", if (s.myTrips > 0) "내가 등록한 일정 ${s.myTrips}개는 마이 트립의 내 일정에 담았어요" else "마이 트립에서 내 일정에 담아요", "${s.trips}개")
            if (s.skippedTrips > 0) { Spacer(Modifier.height(8.dp)); Text("장소를 지도에서 고르지 않은 일정 ${s.skippedTrips}개는 내비로 안내할 수 없어서 뺐어요", color = p.textTertiary, style = Ivi.labelMedium) }
        }
        Spacer(Modifier.height(24.dp))
        SectionTitle("연결 끊기")
        CardBox {
            ValueRow("연결 끊기", "주소 · 키와 ION Studio에서 받은 테마 · 아트를 이 차에서 지우고, 앱에 들어 있는 목록으로 돌아가요", "", "끊기") { StudioLink.disconnect(ctx) }
        }
    }
}

/** 서비스 화면 위쪽의 한 줄: ION Studio와 이어져 있을 때만 보여요. 언제 받았는지와 "새로 받기". */
@Composable
internal fun LinkNotice() {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    LaunchedEffect(Unit) { StudioLink.ensure(ctx) }
    val s = StudioLink.state
    if (!s.linked) return
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f).onBackdrop(p.backgroundSecondary)) {
            Text(
                when {
                    s.busy -> "ION Studio에서 받고 있어요"
                    s.syncedAt == 0L -> "ION Studio와 이어져 있어요"
                    else -> "ION Studio와 이어져 있어요 · ${LinkRules.whenLabel(s.syncedAt, System.currentTimeMillis())}에 받았어요"
                },
                color = p.textSecondary, style = Ivi.bodyMedium,
            )
            s.error?.let { Text(it, color = p.informativeNegative, style = Ivi.labelMedium) }
        }
        if (!s.busy) PillButton("새로 받기", { StudioLink.sync(ctx) })
    }
}

/** 내려받기 단추: ION Studio에서 받는 중이면 "받는 중"이라고만 보여요. */
@Composable
internal fun DownloadButton(id: String, label: String = "내려받기", onClick: () -> Unit) {
    if (id in StudioLink.pending) Text("받는 중", color = LocalPalette.current.textSecondary, style = Ivi.bodySmallStrong, modifier = Modifier.padding(horizontal = 20.dp))
    else PillButton(label, onClick, filled = true)
}

/** 시그니처 송 화면의 절: ION Studio에 올라와 있고 이 기기에는 아직 없는 묶음. 받으면 "이 기기에 있는 시그니처 송"으로 옮겨 가요. */
@Composable
internal fun StudioPacksSection(packs: List<StudioPack>, have: Set<String>, onInstalled: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val rest = packs.filter { it.id !in have }
    if (rest.isEmpty()) return
    SectionTitle("ION Studio에서 받을 수 있는 시그니처 송")
    CardBox {
        rest.forEachIndexed { i, pk ->
            if (i > 0) RowLine()
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(pk.title, color = p.textPrimary, style = Ivi.bodyLargeStrong)
                    Text("${pk.speaker} · 목소리 ${pk.moments}개 · ${won(pk.price)}", color = p.textTertiary, style = Ivi.labelMedium)
                }
                DownloadButton(pk.id) { StudioLink.installPack(ctx, pk) { ok -> if (ok) onInstalled() } }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
}
