package com.obsidian_north.mediastore.metadata.image

import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.obsidian_north.mediastore.metadata.common.ExtractionStatus
import com.obsidian_north.mediastore.metadata.common.MetadataExtractionResult
import com.obsidian_north.mediastore.metadata.common.MetadataValueUtils
import java.text.SimpleDateFormat
import java.util.Locale

object ExifMetadataExtractor {

  fun extract(filePath: String): MetadataExtractionResult {
    val result = mutableMapOf<String, Any?>()
    val warnings = mutableListOf<String>()

    var exif: ExifInterface? = null
    try {
      exif = ExifInterface(filePath)
    } catch (e: Exception) {
      warnings.add("Could not read EXIF: ${e.message}")
    }

    if (exif == null) {
      return MetadataExtractionResult(result, ExtractionStatus.PARTIAL, warnings)
    }

    extractIdentity(exif, result)
    extractCamera(exif, result)
    extractExposure(exif, result)
    extractDates(exif, result)
    extractGps(exif, result)
    extractDimensions(exif, result)
    extractColorSpace(exif, result)
    extractBitsPerSample(exif, result)

    val status = if (warnings.isNotEmpty()) ExtractionStatus.PARTIAL else ExtractionStatus.COMPLETE
    return MetadataExtractionResult(result, status, warnings)
  }

  private fun extractIdentity(exif: ExifInterface, result: MutableMap<String, Any?>) {
    safeString(exif, ExifInterface.TAG_MAKE)?.let { result["make"] = it }
    safeString(exif, ExifInterface.TAG_MODEL)?.let { result["model"] = it }
    safeString(exif, ExifInterface.TAG_SOFTWARE)?.let { result["software"] = it }
    safeString(exif, ExifInterface.TAG_LENS_MAKE)?.let { result["lensMake"] = it }
    safeString(exif, ExifInterface.TAG_LENS_MODEL)?.let { result["lensModel"] = it }
    safeString(exif, ExifInterface.TAG_IMAGE_DESCRIPTION)?.let { result["imageDescription"] = it }
    safeString(exif, ExifInterface.TAG_ARTIST)?.let { result["artist"] = it }
    safeString(exif, ExifInterface.TAG_COPYRIGHT)?.let { result["copyright"] = it }
  }

  private fun extractCamera(exif: ExifInterface, result: MutableMap<String, Any?>) {
    safeDouble(exif, ExifInterface.TAG_FOCAL_LENGTH)?.let { if (it > 0) result["focalLength"] = it }
    safeInt(exif, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM)?.let { result["focalLength35mm"] = it }
    safeDouble(exif, ExifInterface.TAG_F_NUMBER)?.let { if (it > 0) result["aperture"] = it }
    safeInt(exif, ExifInterface.TAG_SCENE_CAPTURE_TYPE)?.let { result["sceneCaptureType"] = sceneCaptureName(it) }
    safeDouble(exif, ExifInterface.TAG_DIGITAL_ZOOM_RATIO)?.let { result["digitalZoomRatio"] = it }
  }

  private fun extractExposure(exif: ExifInterface, result: MutableMap<String, Any?>) {
    safeInt(exif, ExifInterface.TAG_ISO_SPEED_RATINGS)?.let { result["iso"] = it }
    safeDouble(exif, ExifInterface.TAG_SHUTTER_SPEED_VALUE)?.let { result["shutterSpeed"] = it }
    safeDouble(exif, ExifInterface.TAG_EXPOSURE_TIME)?.let { result["exposureTime"] = it }
    safeInt(exif, ExifInterface.TAG_EXPOSURE_PROGRAM)?.let { result["exposureProgram"] = exposureProgramName(it) }
    safeDouble(exif, ExifInterface.TAG_EXPOSURE_BIAS_VALUE)?.let { result["exposureBias"] = it }
    safeInt(exif, ExifInterface.TAG_METERING_MODE)?.let { result["meteringMode"] = meteringModeName(it) }

    safeInt(exif, ExifInterface.TAG_FLASH)?.let { flashVal ->
      result["flash"] = (flashVal and 1) != 0
      result["flashMode"] = flashModeName(flashVal)
    }

    safeInt(exif, ExifInterface.TAG_WHITE_BALANCE)?.let {
      result["whiteBalance"] = if (it == 0) "Auto" else "Manual"
    }

    safeInt(exif, ExifInterface.TAG_CONTRAST)?.let { result["contrast"] = orderedName(it) }
    safeInt(exif, ExifInterface.TAG_SATURATION)?.let { result["saturation"] = orderedName(it) }
    safeInt(exif, ExifInterface.TAG_SHARPNESS)?.let { result["sharpness"] = orderedName(it) }
    safeDouble(exif, ExifInterface.TAG_COMPRESSED_BITS_PER_PIXEL)?.let { result["compressedBitsPerPixel"] = it }
  }

  private fun extractDates(exif: ExifInterface, result: MutableMap<String, Any?>) {
    safeString(exif, ExifInterface.TAG_DATETIME_ORIGINAL)?.let {
      parseExifDate(it)?.let { ms -> result["dateTimeOriginal"] = ms }
    }
    safeString(exif, ExifInterface.TAG_DATETIME_DIGITIZED)?.let {
      parseExifDate(it)?.let { ms -> result["dateTimeDigitized"] = ms }
    }
    safeString(exif, ExifInterface.TAG_DATETIME)?.let {
      parseExifDate(it)?.let { ms -> result["dateTime"] = ms }
    }
    safeInt(exif, ExifInterface.TAG_ORIENTATION)?.let { result["orientation"] = it }
  }

