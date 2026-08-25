package com.obsidian_north.mediastore.metadata.common

import java.text.SimpleDateFormat
import java.util.Locale

object MetadataValueUtils {

  fun parseIntOrNull(value: Any?): Int? = when (value) {
    is Int -> value
    is Long -> value.toInt()
    is Double -> value.toInt()
    is Float -> value.toInt()
    is String -> value.trim().toIntOrNull()
    else -> null
  }

  fun parseLongOrNull(value: Any?): Long? = when (value) {
    is Long -> value
    is Int -> value.toLong()
    is Double -> value.toLong()
    is Float -> value.toLong()
    is String -> value.trim().toLongOrNull()
    else -> null
  }

  fun parseFloatOrNull(value: Any?): Float? = when (value) {
    is Float -> value
    is Double -> value.toFloat()
    is Int -> value.toFloat()
    is Long -> value.toFloat()
    is String -> value.trim().toFloatOrNull()
    else -> null
  }

  fun parseDoubleOrNull(value: Any?): Double? = when (value) {
    is Double -> value
    is Float -> value.toDouble()
    is Int -> value.toDouble()
    is Long -> value.toDouble()
    is String -> value.trim().toDoubleOrNull()
    else -> null
  }

  fun parseBooleanOrNull(value: Any?): Boolean? = when (value) {
    is Boolean -> value
    is Int -> value != 0
    is Long -> value != 0L
    is String -> when (value.trim().lowercase()) {
      "true", "1", "yes" -> true
      "false", "0", "no" -> false
      else -> null
    }
    else -> null
  }

  private val DATE_FORMATS = listOf(
    SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US),
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
    SimpleDateFormat("yyyy-MM-dd", Locale.US),
  )

  fun parseDateOrNull(value: Any?): Long? {
    val str = when (value) {
      is Long -> return value
      is Number -> return value.toLong()
      is String -> value.trim()
      else -> return null
    }
    if (str.isEmpty()) return null
    str.toLongOrNull()?.let { return it }
    for (fmt in DATE_FORMATS) {
      try { fmt.parse(str)?.time?.let { return it } } catch (_: Exception) {}
    }
    return null
  }

  fun normalizeString(value: Any?): String? = when (value) {
    is String -> value.trim().ifEmpty { null }
    null -> null
    else -> value.toString().trim().ifEmpty { null }
  }

  fun isBlankOrUnknown(value: String?): Boolean {
    if (value.isNullOrBlank()) return true
    return value.lowercase() in setOf("unknown", "n/a", "na", "none", "null", "undefined", "-1", "")
  }

  fun normalizeSampleRate(value: Any?): Int? {
    val raw = parseDoubleOrNull(value) ?: return null
    val hz = if (raw > 1000) raw else raw * 1000
    return hz.toInt().takeIf { it > 0 }
  }

  fun normalizeBitrate(value: Any?): Int? {
    val raw = parseLongOrNull(value) ?: return null
    return when {
      raw > 1_000_000 -> raw.toInt()
      raw > 0 -> (raw * 1000).toInt()
      else -> null
    }
  }

  fun normalizeDurationMs(value: Any?): Long? {
    val raw = parseLongOrNull(value) ?: return null
    return when {
      raw < 0 -> null
      raw < 1_000_000 -> raw
      else -> raw / 1000
    }
  }
}
