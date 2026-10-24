plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    // Optional: -PrealDb=/path/to/X-COP_7500s_Baza_GPS.db runs the real-file test.
    providers.gradleProperty("realDb").orNull?.let { systemProperty("realDb", it) }
}
