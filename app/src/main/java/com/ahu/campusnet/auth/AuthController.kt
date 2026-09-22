package com.ahu.campusnet.auth

import android.content.Context
import android.util.Log
import com.ahu.campusnet.data.ConfigRepository
import com.ahu.campusnet.net.DrcomClient
import com.ahu.campusnet.net.NetInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 链接状态。
 *
 * 注意 label 里**不要**再带 "●" 之类的符号：首页会另画一个按 [color] 上色的圆点，
 * 两边都带的话就会看到「小彩点 + 一个大黑点」并排。
 */
enum class LinkState(val label: String, val color: Long) {
    /** 还没检测过 */
    Idle("未检测", 0xFF9E9E9E),
    Checking("检测中…", 0xFF3B82F6),
    Online("已在线", 0xFF16A34A),
    NotAuthed("未认证", 0xFFD97706),
    /** 当前走的是移动数据 */
    MobileData("移动网络", 0xFF9E9E9E),
    NoWifi("未连 Wi-Fi", 0xFF9E9E9E),
    /** 连着 Wi-Fi，但连不到校园网认证服务器 */
    NoCampus("未连校园网", 0xFF9E9E9E),
    Error("异常", 0xFFDC2626),
}

data class AuthUiState(
    val state: LinkState = LinkState.Idle,
    val detail: String = "",
    val ip: String = "",
    val account: String = "",
    val lastCheckAt: Long = 0L,
)

/**
 * 认证流程控制器。
 *
 * 只在「打开 App」或用户手动点按钮时跑一次，**不做后台常驻、不做轮询**：
 *
 *   ① 网络类型：必须是 Wi-Fi（移动数据 / 无网络直接返回）
 *   ② 是否在校园网：TCP 握手探认证服务器
 *   ③ 是否已联网：内核 chkstatus 优先，系统 NET_CAPABILITY_VALIDATED 兜底
 *   ④ 未在线且已配置凭证 → 执行一次认证
 *
 * 状态放在 [MutableStateFlow] 里供 Compose 观察；因为进程生命周期足够短，
 * 用一个进程级 CoroutineScope 即可，无需 Service。
 */
object AuthController {

    private const val TAG = "AuthController"
    private const val MAX_LOGS = 300
    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 防止连点 / 重复触发 */
    private val busy = AtomicBoolean(false)

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    // ------------------------------------------------------------------ 对外入口

    /** App 打开时调用：检测网络环境，未在线且有凭证就自动认证一次 */
    fun onAppOpen(context: Context) = run(context, autoLogin = true)

    /** 「立即认证」：检测，未在线则认证 */
    fun authenticate(context: Context) = run(context, autoLogin = true)

    /** 「检测状态」：只检测，不认证 */
    fun checkOnly(context: Context) = run(context, autoLogin = false)

    /** 注销（注意：注销后本机会断网） */
    fun logout(context: Context) {
        if (!busy.compareAndSet(false, true)) {
            appendLog("上一次操作还没结束，忽略本次注销")
            return
        }
        val appContext = context.applicationContext
        scope.launch {
            try {
                val cfg = ConfigRepository.config.value
                val ip = withContext(Dispatchers.IO) { NetInfo.localIpv4(appContext) }
                    ?: cfg.userIpOverride.ifBlank { null }
                setState(LinkState.Checking, "正在注销…", updateTime = false)
                val result = withContext(Dispatchers.IO) { DrcomClient(cfg, ip).logout() }
                if (result.ok) {
                    setState(LinkState.NotAuthed, "已注销")
                    appendLog("已注销：${result.message}")
                } else {
                    setState(LinkState.Error, result.message)
                    appendLog("注销失败：${result.message}")
                }
            } catch (e: Exception) {
                setState(LinkState.Error, "注销出错：${e.message ?: e.javaClass.simpleName}")
                appendLog("注销异常：${e.message}")
            } finally {
                busy.set(false)
            }
        }
    }

    // ------------------------------------------------------------------ 主流程

    private fun run(context: Context, autoLogin: Boolean) {
        if (!busy.compareAndSet(false, true)) {
            appendLog("上一次检测还没结束，忽略本次请求")
            return
        }
        val appContext = context.applicationContext
        scope.launch {
            try {
                doRun(appContext, autoLogin)
            } catch (e: Exception) {
                setState(LinkState.Error, "检测出错：${e.message ?: e.javaClass.simpleName}")
                appendLog("检测异常：${e.message}")
            } finally {
                busy.set(false)
            }
        }
    }

