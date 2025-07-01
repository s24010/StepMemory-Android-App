// app/build.gradle.kts (モジュールレベル)

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.kic.stepmemory" // あなたのapplicationIdと同じ値を設定

    compileSdk = 35
    defaultConfig {
        applicationId = "com.kic.stepmemory"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isDebuggable = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    implementation(platform("com.google.firebase:firebase-bom:32.8.0"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.code.gson:gson:2.10.1")

    // Google Maps SDK for Android (道の表示に必要)
    implementation("com.google.android.gms:play-services-maps:18.2.0")

    // ★★★ Google Location Services API (FusedLocationProviderClientなどに必要) を追加 ★★★
    implementation("com.google.android.gms:play-services-location:21.0.1") // 最新バージョンはGoogle Developersサイトで確認

    // Coroutines (Kotlinの非同期処理を簡潔に書くため)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // Lifecycle KTX (ViewModel, LiveDataなど、Androidアーキテクチャコンポーネント)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    // Activity KTX (Activityの拡張関数など)
    implementation("androidx.activity:activity-ktx:1.9.0")
    // Fragment KTX (Fragmentの拡張関数など)
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation(libs.androidx.activity)

    // テスト用ライブラリ
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}