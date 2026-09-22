package com.ahu.campusnet.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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

data class BottomTab(val label: String, val iconRes: Int)

/** 按下时整体放大的倍率提供者，由 LiquidBottomTabs 注入 */
private val LocalGlassTabScale = staticCompositionLocalOf<() -> Float> { { 1f } }

/**
 * 液态玻璃底部导航栏。
 *
 * 移植自 Kyant0 AndroidLiquidGlass 官方 catalog 的 `LiquidBottomTabs`
 * （tag 2.0.1，`app/.../catalog/components/LiquidBottomTabs.kt`），
 * 去掉了官方的「按住拖动切换 tab」手势（那套 DampedDragAnimation / InteractiveHighlight
 * 是 demo 内部工具类，不在库里），改用下标动画驱动指示器滑动。
 *
 * 三层结构（缺一不可，官方就是这么做的）：
 *
 *  ① 玻璃底板 Row —— drawBackdrop 把 [backdrop]（页面内容）模糊 + 折射后画出来，
 *     再叠一层 40% 半透明容器色；这一层可见，画的是正常颜色的图标文字。
 *  ② 同一份内容再画一遍，`alpha(0f)` 不可见，但用 `layerBackdrop(tabsBackdrop)`
 *     录进独立图层并整体 `ColorFilter.tint(accentColor)` —— 得到「强调色版」的图标文字。
 *  ③ 选中指示器 Box —— 一块玻璃胶囊，取样 `combined(backdrop, tabsBackdrop)`，
 *     所以它折射出来的是**强调色的图标**；再叠 10% 黑(浅色)/白(深色)。
 *     按住时打开 `chromaticAberration`，就是官方那种彩色色散的液态玻璃效果。
 *
 * 所有 backdrop 相关 API 集中在本文件，库升级只需改这里。
 */
@Composable
fun GlassBottomBar(
    tabs: List<BottomTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    // 跟随「应用内」的深浅色设置，而不是系统 —— 手动切深色时玻璃才跟着变色
    val isLight = !isDarkTheme()
    val accentColor = if (isLight) Color(0xFF0088FF) else Color(0xFF0091FF)
    val containerColor = if (isLight) {
        Color(0xFFFAFAFA).copy(alpha = 0.4f)
    } else {
        Color(0xFF121212).copy(alpha = 0.4f)
    }
    val contentColor = if (isLight) Color.Black else Color.White

    // 按住进度：驱动折射、高光、内阴影与缩放，松手归零
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressProgress = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "glassBarPress",
    )
    // 选中下标做动画，指示器在两格之间滑过去
    val animatedIndex = animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "glassBarIndex",
    )

    // 专门用来录「强调色版 tab 内容」的图层
    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val barShape = RoundedCornerShape(28.dp)
        // 底板左右各留 4dp，所以每格的宽度是 (总宽 - 8dp) / 格数
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8.dp.toPx()) / tabs.size
        }

        // ① 玻璃底板 + 可见的 tab 内容
        Row(
            Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { barShape },
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx())
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    layerBlock = {
                        // 按住时整条微微涨大
                        val s = 1f + (16.dp.toPx() / size.width) * pressProgress.value
                        scaleX = s
                        scaleY = s
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .height(64.dp)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                GlassTabItem(
                    tab = tab,
                    contentColor = contentColor,
                    interactive = true,
                    interactionSource = interactionSource,
                    onClick = { onSelect(index) },
                )
            }
        }

        // ② 不可见的一份，仅用于录制「强调色版」图层供指示器取样
        CompositionLocalProvider(
            LocalGlassTabScale provides { 1f + 0.2f * pressProgress.value }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { barShape },
                        effects = {
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(
                                24.dp.toPx() * pressProgress.value,
                                24.dp.toPx() * pressProgress.value,
                            )
                        },
                        highlight = { Highlight.Default.copy(alpha = pressProgress.value) },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .height(64.dp)
                    .fillMaxWidth()
                    .padding(4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { tab ->
                    // 这一层必须完全不可交互：它叠在可见层之上，
                    // 若也挂 clickable 就会把点击吞掉（官方 demo 两层都有同样的
                    // onClick 才"碰巧"不出问题，这里干脆不挂）
                    GlassTabItem(
                        tab = tab,
                        contentColor = contentColor,
                        interactive = false,
                        interactionSource = null,
                        onClick = {},
                    )
                }
            }
        }

        // ③ 选中指示器：一块只做折射的玻璃胶囊
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX = animatedIndex.value * tabWidth
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { barShape },
                    effects = {
                        lens(
                            10.dp.toPx() * pressProgress.value,
                            14.dp.toPx() * pressProgress.value,
                            chromaticAberration = true,
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = pressProgress.value) },
                    shadow = { Shadow(alpha = pressProgress.value) },
                    innerShadow = {
                        InnerShadow(
                            radius = 8.dp * pressProgress.value,
                            alpha = pressProgress.value,
                        )
                    },
                    layerBlock = {
                        val s = 1f + 0.12f * pressProgress.value
                        scaleX = s
                        scaleY = s
                    },
                    onDrawSurface = {
                        // 静止时的那层淡灰（深色下是淡白），正是参考图里选中项的样子
                        drawRect(
                            color = if (isLight) {
                                Color.Black.copy(alpha = 0.1f)
                            } else {
                                Color.White.copy(alpha = 0.1f)
                            },
                            alpha = 1f - pressProgress.value,
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * pressProgress.value))
                    },
                )
                .height(56.dp)
                .fillMaxWidth(1f / tabs.size),
        )
    }
}

@Composable
private fun RowScope.GlassTabItem(
    tab: BottomTab,
    contentColor: Color,
    interactive: Boolean,
    interactionSource: MutableInteractionSource?,
    onClick: () -> Unit,
) {
    val scaleProvider = LocalGlassTabScale.current
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(28.dp))
            .then(
                // 只有可见那一层才挂点击，不可见层不参与命中测试
                if (interactive) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
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
            modifier = Modifier.size(26.dp),
            colorFilter = ColorFilter.tint(contentColor),
        )
        BasicText(
            text = tab.label,
            style = TextStyle(color = contentColor, fontSize = 12.sp),
        )
    }
}
