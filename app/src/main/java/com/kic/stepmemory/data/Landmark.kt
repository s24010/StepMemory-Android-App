package com.kic.stepmemory.data

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.Date

@IgnoreExtraProperties
data class Landmark(
    var title: String = "",
    var episode: String = "",
    var iconType: String = "PIN",
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    // ★★★ データ型を Long から Date? に変更 ★★★
    var createdAt: Date? = null
) {
    @get:Exclude
    @set:Exclude
    var id: String = ""
}