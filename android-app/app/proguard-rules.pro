# MapLibre uses JNI and reflection for its native map renderer.
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
-dontwarn com.google.gson.**
-dontwarn okhttp3.**
-dontwarn okio.**
