package com.kic.stepmemory.data

data class UserStats(
    val totalRecords: Int,
    val totalDurationMinutes: Long,
    val averageDurationMinutes: Long,
    val totalLandmarks: Int,
    val uniqueIconTypes: Int,
    val totalDistanceKm: Double,
    val nightRecordsCount: Int,
    // ▼▼▼ 追加 ▼▼▼
    val rainyDayRecordCount: Int = 0,
    val weekendRecordCount: Int = 0
    // ▲▲▲ 追加ここまで ▲▲▲
)

data class Challenge(
    val id: String,
    val title: String,
    val description: String,
    val currentProgress: Int,
    val goal: Int,
    val type: ChallengeType,
    val isCompleted: Boolean = false
)

data class ChallengeTemplate(
    val id: String,
    val type: ChallengeType,
    val titleTemplate: String,
    val descriptionTemplate: String,
    val goalMultiplier: Double,
    val prerequisite: (UserStats) -> Boolean,
    val unlocksIconId: String? = null
)

enum class ChallengeType {
    TOTAL_DURATION,
    TOTAL_RECORDS,
    TOTAL_LANDMARKS,
    NEW_LANDMARK_ICON,
    SINGLE_RECORD_DURATION,
    TOTAL_DISTANCE,
    NIGHT_RECORDS,
    // ▼▼▼ 追加 ▼▼▼
    RAINY_DAY_RECORDS,
    WEEKEND_RECORDS
    // ▲▲▲ 追加ここまで ▲▲▲
}

