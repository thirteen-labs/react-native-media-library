package com.obsidian_north.mediastore.metadata.audio

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import com.obsidian_north.mediastore.metadata.common.MetadataExtractionResult
import com.obsidian_north.mediastore.metadata.common.MetadataValueUtils
import com.obsidian_north.mediastore.metadata.common.ExtractionStatus

object AudioMetadataExtractor {

  fun extract(filePath: String, mimeType: String): MetadataExtractionResult {
    val result = mutableMapOf<String, Any?>()
    val warnings = mutableListOf<String>()
    var status = ExtractionStatus.COMPLETE

    extractRetrieverMetadata(filePath, result, warnings)
    extractFormatMetadata(filePath, result, warnings)

    if (warnings.isNotEmpty()) status = ExtractionStatus.PARTIAL
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
        extractIdentityTags(retriever, result)
        extractTrackInfo(retriever, result)
        extractRetrieverTechnical(retriever, result)
        extractArtworkAvailability(retriever, result)
      } catch (e: Exception) {
        warnings.add("MediaMetadataRetriever failed: ${e.message}")
      } finally {
        try { retriever.release() } catch (_: Exception) {}
      }
    } catch (e: Exception) {
      warnings.add("Could not initialize MediaMetadataRetriever: ${e.message}")
    }
  }

  private fun extractIdentityTags(retriever: MediaMetadataRetriever, result: MutableMap<String, Any?>) {
    val audio = mutableMapOf<String, Any?>()

    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { audio["title"] = it }
    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { audio["artist"] = it }
    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.let { audio["albumArtist"] = it }
    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { audio["album"] = it }
    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.let { audio["genre"] = it }
    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)?.let { audio["composer"] = it }
    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)?.let { audio["author"] = it }
    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER)?.let { audio["writer"] = it }

    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.let { raw ->
      val parts = raw.split("/")
      MetadataValueUtils.parseIntOrNull(parts.firstOrNull())?.let { audio["trackNumber"] = it }
      if (parts.size > 1) MetadataValueUtils.parseIntOrNull(parts[1])?.let { audio["totalTracks"] = it }
    }

    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)?.let { raw ->
      val parts = raw.split("/")
      MetadataValueUtils.parseIntOrNull(parts.firstOrNull())?.let { audio["discNumber"] = it }
      if (parts.size > 1) MetadataValueUtils.parseIntOrNull(parts[1])?.let { audio["totalDiscs"] = it }
    }

    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.let {
      MetadataValueUtils.parseIntOrNull(it)?.let { v -> audio["year"] = v }
    }

    if (audio.isNotEmpty()) result["audio"] = audio
  }

  private fun extractTrackInfo(retriever: MediaMetadataRetriever, result: MutableMap<String, Any?>) {
    val audio = result["audio"] as? MutableMap<String, Any?> ?: mutableMapOf()

    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.let {
      MetadataValueUtils.parseLongOrNull(it)?.let { v -> result["durationMs"] = v }
    }

    if (audio.isNotEmpty() && !result.containsKey("audio")) result["audio"] = audio
  }

  private fun extractRetrieverTechnical(retriever: MediaMetadataRetriever, result: MutableMap<String, Any?>) {
    val audio = result["audio"] as? MutableMap<String, Any?> ?: mutableMapOf()

    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.let {
      MetadataValueUtils.parseIntOrNull(it)?.let { v -> audio["bitrate"] = v }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      try {
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.let {
          MetadataValueUtils.normalizeSampleRate(it)?.let { v -> audio["sampleRate"] = v }
        }
      } catch (_: Exception) {}
      try {
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.let {
          MetadataValueUtils.parseIntOrNull(it)?.let { v -> audio["bitsPerSample"] = v }
        }
      } catch (_: Exception) {}
    }

    if (audio.isNotEmpty() && !result.containsKey("audio")) result["audio"] = audio
  }

  private fun extractArtworkAvailability(retriever: MediaMetadataRetriever, result: MutableMap<String, Any?>) {
    val artwork = mutableMapOf<String, Any?>()
    try {
      val picture = retriever.embeddedPicture
      artwork["available"] = picture != null
    } catch (_: Exception) {
      artwork["available"] = false
    }
    if (artwork.isNotEmpty()) result["artwork"] = artwork
  }

  private fun extractFormatMetadata(
    filePath: String,
    result: MutableMap<String, Any?>,
    warnings: MutableList<String>,
  ) {
    val audio = result["audio"] as? MutableMap<String, Any?> ?: mutableMapOf()

    try {
      val extractor = MediaExtractor()
      try {
        extractor.setDataSource(filePath)
        for (i in 0 until extractor.trackCount) {
          val format = extractor.getTrackFormat(i)
          val trackMime = format.getString(MediaFormat.KEY_MIME) ?: continue
          if (trackMime.startsWith("audio/")) {
            extractFormatDetails(format, audio, result)
            break
          }
        }
      } catch (e: Exception) {
        warnings.add("MediaExtractor failed: ${e.message}")
      } finally {
        try { extractor.release() } catch (_: Exception) {}
      }
    } catch (e: Exception) {
      warnings.add("Could not initialize MediaExtractor: ${e.message}")
    }

    if (audio.isNotEmpty() && !result.containsKey("audio")) result["audio"] = audio
  }

  private fun extractFormatDetails(format: MediaFormat, audio: MutableMap<String, Any?>, rootResult: MutableMap<String, Any?>) {
    format.getString(MediaFormat.KEY_MIME)?.let {
      audio["codecMime"] = it
      audio["codec"] = normalizeCodec(it)
    }

    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
      audio.putIfAbsent("bitrate", format.getInteger(MediaFormat.KEY_BIT_RATE))
    }
    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
      audio.putIfAbsent("sampleRate", format.getInteger(MediaFormat.KEY_SAMPLE_RATE))
    }
    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
      val ch = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
      audio["channels"] = ch
      audio["channelLayout"] = channelLayout(ch)
    }
    if (format.containsKey("bits-per-sample")) {
      audio.putIfAbsent("bitsPerSample", format.getInteger("bits-per-sample"))
    }
    if (format.containsKey(MediaFormat.KEY_DURATION)) {
      if (rootResult["durationMs"] == null) {
        rootResult["durationMs"] = format.getLong(MediaFormat.KEY_DURATION) / 1000L
      }
    }
    if (format.containsKey(MediaFormat.KEY_LANGUAGE)) {
      audio["language"] = format.getString(MediaFormat.KEY_LANGUAGE)
    }
    if (format.containsKey(MediaFormat.KEY_PROFILE)) {
      audio["codecProfile"] = format.getInteger(MediaFormat.KEY_PROFILE)
    }
  }

  private fun normalizeCodec(mime: String): String = when (mime) {
    "audio/mpeg" -> "mp3"
    "audio/mp4a-latm" -> "aac"
    "audio/opus" -> "opus"
    "audio/flac" -> "flac"
    "audio/vorbis" -> "vorbis"
    "audio/amr-nb", "audio/amr-wb", "audio/amr" -> "amr"
    "audio/alac" -> "alac"
    "audio/x-wav", "audio/wav" -> "wav"
    else -> mime.substringAfter("/").substringBefore(";")
  }

  private fun channelLayout(channels: Int): String = when (channels) {
    1 -> "mono"
    2 -> "stereo"
    6 -> "5.1"
    8 -> "7.1"
    else -> "${channels}ch"
  }
}
