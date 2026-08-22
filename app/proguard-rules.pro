# 味觉地图混淆规则（M0 未启用，M3 上架前启用时补充）
# 高德 SDK
-keep class com.amap.api.** { *; }
-keep class com.autonavi.** { *; }
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
