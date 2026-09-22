package com.ahu.campusnet.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ahu.campusnet.R
import com.ahu.campusnet.auth.AuthController
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CampusTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val tabs = remember {
        listOf(
            BottomTab("首页", R.drawable.ic_tab_home),
            BottomTab("日志", R.drawable.ic_tab_log),
            BottomTab("设置", R.drawable.ic_tab_settings),
        )
    }
    var index by rememberSaveable { mutableIntStateOf(0) }

    // 打开 App 即检测网络环境；未在线且已配置凭证时自动认证一次
    LaunchedEffect(Unit) {
        AuthController.onAppOpen(context)
    }

    val background = MiuixTheme.colorScheme.surface
    // 背景层：默认 onDraw 就是 drawContent()，配合下面「背景色在内侧」的写法，
    // 页面底色与内容会一起被录进图层，供玻璃导航栏取样。
    //
    // 注意不要写成 rememberLayerBackdrop { ... } —— onDraw 是 remember 的 key，
    // 每次重组都会传一个新 lambda，于是 LayerBackdrop 被反复重建、
    // layerCoordinates 被清空，玻璃效果会闪甚至失效。
    val backdrop = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                // background 放在 layerBackdrop 内侧，才会被一起录进背景层；
                // 同时页面底色改由 Compose 绘制，跟随应用内深浅色设置（而非 values-night）
                .background(background)
        ) {
            when (index) {
                0 -> HomeScreen(onGoSettings = { index = 2 })
                1 -> LogsScreen()
                else -> SettingsScreen()
            }
        }

        GlassBottomBar(
            tabs = tabs,
            selectedIndex = index,
            onSelect = { index = it },
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                // 参考实现（ScheduleBottomBar.kt）：底栏只占屏宽 63% 并水平居中，
                // 且悬浮在离底部一段距离的位置，不是贴着导航栏
                .fillMaxWidth(0.63f)
                .padding(bottom = 20.dp),
        )
    }
}

/** 页面标题区（含状态栏留白） */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp)
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.title1,
            color = MiuixTheme.colorScheme.onBackground,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
