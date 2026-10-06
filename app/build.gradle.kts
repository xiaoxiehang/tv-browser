plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jizai.tvbrowser"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jizai.tvbrowser"
        minSdk = 24
        targetSdk = 34
        versionCode = 7
        versionName = "0.6.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.webkit:webkit:1.10.0")
    // 手机伴侣：WebSocket 服务端
    implementation("org.java-websocket:Java-WebSocket:1.5.6")
    // 二维码生成
    implementation("com.google.zxing:core:3.5.3")
    // 直播播放：ExoPlayer（HLS 必备 media3-hls）
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-hls:1.4.1")
}