    private suspend fun doRun(context: Context, autoLogin: Boolean) {
        val cfg = ConfigRepository.config.value
        setState(LinkState.Checking, "正在检测网络环境…", updateTime = false)

        // ① 网络类型：必须是非移动网络的 Wi-Fi
        if (!withContext(Dispatchers.IO) { NetInfo.isWifi(context) }) {
            val mobile = withContext(Dispatchers.IO) { NetInfo.isMobile(context) }
            if (mobile) {
                setState(LinkState.MobileData, "当前是移动网络，校园网认证仅在 Wi-Fi 下有效")
                appendLog("当前为移动网络，跳过认证")
            } else {
                setState(LinkState.NoWifi, "未连接 Wi-Fi")
                appendLog("未连接 Wi-Fi，跳过认证")
            }
            return
        }

        val ip = withContext(Dispatchers.IO) { NetInfo.localIpv4(context) }
            ?: cfg.userIpOverride.ifBlank { null }
        _ui.value = _ui.value.copy(ip = ip ?: "未获取", account = cfg.username)

        val client = DrcomClient(cfg, ip)

        // ② 是否在校园网：能否 TCP 连到认证服务器
        if (!withContext(Dispatchers.IO) { client.reachable() }) {
            setState(
                LinkState.NoCampus,
                "连不上认证服务器 ${cfg.serverHost}:${cfg.portalPort}",
            )
            appendLog("连不上认证服务器，判定为未接入校园网，不做认证尝试")
            return
        }

        // ③ 是否已联网：内核接口优先；内核说未在线时不全信，再用系统状态/外网探针复核
        val kernel = withContext(Dispatchers.IO) {
            runCatching { client.kernelOnline() }.getOrNull()
        }
        if (kernel == null) {
            // 原始返回只进 logcat 供排查，用户日志保持简洁
            Log.i(TAG, "chkstatus 无结论：${client.lastRawBody.ifBlank { "（空）" }}")
            appendLog("在线状态确认失败，改用其他方式判断")
        } else if (!kernel) {
            appendLog("本地检测未在线，正在复核")
        }

        val online = kernel == true ||
            withContext(Dispatchers.IO) { NetInfo.isInternetOk(context) } ||
            withContext(Dispatchers.IO) { NetInfo.probeInternetOk() }
        if (online) {
            setState(LinkState.Online, "已在线")
            appendLog("已在线，无需认证")
            logKernelInfo(client)
            return
        }

        // ④ 未在线：需要认证
        setState(
            LinkState.NotAuthed,
            if (cfg.hasCredential) "未在线" else "尚未配置账号密码",
        )
        if (!autoLogin) {
            appendLog("当前未认证（仅检测）")
            return
        }
        if (!cfg.hasCredential) {
            setState(LinkState.NotAuthed, "尚未配置账号和密码")
            appendLog("未配置账号密码，请到「设置」里填写")
            return
        }

        appendLog("开始认证：${cfg.username} @ ${ip ?: "未知IP"}")
        val result = withContext(Dispatchers.IO) { client.login() }
        if (result.ok) {
            setState(LinkState.Online, result.message)
            appendLog("认证成功：${result.message}")
        } else {
            setState(LinkState.Error, result.message)
            appendLog("认证失败：${result.message}")
        }
    }

    // ------------------------------------------------------------------ 工具

    private fun logKernelInfo(client: DrcomClient) {
        val info = client.kernelInfo
        if (info.isEmpty()) return
        val ip = info["ss5"] ?: info["v46ip"] ?: info["v4ip"] ?: ""
        val uid = info["uid"] ?: ""
        val vlan = info["vlanid"] ?: ""
        appendLog(
            "内核：IP=${ip.ifBlank { "-" }}  账号=${uid.ifBlank { "-" }}  " +
                "vlan=${vlan.ifBlank { "-" }}"
        )
    }

    private fun setState(state: LinkState, detail: String, updateTime: Boolean = true) {
        _ui.value = _ui.value.copy(
            state = state,
            detail = detail,
            lastCheckAt = if (updateTime) System.currentTimeMillis() else _ui.value.lastCheckAt,
        )
    }

    fun appendLog(line: String) {
        val entry = "${LocalTime.now().format(TIME_FMT)}  $line"
        Log.i(TAG, line)
        _logs.value = (listOf(entry) + _logs.value).take(MAX_LOGS)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
