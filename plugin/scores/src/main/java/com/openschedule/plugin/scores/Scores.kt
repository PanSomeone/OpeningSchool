package com.openschedule.plugin.scores

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class GpaCourse(
    val id: String,
    val name: String,
    val credit: Double,
    val score: Double,
    val term: String = "",
    /** User-controlled switch: some courses are not part of the GPA. */
    val countsInGpa: Boolean = true,
)

data class GpaResult(
    val gpa: Double,
    val credits: Double,
    val courseCount: Int,
    val weightedScore: Double = 0.0,
)

data class GpaTermResult(
    val term: String,
    val result: GpaResult,
)

object GpaCalculator {

    /**
     * Common 4.0 band table. HZAU states a 4.0 scale and 89-point scores map to
     * 3.7; the lower bands still need to be checked against an official
     * transcript before this app is relied on.
     */
    fun gradePoint(score: Double): Double = when {
        score >= 90 -> 4.0
        score >= 85 -> 3.7
        score >= 82 -> 3.3
        score >= 78 -> 3.0
        score >= 75 -> 2.7
        score >= 72 -> 2.3
        score >= 68 -> 2.0
        score >= 64 -> 1.5
        score >= 60 -> 1.0
        else -> 0.0
    }

    fun calculate(courses: List<GpaCourse>): GpaResult {
        val included = courses.filter { it.countsInGpa }
        val credits = included.sumOf { it.credit }
        if (credits <= 0.0) {
            return GpaResult(0.0, 0.0, included.size, 0.0)
        }
        val weighted = included.sumOf { it.credit * gradePoint(it.score) }
        val weightedScore = included.sumOf { it.credit * it.score } / credits
        return GpaResult(
            gpa = weighted / credits,
            credits = credits,
            courseCount = included.size,
            weightedScore = weightedScore,
        )
    }

    fun termResults(courses: List<GpaCourse>): List<GpaTermResult> {
        val included = courses.filter { it.countsInGpa }
        return included
            .groupBy { it.term.ifBlank { "未分学期" } }
            .toSortedMap(compareBy { if (it == "未分学期") "\uFFFF" else it })
            .map { (term, group) -> GpaTermResult(term, calculate(group)) }
    }
}

enum class PeGender { MALE, FEMALE }

data class PeItemScores(
    val bmi: Double = 0.0,
    val vitalCapacity: Double = 0.0,
    val sprint: Double = 0.0,
    val sitAndReach: Double = 0.0,
    val longJump: Double = 0.0,
    val strength: Double = 0.0,
    val endurance: Double = 0.0,
)

object PeCalculator {

    const val BMI_WEIGHT = 0.15
    const val VITAL_WEIGHT = 0.15
    const val SPRINT_WEIGHT = 0.20
    const val SIT_WEIGHT = 0.10
    const val JUMP_WEIGHT = 0.10
    const val STRENGTH_WEIGHT = 0.10
    const val ENDURANCE_WEIGHT = 0.20

    fun standardScore(scores: PeItemScores): Double =
        scores.bmi * BMI_WEIGHT +
            scores.vitalCapacity * VITAL_WEIGHT +
            scores.sprint * SPRINT_WEIGHT +
            scores.sitAndReach * SIT_WEIGHT +
            scores.longJump * JUMP_WEIGHT +
            scores.strength * STRENGTH_WEIGHT +
            scores.endurance * ENDURANCE_WEIGHT

    fun totalScore(scores: PeItemScores, bonus: Double): Double =
        (standardScore(scores) + bonus.coerceIn(0.0, 20.0)).coerceAtMost(120.0)

    fun grade(total: Double): String = when {
        total >= 90 -> "优秀"
        total >= 80 -> "良好"
        total >= 60 -> "及格"
        else -> "不及格"
    }

    /** 毕业当年占 50%，其余学年平均分占 50%。 */
    fun graduationScore(yearTotals: List<Double>): Double {
        if (yearTotals.isEmpty()) return 0.0
        if (yearTotals.size == 1) return yearTotals.first()
        val senior = yearTotals.last()
        val previous = yearTotals.dropLast(1).average()
        return (senior * 0.5 + previous * 0.5).coerceAtMost(120.0)
    }

    fun percentage(total: Double): Int = total.roundToInt()
}
