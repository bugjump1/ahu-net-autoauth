# 安大校园网助手（Android）

安徽大学 Dr.COM（哆点）Portal 认证的自动登录小工具。**打开 App 就自动认证一次，没有后台常驻、没有开机自启。**

UI 使用 [Miuix](https://github.com/compose-miuix-ui/miuix)（小米 HyperOS / MIUI 风格 Compose 组件库），
底部导航栏使用 [Kyant0 的 Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) 实现液态玻璃效果。

**所有编译都在 GitHub Actions 完成，本机不需要安装 JDK / Android SDK / Android Studio。**

---

## 一、功能

| 功能 | 说明 |
|---|---|
| 打开即认证 | App 启动后自动检测网络环境，未在线就用保存的凭证认证一次 |
| 账号密码本地加密 | Android Keystore 的 AES-256-GCM 加密后存储，密钥由系统安全模块保管 |
| 网络环境检测 | 依次判断：是否 Wi-Fi（排除移动数据）→ 是否在校园网 → 是否已联网 |
| 手动操作 | 「立即认证」「检测状态」「注销并断网」 |
| 运行日志 | 记录每次检测/认证结果，便于排查 |
| 液态玻璃底部导航 | 首页 / 日志 / 设置三页 |

**明确不做**（按需求裁剪）：后台守护、掉线轮询、开机自启、常驻通知。

### 打开 App 时的判定顺序

```
① 是 Wi-Fi 吗？           不是 → 显示「移动网络」或「未连 Wi-Fi」，直接结束
② 连得到认证服务器吗？    连不上 → 显示「未连校园网」，不做无效认证
③ 已经在线了吗？          内核 chkstatus 优先，系统 NET_CAPABILITY_VALIDATED 兜底
                          在线 → 显示「已在线」，跳过认证
④ 未在线且已配置凭证       → 用保存的账号密码认证一次
```

第 ③ 步的内核接口是官方 Portal 页面自己用的判据（`GET :80/drcom/chkstatus`），
比外网探针权威，不受「探针被校园网拦截」影响。

---

## 二、怎么拿到 APK

### 1. 建仓库并推代码

在 GitHub 上新建一个空仓库，然后把本目录内容推上去：

```bash
cd android
git init
git add .
git commit -m "feat: 安大校园网助手 Android 版"
git branch -M main
git remote add origin git@github.com:<你的用户名>/<仓库名>.git
git push -u origin main
```

> 注意：推的是 `android/` 目录的内容，让 `.github/`、`gradlew`、`app/` 都处在你仓库的根目录。

### 2. 等 Actions 跑完

推送后到仓库的 **Actions** 页面，`Build APK` 会自动开始（首次约 5–10 分钟，主要花在下 Gradle 和依赖）。

跑完后在运行页面底部 **Artifacts** 下载 `campusnet-apk`，里面有两个 APK：

- `app-debug.apk` —— 调试版，**推荐日常用**
- `app-release.apk` —— 发布版（用 debug 签名，可直接安装）

### 3. 走 Release 直链（可选）

```bash
git tag v1.0.0
git push origin v1.0.0
```

Actions 会自动把 APK 附到 Release 上，手机上点链接就能装。

---

## 三、首次使用

1. 手机安装 APK（需允许「安装未知来源应用」）。
2. 打开 App，进「设置」页填 **学号** 和 **校园网密码**，点「保存设置」。
   - 学号填**裸账号**，例如 `A126300054`，**不要**加 `@xyw` 之类的后缀。
   - 密码是校园网密码，不一定等于教务系统密码。
3. 回「首页」，会自动认证一次（也可以点「立即认证」）。

之后每次打开 App 都会自动跑一遍上面的判定并把状态显示出来。

> 权限说明：只需要网络相关权限。**不申请通知、定位、开机自启**。

---

## 四、状态含义与排错

| 状态 | 含义 | 处理 |
|---|---|---|
| ● 已在线 | 网络已可用 | 无需操作 |
| ● 未认证 | 在校园网内，但还没认证 | 点「立即认证」 |
| ● 移动网络 | 当前走的是流量 | 切到校园 Wi-Fi |
| ● 未连 Wi-Fi | 没连任何 Wi-Fi | 连上校园 Wi-Fi |
| ● 未连校园网 | 连着 Wi-Fi 但连不到认证服务器 | 确认连的是校园网而不是热点/家里路由 |
| ● 异常 | 认证被拒绝或出错 | 看「日志」页里的具体原因 |

认证失败时会显示服务器返回的原因：

| 返回内容 | 含义 | 处理 |
|---|---|---|
| `ret_code=2` 且提示「该终端 IP 已在线」 | **属于成功**，服务器说「你已经登过」 | 无需处理 |
| 带「认证被拒绝」/ `ret_code=1` | 账号或密码不对 | 核对密码，或确认用的是校园网专用密码 |
| 「尚未配置账号和密码」 | 设置没保存 | 回设置页重新保存 |

日志页会打印一行 `内核：IP=… 账号=… vlan=…`，这是内核接口回传的权威数据 ——
有这行说明该接口可用；没有则说明已回退到系统联网状态判断（功能不受影响）。

---

## 五、项目结构

```
android/
├── .github/workflows/build.yml          # GitHub Actions 构建流水线
├── gradle/libs.versions.toml            # 依赖版本集中管理
├── app/build.gradle.kts
└── app/src/main/
    ├── AndroidManifest.xml              # 只有一个 Activity，无 service / receiver
    ├── res/                             # 图标、主题、字符串
    └── java/com/ahu/campusnet/
        ├── CampusApp.kt                 # Application：初始化配置存储
        ├── auth/
        │   └── AuthController.kt        # ★ 认证流程（网络检测 + 登录），无后台常驻
        ├── data/
        │   ├── SecureStore.kt           # Android Keystore AES-256-GCM 加解密
        │   └── ConfigRepository.kt      # 配置模型 + 读写（StateFlow）
        ├── net/
        │   ├── NetInfo.kt               # 是否 Wi-Fi / 是否移动网络 / 本机 IP / TCP 探测
        │   └── DrcomClient.kt           # Dr.COM 协议（登录/注销/内核状态/解析）
        └── ui/
            ├── Theme.kt                 # Miuix 主题
            ├── GlassBottomBar.kt        # 液态玻璃底部导航（Backdrop）
            ├── MainActivity.kt          # 入口 + 页面容器 + 背景层注册
            ├── HomeScreen.kt            # 状态 / 操作
            ├── LogsScreen.kt            # 运行日志
            └── SettingsScreen.kt        # 账号、高级、外观
```

### 关于 Backdrop（液态玻璃）的维护提示

Backdrop 目前是 `2.0.1`，官方标注 API 可能变动。**所有 backdrop 调用只集中在
`ui/GlassBottomBar.kt` 和 `ui/MainActivity.kt`**，日后升级报错只需改这两个文件。

---

## 六、协议要点（已实测确认）

登录请求：

```
GET http://172.16.253.3:801/eportal/?c=Portal&a=login&callback=dr1003&login_method=1
    &user_account=<裸学号>&user_password=<明文>&wlan_user_ip=<本机IP>&wlan_user_ipv6=
    &wlan_user_mac=000000000000&wlan_ac_ip=&wlan_ac_name=&jsVersion=3.3.2&v=<随机数>
Referer: http://172.16.253.3/
Cookie:  PHPSESSID=...（登录前先 GET 一次站点根路径拿）
```

内核在线状态：

```
GET http://172.16.253.3/drcom/chkstatus?callback=dr1002&v=<随机数>
    → {"result":1|0, "ss4":MAC, "ss5":IP, "uid":账号, "vlanid":...}
```

容易踩的点：

- `user_account` 是**裸学号**，不加 `,0,` 前缀也不加 `@xyw` 后缀
- `wlan_user_mac` 官方 Web 端固定发 `000000000000`
- `wlan_ac_ip` / `wlan_ac_name` 官方发空
- 登录前必须先访问站点根路径建立会话（`PHPSESSID`），否则可能被拒
- `ret_code=2` + 空 `msg` = 该终端已在线，**按成功处理**
- 注销接口的账号密码是占位值 `drcom` / `123`，**不校验凭证**，是按 IP 踢会话的
- 内核接口在 **80 端口的 `/drcom/`**，不是 801 的 eportal

---

## 七、本地构建（可选，不推荐）

CI 已经够用。真要本地跑，需要 JDK 21 + Android SDK（platform 36），然后：

```bash
./gradlew :app:assembleDebug
```

国内下载依赖慢的话，把 `gradle/wrapper/gradle-wrapper.properties` 里的
`distributionUrl` 换成腾讯镜像：

```
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-9.5.1-bin.zip
```
