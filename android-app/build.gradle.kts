// Both plugins must share the root classloader, otherwise Kotlin cannot see AGP classes.
buildscript {
    repositories {
        if (findProperty("coreOnly") != "true") google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
        // `-PcoreOnly=true` skips AGP so :core tests run without the Android SDK.
        if (findProperty("coreOnly") != "true") classpath("com.android.tools.build:gradle:8.7.3")
    }
}
