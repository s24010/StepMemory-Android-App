package com.kic.stepmemory.data

// ★★★ ChallengeManagerからUserStatsをこちらに移動 ★★★
/**
 * チャレンジ生成の基礎となるユーザーの統計情報を保持するデータクラス
 */
data class UserStats(
    val totalRecords: Int,
    val totalDurationMinutes: Long,
    val averageDurationMinutes: Long,
    val totalLandmarks: Int,
    val uniqueIconTypes: Int
)

// ユーザーに実際に表示される、生成済みのチャレンジ
data class Challenge(
    val id: String,
    val title: String,
    val description: String,
    val currentProgress: Int,
    val goal: Int,
    val type: ChallengeType,
    val isCompleted: Boolean = false
)

// チャレンジを生成するための「ひな形」
data class ChallengeTemplate(
    val id: String,
    val type: ChallengeType,
    val titleTemplate: String,
    val descriptionTemplate: String,
    val goalMultiplier: Double,
    // ★★★ これでChallengeManagerを参照する必要がなくなる ★★★
    val prerequisite: (UserStats) -> Boolean
)

enum class ChallengeType {
    TOTAL_DURATION,
    TOTAL_RECORDS,
    TOTAL_LANDMARKS,
    NEW_LANDMARK_ICON,
    SINGLE_RECORD_DURATION
}