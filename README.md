# 味觉地图（TasteMap）

一张用口味上色的个人美食地图——记录每一顿值得记住的饭，在想吃的时刻立刻找到它。

- 本地优先：无账号、无服务器、离线可用，数据全在手机里
- 口味是第一维度：辣、甜、咸、鲜甜……口味决定地图上图钉的颜色
- 安卓原生：Kotlin + Jetpack Compose + Room + 高德地图 SDK

## 开发

- 需求与技术决策文档（PRD/SPEC）存于本地工作区 `味觉地图` 目录，以仓库外文档为准
- 构建约束与流程见 [AGENTS.md](AGENTS.md)（本机无 Android Studio，命令行 `gradlew.bat` 构建）
- 高德 Key：在 [lbs.amap.com](https://console.amap.com) 创建应用（包名 `com.tastemap.app` + 调试 SHA1），替换 `AndroidManifest.xml` 中的 `REPLACE_WITH_YOUR_AMAP_KEY` 后方能显示地图

## 状态

- M0（项目搭建 / 高德接入 / 数据层 / 备份恢复）：完成，真机验收通过
- R0-R2（手绘设计系统 / 记录流 / 回顾·日程·想吃 / 搜索 / 分享闭环 / 贴纸工坊）：完成
- R3（三轮反馈打磨：贴纸缩放手感 / 纸面色板 / 全局字号 / 转场动效 / F18 纸面感 / POI 就近搜索 / 定位增强）：代码完成，真机回归进行中（清单见 [docs/r3_regression.md](docs/r3_regression.md)）
- F18 手绘纸面底图：v2 样式随包分发（`assets/mapstyle/handdrawn.json`），设置页可开关（默认关）
