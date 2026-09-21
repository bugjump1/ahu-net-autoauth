package com.ahu.campusnet

import android.app.Application
import com.ahu.campusnet.data.ConfigRepository

/**
 * 应用入口：只负责初始化配置存储。
 *
 * 这里**不再创建通知渠道**、也不注册任何后台组件 —— 安卓版没有守护进程，
 * 认证逻辑在打开 App 时跑一次（见 `auth/AuthController`）。
 */
class CampusApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ConfigRepository.init(this)
    }
}
