package com.ahu.campusnet.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ahu.campusnet.data.ConfigRepository
import com.ahu.campusnet.data.DarkMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 主题入口：Miuix（MIUI / HyperOS 风格）配色。
 *
 * 只提供 MiuixTheme 一处，全应用共用；深浅色由设置里的 DarkMode 决定。
 */
@Composable
fun CampusTheme(content: @Composable () -> Unit) {
    val config by ConfigRepository.config.collectAsState()
    val dark = when (config.darkMode) {
        DarkMode.System -> isSystemInDarkTheme()
        DarkMode.Light -> false
        DarkMode.Dark -> true
    }
    MiuixTheme(
        colors = if (dark) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
