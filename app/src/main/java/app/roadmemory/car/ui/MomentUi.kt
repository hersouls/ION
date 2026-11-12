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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.roadmemory.car.UiState
import app.roadmemory.car.ontology.Ontology
import app.roadmemory.car.pack.MomentMode
import app.roadmemory.car.pack.MomentNote
import app.roadmemory.car.pack.PackInfo

/*
 * 시그니처 송 화면 조각. 글만 있고 누를 것은 없어요(주행 중에도 같은 모양): ION의 문장(차가 준 값으로 채운 글)과, 산 목소리의 자막이에요.
 * 색은 팔레트 토큰만 써요. 안전 카드를 가리지 않도록 카드 아래에 이어 붙이고, 몇 초 뒤에 저절로 사라져요(DriveController).
 */

private fun modeWord(m: MomentMode) = if (m == MomentMode.PUSH) "ION이 먼저 알려요" else "방금 한 일에 맞춰요"

/** 왼쪽 ION 칸의 대화창에 끼워 넣는 말풍선 둘: ION의 문장 → 목소리 자막. */
@Composable
internal fun MomentBubbles(note: MomentNote, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("시그니처 송 · ${note.packTitle} · ${modeWord(note.mode)}", color = p.textTertiary, style = Ivi.labelMedium)
        note.info?.let {
            Text(
                softBreaks(it), color = p.textPrimary, style = Ivi.bodyLarge,
                modifier = Modifier.widthIn(max = 460.dp)
                    .background(p.backgroundSecondary, RoundedCornerShape(topStart = 8.dp, topEnd = 28.dp, bottomEnd = 28.dp, bottomStart = 28.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
        Text(
            "${note.speaker}  “${note.line}”", color = p.textPrimary, style = Ivi.bodyLargeStrong,
            modifier = Modifier.widthIn(max = 460.dp)
                .background(p.surfaceHigh, RoundedCornerShape(topStart = 8.dp, topEnd = 28.dp, bottomEnd = 28.dp, bottomStart = 28.dp))
                .padding(horizontal = 24.dp, vertical = 16.dp),
        )
        if (note.synthetic) Text("합성 음성이에요", color = p.textTertiary, style = Ivi.labelSmall)
    }
}

/** 한 화면을 쓰는 카드(주행 · 안전 카드 · 도착 요약) 아래에 이어 붙이는 줄. 카드를 가리지 않고, 누를 것이 없어요. */
@Composable
internal fun MomentStrip(note: MomentNote) {
    val p = LocalPalette.current
    Spacer(Modifier.height(16.dp))
    Column(
        Modifier.fillMaxWidth().background(p.backgroundPrimary, RoundedCornerShape(32.dp)).padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("시그니처 송 · ${note.packTitle}", color = p.textTertiary, style = Ivi.labelMedium)
        note.info?.let { Text(it, color = p.textPrimary, style = Ivi.bodyLarge) }
        Text("${note.speaker}  “${note.line}”", color = p.textPrimary, style = Ivi.bodyLargeStrong)
        if (note.synthetic) Text("합성 음성이에요", color = p.textTertiary, style = Ivi.labelSmall)
    }
}

/** 설정에서 여는 화면: 이 기기에 있는 시그니처 송을 고르거나 꺼요. */
@Composable
internal fun PackPane(ui: UiState, ontology: Ontology?, onApply: (String?) -> Unit, onBack: () -> Unit, onPreview: (String) -> Unit = {}, onRescan: () -> Unit = {}) {
    val p = LocalPalette.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // ION Studio와 이어져 있으면 거기 올라온 묶음 가운데 이 기기에 없는 것을 받을 수 있어요
    val studioPacks = androidx.compose.runtime.remember(app.roadmemory.car.studio.StudioLink.rev) { app.roadmemory.car.studio.StudioStore.catalog(ctx).packs }
    val have = ui.packs.map { it.id }.toSet()
    PaneFrame("시그니처 송", onBack) {
        CardBox {
            Text("스토어에서 산 목소리 묶음이에요. 적용하면 ION이 차가 알려 주는 값을 지켜보다가, 때가 되면 화면에 글로 알려 주고 산 목소리를 틀어요.", color = p.textPrimary, style = Ivi.bodyLarge)
            Spacer(Modifier.height(10.dp))
            Text("안전벨트 · 뒷좌석 같은 안전 안내는 대신하지 않고, 그 안내가 끝난 뒤에 틀어요.", color = p.textTertiary, style = Ivi.labelMedium)
        }
        Spacer(Modifier.height(20.dp))
        LinkNotice()
        if (ui.packs.isEmpty()) {
            CardBox { Text("이 기기에 시그니처 송이 없어요", color = p.textSecondary, style = Ivi.bodyLarge) }
            Spacer(Modifier.height(20.dp))
            StudioPacksSection(studioPacks, have, onRescan)
            return@PaneFrame
        }
        // 적용한 묶음의 목소리가 어느 순간에 나오는지, 이 차가 그 순간을 알아차리는지 먼저 보여요
        if (ontology != null && ui.pack != null) { MomentsSection(ontology, ui.pack, onlyPack = true); Spacer(Modifier.height(20.dp)) }
        SectionTitle("이 기기에 있는 시그니처 송")
        CardBox {
            val on = ui.pack
            ui.packs.forEachIndexed { i, pk ->
                if (i > 0) RowLine()
                val active = on?.id == pk.id
                val offerable = ontology?.let { o -> pk.moments.count { o.offerable("mo.$it") } } ?: pk.moments.size
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(pk.title, color = p.textPrimary, style = Ivi.bodyLargeStrong)
                        Text(
                            "${pk.speaker} · 소리 ${pk.moments.size}개 중 ${offerable}개를 이 차에서 알아차려요" + if (pk.synthetic) " · 합성 음성" else "",
                            color = p.textTertiary, style = Ivi.labelMedium,
                        )
                    }
                    // 적용하기 전에 목소리를 먼저 들어 봐요(출발 소리 한 토막과 그 자막)
                    Box(
                        Modifier.height(IviSize.buttonSmall.dp)
                            .iviButton(RoundedCornerShape(IviSize.buttonSmall.dp / 2), p.buttonBasic, p.buttonBasicPressed, onClick = { onPreview(pk.id) }, onClickLabel = "${pk.title} 들어 보기")
                            .padding(horizontal = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("들어 보기", color = p.textPrimary, style = Ivi.bodySmallStrong) }
                    Box(
                        Modifier.height(IviSize.buttonSmall.dp)
                            .iviButton(RoundedCornerShape(IviSize.buttonSmall.dp / 2), if (active) p.buttonFilled else p.buttonBasic, if (active) p.buttonFilledPressed else p.buttonBasicPressed, onClick = { onApply(if (active) null else pk.id) })
                            .padding(horizontal = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (active) "적용 중 · 끄기" else "적용", color = if (active) p.textOnFilled else p.textPrimary, style = Ivi.bodySmallStrong) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(if (ui.pack == null) "지금은 꺼져 있어요" else "「${ui.pack.title}」${app.roadmemory.car.brain.Korean.eul(ui.pack.title).removePrefix(ui.pack.title)} 쓰고 있어요", color = p.textSecondary, style = Ivi.bodyMedium)
        Spacer(Modifier.height(20.dp))
        StudioPacksSection(studioPacks, have, onRescan)
    }
}

/** "ION이 닿는 데이터"에 붙는 절: 시그니처 송이 나올 수 있는 순간을 ION-Link 판정(● ◐ ○)과 까닭으로 보여 줘요. */
@Composable
internal fun MomentsSection(ontology: Ontology, pack: PackInfo?, onlyPack: Boolean = false) {
    val p = LocalPalette.current
    // [onlyPack]이면 적용한 묶음에 목소리가 있는 순간만 보여요(시그니처 송 화면)
    val all = ontology.moments.filter { !onlyPack || pack?.moments?.contains(it.id.removePrefix("mo.")) == true }
    if (all.isEmpty()) return
    val offer = all.count { ontology.offerable(it.id) }
    Spacer(Modifier.height(16.dp))
    if (onlyPack && pack != null) SectionTitle("「${pack.title}」의 목소리가 나오는 순간")
    else Text("시그니처 송이 나오는 순간", color = p.textTertiary, style = Ivi.labelMedium, modifier = Modifier.padding(bottom = 8.dp))
    CardBox {
        Text(
            if (onlyPack && offer == all.size) "이 묶음의 목소리 ${all.size}개가 나오는 순간을 이 차가 모두 알아차려요"
            else if (onlyPack) "이 묶음의 목소리 ${all.size}개 가운데 ${offer}개의 순간을 이 차가 알아차려요. 나머지는 차가 그 값을 알려 주지 않아서 나오지 않아요"
            else "차가 알려 주는 값으로 알아차릴 수 있는 순간은 ${all.size}개 중 ${offer}개예요" + (pack?.let { ". 「${it.title}」에는 ${it.moments.size}개의 목소리가 있어요" } ?: ". 시그니처 송을 적용하면 이 순간에 목소리가 나와요"),
            color = p.textPrimary, style = Ivi.bodyLarge,
        )
        listOf("reaction" to "방금 한 일에 맞춰요", "push" to "ION이 먼저 알려요").forEach { (mode, title) ->
            if (all.none { it.props["mode"] == mode }) return@forEach
            Spacer(Modifier.height(16.dp))
            Text(title, color = p.textTertiary, style = Ivi.labelMedium)
            all.filter { it.props["mode"] == mode }.forEach { m ->
                val reason = ontology.momentReason(m.id)
                val has = pack?.moments?.contains(m.id.removePrefix("mo.")) == true
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    VerdictMark(ontology.momentMark(m.id))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.label, color = p.textPrimary, style = Ivi.bodyLargeStrong)
                        Text(m.props["signal"].orEmpty(), color = p.textTertiary, style = Ivi.labelMedium)
                        if (reason != null) Text(reason, color = p.informativeNegative, style = Ivi.labelMedium)
                    }
                    if (has && !onlyPack) Text("목소리 있어요", color = p.textSecondary, style = Ivi.labelMedium)
                }
            }
        }
    }
}
