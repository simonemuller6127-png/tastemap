import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.tastemap.app"
    compileSdk = 35

    // 高德 Key 注入：读 local.properties 的 AMAP_KEY（gitignored），缺省回退占位符，真实 Key 不入库
    val amapKey = runCatching {
        rootProject.file("local.properties").inputStream().use {
            Properties().apply { load(it) }.getProperty("AMAP_KEY")
        }
    }.getOrNull().orEmpty().ifEmpty { "REPLACE_WITH_YOUR_AMAP_KEY" }

    defaultConfig {
        applicationId = "com.tastemap.app"
        minSdk = 26          // 测试机 P40 Pro = API 29；26 覆盖安卓 8+，java.time 原生可用
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-m0"
        manifestPlaceholders["AMAP_KEY"] = amapKey
    }

    buildTypes {
        release {
            isMinifyEnabled = false // M3 上架前再开混淆并补规则
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.withType<Test>().configureEach {
    // 中文 Windows + CJK 工程路径下，测试 JVM 必须显式 UTF-8（配合根目录 buildDirectory 重定向）
    systemProperty("file.encoding", "UTF-8")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.exifinterface) // D5：EXIF 原图判定（纯本地，无网络）
    implementation(libs.zxing.core)             // D6：卡片二维码生成/解析（纯本地，无网络）

    // 唯一允许的网络依赖（AGENTS.md 硬约束 2）。
    // 注意：3dmap 9.x 起已内置定位能力（含 AMapLocationClient），不要再加独立 location SDK，会类冲突
    implementation(libs.amap.map3d)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
