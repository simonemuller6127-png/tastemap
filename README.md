# 味觉地图（TasteMap）

一张用口味上色的个人美食地图——记录每一顿值得记住的饭，在想吃的时刻立刻找到它。

- 本地优先：无账号、无服务器、离线可用，数据全在手机里
- 口味是第一维度：辣、甜、咸、鲜甜……口味决定地图上图钉的颜色
- 安卓原生：Kotlin + Jetpack Compose + Room + 高德地图 SDK

## 开发

- 需求与技术决策文档（PRD/SPEC）存于本地工作区 `味觉地图` 目录，以仓库外文档为准
- 构建约束与流程见 [AGENTS.md](AGENTS.md)（本机无 Android Studio，命令行 `gradlew.bat` 构建）
- 高德 Key：在 [lbs.amap.com](https://console.amap.com) 创建应用（包名 `com.smellmap.app` + 调试 SHA1），替换 `AndroidManifest.xml` 中的 `REPLACE_WITH_YOUR_AMAP_KEY` 后方能显示地图

## 状态

M0（项目搭建 / 高德接入 / 数据层 / 备份恢复）进行中。
