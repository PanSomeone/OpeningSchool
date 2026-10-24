package com.openschedule.core.data

import com.openschedule.core.model.Timetable
import com.openschedule.core.model.TimetableCollection
import kotlinx.serialization.json.Json

object TimetableJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
    }

    fun encode(timetable: Timetable): String = json.encodeToString(Timetable.serializer(), timetable)

    fun decode(raw: String): Result<Timetable> = runCatching {
        json.decodeFromString(Timetable.serializer(), raw)
    }

    fun encodeCollection(collection: TimetableCollection): String =
        json.encodeToString(TimetableCollection.serializer(), collection)

    fun decodeCollection(raw: String): Result<TimetableCollection> = runCatching {
        json.decodeFromString(TimetableCollection.serializer(), raw)
    }
}
