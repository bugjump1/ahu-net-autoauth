package com.ahu.campusnet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ahu.campusnet.R
import com.ahu.campusnet.auth.AuthController
import com.ahu.campusnet.auth.LinkState
import com.ahu.campusnet.data.ConfigRepository
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(onGoSettings: () -> Unit) {
    val context = LocalContext.current
    val ui by AuthController.ui.collectAsState()
    val config by ConfigRepository.config.collectAsState()
    var confirmLogout by remember { mutableStateOf(false) }

    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm:ss") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item {
            // 标题直接用注入的应用名，和应用在桌面上的显示保持一致
            ScreenHeader(stringResource(R.string.app_name), "打开即认证 · Dr.COM")
        }

        // 没填账号密码时给一条醒目提示
        if (!config.hasCredential) {
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = "还没配置账号",
                            style = MiuixTheme.textStyles.main,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "填好学号和校园网密码后，打开 App 就会自动认证。",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onGoSettings,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text("去填写")
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------- 当前状态
        item {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(ui.state.color))
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            text = ui.state.label,
                            style = MiuixTheme.textStyles.title1,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    }

                    if (ui.detail.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = ui.detail,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    InfoRow("本机 IP", ui.ip.ifBlank { "—" })
                    InfoRow(
                        "账号",
                        ui.account.ifBlank { config.username.ifBlank { "未填写" } },
                    )
                    InfoRow("认证服务器", "${config.serverHost}:${config.portalPort}")
                    InfoRow(
                        "上次检测",
                        if (ui.lastCheckAt > 0L) {
                            Instant.ofEpochMilli(ui.lastCheckAt)
                                .atZone(ZoneId.systemDefault())
                                .toLocalTime()
                                .format(timeFormat)
                        } else {
                            "—"
                        },
                    )
                }
            }
        }

        // ---------------------------------------------------------- 操作
        item {
            SmallTitle(text = "操作")
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { AuthController.authenticate(context) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("立即认证")
                    }
                    Button(
                        onClick = { AuthController.checkOnly(context) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("检测状态")
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = "注销并断网",
                        onClick = { confirmLogout = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("确认注销校园网？") },
            text = { Text("注销后本机会立即断网，需要重新打开 App 认证才能恢复。") },
            confirmButton = {
                TextButton(
                    text = "确定注销",
                    onClick = {
                        confirmLogout = false
                        AuthController.logout(context)
                    },
                )
            },
            dismissButton = {
                TextButton(
                    text = "取消",
                    onClick = { confirmLogout = false },
                )
            },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

/** 供其它页面判断是否处于异常态 */
fun LinkState.isProblem(): Boolean =
    this == LinkState.Error || this == LinkState.NoCampus || this == LinkState.NoWifi
