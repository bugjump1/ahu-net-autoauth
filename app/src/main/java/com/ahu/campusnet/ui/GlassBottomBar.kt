package com.ahu.campusnet.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

data class BottomTab(val label: String, val iconRes: Int)

/** 按下时 tab 内容整体放大的倍率提供者，只作用于「捕获层」 */
private val LocalGlassTabScale = staticCompositionLocalOf<() -> Float> { { 1f } }

/** 按住时胶囊的放大倍率，取自参考实现（78dp / 56dp） */
private const val PRESSED_SCALE = 78f / 56f

private val CONTAINER_HEIGHT = 56.dp
private val SELECTOR_HEIGHT = 48.dp

/**
 * 液态玻璃底部导航栏。
 *
 * 结构、尺寸、颜色、手势全部对齐 hyper_schedule 的
 * `ui/components/LiquidBottomTabs.kt`（用 Miuix + Kyant0 backdrop 的真实生产实现），
 * 只去掉两处本项目拿不到的东西：它的 edgeLight 光边、以及 InteractiveHighlight
 * 高光层（后者依赖 backdrop 库的 internal API，Maven 产物里访问不到）。
 *
 * 四层结构（缺一不可）：
 *
 *  ① **可见层** —— `drawBackdrop` 把页面内容模糊+折射后画出来，再叠 60% 白 /
 *     54% 深色的容器色。图标文字用纯黑/纯白，不缩放（放大只发生在捕获层）。
 *  ② **捕获层** —— 同一份内容 `alpha(0f)` 不可见，用 `layerBackdrop(tabsBackdrop)`
 *     录进独立图层，并整体 `ColorFilter.tint(accentColor)`。
 *     ★ accentColor 是**黑/白**（不是主题色）—— 所以选中项看起来仍是黑图标，
 *       只是透过玻璃更"实"。之前误用了主题蓝，是这个底栏一直不像的根本原因。
 *  ③ **胶囊** —— 一块只做折射的玻璃，取样 `combined(页面, 捕获层)`；
 *     静止时叠 8% 黑（深色下 10% 白），按住时才开 `chromaticAberration` 彩色色散。
 *  ④ **统一手势层** —— 覆盖整条底栏，同时负责点击与拖动：
 *       · 按在胶囊上 → 直接 1:1 跟手拖动
 *       · 按在别的 tab 上 → 胶囊飞过去并保持按压（松手才提交）
 *       · 松手 → 吸附最近一格并回调
 *     所以 tab 本身**不挂 clickable**，避免和拖动抢事件。
 */
