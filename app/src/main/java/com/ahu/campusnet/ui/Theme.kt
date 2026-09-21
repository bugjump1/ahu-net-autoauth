package com.ahu.campusnet.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.ahu.campusnet.data.ConfigRepository
import com.ahu.campusnet.data.DarkMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 主题入口：Miuix（MIUI / HyperOS 风格）。
 *
 * 这里用 `ThemeController` + `MiuixTheme(controller = ...)`，
 * 与 Miuix v0.9.3 官方 example 的写法一致。
 */
@Composable
fun CampusTheme(content: @Composable () -> Unit) {
    val config by ConfigRepository.config.collectAsState()
    val mode = when (config.darkMode) {
        DarkMode.System -> ColorSchemeMode.System
        DarkMode.Light -> ColorSchemeMode.Light
        DarkMode.Dark -> ColorSchemeMode.Dark
    }
    val controller = remember(mode) { ThemeController(mode) }
    MiuixTheme(
        controller = controller,
        content = content,
    )
}

/** 当前实际是否深色（含用户在设置里的手动覆盖），供玻璃导航栏取底色 */
@Composable
fun isDarkTheme(): Boolean {
    val config by ConfigRepository.config.collectAsState()
    return when (config.darkMode) {
        DarkMode.System -> isSystemInDarkTheme()
        DarkMode.Light -> false
        DarkMode.Dark -> true
    }
}
