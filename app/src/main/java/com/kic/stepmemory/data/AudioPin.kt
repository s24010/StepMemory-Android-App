package com.kic.stepmemory.data

import java.util.Date

/**
 * 録音された音声とその位置情報を保持するデータクラス
 */
data class AudioPin(
    val audioUrl: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val createdAt: Date = Date()
) {
    // 引数なしコンストラクタ (Firestoreでのデシリアライズに必要)
    constructor() : this("", 0.0, 0.0, Date())
}