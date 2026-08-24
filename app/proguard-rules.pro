# 味觉地图混淆规则（R4：release 已开启 minify+shrink）
# 高德 SDK
-keep class com.amap.api.** { *; }
-keep class com.autonavi.** { *; }
# kotlinx.serialization（卡片/备份 JSON 模型不能被裁）
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.tastemap.app.**$$serializer { *; }
-keepclassmembers class com.tastemap.app.** { *** Companion; }
-keepclasseswithmembers class com.tastemap.app.** { kotlinx.serialization.KSerializer serializer(...); }
# Room 实体与数据库
-keep @androidx.room.Entity class *
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
# ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
