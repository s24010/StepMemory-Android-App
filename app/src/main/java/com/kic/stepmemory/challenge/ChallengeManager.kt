package com.kic.stepmemory.challenge

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import com.kic.stepmemory.data.Challenge
import com.kic.stepmemory.data.ChallengeType
import com.kic.stepmemory.data.Landmark
import com.kic.stepmemory.data.Record
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class ChallengeManager(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val prefs = context.getSharedPreferences("ChallengePrefs", Context.MODE_PRIVATE)

    // ユーザーの統計情報を保持するデータクラス
    data class UserStats(
        val totalRecords: Int,
        val totalDurationMinutes: Long,
        val totalLandmarks: Int,
        val uniqueIconTypes: Int
    )

    // 現在のチャレンジを取得する
    suspend fun getCurrentChallenge(): Challenge {
        val activeChallengeId = prefs.getString("active_challenge_id", null)
        val stats = getUserStats()

        return if (activeChallengeId != null) {
            // 進行中のチャレンジがあれば、それを返す
            generateChallengeById(activeChallengeId, stats)
        } else {
            // なければ新しいチャレンジを生成して保存
            val newChallenge = selectNewChallenge(stats)
            prefs.edit().putString("active_challenge_id", newChallenge.id).apply()
            newChallenge
        }
    }

    // 記録が保存されたときに進捗を更新する
    suspend fun updateProgress() {
        val activeChallengeId = prefs.getString("active_challenge_id", null) ?: return
        val stats = getUserStats()
        val challenge = generateChallengeById(activeChallengeId, stats)

        if (challenge.currentProgress >= challenge.goal) {
            // チャレンジ達成！
            val completedChallenges = prefs.getStringSet("completed_challenges", mutableSetOf()) ?: mutableSetOf()
            completedChallenges.add(activeChallengeId)
            prefs.edit()
                .remove("active_challenge_id")
                .putStringSet("completed_challenges", completedChallenges)
                .apply()
        }
    }

    // Firestoreからユーザーの統計情報を計算する
    private suspend fun getUserStats(): UserStats {
        val records = firestore.collection("records").get().await().toObjects<Record>()
        val landmarks = firestore.collection("landmarks").get().await().toObjects<Landmark>()
        val totalDuration = records.sumOf { it.durationMs ?: 0L }
        val uniqueIcons = landmarks.map { it.iconType }.distinct().count()

        return UserStats(
            totalRecords = records.size,
            totalDurationMinutes = TimeUnit.MILLISECONDS.toMinutes(totalDuration),
            totalLandmarks = landmarks.size,
            uniqueIconTypes = uniqueIcons
        )
    }

    // 統計情報に基づいて新しいチャレンジを選択する
    private fun selectNewChallenge(stats: UserStats): Challenge {
        val completed = prefs.getStringSet("completed_challenges", emptySet()) ?: emptySet()
        val possibleChallenges = mutableListOf<Challenge>()

        // 継続系チャレンジ
        if (!completed.contains("duration_60")) possibleChallenges.add(generateChallengeById("duration_60", stats))
        if (!completed.contains("records_5")) possibleChallenges.add(generateChallengeById("records_5", stats))

        // 挑戦系チャレンジ
        if (stats.totalRecords > 0 && !completed.contains("landmarks_3")) possibleChallenges.add(generateChallengeById("landmarks_3", stats))
        if (stats.totalLandmarks > 0 && !completed.contains("icons_3")) possibleChallenges.add(generateChallengeById("icons_3", stats))

        // 候補からランダムに1つ選択、なければウェルカムチャレンジ
        return possibleChallenges.randomOrNull() ?: generateChallengeById("welcome", stats)
    }

    // IDに基づいてチャレンジオブジェクトを生成する
    private fun generateChallengeById(id: String, stats: UserStats): Challenge {
        return when (id) {
            "duration_60" -> Challenge(id, "合計1時間歩こう", "記録の合計時間が60分に達すると達成です。", stats.totalDurationMinutes.toInt(), 60, ChallengeType.TOTAL_DURATION)
            "records_5" -> Challenge(id, "5回記録してみよう", "合計5回の記録を作成すると達成です。", stats.totalRecords, 5, ChallengeType.TOTAL_RECORDS)
            "landmarks_3" -> Challenge(id, "ランドマークを3つ登録", "思い出の場所を3ヶ所登録すると達成です。", stats.totalLandmarks, 3, ChallengeType.TOTAL_LANDMARKS)
            "icons_3" -> Challenge(id, "3種類のアイコンを使おう", "3種類のアイコンでランドマークを登録すると達成です。", stats.uniqueIconTypes, 3, ChallengeType.NEW_LANDMARK_ICON)
            else -> Challenge("welcome", "最初の記録を作成しよう", "「記録を開始する」ボタンから最初の記録を作成しましょう！", stats.totalRecords, 1, ChallengeType.TOTAL_RECORDS)
        }
    }
}