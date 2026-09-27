plugins {
    id("com.android.application") version "9.4.1" apply false
    // AGP 9 已内置 Kotlin 支持（KGP 2.2.10），不能再显式应用 org.jetbrains.kotlin.android；
    // Compose 编译器插件版本必须与内置的 KGP 版本一致
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
