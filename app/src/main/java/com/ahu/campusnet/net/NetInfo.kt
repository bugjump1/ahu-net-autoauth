package com.ahu.campusnet.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/**
 * 本机网络信息探测。全部为阻塞调用，务必在 IO 线程执行。
 */
object NetInfo {

    /**
     * 取本机在校园网内的 IPv4 地址。
     *
     * 优先用 [ConnectivityManager.getLinkProperties]（系统权威数据），
     * 失败时退回枚举网卡。
     */
    fun localIpv4(context: Context): String? {
        runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val network = cm?.activeNetwork
            val props = network?.let { cm.getLinkProperties(it) }
            props?.linkAddresses?.firstOrNull {
                it.address is Inet4Address &&
                    !it.address.isLoopbackAddress &&
                    !it.address.isLinkLocalAddress
            }?.let { return it.address.hostAddress }
        }

        return runCatching {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                val name = nif.name ?: ""
                if (name.startsWith("rmnet") || name.startsWith("p2p")) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address &&
                        !addr.isLoopbackAddress &&
                        !addr.isLinkLocalAddress
                    ) {
                        return addr.hostAddress
                    }
                }
            }
            null
        }.getOrNull()
    }

    /** 当前是否 Wi-Fi 联网 */
    fun isWifi(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /** 当前是否移动数据网络（流量）。校园网认证只在 Wi-Fi 下有意义。 */
    fun isMobile(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    /**
     * 当前网络是否真的能上外网。
     *
     * 校园网未认证时，系统会把该网络标记为「需要登录」，NET_CAPABILITY_VALIDATED 为 false，
     * 这正是我们判断「要不要去登录」的依据。
     */
    fun isInternetOk(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * 能否连到认证服务器。
     *
     * 用一次 TCP 握手判断：连得上说明身处校园网；Wi-Fi 关闭或连了家里/热点的网络时
     * 路由不到该内网地址，握手失败 —— 据此区分「未连校园网」和「在校园网但未认证」。
     */
    fun reachable(host: String, port: Int, timeoutMs: Int = 2500): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
