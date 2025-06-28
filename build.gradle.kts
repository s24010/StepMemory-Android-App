// build.gradle.kts (プロジェクトレベル)

buildscript {
    val kotlin_version = "1.9.0" // お使いのAndroid Studioの推奨バージョンに合わせる
    val agp_version = "8.4.0"   // Android Gradle Pluginのバージョン

    repositories {
        google()       // Googleのライブラリやプラグイン
        mavenCentral() // 一般的なJava/Kotlinライブラリやプラグイン
    }
    dependencies {
        // AndroidアプリケーションをビルドするためのGradleプラグイン
        classpath("com.android.tools.build:gradle:$agp_version")
        // KotlinコードをコンパイルするためのGradleプラグイン
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
        // Google Services Gradle Pluginの追加 (Firebase用)
        classpath("com.google.gms:google-services:4.4.1") // 最新バージョンはFirebaseサイトで確認してください
    }
}

// ビルド時に生成されるクリーンタスクを定義します。
tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}