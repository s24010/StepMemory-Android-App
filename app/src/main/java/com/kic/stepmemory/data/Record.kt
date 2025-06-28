// app/src/main/java/com/kic/stepmemory/data/Record.kt

package com.kic.stepmemory.data

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties

// このファイルからはGsonとExcludeのimportは不要になります
@IgnoreExtraProperties
data class Record(
    var userId: String? = null,
    var name: String? = null,
    var startTime: Long = 0L,
    var endTime: Long = 0L,
    var durationMs: Long? = null,
    var pathPoints: List<GeoPoint> = listOf(),
    var memo: String? = null,
    var createdAt: Long? = null,
    var updatedAt: Long? = null
){
    // ★ プロパティとしてidUUIDを定義し、Firestoreの対象外にする
    @get:Exclude
    @set:Exclude
    var idUUID: String = ""
}
