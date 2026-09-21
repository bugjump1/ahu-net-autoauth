package com.ahu.campusnet.net

import android.util.Base64
import com.ahu.campusnet.data.DrcomConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.random.Random

data class LoginResult(
    val ok: Boolean,
    val message: String,
    val retCode: Int? = null,
    /** 请求是否成功送达认证服务器（false 表示网络不通） */
    val serverReached: Boolean = true,
)

/**
 * 安徽大学 Dr.COM（哆点）Portal 协议客户端。
 *
 * 参数完全对齐浏览器真实抓包：
 *   GET http://172.16.253.3:801/eportal/?c=Portal&a=login&callback=dr1003&login_method=1
 *       &user_account=<裸学号>&user_password=<明文>&wlan_user_ip=<本机IP>&wlan_user_ipv6=
 *       &wlan_user_mac=000000000000&wlan_ac_ip=&wlan_ac_name=&jsVersion=3.3.2&v=<随机数>
 *   Referer: http://172.16.253.3/   （注意是站点根路径，不是 a79.htm）
 *
 * 注意几处经过实测确认的细节：
 *   - user_account 是**裸账号**，不加 ",0," 前缀也不加 "@xyw" 后缀
 *   - wlan_user_mac 官方 Web 端固定发全 0，服务器不校验该字段
 *   - wlan_ac_ip / wlan_ac_name 官方发空
 *   - 登录前需先访问根路径拿到 PHPSESSID，否则可能被拒
 *   - ret_code=2 且 msg 为空表示「该终端 IP 已在线」，应按成功处理
 */
