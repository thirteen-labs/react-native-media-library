package com.obsidian_north.mediastore.utils

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@Deprecated(
  message = "Use MetadataService + dedicated Audio/Video/Image extractors with Media3 Inspector. " +
    "This monolithic extractor is kept for backward compat and will be removed in 4.0.",
  replaceWith = ReplaceWith("com.obsidian_north.mediastore.metadata.MetadataService")
)
object MediaStoreMetadataExtractor {

  fun extract(mediaType: String, filePath: String, mimeType: String): Map<String, Any?> {
    val result = mutableMapOf<String, Any?>()
    result["mediaType"] = mediaType
    result["mimeType"] = mimeType
    try {
      result["fileSize"] = File(filePath).length()
    } catch (_: Exception) {
      result["fileSize"] = 0L
    }
    try {
      when (mediaType) {
        "audio", "video" -> extractAv(mediaType, filePath, mimeType, result)
        "image" -> extractImage(filePath, mimeType, result)
        "document" -> extractDocument(filePath, mimeType, result)
      }
    } catch (_: Exception) {
    }
    return result
  }

  // ---------------- Audio / Video ----------------

  private fun extractAv(mediaType: String, filePath: String, mimeType: String, result: MutableMap<String, Any?>) {
    val audioMap = mutableMapOf<String, Any?>()
    val videoMap = mutableMapOf<String, Any?>()
    var rootDurationMs: Number? = null
    try {
      val extractor = MediaExtractor()
      extractor.setDataSource(filePath)
      for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val trackMime = format.getString(MediaFormat.KEY_MIME) ?: continue
        if (trackMime.startsWith("audio/")) {
          fillAudioFormat(format, audioMap)
          if (audioMap["durationMs"] != null) rootDurationMs = audioMap["durationMs"] as? Number
        } else if (trackMime.startsWith("video/")) {
          fillVideoFormat(format, videoMap)
          if (rootDurationMs == null && videoMap["durationMs"] != null) rootDurationMs = videoMap["durationMs"] as? Number
        }
      }
      extractor.release()
    } catch (_: Exception) {
    }

