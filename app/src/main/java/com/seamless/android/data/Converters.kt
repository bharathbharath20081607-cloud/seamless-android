package com.seamless.android.data

import androidx.room.TypeConverter
import com.seamless.android.model.BitchatMessageType
import com.seamless.android.model.DeliveryStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromStringList(value: String?): List<String>? {
        if (value == null) return null
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun toStringList(list: List<String>?): String? {
        return gson.toJson(list)
    }
    
    @TypeConverter
    fun toDeliveryStatus(value: String): DeliveryStatus {
        return gson.fromJson(value, DeliveryStatus::class.java) ?: DeliveryStatus.Sent
    }

    @TypeConverter
    fun fromDeliveryStatus(status: DeliveryStatus): String {
        return gson.toJson(status)
    }

    @TypeConverter
    fun toMessageType(value: String): BitchatMessageType {
        return try {
            BitchatMessageType.valueOf(value)
        } catch (e: Exception) {
            BitchatMessageType.Message // Default
        }
    }

    @TypeConverter
    fun fromMessageType(type: BitchatMessageType): String {
        return type.name
    }
}
