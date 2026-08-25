package com.obsidian_north.mediastore.metadata.video

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import com.obsidian_north.mediastore.metadata.common.ExtractionStatus
import com.obsidian_north.mediastore.metadata.common.MetadataExtractionResult
import com.obsidian_north.mediastore.metadata.common.MetadataValueUtils

object VideoMetadataExtractor {

  fun extract(filePath: String, mimeType: String): MetadataExtractionResult {
    val result = mutableMapOf<String, Any?>()
    val warnings = mutableListOf<String>()

    extractRetrieverMetadata(filePath, result, warnings)
    extractFormatMetadata(filePath, result, warnings)
    extractTechnicalDetails(filePath, result, warnings)

    val status = if (warnings.isNotEmpty()) ExtractionStatus.PARTIAL else ExtractionStatus.COMPLETE
    return MetadataExtractionResult(result, status, warnings)
  }

  private fun extractRetrieverMetadata(
    filePath: String,
    result: MutableMap<String, Any?>,
    warnings: MutableList<String>,
  ) {
    try {
      val retriever = MediaMetadataRetriever()
      try {
        retriever.setDataSource(filePath)

        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.let {
          MetadataValueUtils.parseLongOrNull(it)?.let { v -> result["durationMs"] = v }
        }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.let {
          MetadataValueUtils.parseIntOrNull(it)?.let { v -> result["width"] = v }
        }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.let {
          MetadataValueUtils.parseIntOrNull(it)?.let { v -> result["height"] = v }
        }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.let {
          MetadataValueUtils.parseIntOrNull(it)?.let { v ->
            if (v != 0) result["rotation"] = v
          }
        }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.let {
          MetadataValueUtils.parseIntOrNull(it)?.let { v -> result["bitrate"] = v }
        }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)?.let {
          result["hasVideo"] = it == "yes"
        }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)?.let {
          result["hasAudio"] = it == "yes"
        }

        val artwork = mutableMapOf<String, Any?>()
        try {
          val frame = retriever.getFrameAtTime(0)
          artwork["available"] = frame != null
          frame?.recycle()
        } catch (_: Exception) {
          artwork["available"] = false
        }
        result["artwork"] = artwork

      } catch (e: Exception) {
        warnings.add("MediaMetadataRetriever failed: ${e.message}")
      } finally {
        try { retriever.release() } catch (_: Exception) {}
      }
    } catch (e: Exception) {
      warnings.add("Could not initialize MediaMetadataRetriever: ${e.message}")
    }
  }

  private fun extractFormatMetadata(
    filePath: String,
    result: MutableMap<String, Any?>,
    warnings: MutableList<String>,
  ) {
    try {
      val extractor = MediaExtractor()
      try {
        extractor.setDataSource(filePath)
        var audioFound = false
        var videoFound = false

        for (i in 0 until extractor.trackCount) {
          val format = extractor.getTrackFormat(i)
          val trackMime = format.getString(MediaFormat.KEY_MIME) ?: continue

          when {
            trackMime.startsWith("video/") && !videoFound -> {
              extractVideoFormat(format, result)
              videoFound = true
            }
            trackMime.startsWith("audio/") && !audioFound -> {
              extractAudioTrackInfo(format, result)
              audioFound = true
            }
          }
          if (videoFound && audioFound) break
        }
      } catch (e: Exception) {
        warnings.add("MediaExtractor failed: ${e.message}")
      } finally {
        try { extractor.release() } catch (_: Exception) {}
      }
    } catch (e: Exception) {
      warnings.add("Could not initialize MediaExtractor: ${e.message}")
    }
  }

  private fun extractVideoFormat(format: MediaFormat, result: MutableMap<String, Any?>) {
    val video = mutableMapOf<String, Any?>()

    format.getString(MediaFormat.KEY_MIME)?.let {
      video["codecMime"] = it
      video["codec"] = normalizeVideoCodec(it)
    }
    if (format.containsKey(MediaFormat.KEY_PROFILE)) {
      video["profile"] = format.getInteger(MediaFormat.KEY_PROFILE)
    }
    if (format.containsKey(MediaFormat.KEY_LEVEL)) {
      video["level"] = format.getInteger(MediaFormat.KEY_LEVEL)
    }
    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
      result.putIfAbsent("bitrate", format.getInteger(MediaFormat.KEY_BIT_RATE))
    }
    if (format.containsKey(MediaFormat.KEY_WIDTH)) {
      result.putIfAbsent("width", format.getInteger(MediaFormat.KEY_WIDTH))
    }
    if (format.containsKey(MediaFormat.KEY_HEIGHT)) {
      result.putIfAbsent("height", format.getInteger(MediaFormat.KEY_HEIGHT))
    }
    getFrameRate(format)?.let { result["frameRate"] = it }

    if (format.containsKey(MediaFormat.KEY_COLOR_STANDARD)) {
      video["colorStandard"] = format.getInteger(MediaFormat.KEY_COLOR_STANDARD)
    }
    if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
      video["colorTransfer"] = format.getInteger(MediaFormat.KEY_COLOR_TRANSFER)
    }
    if (format.containsKey(MediaFormat.KEY_COLOR_RANGE)) {
      video["colorRange"] = format.getInteger(MediaFormat.KEY_COLOR_RANGE)
    }
    if (format.containsKey(MediaFormat.KEY_LANGUAGE)) {
      video["language"] = format.getString(MediaFormat.KEY_LANGUAGE)
    }

    if (video.isNotEmpty()) result["video"] = video
  }

  private fun extractAudioTrackInfo(format: MediaFormat, result: MutableMap<String, Any?>) {
    val audioTrack = mutableMapOf<String, Any?>()
    format.getString(MediaFormat.KEY_MIME)?.let { audioTrack["codecMime"] = it }
    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
      audioTrack["channels"] = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    }
    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
      audioTrack["sampleRate"] = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    }
    if (audioTrack.isNotEmpty()) result["audioTrack"] = audioTrack
  }

  private fun extractTechnicalDetails(
    filePath: String,
    result: MutableMap<String, Any?>,
    warnings: MutableList<String>,
  ) {
    try {
      val retriever = MediaMetadataRetriever()
      try {
        retriever.setDataSource(filePath)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          try {
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.let {
              MetadataValueUtils.parseFloatOrNull(it)?.let { v -> result["captureFrameRate"] = v }
            }
          } catch (_: Exception) {}
        }

        try {
          retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.let {
            MetadataValueUtils.parseLongOrNull(it)?.let { v -> result["frameCount"] = v }
          }
        } catch (_: Exception) {}

      } catch (_: Exception) {
      } finally {
        try { retriever.release() } catch (_: Exception) {}
      }
    } catch (_: Exception) {}
  }

  private fun getFrameRate(format: MediaFormat): Double? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      try {
        if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
          return format.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble()
        }
      } catch (_: Exception) {}
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      try {
        if (format.containsKey("capture-framerate")) {
          val type = format.getValueTypeForKey("capture-framerate")
          return when (type) {
            MediaFormat.TYPE_INTEGER -> format.getInteger("capture-framerate").toDouble()
            MediaFormat.TYPE_FLOAT -> format.getFloat("capture-framerate").toDouble()
            else -> null
          }
        }
      } catch (_: Exception) {}
    }
    return null
  }

  private fun normalizeVideoCodec(mime: String): String = when (mime) {
    "video/avc" -> "h264"
    "video/hevc" -> "h265"
    "video/x-vnd.on2.vp9" -> "vp9"
    "video/x-vnd.on2.vp8" -> "vp8"
    "video/av01" -> "av1"
    "video/mp4v-es" -> "mpeg4"
    "video/3gpp" -> "h263"
    "video/raw" -> "raw"
    else -> mime.substringAfter("/").substringBefore(";")
  }
}
