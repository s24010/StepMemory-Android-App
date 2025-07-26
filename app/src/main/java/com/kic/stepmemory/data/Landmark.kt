package com.kic.stepmemory.data

import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ServerTimestamp
import java.io.Serializable
import java.util.Date

// Serializableを実装してActivity間でオブジェクトを渡せるようにします
data class Landmark(
    val id: String = "",
    val userId: String = "", // ユーザーを識別するためのID（将来の拡張用）
    val name: String = "",
    val episode: String = "",
    val imageUrl: String? = null, // 写真のURL（今回は実装範囲外）
    val location: GeoPoint = GeoPoint(0.0, 0.0),
    @ServerTimestamp
    val createdAt: Date? = null
) : Serializable