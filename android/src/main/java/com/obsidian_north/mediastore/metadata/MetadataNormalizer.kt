package com.obsidian_north.mediastore.metadata

import com.obsidian_north.mediastore.metadata.common.MetadataValueUtils

object MetadataNormalizer {

  fun normalize(mediaType: String, raw: Map<String, Any?>): Map<String, Any?> {
    val result = mutableMapOf<String, Any?>()

    result["mediaType"] = mediaType
    result["mimeType"] = raw["mimeType"]
    result["fileSize"] = raw["fileSize"]

    raw["durationMs"]?.let { result["durationMs"] = MetadataValueUtils.normalizeDurationMs(it) }
    raw["containerFormat"]?.let { result["containerFormat"] = it }

    when (mediaType) {
      "audio" -> normalizeAudio(raw, result)
      "video" -> normalizeVideo(raw, result)
      "image" -> normalizeImage(raw, result)
      "document" -> normalizeDocument(raw, result)
    }

    raw["artwork"]?.let { result["artwork"] = it }

    return result
  }

  private fun normalizeAudio(raw: Map<String, Any?>, result: MutableMap<String, Any?>) {
    val src = raw["audio"] as? Map<*, *> ?: return
    val audio = mutableMapOf<String, Any?>()

    copyNormalized(audio, src, "title")
    copyNormalized(audio, src, "artist")
    copyNormalized(audio, src, "album")
    copyNormalized(audio, src, "albumArtist")
    copyNormalized(audio, src, "composer")
    copyNormalized(audio, src, "genre")
    copyNormalized(audio, src, "author")
    copyNormalized(audio, src, "writer")

    copyIntNormalized(audio, src, "trackNumber")
    copyIntNormalized(audio, src, "totalTracks")
    copyIntNormalized(audio, src, "discNumber")
    copyIntNormalized(audio, src, "totalDiscs")
    copyIntNormalized(audio, src, "year")

    copyNormalized(audio, src, "codec")
    copyNormalized(audio, src, "codecMime")
    copyNormalized(audio, src, "codecProfile")
    copyIntNormalized(audio, src, "bitrate")?.let {
      audio["bitrate"] = MetadataValueUtils.normalizeBitrate(it) ?: it
    }
    copyIntNormalized(audio, src, "sampleRate")?.let {
      audio["sampleRate"] = MetadataValueUtils.normalizeSampleRate(it) ?: it
    }
    copyIntNormalized(audio, src, "channels")
    copyNormalized(audio, src, "channelLayout")
    copyIntNormalized(audio, src, "bitsPerSample")
    copyNormalized(audio, src, "language")

    if (audio.isNotEmpty()) result["audio"] = audio
  }

  private fun normalizeVideo(raw: Map<String, Any?>, result: MutableMap<String, Any?>) {
    val video = mutableMapOf<String, Any?>()

    result["width"]?.let { video["width"] = it }
    result["height"]?.let { video["height"] = it }
    result["rotation"]?.let { video["rotation"] = it }
    result["frameRate"]?.let { video["frameRate"] = it }
    result["bitrate"]?.let { video["bitrate"] = it }
    result["captureFrameRate"]?.let { video["captureFrameRate"] = it }
    result["frameCount"]?.let { video["frameCount"] = it }
    result["hasAudio"]?.let { video["hasAudio"] = it }
    result["hasVideo"]?.let { video["hasVideo"] = it }

    val src = raw["video"] as? Map<*, *>
    if (src != null) {
      copyNormalized(video, src, "codec")
      copyNormalized(video, src, "codecMime")
      copyNormalized(video, src, "profile")
      copyNormalized(video, src, "level")
      copyNormalized(video, src, "language")
      copyIntNormalized(video, src, "colorStandard")?.let { video["colorStandard"] = it }
      copyIntNormalized(video, src, "colorTransfer")?.let { video["colorTransfer"] = it }
      copyIntNormalized(video, src, "colorRange")?.let { video["colorRange"] = it }
    }

    val audioTrack = raw["audioTrack"] as? Map<*, *>
    if (audioTrack != null) {
      val at = mutableMapOf<String, Any?>()
      copyNormalized(at, audioTrack, "codecMime")
      copyIntNormalized(at, audioTrack, "channels")
      copyIntNormalized(at, audioTrack, "sampleRate")
      if (at.isNotEmpty()) video["audioTrack"] = at
    }

    if (video.isNotEmpty()) result["video"] = video
  }

  private fun normalizeImage(raw: Map<String, Any?>, result: MutableMap<String, Any?>) {
    val src = raw["image"] as? Map<*, *> ?: return
    val image = mutableMapOf<String, Any?>()

    copyNormalized(image, src, "format")
    copyIntNormalized(image, src, "width")
    copyIntNormalized(image, src, "height")
    copyIntNormalized(image, src, "bitsPerSample")
    copyNormalized(image, src, "colorSpace")

    val exifSrc = src["exif"] as? Map<*, *>
    if (exifSrc != null) {
      val exif = mutableMapOf<String, Any?>()
      for (key in exifSrc.keys) {
        val strKey = key?.toString() ?: continue
        exifSrc[key]?.let { exif[strKey] = it }
      }
      if (exif.isNotEmpty()) image["exif"] = exif
    }

    src["location"]?.let { image["location"] = it }

    if (image.isNotEmpty()) result["image"] = image
  }

  private fun normalizeDocument(raw: Map<String, Any?>, result: MutableMap<String, Any?>) {
    val src = raw["document"] as? Map<*, *> ?: return
    val doc = mutableMapOf<String, Any?>()

    copyNormalized(doc, src, "format")
    copyIntNormalized(doc, src, "pageCount")
    copyIntNormalized(doc, src, "wordCount")
    copyIntNormalized(doc, src, "characterCount")
    copyIntNormalized(doc, src, "lineCount")
    copyNormalized(doc, src, "title")
    copyNormalized(doc, src, "author")
    copyNormalized(doc, src, "creator")
    copyNormalized(doc, src, "producer")
    copyNormalized(doc, src, "subject")
    copyNormalized(doc, src, "language")
    copyBoolNormalized(doc, src, "isEncrypted")
    copyIntNormalized(doc, src, "creationDate")
    copyIntNormalized(doc, src, "modificationDate")

    if (doc.isNotEmpty()) result["document"] = doc
  }

  private fun copyNormalized(dst: MutableMap<String, Any?>, src: Map<*, *>, key: String) {
    src[key]?.let { dst[key] = it }
  }

  private fun copyIntNormalized(dst: MutableMap<String, Any?>, src: Map<*, *>, key: String): Any? {
    val v = src[key] ?: return null
    dst[key] = v
    return v
  }

  private fun copyBoolNormalized(dst: MutableMap<String, Any?>, src: Map<*, *>, key: String) {
    src[key]?.let { dst[key] = it }
  }
}
