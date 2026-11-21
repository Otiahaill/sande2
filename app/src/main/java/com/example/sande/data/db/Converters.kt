package com.example.sande.data.db

import androidx.room.TypeConverter
import com.example.sande.data.entities.MovementType

/**
 * Room type converters for values that SQLite cannot store natively.
 *
 * Without a converter for [MovementType], Room's annotation processor fails with
 * `MissingType: Element 'com.example.sande.data.db.AppDatabase' references a type
 * that is not present` when it tries to generate the schema for `movements`.
 */
class Converters {

    @TypeConverter
    fun fromMovementType(type: MovementType?): String? = type?.name

    @TypeConverter
    fun toMovementType(value: String?): MovementType? =
        value?.let { name ->
            MovementType.entries.firstOrNull { it.name == name }
                ?: MovementType.entries.firstOrNull { it.code == name }
        }
}