    if (mediaType == "video") {
      try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(filePath)
        val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        if (!rot.isNullOrBlank()) { val r = rot.toIntOrNull(); if (r != null && r != 0) videoMap["rotation"] = r }
        retriever.release()
      } catch (_: Exception) {
      }
    }

    if (rootDurationMs != null) result["durationMs"] = rootDurationMs
    result["containerFormat"] = deriveContainer(filePath, mimeType)
    if (audioMap.isNotEmpty()) result["audio"] = audioMap
    if (videoMap.isNotEmpty()) result["video"] = videoMap
  }

  private fun fillAudioFormat(format: MediaFormat, map: MutableMap<String, Any?>) {
    val mime = format.getString(MediaFormat.KEY_MIME) ?: return
    map["codec"] = normalizeCodec(mime)
    map["codecMime"] = mime
    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) map["bitrate"] = format.getInteger(MediaFormat.KEY_BIT_RATE)
    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) map["sampleRate"] = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
      val ch = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
      map["channels"] = ch
      channelLayout(ch)?.let { map["channelLayout"] = it }
    }
    if (format.containsKey(MediaFormat.KEY_DURATION)) map["durationMs"] = format.getLong(MediaFormat.KEY_DURATION) / 1000L
    if (format.containsKey("bits-per-sample")) map["bitsPerSample"] = format.getInteger("bits-per-sample")
    if (format.containsKey(MediaFormat.KEY_LANGUAGE)) map["language"] = format.getString(MediaFormat.KEY_LANGUAGE)
    if (format.containsKey(MediaFormat.KEY_PROFILE)) map["codecProfile"] = format.getInteger(MediaFormat.KEY_PROFILE)
  }

  private fun fillVideoFormat(format: MediaFormat, map: MutableMap<String, Any?>) {
    val mime = format.getString(MediaFormat.KEY_MIME) ?: return
    map["codec"] = normalizeCodec(mime)
    map["codecMime"] = mime
    if (format.containsKey(MediaFormat.KEY_PROFILE)) map["profile"] = format.getInteger(MediaFormat.KEY_PROFILE)
    if (format.containsKey(MediaFormat.KEY_LEVEL)) map["level"] = format.getInteger(MediaFormat.KEY_LEVEL)
    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) map["bitrate"] = format.getInteger(MediaFormat.KEY_BIT_RATE)
    if (format.containsKey(MediaFormat.KEY_WIDTH)) map["width"] = format.getInteger(MediaFormat.KEY_WIDTH)
    if (format.containsKey(MediaFormat.KEY_HEIGHT)) map["height"] = format.getInteger(MediaFormat.KEY_HEIGHT)
    val frameRate = format.getNumberAsDouble(MediaFormat.KEY_FRAME_RATE) ?: format.getNumberAsDouble("capture-framerate")
    if (frameRate != null) map["frameRate"] = frameRate
    if (format.containsKey(MediaFormat.KEY_DURATION)) map["durationMs"] = format.getLong(MediaFormat.KEY_DURATION) / 1000L
    if (format.containsKey(MediaFormat.KEY_COLOR_STANDARD)) map["colorStandard"] = format.getInteger(MediaFormat.KEY_COLOR_STANDARD)
    if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) map["colorTransfer"] = format.getInteger(MediaFormat.KEY_COLOR_TRANSFER)
    if (format.containsKey(MediaFormat.KEY_LANGUAGE)) map["language"] = format.getString(MediaFormat.KEY_LANGUAGE)
  }

  private fun MediaFormat.getNumberAsDouble(key: String): Double? = when (getValueTypeForKey(key)) {
    MediaFormat.TYPE_INTEGER -> getInteger(key).toDouble()
    MediaFormat.TYPE_LONG -> getLong(key).toDouble()
    MediaFormat.TYPE_FLOAT -> getFloat(key).toDouble()
    else -> null
  }

  private fun channelLayout(channels: Int): String? = when (channels) {
    1 -> "mono"
    2 -> "stereo"
    6 -> "5.1"
    8 -> "7.1"
    else -> null
  }

  private fun normalizeCodec(mime: String): String = when (mime) {
    "audio/mpeg" -> "mp3"
    "audio/mp4a-latm" -> "aac"
    "audio/opus" -> "opus"
    "audio/flac" -> "flac"
    "audio/vorbis" -> "vorbis"
    "audio/amr-nb", "audio/amr-wb", "audio/amr" -> "amr"
    "video/avc" -> "h264"
    "video/hevc" -> "hevc"
    "video/x-vnd.on2.vp9" -> "vp9"
    "video/av01" -> "av1"
    "video/mp4v-es" -> "mpeg4"
    else -> mime.substringAfter("/").substringBefore(";")
  }

  private fun deriveContainer(filePath: String, mimeType: String): String {
    val ext = filePath.substringAfterLast('.', "").lowercase()
    return when {
      ext in listOf("mp4", "m4a", "m4v", "mov", "3gp") -> "mp4"
      ext == "mkv" -> "mkv"
      ext == "webm" -> "webm"
      ext == "mp3" -> "mp3"
      ext == "aac" -> "aac"
      ext == "flac" -> "flac"
      ext == "ogg" -> "ogg"
      ext == "wav" -> "wav"
      ext == "avi" -> "avi"
      ext == "m4p" -> "mp4"
      else -> mimeType.substringAfter("/").substringBefore(";").ifEmpty { "unknown" }
    }
  }

  // ---------------- Image ----------------

  private fun extractImage(filePath: String, mimeType: String, result: MutableMap<String, Any?>) {
    val imageMap = mutableMapOf<String, Any?>()
    imageMap["format"] = deriveImageFormat(mimeType, filePath)

    var exif: ExifInterface? = null
    try { exif = ExifInterface(filePath) } catch (_: Exception) { }

    var width: Int? = null
    var height: Int? = null
    exif?.let {
      val w = safeExifInt(it, ExifInterface.TAG_PIXEL_X_DIMENSION)
      val h = safeExifInt(it, ExifInterface.TAG_PIXEL_Y_DIMENSION)
      if (w != null && w > 0) width = w
      if (h != null && h > 0) height = h
    }
    if (width == null || height == null) {
      try {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(filePath, opts)
        if (opts.outWidth > 0) width = opts.outWidth
        if (opts.outHeight > 0) height = opts.outHeight
      } catch (_: Exception) { }
    }
    width?.let { imageMap["width"] = it }
    height?.let { imageMap["height"] = it }

    exif?.let {
      safeExifInt(it, ExifInterface.TAG_BITS_PER_SAMPLE)?.let { bps -> if (bps > 0) imageMap["bitsPerSample"] = bps }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        try {
          val cs = it.getAttributeInt(ExifInterface.TAG_COLOR_SPACE, -1)
          if (cs != -1) imageMap["colorSpace"] = colorSpaceName(cs)
        } catch (_: Exception) { }
      }
    }

    val exifMap = buildExif(exif)
    if (exifMap.isNotEmpty()) imageMap["exif"] = exifMap

    result["image"] = imageMap
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
      else -> "unknown"
    }
  }

  private fun colorSpaceName(value: Int): String = when (value) {
    1 -> "sRGB"
    2 -> "AdobeRGB"
    else -> "Uncalibrated"
  }

  private fun buildExif(exif: ExifInterface?): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    if (exif == null) return map
    fun str(tag: String) = exif.getAttribute(tag)?.takeIf { it.isNotBlank() }
    fun int(tag: String): Int? {
      val s = str(tag)
      if (s != null) return s.toIntOrNull()
      return try { val v = exif.getAttributeInt(tag, Int.MIN_VALUE); if (v == Int.MIN_VALUE) null else v } catch (_: Exception) { null }
    }
    fun dbl(tag: String): Double? {
      if (str(tag) == null) return null
      return try { exif.getAttributeDouble(tag, 0.0) } catch (_: Exception) { null }
    }

    str(ExifInterface.TAG_MAKE)?.let { map["make"] = it }
    str(ExifInterface.TAG_MODEL)?.let { map["model"] = it }
    str(ExifInterface.TAG_SOFTWARE)?.let { map["software"] = it }
    str(ExifInterface.TAG_LENS_MAKE)?.let { map["lensMake"] = it }
    str(ExifInterface.TAG_LENS_MODEL)?.let { map["lensModel"] = it }
    str(ExifInterface.TAG_IMAGE_DESCRIPTION)?.let { map["imageDescription"] = it }
    str(ExifInterface.TAG_ARTIST)?.let { map["artist"] = it }
    str(ExifInterface.TAG_COPYRIGHT)?.let { map["copyright"] = it }

    str(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { parseExifDate(it)?.let { ms -> map["dateTimeOriginal"] = ms } }
    str(ExifInterface.TAG_DATETIME_DIGITIZED)?.let { parseExifDate(it)?.let { ms -> map["dateTimeDigitized"] = ms } }

    str(ExifInterface.TAG_ORIENTATION)?.let { it.toIntOrNull()?.let { v -> map["orientation"] = v } }
    dbl(ExifInterface.TAG_F_NUMBER)?.let { if (it > 0) map["aperture"] = it }
    int(ExifInterface.TAG_ISO_SPEED_RATINGS)?.let { map["iso"] = it }
    dbl(ExifInterface.TAG_SHUTTER_SPEED_VALUE)?.let { map["shutterSpeed"] = it }
    dbl(ExifInterface.TAG_EXPOSURE_TIME)?.let { map["exposureTime"] = it }
    int(ExifInterface.TAG_EXPOSURE_PROGRAM)?.let { map["exposureProgram"] = exposureProgramName(it) }
    dbl(ExifInterface.TAG_EXPOSURE_BIAS_VALUE)?.let { map["exposureBias"] = it }
    int(ExifInterface.TAG_METERING_MODE)?.let { map["meteringMode"] = meteringModeName(it) }

    int(ExifInterface.TAG_FLASH)?.let { flashVal ->
      map["flash"] = (flashVal and 1) != 0
      map["flashMode"] = flashModeName(flashVal)
    }

    int(ExifInterface.TAG_WHITE_BALANCE)?.let { map["whiteBalance"] = if (it == 0) "Auto" else "Manual" }
    dbl(ExifInterface.TAG_FOCAL_LENGTH)?.let { map["focalLength"] = it }
    int(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM)?.let { map["focalLength35mm"] = it }
    int(ExifInterface.TAG_SCENE_CAPTURE_TYPE)?.let { map["sceneCaptureType"] = sceneCaptureName(it) }
    int(ExifInterface.TAG_CONTRAST)?.let { map["contrast"] = orderedName(it) }
    int(ExifInterface.TAG_SATURATION)?.let { map["saturation"] = orderedName(it) }
    int(ExifInterface.TAG_SHARPNESS)?.let { map["sharpness"] = orderedName(it) }
    dbl(ExifInterface.TAG_DIGITAL_ZOOM_RATIO)?.let { map["digitalZoomRatio"] = it }
    dbl(ExifInterface.TAG_COMPRESSED_BITS_PER_PIXEL)?.let { map["compressedBitsPerPixel"] = it }

    // GPS
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      try {
        val latLong = FloatArray(2)
        if (exif.getLatLong(latLong)) {
          map["gpsLatitude"] = latLong[0].toDouble()
          map["gpsLongitude"] = latLong[1].toDouble()
        }
      } catch (_: Exception) { }
    }
    str(ExifInterface.TAG_GPS_ALTITUDE)?.let {
      try { map["gpsAltitude"] = exif.getAltitude(0.0) } catch (_: Exception) { }
    }
    str(ExifInterface.TAG_GPS_TIMESTAMP)?.let { tsStr ->
      val ts = tsStr.toIntOrNull()?.toLong()
      val date = str(ExifInterface.TAG_GPS_DATESTAMP)
      if (ts != null) {
        val ms = if (date != null) combineGpsDate(date, ts) else ts * 1000L
        map["gpsTimestamp"] = ms
      }
    }
    str(ExifInterface.TAG_GPS_PROCESSING_METHOD)?.let { map["gpsProcessingMethod"] = it }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      try {
        val cs = exif.getAttributeInt(ExifInterface.TAG_COLOR_SPACE, -1)
        if (cs != -1) map["colorSpace"] = colorSpaceName(cs)
      } catch (_: Exception) { }
    }

    exifIntOrNull(exif, ExifInterface.TAG_PIXEL_X_DIMENSION)?.let { map["pixelXDimension"] = it }
    exifIntOrNull(exif, ExifInterface.TAG_PIXEL_Y_DIMENSION)?.let { map["pixelYDimension"] = it }

    return map
  }

  private fun exifIntOrNull(exif: ExifInterface, tag: String): Int? {
    return try { val v = exif.getAttributeInt(tag, Int.MIN_VALUE); if (v == Int.MIN_VALUE) null else v } catch (_: Exception) { null }
  }

  private fun safeExifInt(exif: ExifInterface, tag: String): Int? = exifIntOrNull(exif, tag)

  private fun parseExifDate(value: String): Long? {
    return try {
      val fmt = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
      fmt.parse(value)?.time
    } catch (_: Exception) { null }
  }

  private fun combineGpsDate(date: String, seconds: Long): Long? {
    return try {
      val fmt = SimpleDateFormat("yyyy:MM:dd", Locale.US)
      val base = fmt.parse(date)?.time ?: 0L
      base + seconds * 1000L
    } catch (_: Exception) { seconds * 1000L }
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

  private fun flashModeName(v: Int): String {
    return when (v and 0x1F) {
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

  // ---------------- Document ----------------

  private val TEXT_MIME_TYPES = setOf(
    "text/plain", "text/markdown", "text/csv", "text/html", "text/x-markdown",
    "application/json", "application/xml", "application/javascript", "application/x-yaml", "text/yaml"
  )

  private fun extractDocument(filePath: String, mimeType: String, result: MutableMap<String, Any?>) {
    val docMap = mutableMapOf<String, Any?>()
    docMap["format"] = deriveDocFormat(mimeType, filePath)
    val file = File(filePath)
    try {
      when {
        mimeType == "application/pdf" -> {
          val text = try { String(file.readBytes(), Charsets.ISO_8859_1) } catch (_: Exception) { "" }
          docMap["isEncrypted"] = text.contains("/Encrypt")
          docMap["pageCount"] = Regex("(?i)/Type\\s*/\\s*Page(?![s])").findAll(text).count()
        }
        mimeType in TEXT_MIME_TYPES || filePath.substringAfterLast('.', "").lowercase() in setOf("txt", "md", "markdown", "csv") -> {
          if (file.length() <= 5 * 1024 * 1024) {
            val content = try { file.readText() } catch (_: Exception) { "" }
            docMap["characterCount"] = content.length
            docMap["wordCount"] = content.split(Regex("\\s+")).count { it.isNotBlank() }
            docMap["lineCount"] = content.count { it == '\n' } + 1
          }
        }
      }
    } catch (_: Exception) { }

    try {
      val lastMod = file.lastModified()
      docMap["modificationDate"] = lastMod
      docMap["creationDate"] = lastMod
    } catch (_: Exception) { }

    result["document"] = docMap
  }

  private fun deriveDocFormat(mimeType: String, filePath: String): String {
    val ext = filePath.substringAfterLast('.', "").lowercase()
    if (ext.isNotEmpty()) return ext
    return mimeType.substringAfter("/").substringBefore(";").ifEmpty { "unknown" }
  }
}
