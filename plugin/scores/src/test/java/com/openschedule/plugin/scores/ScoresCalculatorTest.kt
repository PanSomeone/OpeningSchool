package com.openschedule.plugin.scores

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoresCalculatorTest {

    @Test
    fun mapsScoresToGradePoints() {
        assertEquals(4.0, GpaCalculator.gradePoint(100.0), 0.0001)
        assertEquals(4.0, GpaCalculator.gradePoint(90.0), 0.0001)
        assertEquals(3.7, GpaCalculator.gradePoint(89.9), 0.0001)
        assertEquals(3.7, GpaCalculator.gradePoint(85.0), 0.0001)
        assertEquals(3.3, GpaCalculator.gradePoint(84.9), 0.0001)
        assertEquals(1.0, GpaCalculator.gradePoint(60.0), 0.0001)
        assertEquals(0.0, GpaCalculator.gradePoint(59.9), 0.0001)
    }

    @Test
    fun calculatesCreditWeightedGpa() {
        val courses = listOf(
            GpaCourse("1", "高数", credit = 4.0, score = 90.0),
            GpaCourse("2", "英语", credit = 3.0, score = 80.0),
            GpaCourse("3", "体育", credit = 2.0, score = 60.0),
        )
        val result = GpaCalculator.calculate(courses)
        assertEquals(3.0, result.gpa, 0.0001)
        assertEquals(9.0, result.credits, 0.0001)
        assertEquals(3, result.courseCount)
        assertEquals((90.0 * 4 + 80.0 * 3 + 60.0 * 2) / 9.0, result.weightedScore, 0.0001)
    }

    @Test
    fun excludedCoursesDoNotCount() {
        val courses = listOf(
            GpaCourse("1", "高数", credit = 4.0, score = 90.0),
            GpaCourse("2", "音乐", credit = 2.0, score = 100.0, countsInGpa = false),
        )
        val result = GpaCalculator.calculate(courses)
        assertEquals(4.0, result.gpa, 0.0001)
        assertEquals(4.0, result.credits, 0.0001)
        assertEquals(1, result.courseCount)
    }

    @Test
    fun groupsResultsByTerm() {
        val courses = listOf(
            GpaCourse("1", "高数", credit = 4.0, score = 90.0, term = "2025-2026 1"),
            GpaCourse("2", "英语", credit = 2.0, score = 80.0, term = "2025-2026 1"),
            GpaCourse("3", "物理", credit = 3.0, score = 70.0, term = "2025-2026 2"),
            GpaCourse("4", "选修", credit = 1.0, score = 100.0, term = "2025-2026 2", countsInGpa = false),
        )
        val terms = GpaCalculator.termResults(courses)
        assertEquals(2, terms.size)
        assertEquals("2025-2026 1", terms[0].term)
        assertEquals(6.0, terms[0].result.credits, 0.0001)
        assertEquals(1, terms[1].result.courseCount)
        assertEquals(GpaCalculator.gradePoint(70.0), terms[1].result.gpa, 0.0001)
    }

    @Test
    fun emptyGpaIsZero() {
        val result = GpaCalculator.calculate(emptyList())
        assertEquals(0.0, result.gpa, 0.0001)
        assertEquals(0.0, result.credits, 0.0001)
    }

    @Test
    fun peWeightsAddToHundredAndBonusCapsAtTwenty() {
        val perfect = PeItemScores(
            bmi = 100.0,
            vitalCapacity = 100.0,
            sprint = 100.0,
            sitAndReach = 100.0,
            longJump = 100.0,
            strength = 100.0,
            endurance = 100.0,
        )
        assertEquals(100.0, PeCalculator.standardScore(perfect), 0.0001)
        assertEquals(120.0, PeCalculator.totalScore(perfect, bonus = 30.0), 0.0001)
        assertEquals("优秀", PeCalculator.grade(120.0))
    }

    @Test
    fun peGradesFollowOfficialThresholds() {
        assertEquals("优秀", PeCalculator.grade(90.0))
        assertEquals("良好", PeCalculator.grade(89.9))
        assertEquals("良好", PeCalculator.grade(80.0))
        assertEquals("及格", PeCalculator.grade(79.9))
        assertEquals("及格", PeCalculator.grade(60.0))
        assertEquals("不及格", PeCalculator.grade(59.9))
    }

    @Test
    fun graduationUsesSeniorHalfAndPreviousAverage() {
        val score = PeCalculator.graduationScore(listOf(80.0, 90.0, 100.0, 110.0))
        assertEquals(100.0, score, 0.0001)
        assertEquals(75.0, PeCalculator.graduationScore(listOf(75.0)), 0.0001)
    }
}
