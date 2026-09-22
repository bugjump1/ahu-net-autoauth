// 安大校园网助手 - 应用模块构建配置
// 注意：.kts 是 Kotlin 脚本，注释必须用 // 或 /* */，不能用 #（那是 .properties/.toml 的写法）

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// 可由 CI 传入的构建参数（GitHub Actions 的「Run workflow」表单 → 以 -P 传进来）：
//   -PappId=com.bugjump.ahuweb   包名（applicationId，决定 APK 的唯一标识）
//   -PappName=Ahu Plus           应用名称（桌面 / 设置里显示）
//   -PversionCode=1              版本号（整数，每次更新须递增）
//   -PversionName=1.0.0          版本名（展示用字符串）
// 本地构建不传就用下面的默认值。
//
// 注意：namespace 固定为 com.ahu.campusnet —— 那是 Kotlin 源码所在的包名，
//       与 APK 的 applicationId 是两回事，改包名不需要动任何源码。
// ---------------------------------------------------------------------------
val appId = providers.gradleProperty("appId").orNull?.takeIf { it.isNotBlank() }
    ?: "com.bugjump.ahuweb"
val appName = providers.gradleProperty("appName").orNull?.takeIf { it.isNotBlank() }
    ?: "Ahu Plus"
val appVersionCode = providers.gradleProperty("versionCode").orNull?.trim()?.toIntOrNull()
    ?: 7
val appVersionName = providers.gradleProperty("versionName").orNull?.takeIf { it.isNotBlank() }
    ?: "1.4.0"

android {
    namespace = "com.ahu.campusnet"
    // Miuix 0.9.3 / Backdrop 2.0.1 等依赖的 AAR 元数据要求 compileSdk >= 37，
    // 否则 CheckAarMetadata 会直接失败（16 issues were found when checking AAR metadata）。
    compileSdk = 37

    defaultConfig {
        applicationId = appId
        minSdk = 26
        // targetSdk 保持 36：compileSdk 只影响"能调用哪些 API"，
        // targetSdk 才决定启用哪些新系统的运行时行为，两者可以不同。
        // 不跟到 37 是为了不贸然引入尚未验证的系统行为变更。
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        // 应用名从这里注入，因此 res/values/strings.xml 里不再定义 app_name
        resValue("string", "app_name", appName)
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // 首次构建先关闭 R8 压缩，确保一定能跑起来；
            // 稳定后想减肥可把下面两行改成 true（已附 proguard-rules.pro）。
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 用 debug 签名，保证产出的 release APK 能直接安装。
            // 想用自己的签名，把 keystore 配置加进 signingConfigs 并替换这里。
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        // AGP 9.0 起 resValues 默认由 true 改为 false（见 AGP 9.0 release notes 的
        // android.defaults.buildfeatures.resvalues），必须显式开启，
        // 否则 defaultConfig 里的 resValue(...) 会报
        // "contains custom resource values, but the feature is disabled"。
        resValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
        }
    }
}

dependencies {
    // ===== AndroidX 基础 =====
    implementation(libs.androidx.core.ktx)
    // lifecycle-runtime-ktx 同时带来 kotlinx-coroutines-android（Dispatchers.Main / IO）
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // ===== Compose =====
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    // ===== Miuix（MIUI / HyperOS 风格组件）=====
    // 只用到 top.yukonga.miuix.kmp.basic.* 与 .theme.*，
    // 因此只引入 miuix-ui 本体；miuix-preference / miuix-icons 当前未使用，不引入。
    implementation(libs.miuix.ui)

    // ===== 液态玻璃底部导航栏（Kyant0 / Backdrop）=====
    implementation(libs.backdrop)
    // Backdrop 的 AGSL 运行时引用了 org.intellij.lang.annotations.Language，显式补上
    implementation("org.jetbrains:annotations:26.1.0")

    // ===== 网络 =====
    implementation(libs.okhttp)

    // ===== 调试 =====
    debugImplementation(libs.androidx.compose.ui.tooling)
}
