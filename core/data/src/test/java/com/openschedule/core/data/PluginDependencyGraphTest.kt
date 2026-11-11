package com.openschedule.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginDependencyGraphTest {

    private fun app(id: String, requires: List<String> = emptyList()) = ModuleManifest(
        id = id,
        name = id,
        apiVersion = ModuleManifest.API_V2,
        type = ModuleManifest.TYPE_APP,
        requires = requires,
    )

    @Test
    fun loadsDependenciesFirst() {
        val resolved = PluginDependencyGraph.resolve(
            listOf(app("b", listOf("a")), app("a")),
        )
        assertEquals(listOf("a", "b"), resolved.order.map { it.id })
        assertTrue(resolved.blocked.isEmpty())
    }

    @Test
    fun loadsIndependentModulesInGivenOrder() {
        val resolved = PluginDependencyGraph.resolve(
            listOf(app("x"), app("y"), app("z")),
        )
        assertEquals(listOf("x", "y", "z"), resolved.order.map { it.id })
    }

    @Test
    fun blocksMissingDependencyAndDependents() {
        val resolved = PluginDependencyGraph.resolve(
            listOf(app("b", listOf("a")), app("c", listOf("b"))),
        )
        assertTrue(resolved.order.isEmpty())
        assertEquals("缺少依赖 a", resolved.blocked["b"])
        assertEquals("依赖 b 不可用", resolved.blocked["c"])
    }

    @Test
    fun blocksDependencyCycle() {
        val resolved = PluginDependencyGraph.resolve(
            listOf(app("a", listOf("b")), app("b", listOf("a")), app("c")),
        )
        assertEquals(listOf("c"), resolved.order.map { it.id })
        assertEquals("存在循环依赖", resolved.blocked["a"])
        assertEquals("存在循环依赖", resolved.blocked["b"])
    }

    @Test
    fun unblockedModuleStillLoadsWhenOtherIsBlocked() {
        val resolved = PluginDependencyGraph.resolve(
            listOf(app("orphan", listOf("ghost")), app("fine")),
        )
        assertEquals(listOf("fine"), resolved.order.map { it.id })
        assertFalse(resolved.blocked.containsKey("fine"))
    }
}
