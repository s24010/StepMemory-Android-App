package com.kic.stepmemory.data

data class Challenge(
    val id: String,
    val title: String,
    val description: String,
    val currentProgress: Int,
    val goal: Int,
    val type: ChallengeType
)

enum class ChallengeType {
    TOTAL_DURATION,
    TOTAL_RECORDS,
    TOTAL_LANDMARKS,
    NEW_LANDMARK_ICON
}