@Composable
fun GlassBottomBar(
    tabs: List<BottomTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    // 跟随「应用内」深浅色，而不是系统 —— 手动切深色时玻璃才跟着变
    val isLight = !isDarkTheme()
    val accentColor = if (isLight) Color.Black else Color.White
    val containerColor = if (isLight) {
        Color(0xFFFFFFFF).copy(alpha = 0.6f)
    } else {
        Color(0xFF121212).copy(alpha = 0.54f)
    }
    val contentColor = if (isLight) Color.Black else Color.White
    // 胶囊/容器形状：等价的「胶囊」（参考实现用的是连续曲率胶囊 com.kyant.capsule，
    // 那个包不在 backdrop 的公开依赖里，用 50% 圆角代替，几何上同为胶囊）
    val capsule = RoundedCornerShape(percent = 50)

    val tabsBackdrop = rememberLayerBackdrop()
    val animationScope = rememberCoroutineScope()
    val latestOnSelect by rememberUpdatedState(onSelect)

    BoxWithConstraints(
        // 必须给死高度：内部手势层用 fillMaxSize()，高度不固定的话会撑满整屏、抢走页面手势
        modifier = modifier.height(CONTAINER_HEIGHT),
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val viewConfiguration = LocalViewConfiguration.current
        val padPx = with(density) { 4.dp.toPx() }
        val tabWidth = (constraints.maxWidth.toFloat() - padPx * 2f) / tabs.size
        val maxIndex = (tabs.size - 1).toFloat()

        val drag = remember(animationScope) {
            GlassDragState(
                scope = animationScope,
                initialIndex = selectedIndex.toFloat(),
                maxIndex = maxIndex,
            )
        }

        // 外部改选中（比如首页「去填写」跳到设置页）时，胶囊跟着滑过去
        val latestSelected by rememberUpdatedState(selectedIndex)
        LaunchedEffect(drag) {
            snapshotFlow { latestSelected }.collectLatest { index ->
                if (index != drag.value.value.roundToInt()) {
                    drag.animateToIndex(index)
                }
            }
        }

        // 拖动时整条底栏的橡皮筋位移
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (drag.offset.value / constraints.maxWidth).coerceIn(-1f, 1f)
                with(density) { 4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }

        // ① 可见层
        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { capsule },
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx())
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    highlight = null,
                    layerBlock = {
                        val scale = 1f + (16.dp.toPx() / size.width) * drag.pressProgress.value
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .height(CONTAINER_HEIGHT)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { GlassTabItem(tab = it, contentColor = contentColor) }
        }

        // ② 捕获层（透明，只透过胶囊显形）
        CompositionLocalProvider(
            LocalGlassTabScale provides { 1f + 0.2f * drag.pressProgress.value }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { capsule },
                        effects = {
                            val progress = drag.pressProgress.value
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(24.dp.toPx() * progress, 24.dp.toPx() * progress)
                        },
                        highlight = { Highlight.Default.copy(alpha = drag.pressProgress.value) },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .height(SELECTOR_HEIGHT)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { GlassTabItem(tab = it, contentColor = contentColor) }
            }
        }

        // ③ 胶囊（纯视觉）
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX = drag.value.value * tabWidth + panelOffset
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { capsule },
                    effects = {
                        val progress = drag.pressProgress.value
                        lens(
                            10.dp.toPx() * progress,
                            14.dp.toPx() * progress,
                            chromaticAberration = true,
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = drag.pressProgress.value) },
                    shadow = { Shadow(alpha = drag.pressProgress.value) },
                    innerShadow = {
                        InnerShadow(
                            radius = 8.dp * drag.pressProgress.value,
                            alpha = drag.pressProgress.value,
                        )
                    },
                    layerBlock = {
                        scaleX = drag.scaleX.value
                        scaleY = drag.scaleY.value
                        // 甩动时横向拉长、纵向压扁，制造液体的黏滞感
                        val velocity = drag.velocity.value / 10f
                        scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = drag.pressProgress.value
                        drawRect(
                            color = if (isLight) {
                                Color.Black.copy(alpha = 0.08f)
                            } else {
                                Color.White.copy(alpha = 0.1f)
                            },
                            alpha = 1f - progress,
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    },
                )
                .height(SELECTOR_HEIGHT)
                .fillMaxWidth(1f / tabs.size),
        )

        // ④ 统一手势层：点击 + 拖动都在这
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(tabs.size, tabWidth, padPx) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downX = down.position.x

                        // 落点离胶囊近 -> 直接拖；落在别的 tab 上 -> 胶囊先飞过去并按住
                        val capsuleCenterX = padPx + (drag.value.value + 0.5f) * tabWidth
                        var dragging = abs(downX - capsuleCenterX) <= tabWidth * 0.55f
                        var pressedTab = -1

                        if (dragging) {
                            drag.press()
                        } else {
                            pressedTab = floor((downX - padPx) / tabWidth)
                                .toInt()
                                .coerceIn(0, tabs.size - 1)
                            drag.previewIndex(pressedTab)
                        }

                        var lastX = downX
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                val target = if (dragging) {
                                    drag.value.value.roundToInt().coerceIn(0, tabs.size - 1)
                                } else {
                                    pressedTab.coerceIn(0, tabs.size - 1)
                                }
                                drag.settle(target, tabs.size) { latestOnSelect(it) }
                                break
                            }

                            val dx = change.position.x - lastX
                            val totalDx = change.position.x - downX
                            lastX = change.position.x

                            // 点在 tab 上之后继续横向滑 -> 接管成拖动
                            if (!dragging && abs(totalDx) > touchSlop) {
                                dragging = true
                            }

                            if (dragging && abs(dx) > 0.01f) {
                                drag.dragBy(dx, tabWidth)
                                drag.dragOffsetBy(dx)
                            }

                            change.consume()
                        }
                    }
                }
        )
    }
}

