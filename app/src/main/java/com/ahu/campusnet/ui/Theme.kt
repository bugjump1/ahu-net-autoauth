package com.ahu.campusnet.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ahu.campusnet.data.ConfigRepository
import com.ahu.campusnet.data.DarkMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 主题入口：Miuix（MIUI / HyperOS 风格）。
 *
 * 用 `ThemeController` + `MiuixTheme(controller = ...)`，与 Miuix v0.9.3 官方 example 一致。
 *
 * 这里还负责把「应用内」的深浅色同步到窗口：
 *  - `windowBackground` 改成当前主题的 surface 色
 *  - 状态栏 / 导航栏图标明暗跟随当前主题
 *
 * 为什么必须做：`res/values-night` 只认**系统**深浅色，而本应用允许在设置里手动覆盖。
 * 若系统是浅色、用户手动选深色，XML 主题仍是 `values/themes.xml`，
 * 窗口背景就是白色 —— 深色界面画在纯白底上，正是"深色模式不对"的来源。
 */
@Composable
fun CampusTheme(content: @Composable () -> Unit) {
    val config by ConfigRepository.config.collectAsState()
    val dark = when (config.darkMode) {
        DarkMode.System -> isSystemInDarkTheme()
        DarkMode.Light -> false
        DarkMode.Dark -> true
    }
    val mode = when (config.darkMode) {
        DarkMode.System -> ColorSchemeMode.System
        DarkMode.Light -> ColorSchemeMode.Light
        DarkMode.Dark -> ColorSchemeMode.Dark
    }
    val controller = remember(mode) { ThemeController(mode) }

    MiuixTheme(
        controller = controller,
        content = {
            val view = LocalView.current
            val background = MiuixTheme.colorScheme.surface

            if (!view.isInEditMode) {
                SideEffect {
                    val window = view.context.findHostActivity()?.window ?: return@SideEffect
                    window.setBackgroundDrawable(ColorDrawable(background.toArgb()))
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
            }

            content()
        },
    )
}

/** 当前实际是否深色（含用户在设置里的手动覆盖），供玻璃导航栏与背景取色 */
@Composable
fun isDarkTheme(): Boolean {
    val config by ConfigRepository.config.collectAsState()
    return when (config.darkMode) {
        DarkMode.System -> isSystemInDarkTheme()
        DarkMode.Light -> false
        DarkMode.Dark -> true
    }
}

/** Compose 的 view.context 是 ContextThemeWrapper，需要逐层解到 Activity */
private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}
