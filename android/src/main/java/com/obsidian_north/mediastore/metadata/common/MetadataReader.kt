package com.obsidian_north.mediastore.metadata.common

import android.database.Cursor
import android.media.MediaFormat
import android.os.Build

sealed class MetadataValue {
  data class StringVal(val value: String) : MetadataValue()
  data class IntVal(val value: Int) : MetadataValue()
  data class LongVal(val value: Long) : MetadataValue()
  data class DoubleVal(val value: Double) : MetadataValue()
  data class FloatVal(val value: Float) : MetadataValue()
  data class BoolVal(val value: Boolean) : MetadataValue()
  data class BlobVal(val value: ByteArray) : MetadataValue()
  data object NullVal : MetadataValue()

  fun asString(): String? = (this as? StringVal)?.value
  fun asInt(): Int? = when (this) {
    is IntVal -> value
    is LongVal -> value.toInt()
    is DoubleVal -> value.toInt()
    is FloatVal -> value.toInt()
    is StringVal -> value.toIntOrNull()
    else -> null
  }
  fun asLong(): Long? = when (this) {
    is LongVal -> value
    is IntVal -> value.toLong()
    is DoubleVal -> value.toLong()
    is FloatVal -> value.toLong()
    is StringVal -> value.toLongOrNull()
    else -> null
  }
  fun asDouble(): Double? = when (this) {
    is DoubleVal -> value
    is FloatVal -> value.toDouble()
    is IntVal -> value.toDouble()
    is LongVal -> value.toDouble()
    is StringVal -> value.toDoubleOrNull()
    else -> null
  }
  fun asBoolean(): Boolean? = when (this) {
    is BoolVal -> value
    is IntVal -> value != 0
    is LongVal -> value != 0L
    is StringVal -> MetadataValueUtils.parseBooleanOrNull(value)
    else -> null
  }
  fun isNull(): Boolean = this is NullVal
}

interface MetadataReader {
  fun get(key: String): MetadataValue
  fun has(key: String): Boolean
  fun keys(): Set<String>
}

class MapMetadataReader(private val map: Map<String, Any?>) : MetadataReader {
  override fun get(key: String): MetadataValue {
    val v = map[key] ?: return MetadataValue.NullVal
    return when (v) {
      is String -> MetadataValue.StringVal(v)
      is Int -> MetadataValue.IntVal(v)
      is Long -> MetadataValue.LongVal(v)
      is Double -> MetadataValue.DoubleVal(v)
      is Float -> MetadataValue.FloatVal(v)
      is Boolean -> MetadataValue.BoolVal(v)
      is ByteArray -> MetadataValue.BlobVal(v)
      else -> MetadataValue.StringVal(v.toString())
    }
  }
  override fun has(key: String): Boolean = map.containsKey(key)
  override fun keys(): Set<String> = map.keys
}

class CursorMetadataReader(private val cursor: Cursor) : MetadataReader {
  private val columnIndexCache = mutableMapOf<String, Int>()

  private fun columnIndex(key: String): Int {
    return columnIndexCache.getOrPut(key) {
      val idx = cursor.getColumnIndex(key)
      if (idx >= 0) idx else -1
    }
  }

  override fun get(key: String): MetadataValue {
    val idx = columnIndex(key)
    if (idx < 0) return MetadataValue.NullVal
    if (cursor.isNull(idx)) return MetadataValue.NullVal
    return try {
      val type = cursor.getType(idx)
      when (type) {
        Cursor.FIELD_TYPE_NULL -> MetadataValue.NullVal
        Cursor.FIELD_TYPE_INTEGER -> MetadataValue.LongVal(cursor.getLong(idx))
        Cursor.FIELD_TYPE_FLOAT -> MetadataValue.DoubleVal(cursor.getDouble(idx))
        Cursor.FIELD_TYPE_STRING -> MetadataValue.StringVal(cursor.getString(idx) ?: "")
        Cursor.FIELD_TYPE_BLOB -> MetadataValue.BlobVal(cursor.getBlob(idx) ?: ByteArray(0))
        else -> MetadataValue.NullVal
      }
    } catch (_: Exception) {
      MetadataValue.NullVal
    }
  }

  override fun has(key: String): Boolean = columnIndex(key) >= 0
  override fun keys(): Set<String> {
    val set = mutableSetOf<String>()
    for (i in 0 until cursor.columnCount) {
      cursor.getColumnName(i)?.let { set.add(it) }
    }
    return set
  }
}

class MediaFormatMetadataReader(private val format: MediaFormat) : MetadataReader {
  override fun get(key: String): MetadataValue {
    if (!format.containsKey(key)) return MetadataValue.NullVal
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val type = format.getValueTypeForKey(key)
        when (type) {
          MediaFormat.TYPE_INTEGER -> MetadataValue.IntVal(format.getInteger(key))
          MediaFormat.TYPE_LONG -> MetadataValue.LongVal(format.getLong(key))
          MediaFormat.TYPE_FLOAT -> MetadataValue.FloatVal(format.getFloat(key))
          MediaFormat.TYPE_STRING -> MetadataValue.StringVal(format.getString(key) ?: "")
          else -> MetadataValue.NullVal
        }
      } else {
        try { MetadataValue.IntVal(format.getInteger(key)) } catch (_: Exception) {
          try { MetadataValue.LongVal(format.getLong(key)) } catch (_: Exception) {
            try { MetadataValue.FloatVal(format.getFloat(key)) } catch (_: Exception) {
              try { MetadataValue.StringVal(format.getString(key) ?: "") } catch (_: Exception) {
                MetadataValue.NullVal
              }
            }
          }
        }
      }
    } catch (_: Exception) {
      MetadataValue.NullVal
    }
  }

  override fun has(key: String): Boolean = format.containsKey(key)
  override fun keys(): Set<String> = format.keys
}

class CompositeMetadataReader(private val readers: List<MetadataReader>) : MetadataReader {
  override fun get(key: String): MetadataValue {
    for (reader in readers) {
      if (reader.has(key)) {
        val v = reader.get(key)
        if (!v.isNull()) return v
      }
    }
    return MetadataValue.NullVal
  }

  override fun has(key: String): Boolean = readers.any { it.has(key) }
  override fun keys(): Set<String> = readers.flatMap { it.keys() }.toSet()
}
