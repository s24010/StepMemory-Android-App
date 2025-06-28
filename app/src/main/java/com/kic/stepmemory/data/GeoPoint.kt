// app/src/main/java/com/kic/stepmemory/data/GeoPoint.kt

package com.kic.stepmemory.data

/**
 * Firestoreと互換性のある、緯度経度を保持するためのシンプルなデータクラス
 */
data class GeoPoint(
    var latitude: Double = 0.0,
    var longitude: Double = 0.0
)