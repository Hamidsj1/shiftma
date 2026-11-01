pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.devtools.ksp") {
                useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
        // فقط برای کتابخانهٔ پرداخت کافه‌بازار (Poolakey)؛ هیچ چیز دیگری از JitPack گرفته نمی‌شود
        maven("https://jitpack.io") { content { includeGroup("com.github.cafebazaar.Poolakey") } }
    }
}

rootProject.name = "ShiftMa"
include(":app")
