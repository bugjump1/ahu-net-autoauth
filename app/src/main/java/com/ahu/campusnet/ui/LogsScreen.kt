package com.ahu.campusnet.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahu.campusnet.auth.AuthController
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LogsScreen() {
    val logs by AuthController.logs.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item {
            ScreenHeader("运行日志", "最近 ${logs.size} 条")
        }

        // 顶部长条主色按钮（与首页「去填写」同款）
        item {
            Button(
                onClick = { AuthController.clearLogs() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("清空日志")
            }
        }

        if (logs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "暂无日志。打开 App 会自动认证一次，" +
                            "或点首页的「检测状态」「立即认证」后，记录会出现在这里。",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            // 全部日志包进一张白底卡片
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        logs.forEach { line ->
                            Text(
                                text = line,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
