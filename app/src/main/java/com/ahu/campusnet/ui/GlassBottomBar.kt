package com.ahu.campusnet.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class BottomTab(val label: String, val iconRes: Int)

/**
 * 液态玻璃底部导航栏（Kyant0 / Backdrop）。
 *
 * 实现要点：
 *  - 页面内容通过 `Modifier.layerBackdrop(backdrop)` 注册为「背景层」
 *  - 导航栏自身用 `Modifier.drawBackdrop(...)` 把背景层内容模糊、折射后再画出来
 *  - `vibrancy()` 提升饱和度，`blur()` 磨砂，`lens()` 产生玻璃边缘的折射
 *  - `onDrawSurface` 叠一层半透明底色，保证图标文字可读
 *
 * 所有 backdrop 相关的 API 都集中在这个文件里，方便日后库升级时只改这一处。
 */
@Composable
fun GlassBottomBar(
    tabs: List<BottomTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val light = !isDarkTheme()
    val surfaceColor = if (light) {
        Color(0xFFFAFAFA).copy(alpha = 0.42f)
    } else {
        Color(0xFF141414).copy(alpha = 0.42f)
    }
    val accent = MiuixTheme.colorScheme.primary
    val idle = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val shape = RoundedCornerShape(30.dp)

    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .fillMaxWidth()
            .height(64.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(10.dp.toPx())
                    lens(14.dp.toPx(), 28.dp.toPx())
                },
                onDrawSurface = { drawRect(surfaceColor) },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = 6.dp, horizontal = 4.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selected) accent.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(tab.iconRes),
                    contentDescription = tab.label,
                    modifier = Modifier.size(22.dp),
                    colorFilter = ColorFilter.tint(if (selected) accent else idle),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = tab.label,
                    style = MiuixTheme.textStyles.body2,
                    color = if (selected) accent else idle,
                )
            }
        }
    }
}
