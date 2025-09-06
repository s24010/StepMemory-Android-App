package com.kic.stepmemory.data

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.Date

@IgnoreExtraProperties
data class Record(
    var userId: String? = null,
    var name: String? = null,
    var startTime: Long = 0L,
    var endTime: Long = 0L,
    var durationMs: Long? = null,
    var pathPoints: List<GeoPoint> = listOf(),
    var memo: String? = null,
    var audioUrl: String? = null,
    // ★★★ データ型を Long? から Date? に変更 ★★★
    var createdAt: Date? = null,
    var updatedAt: Date? = null
){
    @get:Exclude
    @set:Exclude
    var idUUID: String = ""
}