package app.roadmemory.car.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/*
 * 설정 한 줄(Moonwave 설정 규칙): 이름이 왼쪽, 스위치 · 값 · 작은 버튼 하나가 오른쪽 끝에 와요. 설정마다 줄 하나이고,
 * 줄 사이는 1px 선으로만 나눠요(카드 안에 또 상자를 두지 않아요). 색은 팔레트 토큰의 회색 단계만 써요.
 */

/** 스위치. 켜짐 · 꺼짐은 손잡이 위치와 글로 말해요(색만으로 알리지 않아요). 누르는 칸은 줄 전체라 64dp를 넘어요. */
@Composable
internal fun IviSwitch(on: Boolean) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(if (on) "켜짐" else "꺼짐", color = if (on) p.textPrimary else p.textTertiary, style = Ivi.bodySmallStrong)
        Box(Modifier.width(96.dp).height(56.dp).background(if (on) p.buttonFilled else p.surfaceAccent, RoundedCornerShape(28.dp)).padding(6.dp)) {
            Box(
                Modifier.width(44.dp).height(44.dp).align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                    .background(p.backgroundPrimary, RoundedCornerShape(22.dp)),
            )
        }
    }
}

/** 이름 + 한 줄 설명 + 오른쪽 끝 스위치. 줄 어디를 눌러도 바뀌어요. */
@Composable
internal fun SwitchRow(title: String, hint: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth()
            .clickable(role = Role.Switch, onClickLabel = title) { onChange(!on) }
            .semantics { stateDescription = if (on) "켜짐" else "꺼짐" }
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.textPrimary, style = Ivi.bodyLargeStrong)
            Text(hint, color = p.textTertiary, style = Ivi.labelMedium)
        }
        IviSwitch(on)
    }
}

/** 이름 + 한 줄 설명 + 오른쪽 끝 값. 바꿀 수 있으면 작은 버튼 하나를 값 옆에 둬요(action이 null이면 값만 보여 줘요). */
@Composable
internal fun ValueRow(title: String, hint: String, value: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.textPrimary, style = Ivi.bodyLargeStrong)
            Text(hint, color = p.textTertiary, style = Ivi.labelMedium)
        }
        Text(value, color = p.textSecondary, style = Ivi.bodyMedium)
        if (actionLabel != null && onAction != null) {
            Box(
                Modifier.height(IviSize.buttonSmall.dp)
                    .iviButton(RoundedCornerShape(IviSize.buttonSmall.dp / 2), p.buttonBasic, p.buttonBasicPressed, onAction)
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center,
            ) { Text(actionLabel, color = p.textPrimary, style = Ivi.bodySmallStrong) }
        }
    }
}

/** 줄 사이의 1px 선. */
@Composable
internal fun RowLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalPalette.current.surfaceAccent))
}
