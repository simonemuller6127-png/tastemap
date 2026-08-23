# AGENTS.md — 味觉地图（TasteMap）

本文件是给 AI 开发 agent 的常驻约束，每次会话自动生效。改动本文件需在 SPEC 登记理由。

## 常读文档（仓库外，本地工作区）
- PRD：`C:\Users\86137\.zcode\workspace\default\味觉地图\03-PRD-v1.0.md`（功能编号 F01-F24 与验收标准，实现前后各读一遍对应章节）
- SPEC：`C:\Users\86137\.zcode\workspace\default\味觉地图\04-SPEC-v0.1.md`（技术决策 D1-D14；每个里程碑结束更新其"现状"章节）

## 构建与运行（本机无 Android Studio，Windows cmd）
```
call env.bat                       # 设置 JAVA_HOME/ANDROID_HOME/GRADLE_USER_HOME/PATH（env.bat 不入库）
gradlew.bat test                   # 单元测试
gradlew.bat :app:assembleDebug     # 构建调试包
adb install -r D:\smellmap-build\app\outputs\apk\debug\app-debug.apk
adb logcat | findstr /i smellmap com.amap   # 看运行日志
```
工具链位置（全在 D 盘，SPEC D10）：JDK17=`D:\jdk-17`，Git=`D:\PortableGit`，Gradle=`D:\gradle`（项目内优先用 wrapper），SDK=`D:\android-sdk`，Gradle 缓存=`D:\gradle-home`。CI：GitHub Actions 见 `.github/workflows/android.yml`。

**构建产物路径**：仓库路径含中文，`android.overridePathCheck` + 根 build.gradle.kts 里把 buildDirectory 重定向到了 `D:\smellmap-build\`——APK、测试报告都在那里，不要在仓库目录下找 build/。gradle wrapper 分发地址用腾讯镜像（services.gradle.org 国内不通）。

## 硬约束
1. 架构：单模块 MVVM + Repository，分包 `data/ map/ ui/ share/ deeplink/ sticker/`（SPEC D2）。不引入 multi-module。
2. 网络依赖只有高德 SDK。禁止引入 Retrofit/OkHttp/Firebase 等任何网络或账号相关框架（本地优先原则）。
3. 数据只存本地：Room（`tastemap.db`）+ App 私有目录照片 + DataStore 偏好。无服务器。
4. SDK 版本：minSdk 26 / target 35 / compile 35 / Kotlin 2.0 + Compose。新增依赖必须先在 SPEC ADR 登记。
5. 测试机：华为 P40 Pro（EMUI 10.1，Android 10 / API 29，**无 GMS**）。不得依赖 Google Play Services（Firebase 推送、ML Kit 按需下发模型等一律不可用）。
6. 高德 Key 在 `AndroidManifest.xml` 的 meta-data 占位（`REPLACE_WITH_YOUR_AMAP_KEY`），真实 Key 不入库。
7. 签名证书指纹与备案信息必须一致（报告二风险 1）：换正式签名前不动 release 构建。
8. commit message 引用功能编号（`F01 地图主页…`），里程碑打 tag（`m0`、`m1`…）。

## 口味色值表（全局唯一来源：美食贴纸边框 / 地图样式 / UI 主题同源；M0-M1 过渡期同用于图钉）
| 口味 | 色值 | | 口味 | 色值 |
|------|------|-|------|------|
| 辣 | `#D9482B` | | 酸 | `#C9B458` |
| 甜 | `#E9C46A` | | 清淡 | `#A8B5A2` |
| 咸 | `#4C90A8` | | 酥脆 | `#C77B4F` |
| 鲜甜 | `#6BA292` | | 无记录（中性） | `#4A4238` |

预置口味与色值定义在 `data/repository/PresetTastes.kt`，改色值先改那里再同步本表。

## 包结构（当前，M0）
```
app/src/main/java/com/smellmap/app/
├─ data/db/          Room 实体/DAO/AppDatabase（五表：shops meal_records taste_tags record_tastes wishlist schedules）
├─ data/repository/  Repository 层 + 预置口味
├─ data/backup/      .tastemap 备份导出/导入（zip：manifest.json + data.json + photos/）
├─ map/              高德封装（MarkerFactory：口味着色图钉）
└─ ui/               Compose 界面（MapHomeScreen + 主题；主题 M0 为占位，M1 换手绘设计系统 D11）
```

## 当前状态
M0 进行中（F01/F02/F06 基础）：地图主页、长按新建记录、Room 数据层、备份恢复。下一里程碑 M1 见 PRD §8。
