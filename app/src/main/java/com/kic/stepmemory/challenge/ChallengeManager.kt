package com.kic.stepmemory.challenge

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import com.kic.stepmemory.BuildConfig // ▼▼▼ BuildConfig をインポート ▼▼▼
import com.kic.stepmemory.R // Rクラスをインポート (strings.xmlアクセス用)
import com.kic.stepmemory.data.*
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class ChallengeManager(private val context: Context) { // context を private val に変更
    companion object {
        const val CHALLENGE_ID_RECORDS_FOR_FLASHBACK = "records_for_flashback"
        const val CHALLENGE_ID_NIGHT_WALK_5 = "night_walk_5" // 夜の散歩チャレンジ（フィルターアンロック用）
        const val CHALLENGE_ID_UNLOCK_RAINY_FILTER = "unlock_rainy_filter"
        const val CHALLENGE_ID_UNLOCK_WEEKEND_FILTER = "unlock_weekend_filter"

        private const val PREF_FLASHBACK_FEATURE_UNLOCKED = "flashback_feature_unlocked"
        private const val PREF_RAINY_DAY_FILTER_UNLOCKED = "rainy_day_filter_unlocked"
        private const val PREF_NIGHT_WALK_FILTER_UNLOCKED = "night_walk_filter_unlocked"
        private const val PREF_WEEKEND_FILTER_UNLOCKED = "weekend_filter_unlocked"

    }

    private val prefs: SharedPreferences = context.getSharedPreferences("challenge_prefs", Context.MODE_PRIVATE)
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val unlockedIconManager = UnlockedIconManager(context)

    private val allTemplates = listOf(
        ChallengeTemplate("welcome", ChallengeType.TOTAL_RECORDS,
            titleTemplate = "最初の記録を作成しよう",
            descriptionTemplate = "「記録を開始する」ボタンから最初の記録を作成しましょう！",
            goalMultiplier = 1.0,
            prerequisite = { it.totalRecords == 0 }
        ),
        ChallengeTemplate("records_tier1", ChallengeType.TOTAL_RECORDS,
            titleTemplate = "合計{goal}回記録してみよう (ステップ1)",
            descriptionTemplate = "まずは合計{goal}回の記録を目指しましょう！",
            goalMultiplier = 5.0,
            prerequisite = { true }
        ),
        ChallengeTemplate("records_tier2", ChallengeType.TOTAL_RECORDS,
            titleTemplate = "合計{goal}回記録してみよう (ステップ2)",
            descriptionTemplate = "素晴らしい！次は合計{goal}回の記録に挑戦です。",
            goalMultiplier = 15.0,
            prerequisite = { it.totalRecords >= 5 }
        ),
        ChallengeTemplate("records_tier3", ChallengeType.TOTAL_RECORDS,
            titleTemplate = "合計{goal}回記録してみよう (ステップ3)",
            descriptionTemplate = "もうベテランの域！合計{goal}回記録で新たな道が開けるかも？",
            goalMultiplier = 25.0,
            prerequisite = { it.totalRecords >= 15 }
        ),
        ChallengeTemplate(
            CHALLENGE_ID_RECORDS_FOR_FLASHBACK, ChallengeType.TOTAL_RECORDS,
            titleTemplate = "合計{goal}回記録して特別な機能を開放！",
            descriptionTemplate = "記録の合計回数が{goal}回に達すると、過去の記録を振り返る特別な機能が使えるようになります。",
            goalMultiplier = 30.0,
            prerequisite = { it.totalRecords >= 25 }
        ),
        ChallengeTemplate("duration_tier1", ChallengeType.TOTAL_DURATION,
            titleTemplate = "合計{goal}分歩いてみよう (第1章)",
            descriptionTemplate = "記録の合計時間が{goal}分に達すると達成です。",
            goalMultiplier = 60.0,
            prerequisite = { true }
        ),
        ChallengeTemplate("duration_tier2", ChallengeType.TOTAL_DURATION,
            titleTemplate = "合計{goal}分歩いてみよう (第2章)",
            descriptionTemplate = "次は合計{goal}分！さらに長い時間を記録に残しましょう。",
            goalMultiplier = 300.0,
            prerequisite = { it.totalDurationMinutes >= 60 }
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
            prerequisite = { it.totalRecords > 2 && it.averageDurationMinutes > 0 }
        ),
        ChallengeTemplate("distance_bronze", ChallengeType.TOTAL_DISTANCE,
            titleTemplate = "合計{goal}km歩こう（ブロンズ）",
            descriptionTemplate = "ブロンズピンのアイコンがアンロックされます！",
            goalMultiplier = 10.0,
            prerequisite = { true },
            unlocksIconId = "bronze_pin"
        ),
        ChallengeTemplate("distance_silver", ChallengeType.TOTAL_DISTANCE,
            titleTemplate = "合計{goal}km歩こう（シルバー）",
            descriptionTemplate = "シルバーピンのアイコンがアンロックされます！",
            goalMultiplier = 50.0,
            prerequisite = { it.totalDistanceKm >= 10 },
            unlocksIconId = "silver_pin"
        ),
        ChallengeTemplate("distance_gold", ChallengeType.TOTAL_DISTANCE,
            titleTemplate = "合計{goal}km歩こう（ゴールド）",
            descriptionTemplate = "ゴールドピンのアイコンがアンロックされます！",
            goalMultiplier = 100.0,
            prerequisite = { it.totalDistanceKm >= 50 },
            unlocksIconId = "gold_pin"
        ),
        ChallengeTemplate(CHALLENGE_ID_NIGHT_WALK_5, ChallengeType.NIGHT_RECORDS,
            titleTemplate = "夜の散歩を{goal}回記録しよう",
            descriptionTemplate = "月のアイコンと「夜の散歩フィルター」がアンロックされます！",
            goalMultiplier = 5.0,
            prerequisite = { it.totalRecords > 0 },
            unlocksIconId = "moon_icon"
        ),
        ChallengeTemplate(CHALLENGE_ID_UNLOCK_RAINY_FILTER, ChallengeType.RAINY_DAY_RECORDS,
            titleTemplate = "雨の日に{goal}回記録しよう",
            descriptionTemplate = "「雨の日フィルター」がアンロックされます！これで雨の日の記録だけを見返せるようになります。",
            goalMultiplier = 3.0,
            prerequisite = { it.totalRecords >= 1 }
        ),
        ChallengeTemplate(CHALLENGE_ID_UNLOCK_WEEKEND_FILTER, ChallengeType.WEEKEND_RECORDS,
            titleTemplate = "週末に{goal}回記録しよう",
            descriptionTemplate = "「週末フィルター」がアンロックされます！土日の思い出を振り返りましょう。",
            goalMultiplier = 5.0,
            prerequisite = { it.totalRecords >= 1 }
        )
    )

    suspend fun getCurrentChallenge(): Challenge {
        val stats = getUserStats()
        unlockFlashbackFeatureIfEligible(stats)

        val activeChallengeId = prefs.getString("active_challenge_id", null)
        if (activeChallengeId != null) {
            val template = findTemplateById(activeChallengeId)
            val challenge = generateChallengeFromTemplate(template, stats)
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
        val stats = getUserStats()
        unlockFlashbackFeatureIfEligible(stats)

        val activeChallengeId = prefs.getString("active_challenge_id", null) ?: return
        val template = findTemplateById(activeChallengeId)
        val challenge = generateChallengeFromTemplate(template, stats)

        if (challenge.isCompleted) {
            template.unlocksIconId?.let { unlockedIconManager.unlockIcon(it) }

            when (activeChallengeId) {
                CHALLENGE_ID_RECORDS_FOR_FLASHBACK -> {
                    // Already handled by unlockFlashbackFeatureIfEligible
                }
                CHALLENGE_ID_NIGHT_WALK_5 -> {
                    if (!isNightWalkFilterUnlockedInternal()) { // Use internal check for writing to prefs
                        prefs.edit().putBoolean(PREF_NIGHT_WALK_FILTER_UNLOCKED, true).apply()
                        Log.d("ChallengeManager", "Night walk filter unlocked!")
                    }
                }
                CHALLENGE_ID_UNLOCK_RAINY_FILTER -> {
                    if (!isRainyDayFilterUnlockedInternal()) { // Use internal check for writing to prefs
                        prefs.edit().putBoolean(PREF_RAINY_DAY_FILTER_UNLOCKED, true).apply()
                        Log.d("ChallengeManager", "Rainy day filter unlocked!")
                    }
                }
                CHALLENGE_ID_UNLOCK_WEEKEND_FILTER -> {
                    if (!isWeekendFilterUnlockedInternal()) { // Use internal check for writing to prefs
                        prefs.edit().putBoolean(PREF_WEEKEND_FILTER_UNLOCKED, true).apply()
                        Log.d("ChallengeManager", "Weekend filter unlocked!")
                    }
                }
            }

            val completed = prefs.getStringSet("completed_challenges", mutableSetOf()) ?: mutableSetOf()
            completed.add(activeChallengeId)
            prefs.edit()
                .remove("active_challenge_id")
                .putStringSet("completed_challenges", completed)
                .apply()
        }
    }

    private fun unlockFlashbackFeatureIfEligible(stats: UserStats) {
        if (stats.totalRecords >= 30 && !isFlashbackFeatureUnlockedInternal()) { // Use internal check for writing to prefs
            prefs.edit().putBoolean(PREF_FLASHBACK_FEATURE_UNLOCKED, true).apply()
            Log.d("ChallengeManager", "Flashback feature unlocked! (Total records: ${stats.totalRecords})")
        }
    }

    // Public methods considering BuildConfig flag
    fun isFlashbackFeatureUnlocked(): Boolean {
        if (BuildConfig.ALL_FEATURES_UNLOCKED) return true
        return isFlashbackFeatureUnlockedInternal()
    }

    fun isRainyDayFilterUnlocked(): Boolean {
        if (BuildConfig.ALL_FEATURES_UNLOCKED) return true
        return isRainyDayFilterUnlockedInternal()
    }

    fun isNightWalkFilterUnlocked(): Boolean {
        if (BuildConfig.ALL_FEATURES_UNLOCKED) return true
        return isNightWalkFilterUnlockedInternal()
    }

    fun isWeekendFilterUnlocked(): Boolean {
        if (BuildConfig.ALL_FEATURES_UNLOCKED) return true
        return isWeekendFilterUnlockedInternal()
    }

    // Internal methods to check actual SharedPreferences
    private fun isFlashbackFeatureUnlockedInternal(): Boolean = prefs.getBoolean(PREF_FLASHBACK_FEATURE_UNLOCKED, false)
    private fun isRainyDayFilterUnlockedInternal(): Boolean = prefs.getBoolean(PREF_RAINY_DAY_FILTER_UNLOCKED, false)
    private fun isNightWalkFilterUnlockedInternal(): Boolean = prefs.getBoolean(PREF_NIGHT_WALK_FILTER_UNLOCKED, false)
    private fun isWeekendFilterUnlockedInternal(): Boolean = prefs.getBoolean(PREF_WEEKEND_FILTER_UNLOCKED, false)

    private suspend fun getUserStats(): UserStats {
        val records = firestore.collection("records").get().await().toObjects<Record>()
        val landmarks = firestore.collection("landmarks").get().await().toObjects<Landmark>()
        val totalDuration = records.sumOf { it.durationMs ?: 0L }
        val avgDuration = if (records.isNotEmpty()) totalDuration / records.size else 0L
        val totalDistanceMeters = records.sumOf { calculateDistance(it.pathPoints) }
        val nightRecordsCount = records.count { record ->
            record.startTime?.let { isNightTime(it) } ?: false
        }
        var rainyDayRecordCount = 0
        var weekendRecordCount = 0
        val calendar = Calendar.getInstance()
        val rainyWeatherString = context.getString(R.string.weather_rainy_value) // getStringを使用して比較

        for (record in records) {
            if (record.weather == rainyWeatherString) { // strings.xmlの値と比較
                rainyDayRecordCount++
            }
            record.startTime?.let {
                calendar.timeInMillis = it
                when (calendar.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.SATURDAY, Calendar.SUNDAY -> weekendRecordCount++
                    else -> { /* 土日以外は特に何もしない */ } // 修正点1: else を追加
                }
            }
        }

        return UserStats(
            totalRecords = records.size,
            totalDurationMinutes = TimeUnit.MILLISECONDS.toMinutes(totalDuration),
            averageDurationMinutes = TimeUnit.MILLISECONDS.toMinutes(avgDuration),
            totalLandmarks = landmarks.size,
            uniqueIconTypes = landmarks.map { it.iconType }.distinct().count(),
            totalDistanceKm = totalDistanceMeters / 1000.0,
            nightRecordsCount = nightRecordsCount,
            rainyDayRecordCount = rainyDayRecordCount,
            weekendRecordCount = weekendRecordCount
        )
    }

    private fun isNightTime(timeInMillis: Long): Boolean {
        val calendar = Calendar.getInstance().apply { this.timeInMillis = timeInMillis }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        return hour >= 19 || hour < 5 // 19:00 - 04:59
    }

    private fun calculateDistance(path: List<com.kic.stepmemory.data.GeoPoint>): Double {
        var distance = 0.0
        for (i in 0 until path.size - 1) {
            val start = path[i]
            val end = path[i + 1]
            val results = FloatArray(1)
            android.location.Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, results)
            distance += results[0]
        }
        return distance
    }

    @Deprecated("Use isNightTime(timeInMillis: Long) instead for individual record checks, or UserStats.nightRecordsCount for aggregated count.")
    private fun isNightRecord(startTime: Long): Boolean {
        val calendar = Calendar.getInstance().apply { timeInMillis = startTime }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        return hour >= 19 || hour < 5
    }

    private fun selectNewChallenge(stats: UserStats): Challenge {
        val completed = prefs.getStringSet("completed_challenges", emptySet()) ?: emptySet()
        val welcomeTemplateId = "welcome"
        val welcomeChallengeTemplate = allTemplates.first { it.id == welcomeTemplateId }

        if (stats.totalRecords == 0 && !completed.contains(welcomeTemplateId)) {
            return generateChallengeFromTemplate(welcomeChallengeTemplate, stats)
        }
        val possibleTemplates = allTemplates.filter {
            !completed.contains(it.id) && it.prerequisite(stats)
        }
        var selectedTemplate = possibleTemplates.randomOrNull()
        if (selectedTemplate == null) {
            Log.d("ChallengeManager", "No new suitable challenges found. Defaulting to welcome challenge or a generic one.")
            // Consider a fallback if all challenges are completed and prerequisites for none are met.
            selectedTemplate = allTemplates.firstOrNull { !completed.contains(it.id) } ?: welcomeChallengeTemplate
        }
        return generateChallengeFromTemplate(selectedTemplate, stats)
    }

    private fun findTemplateById(id: String): ChallengeTemplate {
        return allTemplates.firstOrNull { it.id == id } ?: allTemplates.first()
    }

    private fun generateChallengeFromTemplate(template: ChallengeTemplate, stats: UserStats): Challenge {
        var goal = 0
        var progress = 0
        val title = template.titleTemplate
        var description = template.descriptionTemplate

        // 修正点2: 以前コメントアウトした when ブロックのコメントを解除
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
                val avgMinutes = stats.averageDurationMinutes
                val calculatedGoal = if (avgMinutes > 0) {
                    ceil((avgMinutes * template.goalMultiplier) / 5).toInt() * 5
                } else {
                    10
                }
                goal = if (calculatedGoal > 0) calculatedGoal else 10
                progress = 0
                description = description.replace("{avg}", avgMinutes.toString())
            }
            ChallengeType.TOTAL_DISTANCE -> {
                goal = template.goalMultiplier.toInt()
                progress = stats.totalDistanceKm.toInt()
            }
            ChallengeType.NIGHT_RECORDS -> {
                goal = template.goalMultiplier.toInt()
                progress = stats.nightRecordsCount
            }
            ChallengeType.RAINY_DAY_RECORDS -> {
                goal = template.goalMultiplier.toInt()
                progress = stats.rainyDayRecordCount
            }
            ChallengeType.WEEKEND_RECORDS -> {
                goal = template.goalMultiplier.toInt()
                progress = stats.weekendRecordCount
            }
            else -> {
                Log.w("ChallengeManager", "Unknown ChallengeType encountered: ${template.type}. Using default goal/progress.")
                // goal と progress は既に 0 で初期化されているため、
                // ここで明示的に再設定する必要は必ずしもありませんが、
                // 安全策としてデフォルト値を設定することも可能です。
                // goal = 0
                // progress = 0
                // description = "不明なチャレンジです。" // 必要に応じて説明も変更
            }
        }

        val finalTitle = title.replace("{goal}", goal.toString())
        val finalDescription = description.replace("{goal}", goal.toString()).replace("{progress}", progress.toString())

        return Challenge(
            id = template.id,
            title = finalTitle,
            description = finalDescription,
            currentProgress = progress,
            goal = goal,
            type = template.type,
            isCompleted = progress >= goal && goal > 0
        )
    }
}
