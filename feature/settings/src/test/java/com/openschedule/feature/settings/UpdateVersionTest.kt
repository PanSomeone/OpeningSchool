package com.openschedule.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {

    @Test
    fun betaVersionsCompareNumerically() {
        val beta2 = parseAppVersion("1.0.0-beta2")!!
        val beta10 = parseAppVersion("1.0.0-beta10")!!
        assertTrue(beta10 > beta2)
        assertTrue(parseAppVersion("v1.0.0-beta3")!! > beta2)
    }

    @Test
    fun stableBeatsPrerelease() {
        val beta = parseAppVersion("1.0.0-beta9")!!
        val stable = parseAppVersion("1.0.0")!!
        assertTrue(stable > beta)
        assertTrue(parseAppVersion("1.0.1-beta1")!! > stable)
    }

    @Test
    fun prereleaseOrdering() {
        assertTrue(parseAppVersion("1.0.0-rc1")!! > parseAppVersion("1.0.0-beta9")!!)
        assertTrue(parseAppVersion("1.0.0-alpha1")!! < parseAppVersion("1.0.0-beta1")!!)
    }

    @Test
    fun invalidVersionsAreRejected() {
        assertNull(parseAppVersion(""))
        assertNull(parseAppVersion("latest"))
        assertNull(parseAppVersion("1.0"))
    }

    @Test
    fun equalVersionsCompareAsZero() {
        assertEquals(
            0,
            parseAppVersion("1.0.0-beta2")!!.compareTo(parseAppVersion("v1.0.0-beta2")!!),
        )
    }
}
