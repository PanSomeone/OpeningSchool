package com.openschedule.core.data

import android.content.Context
import com.openschedule.core.model.Timetable
import com.openschedule.core.model.TimetableCollection
import com.openschedule.core.model.uniqueTimetableName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

/**
 * All timetables, plus which one is showing.
 *
 * Writes go to a sibling file first and are then renamed, so a process death
 * mid-write leaves the previous file intact. The single-timetable file used by
 * earlier versions is read once and folded into the collection.
 */
class TimetableStore(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, FILE_NAME)
    private val legacyFile = File(appContext.filesDir, LEGACY_FILE_NAME)

    private val _collection = MutableStateFlow(load())
    val collection: StateFlow<TimetableCollection> = _collection.asStateFlow()

    private val _timetable = MutableStateFlow(activeOf(_collection.value))
    val timetable: StateFlow<Timetable> = _timetable.asStateFlow()

    val active: Timetable get() = _timetable.value

    fun save(timetable: Timetable) {
        val current = _collection.value
        val targetId = current.activeId
            .takeIf { id -> current.items.any { it.id == id } }
            ?: current.items.firstOrNull()?.id
            ?: timetable.id.ifBlank { newId() }
        val normalized = timetable.copy(id = targetId)
        val exists = current.items.any { it.id == targetId }
        val items = if (exists) {
            current.items.map { if (it.id == targetId) normalized else it }
        } else {
            current.items + normalized
        }
        persist(current.copy(activeId = targetId, items = items))
    }

    fun update(transform: (Timetable) -> Timetable) {
        save(transform(active))
    }

    fun add(name: String, timetable: Timetable? = null): Timetable {
        val current = _collection.value
        val created = (timetable ?: Timetable()).copy(
            id = newId(),
            name = uniqueTimetableName(
                name.ifBlank { timetable?.name.orEmpty() },
                current.items,
            ),
        )
        persist(current.copy(activeId = created.id, items = current.items + created))
        return created
    }

    fun switchTo(id: String) {
        val current = _collection.value
        if (id == current.activeId || current.items.none { it.id == id }) return
        persist(current.copy(activeId = id))
    }

    /** Returns false when this is the last timetable, which is kept as a fallback. */
    fun delete(id: String): Boolean {
        val current = _collection.value
        if (current.items.size <= 1) return false
        val remaining = current.items.filterNot { it.id == id }
        if (remaining.size == current.items.size) return false
        val activeId = if (current.activeId == id) remaining.first().id else current.activeId
        persist(current.copy(activeId = activeId, items = remaining))
        return true
    }

    fun rename(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val current = _collection.value
        val unique = uniqueTimetableName(trimmed, current.items.filterNot { it.id == id })
        persist(
            current.copy(items = current.items.map { if (it.id == id) it.copy(name = unique) else it }),
        )
    }

    fun replaceFromJson(raw: String): Result<Timetable> =
        TimetableJson.decode(raw).onSuccess { save(it.copy(id = active.id)) }

    fun exportJson(): String = TimetableJson.encode(active)

    private fun load(): TimetableCollection {
        val fromDisk = if (file.exists()) {
            runCatching { TimetableJson.decodeCollection(file.readText()).getOrThrow() }.getOrNull()
        } else {
            null
        }
        val loaded = fromDisk ?: loadLegacy() ?: TimetableCollection()
        val normalized = normalize(loaded)
        if (fromDisk != normalized || !file.exists()) {
            writeAtomically(TimetableJson.encodeCollection(normalized))
        }
        return normalized
    }

    private fun loadLegacy(): TimetableCollection? {
        if (!legacyFile.exists()) return null
        return runCatching {
            val single = TimetableJson.decode(legacyFile.readText()).getOrThrow()
            TimetableCollection(activeId = single.id, items = listOf(single))
        }.getOrNull()
    }

    private fun normalize(raw: TimetableCollection): TimetableCollection {
        val usedIds = mutableSetOf<String>()
        val items = raw.items.map { timetable ->
            val id = timetable.id.takeIf { it.isNotBlank() && usedIds.add(it) } ?: newId()
            usedIds += id
            timetable.copy(id = id)
        }.ifEmpty {
            listOf(Timetable(id = newId()))
        }
        val activeId = raw.activeId.takeIf { id -> items.any { it.id == id } } ?: items.first().id
        return TimetableCollection(activeId = activeId, items = items)
    }

    private fun persist(collection: TimetableCollection) {
        writeAtomically(TimetableJson.encodeCollection(collection))
        _collection.value = collection
        _timetable.value = activeOf(collection)
    }

    private fun activeOf(collection: TimetableCollection): Timetable =
        collection.items.firstOrNull { it.id == collection.activeId }
            ?: collection.items.firstOrNull()
            ?: Timetable(id = newId())

    private fun writeAtomically(text: String) {
        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        temp.writeText(text)
        if (!temp.renameTo(file)) {
            file.writeText(text)
            temp.delete()
        }
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private companion object {
        const val FILE_NAME = "timetables.json"
        const val LEGACY_FILE_NAME = "timetable.json"
    }
}