class DrcomClient(
    private val cfg: DrcomConfig,
    private val detectedIp: String?,
) {

    private val host: String = cfg.serverHost.trim()
    private val port: Int = cfg.portalPort
    private val base: String = "http://$host:$port/eportal/"

    private val cookies = LinkedHashMap<String, String>()

    /** 最近一次内核 chkstatus 返回的权威字段（ss4/ss5/uid/vlanid 等） */
    var kernelInfo: Map<String, String> = emptyMap()
        private set

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    /** 内核状态查询专用：短超时，避免离开校园网时干等 */
    private val fastHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    /** 本机在校园网内的 IP；探测不到时退回用户手工填写的值 */
    fun resolveIp(): String {
        if (!detectedIp.isNullOrBlank()) return detectedIp
        return cfg.userIpOverride.trim()
    }

    /** TCP 握手判断能否连到认证服务器 */
    fun reachable(timeoutMs: Int = 2500): Boolean = NetInfo.reachable(host, port, timeoutMs)

    // ------------------------------------------------------------ 内核在线状态

    /**
     * 向校园网内核查询在线状态 —— 官方 Portal 页面自己就用这个接口。
     *
     * 来自 a41.js 的 checkStatus()：
     *   GET http://{host}/drcom/chkstatus?callback=dr{...}&v={...}
     * 注意它在 **80 端口的 /drcom/** 下，不是 801 的 eportal。
     *
     * 返回 true/false 表示内核给出的权威结论；
     * 返回 null 表示内核不可用（离开了校园网、或内核版本不支持），
     * 调用方应退回系统联网状态判断，以免误判掉线。
     */
    fun kernelOnline(): Boolean? {
        val url = "http://$host/drcom/chkstatus?callback=$CALLBACK_KERNEL&v=${randomV()}"
        val body = request(fastHttp, url, referer = null) ?: return null
        val json = extractJson(body) ?: return null
        if (!json.has("result")) return null

        val info = linkedMapOf<String, String>()
        for (key in KERNEL_INFO_KEYS) {
            if (json.has(key) && !json.isNull(key)) info[key] = json.optString(key, "")
        }
        kernelInfo = info
        return json.optString("result") == "1"
    }

    // ------------------------------------------------------------------ 登录

    fun login(): LoginResult {
        val ip = resolveIp()
        if (ip.isBlank()) {
            return LoginResult(false, "未能获取本机 IP，请在「设置」里手动填写后重试")
        }

        warmUp(ip)

        val params = linkedMapOf(
            "c" to "Portal",
            "a" to "login",
            "callback" to CALLBACK_LOGIN,
            "login_method" to "1",
            "user_account" to cfg.username.trim(),
            "user_password" to cfg.password,
            "wlan_user_ip" to ip,
            "wlan_user_ipv6" to "",
            "wlan_user_mac" to MAC_PLACEHOLDER,
            "wlan_ac_ip" to cfg.acIp,
            "wlan_ac_name" to cfg.acName,
            "jsVersion" to JS_VERSION,
            "v" to randomV(),
        )

        val body = get(buildUrl(params), referer = "http://$host/")
            ?: return LoginResult(false, "连不上认证服务器（$host:$port）", serverReached = false)

        return parseLogin(body)
    }

    // ------------------------------------------------------------------ 注销

    /**
     * 注销当前会话。
     *
     * 官方 logout_portal 的账号密码是占位值（drcom / 123）——注销**不校验凭证**，
     * 服务端是按 IP（+MAC）定位会话的，所以调用它会直接让本机下线。
     */
    fun logout(): LoginResult {
        val ip = resolveIp()
        warmUp(ip)

        val params = linkedMapOf(
            "c" to "Portal",
            "a" to "logout",
            "callback" to CALLBACK_LOGOUT,
            "login_method" to "1",
            "user_account" to LOGOUT_ACCOUNT,
            "user_password" to LOGOUT_PASSWORD,
            "ac_logout" to "0",
            "register_mode" to "1",
            "wlan_user_ip" to ip,
            "wlan_user_ipv6" to "",
            "wlan_vlan_id" to cfg.vlanId,
            "wlan_user_mac" to MAC_PLACEHOLDER,
            "wlan_ac_ip" to cfg.acIp,
            "wlan_ac_name" to cfg.acName,
            "jsVersion" to JS_VERSION,
            "v" to randomV(),
        )

        val body = get(buildUrl(params), referer = "http://$host/")
            ?: return LoginResult(false, "连不上认证服务器（$host:$port）", serverReached = false)

        val json = extractJson(body)
            ?: return LoginResult(false, "返回内容无法解析：${body.take(160)}")

        val result = json.optString("result", "")
        val msg = decodeMsg(json.optString("msg", ""))
        val ok = result == "1" || result.equals("ok", ignoreCase = true)
        return LoginResult(ok, msg.ifBlank { if (ok) "已注销" else "注销失败" }, json.optIntOrNull("ret_code"))
    }

    // ------------------------------------------------------------ 会话与请求

    /** 复刻浏览器：先访问站点根路径拿到 Cookie，再访问认证页 */
    private fun warmUp(ip: String) {
        runCatching { get("http://$host/", referer = null) }
        val acName = cfg.acName
        val acIp = cfg.acIp
        runCatching {
            get(
                "http://$host/a79.htm?wlanuserip=${enc(ip)}&wlanacname=${enc(acName)}&wlanacip=${enc(acIp)}",
                referer = "http://$host/",
            )
        }
    }

    private fun get(url: String, referer: String?): String? = request(http, url, referer)

    private fun request(client: OkHttpClient, url: String, referer: String?): String? {
        return try {
            val builder = Request.Builder()
                .url(url)
                .header("Accept", "*/*")
                .header("Accept-Encoding", "gzip, deflate")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Connection", "keep-alive")
                .header("User-Agent", USER_AGENT)
            if (referer != null) builder.header("Referer", referer)
            if (cookies.isNotEmpty()) builder.header("Cookie", cookieHeader())

            client.newCall(builder.build()).execute().use { response ->
                captureCookies(response)
                response.body?.string()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun captureCookies(response: Response) {
        for (header in response.headers("Set-Cookie")) {
            val pair = header.substringBefore(';')
            val idx = pair.indexOf('=')
            if (idx > 0) {
                cookies[pair.substring(0, idx).trim()] = pair.substring(idx + 1).trim()
            }
        }
    }

    private fun cookieHeader(): String =
        cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

    // ------------------------------------------------------------ 响应解析

    private fun parseLogin(body: String): LoginResult {
        val json = extractJson(body)
            ?: return LoginResult(false, "返回内容无法解析：${body.take(160)}")

        val result = json.optString("result", "")
        val msg = decodeMsg(json.optString("msg", ""))
        val ret = json.optIntOrNull("ret_code")

        // 认证成功
        if (result == "1") {
            return LoginResult(true, msg.ifBlank { "认证成功" }, ret)
        }

        // 关键：这台网关上 ret_code=2 + 空 msg 表示「该终端 IP 已经在线」，
        // 属于「无需重复登录」，必须按成功处理，否则会误报成密码错误。
        if (ret == 2 && msg.isBlank()) {
            return LoginResult(true, "该终端 IP 已在线（无需重复登录）", ret)
        }

        val reason = if (msg.isNotBlank()) msg else "认证被拒绝"
        val hint = RET_CODE_HINT[ret]?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
        val codeText = ret?.let { "（ret_code=$it）" } ?: ""
        return LoginResult(false, "$reason$codeText$hint", ret)
    }

    private fun extractJson(body: String): JSONObject? {
        val text = body.trim().trimEnd(';')
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull()
    }

    /** 服务器有时把出错原因做 base64，这里还原成人话 */
    private fun decodeMsg(raw: String): String {
        if (raw.isBlank()) return ""
        val looksBase64 = raw.length >= 8 &&
            raw.length % 4 == 0 &&
            BASE64_LIKE.matches(raw)
        if (!looksBase64) return raw
        return runCatching {
            val decoded = String(Base64.decode(raw, Base64.DEFAULT), Charsets.UTF_8)
            if (decoded.isNotBlank() && decoded.none { it.code < 32 && it != '\n' && it != '\t' }) {
                decoded
            } else {
                raw
            }
        }.getOrDefault(raw)
    }

    // ------------------------------------------------------------------ 工具

    private fun buildUrl(params: Map<String, String>): String =
        base + "?" + params.entries.joinToString("&") { "${it.key}=${enc(it.value)}" }

    private fun enc(value: String): String =
        URLEncoder.encode(value, "UTF-8")

    private fun randomV(): String = Random.nextInt(1000, 99999).toString()

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key, -1) else null

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"

        private const val JS_VERSION = "3.3.2"
        private const val MAC_PLACEHOLDER = "000000000000"
        private const val CALLBACK_LOGIN = "dr1003"
        private const val CALLBACK_LOGOUT = "dr1004"
        /** 内核状态接口的 jsonp 回调名，服务端只是原样包一层，名字随意 */
        private const val CALLBACK_KERNEL = "dr1002"

        /** 内核 chkstatus 里值得留存的字段 */
        private val KERNEL_INFO_KEYS =
            listOf("ss4", "ss5", "v46ip", "v4ip", "uid", "vlanid")

        /** 官方注销接口用的就是占位值，不校验凭证 */
        private const val LOGOUT_ACCOUNT = "drcom"
        private const val LOGOUT_PASSWORD = "123"

        private val BASE64_LIKE = Regex("^[A-Za-z0-9+/]+={0,2}$")

        private val RET_CODE_HINT = mapOf(
            1 to "服务器拒绝认证，检查账号/密码是否正确",
            2 to "该终端 IP 已在线",
            3 to "认证失败，请稍后重试",
            4 to "账号被占用",
            5 to "账号已停机",
            6 to "非本机绑定账号",
            7 to "密码错误",
        )
    }
}
