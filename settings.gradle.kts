// 镜像放前面（国内同学本地构建快），官方源留在后面兜底：
// 阿里云偶发 502，而 Gradle 9 遇到传输错误会把该仓库整场构建禁用，没有备用源就直接红
// （run 36871570310 attempt 1 就是这么挂的，重跑才过）
pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        google()
        mavenCentral()
    }
}

rootProject.name = "HBUschedule"
include(":app")