  private fun extractGps(exif: ExifInterface, result: MutableMap<String, Any?>) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      try {
        val latLong = FloatArray(2)
        if (exif.getLatLong(latLong)) {
          result["gpsLatitude"] = latLong[0].toDouble()
          result["gpsLongitude"] = latLong[1].toDouble()
        }
      } catch (_: Exception) {}
    }
    safeString(exif, ExifInterface.TAG_GPS_ALTITUDE)?.let {
      try { result["gpsAltitude"] = exif.getAltitude(0.0) } catch (_: Exception) {}
    }
    safeString(exif, ExifInterface.TAG_GPS_TIMESTAMP)?.let { tsStr ->
      val ts = tsStr.toIntOrNull()?.toLong()
      val date = safeString(exif, ExifInterface.TAG_GPS_DATESTAMP)
      if (ts != null) {
        val ms = if (date != null) combineGpsDate(date, ts) else ts * 1000L
        result["gpsTimestamp"] = ms
      }
    }
    safeString(exif, ExifInterface.TAG_GPS_PROCESSING_METHOD)?.let { result["gpsProcessingMethod"] = it }
  }

  private fun extractDimensions(exif: ExifInterface, result: MutableMap<String, Any?>) {
    safeInt(exif, ExifInterface.TAG_PIXEL_X_DIMENSION)?.let { result["width"] = it }
    safeInt(exif, ExifInterface.TAG_PIXEL_Y_DIMENSION)?.let { result["height"] = it }
  }

  private fun extractColorSpace(exif: ExifInterface, result: MutableMap<String, Any?>) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      try {
        val cs = exif.getAttributeInt(ExifInterface.TAG_COLOR_SPACE, -1)
        if (cs != -1) result["colorSpace"] = colorSpaceName(cs)
      } catch (_: Exception) {}
    }
  }

  private fun extractBitsPerSample(exif: ExifInterface, result: MutableMap<String, Any?>) {
    safeInt(exif, ExifInterface.TAG_BITS_PER_SAMPLE)?.let { bps ->
      if (bps > 0) result["bitsPerSample"] = bps
    }
  }

  private fun safeString(exif: ExifInterface, tag: String): String? {
    return try { exif.getAttribute(tag)?.takeIf { it.isNotBlank() } } catch (_: Exception) { null }
  }

  private fun safeInt(exif: ExifInterface, tag: String): Int? {
    return try {
      val attr = exif.getAttribute(tag)
      if (attr != null) {
        attr.toIntOrNull() ?: exif.getAttributeInt(tag, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
      } else {
        exif.getAttributeInt(tag, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
      }
    } catch (_: Exception) { null }
  }

  private fun safeDouble(exif: ExifInterface, tag: String): Double? {
    return try {
      val attr = exif.getAttribute(tag)
      if (attr != null) {
        attr.toDoubleOrNull() ?: exif.getAttributeDouble(tag, Double.NaN).takeIf { !it.isNaN() }
      } else {
        exif.getAttributeDouble(tag, Double.NaN).takeIf { !it.isNaN() }
      }
    } catch (_: Exception) { null }
  }

  private fun parseExifDate(value: String): Long? {
    return try {
      val fmt = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
      fmt.parse(value)?.time
    } catch (_: Exception) { null }
  }

  private fun combineGpsDate(date: String, seconds: Long): Long {
    return try {
      val fmt = SimpleDateFormat("yyyy:MM:dd", Locale.US)
      val base = fmt.parse(date)?.time ?: 0L
      base + seconds * 1000L
    } catch (_: Exception) { seconds * 1000L }
  }

  private fun colorSpaceName(value: Int): String = when (value) {
    1 -> "sRGB"
    2 -> "AdobeRGB"
    else -> "Uncalibrated"
  }

  private fun exposureProgramName(v: Int): String = when (v) {
    0 -> "Undefined"
    1 -> "Manual"
    2 -> "Normal"
    3 -> "Aperture"
    4 -> "Shutter"
    5 -> "Creative"
    6 -> "Action"
    7 -> "Portrait"
    8 -> "Landscape"
    else -> "Unknown"
  }

  private fun meteringModeName(v: Int): String = when (v) {
    0 -> "Unknown"
    1 -> "Average"
    2 -> "Center"
    3 -> "Spot"
    4 -> "MultiSpot"
    5 -> "Pattern"
    6 -> "Partial"
    255 -> "Other"
    else -> "Unknown"
  }

  private fun flashModeName(v: Int): String = when (v and 0x1F) {
    0x00 -> "No Flash"
    0x01 -> "Flash"
    0x05 -> "Flash, No Return"
    0x07 -> "Flash, Return"
    0x09 -> "Flash, Compulsory"
    0x0D -> "Flash, Compulsory, No Return"
    0x0F -> "Flash, Compulsory, Return"
    0x10 -> "No Flash Function"
    0x18 -> "Flash, Auto"
    0x19 -> "Flash, Auto, No Return"
    0x1D -> "Flash, Auto, No Return, Red-eye"
    0x1F -> "Flash, Auto, Return, Red-eye"
    else -> "Unknown"
  }

  private fun sceneCaptureName(v: Int): String = when (v) {
    0 -> "Standard"
    1 -> "Landscape"
    2 -> "Portrait"
    3 -> "Night"
    else -> "Unknown"
  }

  private fun orderedName(v: Int): String = when (v) {
    0 -> "Normal"
    1 -> "Soft"
    2 -> "Hard"
    else -> "Unknown"
  }
}
