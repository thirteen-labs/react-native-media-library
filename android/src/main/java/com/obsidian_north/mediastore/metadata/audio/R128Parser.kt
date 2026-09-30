package com.obsidian_north.mediastore.metadata.audio

/**
 * R128 / ReplayGain normalization. The native layer owns the conversion so
 * JS never needs to understand the underlying tag encoding.
 *
 * R128 tags (`R128_TRACK_GAIN`, `R128_ALBUM_GAIN`) are Q8.8 fixed-point
 * integers where 256 = 1 dB (Opus/Vorbis convention). ReplayGain 1.0 tags
 * (`REPLAYGAIN_TRACK_GAIN`, ...) are human-readable dB strings ("-5.42 dB").
 */
object R128Parser {

  /** Q8.8 fixed-point step: 256 units per dB. */
  const val Q8_8_PER_DB = 256.0

  /**
   * Convert a raw R128 tag value to dB.
   * Accepts "−512", "−512 dB", or already-normalized floats ("−2.0").
   */
  fun r128RawToDb(raw: String?): Double? {
    if (raw.isNullOrBlank()) return null
    val cleaned = raw.trim().replace(Regex("(?i)\\s*dB\\s*$"), "").trim()
    if (cleaned.isEmpty()) return null
    val asDouble = cleaned.toDoubleOrNull() ?: return null
    // Heuristic: integer magnitudes >= 64 are Q8.8 raw values; small
    // magnitudes / fractional values are already dB.
    return if (!cleaned.contains('.') && !cleaned.contains('e', ignoreCase = true) &&
      kotlin.math.abs(asDouble) >= 64
    ) {
      asDouble / Q8_8_PER_DB
    } else {
      asDouble
    }
  }

  fun r128RawToDb(raw: Number?): Double? {
    if (raw == null) return null
    val v = raw.toDouble()
    if (!v.isFinite()) return null
    return if (v == kotlin.math.floor(v) && kotlin.math.abs(v) >= 64) v / Q8_8_PER_DB else v
  }

  /** Parse a ReplayGain 1.0 gain string ("−5.42 dB") to dB float. */
  fun parseReplayGainDb(raw: String?): Double? {
    if (raw.isNullOrBlank()) return null
    val cleaned = raw.trim().replace(Regex("(?i)\\s*dB\\s*$"), "").trim()
    if (cleaned.isEmpty()) return null
    return cleaned.toDoubleOrNull()?.takeIf { it.isFinite() }
  }

  /** Parse a ReplayGain peak string ("0.98") to linear float. */
  fun parsePeak(raw: String?): Double? {
    if (raw.isNullOrBlank()) return null
    return raw.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
  }
}
