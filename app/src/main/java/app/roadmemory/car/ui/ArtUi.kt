package app.roadmemory.car.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roadmemory.car.Pane
import app.roadmemory.car.UiState
import app.roadmemory.car.brain.ArtShelf
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.studio.Art
import app.roadmemory.car.studio.StudioLibrary
import app.roadmemory.car.studio.StudioStore
import java.util.Calendar

// ───────── 디지털 아트: 지금 어울리는 작품 → 갈래 → 내려받고 → 걸고 → 앱 창 가득 감상. 규칙은 brain/Shelves.kt의 ArtShelf ─────────

/**
 * 디지털 아트 = 파트너 아티스트의 작품을 내려받아 ION 칸과 충전 화면에 걸어 두고 보는 서비스예요.
 *  · 지금 어울리는 작품: 시간대(아침 · 낮 · 밤)에 맞는 태그가 가장 많은 작품 한 점과 까닭. 이미 걸어 둔 작품이면 권하지 않아요.
 *  · 갈래: 두 점 이상에 붙은 태그와 "내려받은 것"으로 골라 봐요.
 *  · 감상: 내려받은 작품의 사진을 앱 창 가득 넘겨 봐요(달리는 중에는 열지 않아요).
 */
@Composable
internal fun ArtWorkflowPane(ui: UiState, def: ServiceDef, onHome: () -> Unit) {
    val ctx = LocalContext.current
    StudioLibrary.ensure(ctx)
    val cat = remember(app.roadmemory.car.studio.StudioLink.rev) { StudioStore.catalog(ctx) }
    val lib = StudioLibrary.state
    val p = LocalPalette.current
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    var open by remember { mutableStateOf<String?>(null) }
    var kind by remember { mutableStateOf<String?>(null) }
    var mineOnly by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Art?>(null) }
    val hungId = lib.appliedId(Pane.SVC_ART)
    val driving = ui.mode == Mode.DRIVING

    @Composable
    fun actions(a: Art) {
        val got = lib.has(a.id)
        val hung = hungId == a.id
        when {
            hung -> PillButton("내리기", { StudioLibrary.unapply(ctx, Pane.SVC_ART) })
            got -> PillButton("걸기", { StudioLibrary.apply(ctx, Pane.SVC_ART, a.id) }, filled = true)
            else -> DownloadButton(a.id) { StudioLibrary.download(ctx, a.id, a.files) }
        }
    }

    PaneFrame(def.title, onHome) {
        LinkNotice()
        ArtShelf.forHour(cat.arts, hour)?.takeIf { it.art.id != hungId }?.let { pick ->
            SectionTitle("지금 어울리는 작품")
            Card {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StudioImage(pick.art.cover, Modifier.size(width = 240.dp, height = 135.dp).clip(RoundedCornerShape(16.dp)), pick.art.title)
                    Column(Modifier.weight(1f)) {
                        Text(pick.art.title, color = p.textPrimary, style = Ivi.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(pick.art.artist, color = p.textTertiary, style = Ivi.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("지금 ${hour}시 · ${pick.part}이에요 · 태그 ${app.roadmemory.car.brain.Korean.gwa(pick.hits.joinToString(" · "))} 맞아요", color = p.textSecondary, style = Ivi.bodyMedium)
                    }
                    actions(pick.art)
                }
            }
        }

        val kinds = ArtShelf.kinds(cat.arts)
        val mine = cat.arts.count { lib.has(it.id) }
        val onlyMine = mineOnly && mine > 0
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("전체 ${cat.arts.size}", { kind = null; mineOnly = false }, filled = kind == null && !onlyMine)
            kinds.forEach { (tag, n) -> PillButton("$tag $n", { kind = if (kind == tag) null else tag; mineOnly = false }, filled = kind == tag && !onlyMine) }
            if (mine > 0) PillButton("내려받은 것 $mine", { mineOnly = !mineOnly; kind = null }, filled = onlyMine)
        }
        val shown = when { onlyMine -> cat.arts.filter { lib.has(it.id) }; kind != null -> cat.arts.filter { kind in it.tags }; else -> cat.arts }
        shown.forEach { a ->
            val got = lib.has(a.id)
            val hung = hungId == a.id
            Card(Modifier.clickable(role = Role.Button, onClickLabel = "${a.title} 열기") { open = if (open == a.id) null else a.id }) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StudioImage(a.cover, Modifier.size(width = 200.dp, height = 112.dp).clip(RoundedCornerShape(16.dp)), a.title)
                    Column(Modifier.weight(1f)) {
                        Text(a.title + if (hung) " · 걸어 둠" else "", color = p.textPrimary, style = Ivi.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${a.artist} · ${won(a.price)}", color = p.textTertiary, style = Ivi.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        Text(a.summary, color = p.textSecondary, style = Ivi.bodyMedium, maxLines = if (open == a.id) 6 else 2, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        actions(a)
                        if (got && !driving) PillButton("감상", { viewing = a })
                    }
                }
                if (open == a.id) {
                    Spacer(Modifier.height(14.dp))
                    Text(listOf("태그 ${a.tags.joinToString(" · ")}", "사진 ${a.photos.size}장").joinToString(" · "), color = p.textTertiary, style = Ivi.labelMedium)
                    if (got) {
                        Spacer(Modifier.height(10.dp))
                        PillButton("지우기", { StudioLibrary.remove(ctx, a.id, a.files) })
                    } else Text("내려받으면 작품 사진을 앱 창 가득 넘겨 볼 수 있어요", color = p.textSecondary, style = Ivi.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        NoteLine("내려받은 작품은 \"걸기\"로 ION 칸과 충전 화면에 걸어 두고, \"감상\"으로 사진을 크게 넘겨 봐요.")
    }
    viewing?.let { a ->
        val photos = a.photos.ifEmpty { listOf(a.cover) }
        PhotoLightbox(photos.mapIndexed { i, ph -> ph to a.title }, 0, notes = photos.indices.map { "${a.artist} · ${it + 1} / ${photos.size}" }, autoplay = photos.size > 1, driving = driving) { viewing = null }
    }
}
