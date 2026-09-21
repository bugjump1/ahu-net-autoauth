package com.ahu.campusnet.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class DarkMode { System, Light, Dark }

/**
 * 认证参数与外观设置。
 *
 * 默认值与电脑版 / 真实抓包保持一致；这里没有「守护」「自启」「轮询间隔」之类的
 * 后台选项 —— 安卓版只在打开 App 时认证一次。
 */
data class DrcomConfig(
    val serverHost: String = "172.16.253.3",
    val portalPort: Int = 801,
    /** 抓包里 wlan_ac_ip 为空，默认留空 */
    val acIp: String = "",
    /** 抓包里 wlan_ac_name 为空，默认留空 */
    val acName: String = "",
    /** a79.htm 里的 vlanid，注销接口需要 */
    val vlanId: String = "0",
    val username: String = "",
    val password: String = "",
    /** 自动探测不到本机 IP 时的兜底值 */
    val userIpOverride: String = "",
    val darkMode: DarkMode = DarkMode.System,
) {
    val hasCredential: Boolean get() = username.isNotBlank() && password.isNotBlank()
}

/**
 * 配置读写。密码字段经 [SecureStore]（Android Keystore AES-256-GCM）加密后落盘。
 */
object ConfigRepository {

    private const val PREF_NAME = "campusnet_config"
    private const val KEY_JSON = "config_json"

    private var prefs: SharedPreferences? = null

    private val _config = MutableStateFlow(DrcomConfig())
    val config: StateFlow<DrcomConfig> = _config.asStateFlow()

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs = p
        _config.value = runCatching { read(p) }.getOrDefault(DrcomConfig())
    }

    fun update(newConfig: DrcomConfig) {
        _config.value = newConfig
        prefs?.let { runCatching { write(it, newConfig) } }
    }

    fun update(block: (DrcomConfig) -> DrcomConfig) = update(block(_config.value))

    // ---------------------------------------------------------------- 内部实现

    private fun read(p: SharedPreferences): DrcomConfig {
        val raw = p.getString(KEY_JSON, null) ?: return DrcomConfig()
        val o = JSONObject(raw)
        val d = DrcomConfig()
        return DrcomConfig(
            serverHost = o.optString("serverHost", d.serverHost).ifBlank { d.serverHost },
            portalPort = o.optInt("portalPort", d.portalPort).takeIf { it in 1..65535 } ?: d.portalPort,
            acIp = o.optString("acIp", d.acIp),
            acName = o.optString("acName", d.acName),
            vlanId = o.optString("vlanId", d.vlanId),
            username = o.optString("username", d.username),
            password = SecureStore.decrypt(o.optString("password", "")),
            userIpOverride = o.optString("userIpOverride", d.userIpOverride),
            darkMode = runCatching {
                DarkMode.valueOf(o.optString("darkMode", d.darkMode.name))
            }.getOrDefault(DarkMode.System),
        )
    }

    private fun write(p: SharedPreferences, c: DrcomConfig) {
        val o = JSONObject()
            .put("serverHost", c.serverHost)
            .put("portalPort", c.portalPort)
            .put("acIp", c.acIp)
            .put("acName", c.acName)
            .put("vlanId", c.vlanId)
            .put("username", c.username)
            .put("password", SecureStore.encrypt(c.password))
            .put("userIpOverride", c.userIpOverride)
            .put("darkMode", c.darkMode.name)
        p.edit().putString(KEY_JSON, o.toString()).apply()
    }
}
