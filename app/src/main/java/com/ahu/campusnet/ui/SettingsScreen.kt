package com.ahu.campusnet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ahu.campusnet.auth.AuthController
import com.ahu.campusnet.data.ConfigRepository
import com.ahu.campusnet.data.DarkMode
import com.ahu.campusnet.data.DrcomConfig
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val config by ConfigRepository.config.collectAsState()

    var username by remember(config.username) { mutableStateOf(config.username) }
    var password by remember(config.password) { mutableStateOf(config.password) }
    var host by remember(config.serverHost) { mutableStateOf(config.serverHost) }
    var port by remember(config.portalPort) { mutableStateOf(config.portalPort.toString()) }
    var ipOverride by remember(config.userIpOverride) { mutableStateOf(config.userIpOverride) }
    var showPassword by remember { mutableStateOf(false) }

    fun persist() {
        ConfigRepository.update(
            config.copy(
                username = username.trim(),
                password = password,
                serverHost = host.trim().ifBlank { config.serverHost },
                portalPort = port.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: config.portalPort,
                userIpOverride = ipOverride.trim(),
            )
        )
        AuthController.appendLog("设置已保存")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item { ScreenHeader("设置", "凭证仅加密保存在本机") }

        // ---------------------------------------------------------- 账号
        item {
            SmallTitle(text = "账号")
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    TextField(
                        value = username,
                        onValueChange = { username = it },
                        label = "学号 / 账号",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    TextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "校园网密码",
                        singleLine = true,
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            Text(
                                text = if (showPassword) "隐藏" else "显示",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(end = 14.dp)
                                    .clickable { showPassword = !showPassword },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "校园网密码默认身份证号后六位",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        // ---------------------------------------------------------- 保存
        item {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Button(
                        onClick = { persist() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("保存设置")
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "保存后回到「首页」会自动认证一次。密码用 Android Keystore" +
                            "（AES-256-GCM）加密存储，密钥由系统安全模块保管，" +
                            "应用数据被拷到其它设备也无法解密。",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        // ---------------------------------------------------------- 高级
        item {
            SmallTitle(text = "高级（一般不用改）")
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    TextField(
                        value = host,
                        onValueChange = { host = it },
                        label = "认证服务器地址",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    TextField(
                        value = port,
                        onValueChange = { port = it },
                        label = "端口",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    TextField(
                        value = ipOverride,
                        onValueChange = { ipOverride = it },
                        label = "本机 IP 兜底（可留空，一般自动获取）",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "只有在自动获取不到本机 IP、且认证一直报 IP 相关错误时，" +
                            "才需要手工填这里。",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        // ---------------------------------------------------------- 外观
        item {
            SmallTitle(text = "外观")
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DarkMode.entries.forEach { mode ->
                        val selected = config.darkMode == mode
                        Button(
                            onClick = { ConfigRepository.update { it.copy(darkMode = mode) } },
                            modifier = Modifier.weight(1f),
                            colors = if (selected) {
                                ButtonDefaults.buttonColorsPrimary()
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                        ) {
                            Text(
                                when (mode) {
                                    DarkMode.System -> "跟随系统"
                                    DarkMode.Light -> "浅色"
                                    DarkMode.Dark -> "深色"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
