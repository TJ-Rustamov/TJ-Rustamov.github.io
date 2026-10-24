# osmdroid uses reflection-free APIs; keep its tile source classes just in case.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
