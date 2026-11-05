plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.kongye.reader"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kongye.reader"
        minSdk = 26
        targetSdk = 36
        versionCode = 20
        versionName = "2.0.4"
        testInstrumentationRunner = "com.kongye.reader.VisualRegression"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 本地使用：沿用 debug 签名，生成的 release APK 可直接安装
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    sourceSets {
        // 小马宝莉是孩之宝的形象，图不进仓库：本机运行 tools/import_shop_assets.py 时生成在这里
        getByName("main").res.srcDir("src/main/res-private")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
