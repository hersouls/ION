package app.roadmemory.car.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

/**
 * 모션 길이(가이드 Animation › Duration 토큰, ivi_motion_duration_*). 화면 안의 전환은 long2(450ms)를 넘기지 않아요.
 * 값은 가이드의 표를 그대로 옮겼고, 앱의 모든 전환은 이 이름으로만 길이를 골라요.
 */
object IviMotion {
    const val SHORT1 = 100
    const val SHORT2 = 150
    const val MEDIUM1 = 250
    const val MEDIUM2 = 300
    const val LONG1 = 400
    const val LONG2 = 450
}

/** 가이드 Interaction › Button: 누르는 동안 95%로 줄어들고 눌림 색이 되며, 손가락이 단추 밖으로 나가면 눌림이 취소돼요. */
private const val PRESSED_SCALE = 0.95f

/**
 * 가이드의 단추 상호작용을 한 번에 붙여요: 모양 · 바탕색 · 눌림 색 · 누르면 줄어듦 · 접근성 역할.
 * [clickable]이 손가락이 영역 밖으로 나가면 눌림을 취소하므로(PressInteraction.Cancel) 취소 규칙도 함께 지켜져요.
 * 클릭 영역은 줄어들기 전 크기 그대로라서, 누르는 동안 가장자리에서 눌림이 풀려 버리지 않아요.
 */
@Composable
internal fun Modifier.iviButton(
    shape: Shape,
    color: Color,
    pressedColor: Color,
    onClick: () -> Unit,
    onClickLabel: String? = null,
    enabled: Boolean = true,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) PRESSED_SCALE else 1f,
        animationSpec = tween(IviMotion.SHORT1),
        label = "press",
    )
    return this
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .background(if (pressed && enabled) pressedColor else color, shape)
}
