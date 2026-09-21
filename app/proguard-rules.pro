# 若开启 R8（isMinifyEnabled = true），以下规则用于保留 Miuix / Backdrop / Compose 所需符号。
# 目前 release 默认未开启压缩，文件先备着。

# Compose / Miuix / Backdrop 都基于 Compose Runtime，官方默认规则已足够，这里补充保守项
-dontwarn org.jetbrains.annotations.**
-keep class org.intellij.lang.annotations.** { *; }

# 序列化 / JSON
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses

# 组件在 Manifest 中声明，R8 会自动保留，此处显式兜底
-keep class com.ahu.campusnet.service.AutoLoginService { *; }
-keep class com.ahu.campusnet.service.BootReceiver { *; }
