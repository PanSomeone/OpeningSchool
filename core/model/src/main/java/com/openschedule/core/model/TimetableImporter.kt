package com.openschedule.core.model

import kotlinx.serialization.Serializable

/**
 * One field an importer needs from the user.
 *
 * [secret] fields are kept out of logs and are never written to disk by the
 * host; an importer that needs to remember them owns that decision.
 */
@Serializable
data class AdapterConfigField(
    val key: String,
    val label: String,
    val placeholder: String = "",
    val secret: Boolean = false,
    val required: Boolean = true,
    val defaultValue: String = "",
)

/**
 * A source of timetable data.
 *
 * Built-in adapters implement this directly. Third-party adapters are wrapped
 * into the same interface by the host, so the import screen never needs to know
 * which kind it is talking to.
 */
interface TimetableImporter {
    val id: String
    val name: String
    val description: String
    val configFields: List<AdapterConfigField>

    suspend fun import(config: Map<String, String>): ImportResult
}

sealed interface ImportResult {
    data class Success(
        val timetable: Timetable,
        val warnings: List<String> = emptyList(),
    ) : ImportResult

    data class Failure(val message: String) : ImportResult
}