/**
 * 拖拽 / 按压动画状态，对应参考实现的 `DampedDragAnimation`
 * （只保留本项目用得到的部分：值、按压进度、缩放、速度、面板位移）。
 */
private class GlassDragState(
    private val scope: CoroutineScope,
    initialIndex: Float,
    private val maxIndex: Float,
) {
    private val valueSpec = spring<Float>(1f, 1000f, 0.001f)
    private val velocitySpec = spring<Float>(0.5f, 300f, 0.005f)
    private val pressSpec = spring<Float>(1f, 1000f, 0.001f)
    private val scaleXSpec = spring<Float>(0.6f, 250f, 0.001f)
    private val scaleYSpec = spring<Float>(0.7f, 250f, 0.001f)

    /** 当前选中位置，可以是小数（拖动过程中） */
    val value = Animatable(initialIndex, 0.001f)
    val pressProgress = Animatable(0f, 0.001f)
    val scaleX = Animatable(1f, 0.001f)
    val scaleY = Animatable(1f, 0.001f)
    val velocity = Animatable(0f, 5f)
    /** 橡皮筋位移的原始累计量，由调用方换算成 px */
    val offset = Animatable(0f)

    private val tracker = VelocityTracker()

    fun press() {
        tracker.resetTracking()
        scope.launch { pressProgress.animateTo(1f, pressSpec) }
        scope.launch { scaleX.animateTo(PRESSED_SCALE, scaleXSpec) }
        scope.launch { scaleY.animateTo(PRESSED_SCALE, scaleYSpec) }
    }

    fun release() {
        scope.launch { pressProgress.animateTo(0f, pressSpec) }
        scope.launch { scaleX.animateTo(1f, scaleXSpec) }
        scope.launch { scaleY.animateTo(1f, scaleYSpec) }
    }

    /** 按住某个 tab：胶囊飞过去并保持按压，松手才提交选中 */
    fun previewIndex(index: Int) {
        press()
        scope.launch { value.animateTo(index.toFloat().coerceIn(0f, maxIndex), valueSpec) }
    }

    fun animateToIndex(index: Int) {
        scope.launch { value.animateTo(index.toFloat().coerceIn(0f, maxIndex), valueSpec) }
    }

    /** 拖动跟手：用阻尼弹簧追，手感更"黏" */
    fun dragBy(deltaPx: Float, tabWidthPx: Float) {
        if (tabWidthPx <= 0f) return
        val target = (value.value + deltaPx / tabWidthPx).coerceIn(0f, maxIndex)
        scope.launch { value.animateTo(target, valueSpec) { trackVelocity() } }
    }

    fun dragOffsetBy(deltaPx: Float) {
        scope.launch { offset.snapTo(offset.value + deltaPx) }
    }

    /** 松手：吸附到最近一格、橡皮筋回弹、结束按压，并提交选中 */
    fun settle(target: Int, tabsCount: Int, commit: (Int) -> Unit) {
        val safe = target.coerceIn(0, (tabsCount - 1).coerceAtLeast(0))
        scope.launch { value.animateTo(safe.toFloat(), valueSpec) { trackVelocity() } }
        scope.launch { offset.animateTo(0f, spring(1f, 300f, 0.5f)) }
        release()
        commit(safe)
    }

    private fun trackVelocity() {
        if (maxIndex <= 0f) return
        tracker.addPosition(System.currentTimeMillis(), Offset(value.value, 0f))
        val target = tracker.calculateVelocity().x / maxIndex
        scope.launch { velocity.animateTo(target, velocitySpec) }
    }
}

@Composable
private fun RowScope.GlassTabItem(tab: BottomTab, contentColor: Color) {
    val scaleProvider = LocalGlassTabScale.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .semantics { role = Role.Tab }
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scaleProvider()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(tab.iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            // 参考实现：图标/文字用 onSurfaceContainer@0.8（深灰而非纯黑），观感更柔和
            colorFilter = ColorFilter.tint(contentColor.copy(alpha = 0.8f)),
        )
        BasicText(
            text = tab.label,
            style = TextStyle(color = contentColor, fontSize = 11.sp),
        )
    }
}
