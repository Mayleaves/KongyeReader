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
        versionCode = 30
        versionName = "2.0.14"
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
        // 不进仓库、也不进公开发行包的东西：小马宝莉是孩之宝的形象（本机运行 tools/import_shop_assets.py 时生成在 res-private），
        // 两款楷体在 assets-private/fonts（仓耳今楷不许再分发，霞鹜文楷裁剪过不能再叫这个名字，见 README）。
        // 公开发行包（Release 里附的 APK）加 -Ppublic 构建，不带这两样
        if (!project.hasProperty("public")) {
            getByName("main").res.srcDir("src/main/res-private")
            getByName("main").assets.srcDir("src/main/assets-private")
        }
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
