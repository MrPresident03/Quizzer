package com.example.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return value.joinToString("##")
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return value.split("##")
    }
}
