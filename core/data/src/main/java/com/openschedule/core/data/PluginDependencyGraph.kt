package com.openschedule.core.data

/**
 * Resolves the load order for API v2 app modules (the "plugin tree").
 *
 * - Modules with a missing or unusable dependency are blocked, and everything
 *   that (transitively) depends on a blocked module is blocked as well.
 * - Surviving modules are topologically sorted: dependencies always load
 *   before their dependents.
 * - A dependency cycle blocks every module involved in it.
 */
object PluginDependencyGraph {

    data class Resolved(
        /** Loadable modules in dependency order (dependencies first). */
        val order: List<ModuleManifest>,
        /** pluginId -> human readable reason why it cannot load. */
        val blocked: Map<String, String>,
    )

    fun resolve(manifests: List<ModuleManifest>): Resolved {
        val byId = manifests.associateBy { it.id }
        val blocked = linkedMapOf<String, String>()

        // Propagate missing/blocked dependencies until the set is stable.
        var changed = true
        while (changed) {
            changed = false
            for (manifest in manifests) {
                if (manifest.id in blocked) continue
                val unusable = manifest.requires.firstOrNull { dependency ->
                    dependency !in byId || dependency in blocked
                }
                if (unusable != null) {
                    val reason = if (unusable in byId) {
                        "依赖 $unusable 不可用"
                    } else {
                        "缺少依赖 $unusable"
                    }
                    blocked[manifest.id] = reason
                    changed = true
                }
            }
        }

        val remaining = manifests.filter { it.id !in blocked }.toMutableList()
        val order = mutableListOf<ModuleManifest>()
        val loaded = mutableSetOf<String>()

        // Kahn's algorithm; sizes are tiny so the quadratic rescan is fine.
        while (remaining.isNotEmpty()) {
            val ready = remaining.firstOrNull { candidate ->
                candidate.requires.all { it in loaded }
            }
            if (ready == null) {
                // Every survivor needs another survivor: a cycle.
                remaining.forEach { manifest ->
                    blocked[manifest.id] = "存在循环依赖"
                }
                break
            }
            remaining.remove(ready)
            loaded += ready.id
            order += ready
        }
        return Resolved(order, blocked)
    }
}
