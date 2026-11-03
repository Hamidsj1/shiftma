import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// امضای انتشار: فایل keystore.properties (کنار settings.gradle.kts) با کلیدهای storeFile, storePassword, keyAlias, keyPassword بسازید.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "ir.shiftapp"
    compileSdk = 34
    defaultConfig {
        applicationId = "ir.shiftapp.shiftma"   // قبل از اولین انتشار به شناسهٔ دلخواه خودتان تغییر دهید؛ بعد از انتشار قابل تغییر نیست
        minSdk = 26            // java.time بدون desugaring
        targetSdk = 34
        versionCode = 11           // با هر انتشار جدید یکی زیاد شود
        versionName = "1.0.10"
    }
    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true         // R8: کوچک‌سازی کد کتابخانه‌ها (کد خود برنامه در proguard-rules.pro نگه داشته می‌شود)
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystoreProps.isNotEmpty()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.10.0")      // حذف هدر X-Requested-With از مرورگر داخلی (بعضی سایت‌ها با آن صفحه را خالی می‌فرستند)
    implementation("androidx.browser:browser:1.8.0")       // Chrome Custom Tab برای سایت‌هایی که WebView را رد می‌کنند (رسا)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("com.github.cafebazaar.Poolakey:poolakey:2.3.0")      // پرداخت درون‌برنامه‌ای کافه‌بازار (مجوز و queries را خودش به مانیفست اضافه می‌کند)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
