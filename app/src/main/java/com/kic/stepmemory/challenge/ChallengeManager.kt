package com.kic.stepmemory.challenge

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import com.kic.stepmemory.data.Challenge
import com.kic.stepmemory.data.ChallengeTemplate
import com.kic.stepmemory.data.ChallengeType
import com.kic.stepmemory.data.Landmark
import com.kic.stepmemory.data.Record
import com.kic.stepmemory.data.UserStats // ★★★ 正しいUserStatsをインポート ★★★
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class ChallengeManager(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val prefs = context.getSharedPreferences("ChallengePrefs", Context.MODE_PRIVATE)

    // ★★★ 不要になった古いUserStatsの定義をここから削除 ★★★

    private val allTemplates = listOf(
        ChallengeTemplate("welcome", ChallengeType.TOTAL_RECORDS,
            titleTemplate = "最初の記録を作成しよう",
            descriptionTemplate = "「記録を開始する」ボタンから最初の記録を作成しましょう！",
            goalMultiplier = 1.0,
            prerequisite = { it.totalRecords == 0 }
        ),
        ChallengeTemplate("duration_tier1", ChallengeType.TOTAL_DURATION,
            titleTemplate = "合計{goal}分歩いてみよう",
            descriptionTemplate = "記録の合計時間が{goal}分に達すると達成です。",
            goalMultiplier = 60.0,
            prerequisite = { true }
        ),
        ChallengeTemplate("records_tier1", ChallengeType.TOTAL_RECORDS,
            titleTemplate = "合計{goal}回記録してみよう",
            descriptionTemplate = "これまでに{progress}回記録しました。合計{goal}回を目指しましょう！",
            goalMultiplier = 5.0,
            prerequisite = { true }
        ),
        ChallengeTemplate("landmarks_tier1", ChallengeType.TOTAL_LANDMARKS,
            titleTemplate = "ランドマークを{goal}つ登録しよう",
            descriptionTemplate = "思い出の場所を合計{goal}ヶ所登録すると達成です。",
            goalMultiplier = 3.0,
            prerequisite = { it.totalRecords > 0 }
        ),
        ChallengeTemplate("icons_tier1", ChallengeType.NEW_LANDMARK_ICON,
            titleTemplate = "{goal}種類のアイコンを使ってみよう",
            descriptionTemplate = "異なる種類のアイコンでランドマークを登録してみましょう。",
            goalMultiplier = 3.0,
            prerequisite = { it.totalLandmarks > 0 }
        ),
        ChallengeTemplate("long_walk", ChallengeType.SINGLE_RECORD_DURATION,
            titleTemplate = "1回の記録で{goal}分を目指そう",
            descriptionTemplate = "あなたの平均記録時間は約{avg}分です。少し長い{goal}分の記録に挑戦！",
            goalMultiplier = 1.2,
            prerequisite = { it.totalRecords > 2 }
        )
    )

    suspend fun getCurrentChallenge(): Challenge {
        val stats = getUserStats()
        val activeChallengeId = prefs.getString("active_challenge_id", null)

        if (activeChallengeId != null) {
            val challenge = generateChallengeFromTemplate(findTemplateById(activeChallengeId), stats)
            if (!challenge.isCompleted) {
                return challenge
            }
        }

        val newChallenge = selectNewChallenge(stats)
        prefs.edit().putString("active_challenge_id", newChallenge.id).apply()
        return newChallenge
    }

    suspend fun updateProgressAndGetNewChallengeIfNeeded(): Challenge {
        updateProgress()
        return getCurrentChallenge()
    }

    private suspend fun updateProgress() {
        val activeChallengeId = prefs.getString("active_challenge_id", null) ?: return
        val stats = getUserStats()
        val challenge = generateChallengeFromTemplate(findTemplateById(activeChallengeId), stats)

        if (challenge.isCompleted) {
            val completed = prefs.getStringSet("completed_challenges", mutableSetOf()) ?: mutableSetOf()
            completed.add(activeChallengeId)
            prefs.edit()
                .remove("active_challenge_id")
                .putStringSet("completed_challenges", completed)
                .apply()
        }
    }

    private suspend fun getUserStats(): UserStats {
        val records = firestore.collection("records").get().await().toObjects<Record>()
        val landmarks = firestore.collection("landmarks").get().await().toObjects<Landmark>()
        val totalDuration = records.sumOf { it.durationMs ?: 0L }
        val avgDuration = if (records.isNotEmpty()) totalDuration / records.size else 0L

        return UserStats(
            totalRecords = records.size,
            totalDurationMinutes = TimeUnit.MILLISECONDS.toMinutes(totalDuration),
            averageDurationMinutes = TimeUnit.MILLISECONDS.toMinutes(avgDuration),
            totalLandmarks = landmarks.size,
            uniqueIconTypes = landmarks.map { it.iconType }.distinct().count()
        )
    }

    private fun selectNewChallenge(stats: UserStats): Challenge {
        val completed = prefs.getStringSet("completed_challenges", emptySet()) ?: emptySet()

        val possibleTemplates = allTemplates
            .filter { !completed.contains(it.id) && it.prerequisite(stats) }

        val template = possibleTemplates.randomOrNull() ?: findTemplateById("welcome")
        return generateChallengeFromTemplate(template, stats)
    }

    private fun findTemplateById(id: String): ChallengeTemplate {
        return allTemplates.firstOrNull { it.id == id } ?: allTemplates.first()
    }

    private fun generateChallengeFromTemplate(template: ChallengeTemplate, stats: UserStats): Challenge {
        var goal = 0
        var progress = 0
        var title = template.titleTemplate
        var description = template.descriptionTemplate

        when (template.type) {
            ChallengeType.TOTAL_DURATION -> {
                goal = template.goalMultiplier.toInt()
                progress = stats.totalDurationMinutes.toInt()
            }
            ChallengeType.TOTAL_RECORDS -> {
                goal = template.goalMultiplier.toInt()
                progress = stats.totalRecords
            }
            ChallengeType.TOTAL_LANDMARKS -> {
                goal = template.goalMultiplier.toInt()
                progress = stats.totalLandmarks
            }
            ChallengeType.NEW_LANDMARK_ICON -> {
                goal = template.goalMultiplier.toInt()
                progress = stats.uniqueIconTypes
            }
            ChallengeType.SINGLE_RECORD_DURATION -> {
                val calculatedGoal = ceil((stats.averageDurationMinutes * template.goalMultiplier) / 5).toInt() * 5
                goal = if (calculatedGoal > 0) calculatedGoal else 10
                progress = 0
                description = description.replace("{avg}", stats.averageDurationMinutes.toString())
            }
        }

        title = title.replace("{goal}", goal.toString())
        description = description.replace("{goal}", goal.toString()).replace("{progress}", progress.toString())

        return Challenge(
            id = template.id,
            title = title,
            description = description,
            currentProgress = progress,
            goal = goal,
            type = template.type,
            isCompleted = progress >= goal
        )
    }
}