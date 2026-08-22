// 味觉地图（TasteMap）根构建脚本。单模块（SPEC D2），版本统一由 gradle/libs.versions.toml 管理。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// 仓库路径含中文（D:\技术师范大学\...），已用 android.overridePathCheck 放行编译/aapt2；
// 但单测类加载等仍会被 CJK 路径绊倒，构建产物统一输出到 ASCII 路径（源码不动）。
allprojects {
    layout.buildDirectory.set(file("D:/smellmap-build/${project.name}"))
}
