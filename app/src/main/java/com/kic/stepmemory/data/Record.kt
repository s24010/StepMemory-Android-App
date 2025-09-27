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
    var audioPins: List<AudioPin> = listOf(),
    var weather: String? = null, // <<< 追加
    var createdAt: Date? = null,
    var updatedAt: Date? = null
){
    @get:Exclude
    @set:Exclude
    var idUUID: String = ""
}
