// settings.gradle.kts

// プロジェクトのルート名を定義します。
// これはAndroid Studioのプロジェクトウィンドウに表示される名前であり、Gradleのビルドシステムでプロジェクトを識別するために使用されます。
rootProject.name = "StepMemory" // プロジェクト作成時に入力した名前と一致させます

// プロジェクトに含めるモジュールを指定します。
// ここでは、アプリケーションのメインモジュールである "app" を含めることを指定しています。
// 複数のモジュールを持つプロジェクトの場合（例: ライブラリモジュールなど）、
// ここにそれらのモジュール名をカンマ区切りで追加します。
include(":app")

// 以下は、依存関係解決のためのリポジトリ設定です。
// 通常、新しいAndroid Studioのプロジェクトでは、pluginManagementとdependencyResolutionManagementブロックで
// 中央リポジトリが設定されており、これにより必要なGradleプラグインやライブラリが取得されます。
// これらのブロックは、プロジェクト全体の依存関係解決ポリシーを定義します。

pluginManagement {
    // Gradleプラグインを検索するリポジトリを指定します。
    // GoogleのMavenリポジトリとGradle Plugin Portalは、Android開発でよく使用されるプラグインのソースです。
    repositories {
        google() // Googleが提供するプラグイン（例: Android Gradle Plugin）
        mavenCentral() // 一般的なJava/Kotlinのプラグイン
        gradlePluginPortal() // Gradleの公式プラグインポータル
    }
}

dependencyResolutionManagement {
    // 依存関係（ライブラリ）を検索するリポジトリを指定します。
    // この設定は、各モジュールの build.gradle.kts で定義される依存関係に適用されます。
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS) // プロジェクトレベルのリポジトリ設定ミスを検出するためのモード
    repositories {
        google() // Googleが提供するライブラリ（例: AndroidX, Firebase, Play Servicesなど）
        mavenCentral() // 一般的なJava/Kotlinのライブラリ
    }
}