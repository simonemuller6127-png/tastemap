// 国内开发机走阿里云镜像加速；CI（GitHub runner，境外）跳过镜像直连官方源，
// 避免镜像国际链路抖动把插件解析拖挂（CI 曾因此 KSP 解析失败）。
val onCi = System.getenv("CI") == "true"

pluginManagement {
    repositories {
        google()
        if (!onCi) maven { url = uri("https://maven.aliyun.com/repository/google") }
        mavenCentral()
        if (!onCi) maven { url = uri("https://maven.aliyun.com/repository/public") }
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
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
