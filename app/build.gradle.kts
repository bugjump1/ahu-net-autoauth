# 安大校园网助手 - 应用模块构建配置

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ahu.campusnet"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ahu.campusnet"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
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
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)

    // ===== 液态玻璃底部导航栏（Kyant0 / Backdrop）=====
    implementation(libs.backdrop)
    // Backdrop 的 AGSL 运行时引用了 org.intellij.lang.annotations.Language，显式补上
    implementation("org.jetbrains:annotations:26.1.0")

    // ===== 网络 =====
    implementation(libs.okhttp)

    // ===== 调试 =====
    debugImplementation(libs.androidx.compose.ui.tooling)
}
