package com.obsidian_north.mediastore.metadata.image

import android.os.Build
import com.obsidian_north.mediastore.metadata.common.ExtractionStatus
import com.obsidian_north.mediastore.metadata.common.MetadataExtractionResult
import com.obsidian_north.mediastore.metadata.common.MetadataValueUtils

object ImageMetadataExtractor {

  fun extract(filePath: String, mimeType: String): MetadataExtractionResult {
    val result = mutableMapOf<String, Any?>()
    val warnings = mutableListOf<String>()

    result["format"] = deriveImageFormat(mimeType, filePath)

    extractDimensions(filePath, result, warnings)
    val exifResult = ExifMetadataExtractor.extract(filePath)
    val exifMap = exifResult.metadata
    val exifWarnings = exifResult.warnings

    val imageMap = mutableMapOf<String, Any?>()
    imageMap["format"] = result["format"]

    (exifMap["width"] as? Int)?.let { imageMap["width"] = it }
    (exifMap["height"] as? Int)?.let { imageMap["height"] = it }
    (result["width"] as? Int)?.let { imageMap.putIfAbsent("width", it) }
    (result["height"] as? Int)?.let { imageMap.putIfAbsent("height", it) }

    (exifMap["bitsPerSample"] as? Int)?.let { imageMap["bitsPerSample"] = it }
    (exifMap["colorSpace"] as? String)?.let { imageMap["colorSpace"] = it }

    val exifSubMap = mutableMapOf<String, Any?>()
    val exifFields = listOf(
      "make", "model", "software", "lensMake", "lensModel", "imageDescription",
      "artist", "copyright", "dateTimeOriginal", "dateTimeDigitized", "orientation",
      "aperture", "iso", "shutterSpeed", "exposureTime", "exposureProgram",
      "exposureBias", "meteringMode", "flash", "flashMode", "whiteBalance",
      "focalLength", "focalLength35mm", "sceneCaptureType", "contrast",
      "saturation", "sharpness", "digitalZoomRatio", "compressedBitsPerPixel",
      "gpsLatitude", "gpsLongitude", "gpsAltitude", "gpsTimestamp",
      "gpsProcessingMethod", "pixelXDimension", "pixelYDimension",
    )
    for (field in exifFields) {
      exifMap[field]?.let { exifSubMap[field] = it }
    }

    if (exifSubMap.isNotEmpty()) imageMap["exif"] = exifSubMap

    extractLocationInfo(exifMap, imageMap, warnings)

    warnings.addAll(exifWarnings)

    result["image"] = imageMap
    result.remove("width")
    result.remove("height")

    val status = if (warnings.isNotEmpty()) ExtractionStatus.PARTIAL else ExtractionStatus.COMPLETE
    return MetadataExtractionResult(result, status, warnings)
  }

  private fun extractDimensions(
    filePath: String,
    result: MutableMap<String, Any?>,
    warnings: MutableList<String>,
  ) {
    try {
      val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
      android.graphics.BitmapFactory.decodeFile(filePath, opts)
      if (opts.outWidth > 0) result["width"] = opts.outWidth
      if (opts.outHeight > 0) result["height"] = opts.outHeight
    } catch (e: Exception) {
      warnings.add("BitmapFactory dimension extraction failed: ${e.message}")
    }
  }

  private fun extractLocationInfo(
    exifMap: Map<String, Any?>,
    imageMap: MutableMap<String, Any?>,
    warnings: MutableList<String>,
  ) {
    val hasLat = exifMap.containsKey("gpsLatitude")
    val hasLon = exifMap.containsKey("gpsLongitude")

    val location = mutableMapOf<String, Any?>()
    location["latitude"] = exifMap["gpsLatitude"]
    location["longitude"] = exifMap["gpsLongitude"]
    location["available"] = hasLat && hasLon
    location["redacted"] = false

    if (hasLat && exifMap["gpsLatitude"] == null) {
      location["redacted"] = true
      location["available"] = true
      warnings.add("GPS location available but redacted by Android (ACCESS_MEDIA_LOCATION required)")
    }

    if (location["available"] == true || location["redacted"] == true) {
      imageMap["location"] = location
    }
  }

  private fun deriveImageFormat(mimeType: String, filePath: String): String {
    val ext = filePath.substringAfterLast('.', "").lowercase()
    val fromExt = when (ext) {
      "jpg", "jpeg" -> "jpeg"
      "png" -> "png"
      "heic", "heif" -> "heic"
      "gif" -> "gif"
      "webp" -> "webp"
      "bmp" -> "bmp"
      "tif", "tiff" -> "tiff"
      "avif" -> "avif"
      "svg" -> "svg"
      else -> null
    }
    if (fromExt != null) return fromExt
    return when {
      mimeType.contains("jpeg") -> "jpeg"
      mimeType.contains("png") -> "png"
      mimeType.contains("heic") || mimeType.contains("heif") -> "heic"
      mimeType.contains("gif") -> "gif"
      mimeType.contains("webp") -> "webp"
      mimeType.contains("bmp") -> "bmp"
      mimeType.contains("tiff") -> "tiff"
      mimeType.contains("avif") -> "avif"
      else -> "unknown"
    }
  }
}
