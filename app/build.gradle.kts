plugins {
    alias(libs.plugins.android.application)
    // ✅ KHÔNG ghi version ở đây nữa vì đã khai ở settings.gradle.kts
    id("com.chaquo.python")
}

android {
    namespace = "com.example.into_demo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.into_demo"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ✅ Bắt buộc cho Chaquopy
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Thêm block Chaquopy (cài pip packages cho client.py)
chaquopy {
    defaultConfig {
        version = "3.8"  // Python 3.12 (mới nhất, hỗ trợ tốt hơn 3.8)
        pip {
            install("requests")  // Cho HTTP GET/POST trong client.py
            install("cryptography")  // Cho Fernet trong encryption.py
            install("inputimeout")  // Nếu cần cho server.py test
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("org.json:json:20240303")  // JSON payload

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
