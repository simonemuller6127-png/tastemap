// 国内开发机走阿里云镜像加速；CI（GitHub runner，境外）跳过镜像直连官方源，
// 避免镜像国际链路抖动把插件解析拖挂（CI 曾因此 KSP 解析失败）。
// 注意：pluginManagement 块在脚本早期求值，读不到顶层 val，变量需在块内各自声明。
pluginManagement {
    val onCi = System.getenv("CI") == "true"
    repositories {
        google()
        if (!onCi) maven { url = uri("https://maven.aliyun.com/repository/google") }
        mavenCentral()
        if (!onCi) maven { url = uri("https://maven.aliyun.com/repository/public") }
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    val onCi = System.getenv("CI") == "true"
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        if (!onCi) maven { url = uri("https://maven.aliyun.com/repository/google") }
        mavenCentral()
        if (!onCi) maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

rootProject.name = "smellmap"
include(":app")
