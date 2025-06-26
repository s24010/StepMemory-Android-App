package com.kic.stepmemory.data

import com.google.gson.Gson // JSON変換のためにGsonライブラリを使用
import com.google.android.gms.maps.model.LatLng // Google Mapsの緯度経度データ型

/**
 * ステップメモリアプリの記録データモデルを表すデータクラス。
 * Firebase Firestoreに保存されるデータの構造に直接対応します。
 */
data class Record(
    // 各記録の一意なID。FirestoreのドキュメントIDをUUIDとして利用します。
    // クライアント側で生成せず、Firestoreが自動生成するIDを使用するため、
    // ここではFirestoreのIDを保持するためのフィールドとしてString型で定義します。
    // 通常はFirestoreのDocumentSnapshotから取得します。
    var idUUID: String = "",

    // (オプション) ユーザーを識別するためのID。複数ユーザー対応時に必要。
    var userId: String? = null, // NullableなString

    // (オプション) ユーザーが記録に付けられる名前（例：「朝の散歩」、「週末のハイキング」など）。
    var name: String? = null, // NullableなString

    // 記録開始時刻 (Unix Milliseconds)。Kotlin/JavaのSystem.currentTimeMillis()などを使用。
    var startTime: Long = 0L,

    // 記録終了時刻 (Unix Milliseconds)。
    var endTime: Long = 0L,

    // (オプション) 記録時間（ミリ秒）。endTime - startTimeで計算可能ですが、保存すると便利です。
    var durationMs: Long? = null, // NullableなLong

    // 歩いた道の緯度経度データのリスト。List<LatLng>をJSON文字列にシリアライズして保存します。
    // Firestoreに直接List<LatLng>を保存することも可能ですが、ここではJSON文字列を使うという要件に合わせます。
    var pathData: String = "[]", // デフォルトは空のJSON配列として初期化

    // 記録後にユーザーが残すメモ。
    var memo: String? = null, // NullableなString

    // (オプション) 記録がサーバーに作成されたタイムスタンプ。Firestoreが自動で管理することが多いです。
    // クライアント側では主に読み取り用として使います。
    var createdAt: Long? = null, // NullableなLong

    // (オプション) 記録がサーバーで最後に更新されたタイムスタンプ（メモ追加時など）。
    // クライアント側では主に読み取り用として使います。
    var updatedAt: Long? = null // NullableなLong
) {
    /**
     * `pathData` (JSON文字列) を `List<LatLng>` に変換して返します。
     * JSONパースエラーが発生した場合は空のリストを返します。
     */
    fun getPathLatLngList(): List<LatLng> {
        return try {
            // Gsonを使ってJSON文字列をList<LatLng>にデシリアライズ
            // `Array<LatLng>::class.java` を使用して配列にデシリアライズし、その後toList()でリストに変換
            Gson().fromJson(pathData, Array<LatLng>::class.java)?.toList() ?: emptyList()
        } catch (e: Exception) {
            // エラーログを出力し、空のリストを返す
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * `List<LatLng>` を JSON文字列に変換して `pathData` にセットします。
     */
    fun setPathLatLngList(pathList: List<LatLng>) {
        pathData = Gson().toJson(pathList)
    }
}