plugins {
    id("com.android.application")
}

android {
    namespace = "com.livephoto.viewer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.livephoto.viewer"
        minSdk = 29
        targetSdk = 34
        versionCode = 3
        versionName = "1.2.1"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// 纯 Android Framework，无第三方依赖
