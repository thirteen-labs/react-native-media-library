package com.obsidian_north.mediastore.metadata

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.obsidian_north.mediastore.metadata.common.MetadataValueUtils
import java.util.concurrent.TimeUnit

/**
 * Media3 Inspector integration — modern replacement for
 * `android.media.MediaMetadataRetriever` / `MediaExtractor`.
 *
 * Maps:
 *   MediaMetadataRetriever -> androidx.media3.inspector.MetadataRetriever
 *   MediaExtractor         -> androidx.media3.inspector.MediaExtractorCompat
 *   getFrameAtTime         -> androidx.media3.inspector.frame.FrameExtractor
 *
 * All methods are **best-effort**: if Media3 is not on classpath or extraction
 * fails, they return false and callers fall back to the legacy Stagefright path
 * so the library never hard-crashes on old devices / missing deps.
 *
 * @see <a href="https://developer.android.com/media/media3/inspector">Media3 Inspector</a>
 */
object Media3InspectorExtractor {

  private const val TAG = "Media3Inspector"
  private const val TIMEOUT_SEC = 3L

  /**
   * Try to enrich [result] using MetadataRetriever for an audio file.
   * Returns true if data was added.
   */
  fun enrichAudio(
    context: Context,
    uri: Uri,
    result: MutableMap<String, Any?>,
    warnings: MutableList<String>,
  ): Boolean {
    return try {
      // Use reflection so the library still compiles/runs if media3-inspector is absent
      val mediaItemClass = Class.forName("androidx.media3.common.MediaItem")
      val fromUri = mediaItemClass.getMethod("fromUri", Uri::class.java)
      val mediaItem = fromUri.invoke(null, uri)

      val builderClass = Class.forName("androidx.media3.inspector.MetadataRetriever\$Builder")
      val builderCtor = builderClass.getConstructor(Context::class.java, mediaItemClass)
      val builder = builderCtor.newInstance(context, mediaItem)
      val buildMethod = builderClass.getMethod("build")
      val retriever = buildMethod.invoke(builder) as AutoCloseable

      try {
        val retrieverClass = retriever.javaClass
        val durationFuture = retrieverClass.getMethod("retrieveDurationUs").invoke(retriever) as java.util.concurrent.Future<*>
        val groupsFuture = retrieverClass.getMethod("retrieveTrackGroups").invoke(retriever) as java.util.concurrent.Future<*>

        val durationUs = (durationFuture.get(TIMEOUT_SEC, TimeUnit.SECONDS) as? Long)
        if (durationUs != null && durationUs != -9223372036854775807L && durationUs > 0) {
          if (result["durationMs"] == null) result["durationMs"] = durationUs / 1000L
        }

        @Suppress("UNCHECKED_CAST")
        val trackGroups = groupsFuture.get(TIMEOUT_SEC, TimeUnit.SECONDS)
        if (trackGroups != null) {
          parseTrackGroups(trackGroups, result)
        }
        true
      } finally {
        try { retriever.close() } catch (_: Exception) {}
      }
    } catch (e: ClassNotFoundException) {
      // media3-inspector not on classpath — expected if consumer stripped the dep
      if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "Media3 not on classpath: ${e.message}")
      false
    } catch (e: java.util.concurrent.TimeoutException) {
      warnings.add("Media3 MetadataRetriever timeout: ${e.message}")
      Log.w(TAG, "Media3 audio enrichment timeout", e)
      false
    } catch (e: Exception) {
      warnings.add("Media3 audio enrichment failed: ${e.message}")
      Log.w(TAG, "Media3 audio enrichment failed", e)
      false
    }
  }

  /**
   * Try to enrich [result] using MetadataRetriever for a video file.
   * Also extracts duration, width/height via TrackGroups + Timeline.
   */
  fun enrichVideo(
    context: Context,
    uri: Uri,
    result: MutableMap<String, Any?>,
    warnings: MutableList<String>,
  ): Boolean {
    return try {
      val mediaItemClass = Class.forName("androidx.media3.common.MediaItem")
      val fromUri = mediaItemClass.getMethod("fromUri", Uri::class.java)
      val mediaItem = fromUri.invoke(null, uri)

      val builderClass = Class.forName("androidx.media3.inspector.MetadataRetriever\$Builder")
      val builderCtor = builderClass.getConstructor(Context::class.java, mediaItemClass)
      val builder = builderCtor.newInstance(context, mediaItem)
      val buildMethod = builderClass.getMethod("build")
      val retriever = buildMethod.invoke(builder) as AutoCloseable

      try {
        val retrieverClass = retriever.javaClass
        val durationFuture = retrieverClass.getMethod("retrieveDurationUs").invoke(retriever) as java.util.concurrent.Future<*>
        val groupsFuture = retrieverClass.getMethod("retrieveTrackGroups").invoke(retriever) as java.util.concurrent.Future<*>

        val durationUs = (durationFuture.get(TIMEOUT_SEC, TimeUnit.SECONDS) as? Long)
        if (durationUs != null && durationUs != -9223372036854775807L && durationUs > 0) {
          if (result["durationMs"] == null) result["durationMs"] = durationUs / 1000L
        }

        @Suppress("UNCHECKED_CAST")
        val trackGroups = groupsFuture.get(TIMEOUT_SEC, TimeUnit.SECONDS)
        if (trackGroups != null) {
          parseTrackGroups(trackGroups, result)
        }
        true
      } finally {
        try { retriever.close() } catch (_: Exception) {}
      }
    } catch (e: ClassNotFoundException) {
      if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "Media3 not on classpath: ${e.message}")
      false
    } catch (e: java.util.concurrent.TimeoutException) {
      warnings.add("Media3 MetadataRetriever timeout: ${e.message}")
      Log.w(TAG, "Media3 video enrichment timeout", e)
      false
    } catch (e: Exception) {
      warnings.add("Media3 video enrichment failed: ${e.message}")
      Log.w(TAG, "Media3 video enrichment failed", e)
      false
    }
  }

  /**
   * Attempt to extract a frame/thumbnail using Media3 FrameExtractor.
   * Returns bitmap or null. Caller is responsible for recycling.
   *
   * @param positionMs 0 for thumbnail
   */
  fun extractFrame(
    context: Context,
    uri: Uri,
    positionMs: Long,
    width: Int,
    height: Int,
  ): android.graphics.Bitmap? {
    return try {
      val mediaItemClass = Class.forName("androidx.media3.common.MediaItem")
      val fromUri = mediaItemClass.getMethod("fromUri", Uri::class.java)
      val mediaItem = fromUri.invoke(null, uri)

      val frameExtractorBuilderClass = Class.forName("androidx.media3.inspector.frame.FrameExtractor\$Builder")
      val builderCtor = frameExtractorBuilderClass.getConstructor(Context::class.java, mediaItemClass)
      val builder = builderCtor.newInstance(context, mediaItem)

      // Optional: downscale via Presentation effect to reduce memory
      try {
        val presentationClass = Class.forName("androidx.media3.effect.Presentation")
        val createMethod = presentationClass.getMethod(
          "createForWidthAndHeight",
          Int::class.javaPrimitiveType,
          Int::class.javaPrimitiveType,
          Int::class.javaPrimitiveType,
        )
        // LAYOUT_SCALE_TO_FIT = 1
        val effect = createMethod.invoke(null, width, height, 1)
        val setEffects = frameExtractorBuilderClass.getMethod("setEffects", List::class.java)
        setEffects.invoke(builder, listOf(effect))
      } catch (_: Exception) {
        // effect is optional, ignore
      }

      val buildMethod = frameExtractorBuilderClass.getMethod("build")
      val extractor = buildMethod.invoke(builder) as AutoCloseable

      try {
        val extractorClass = extractor.javaClass
        val future: java.util.concurrent.Future<*>
        if (positionMs <= 0) {
          future = extractorClass.getMethod("getThumbnail").invoke(extractor) as java.util.concurrent.Future<*>
        } else {
          future = extractorClass.getMethod("getFrame", Long::class.javaPrimitiveType).invoke(extractor, positionMs) as java.util.concurrent.Future<*>
        }
        val frame = future.get(TIMEOUT_SEC + 2, TimeUnit.SECONDS) ?: return null
        val frameClass = frame.javaClass
        val bitmapMethod = frameClass.getMethod("getBitmap")
        @Suppress("UNCHECKED_CAST")
        val bitmap = bitmapMethod.invoke(frame) as? android.graphics.Bitmap
        bitmap
      } finally {
        try { extractor.close() } catch (_: Exception) {}
      }
    } catch (_: ClassNotFoundException) {
      null
    } catch (_: Exception) {
      null
    }
  }

  // ---- private helpers ----

  private fun parseTrackGroups(trackGroups: Any, result: MutableMap<String, Any?>) {
    try {
      val tgArrayClass = trackGroups.javaClass
      val lengthMethod = tgArrayClass.getMethod("length")
      val getMethod = tgArrayClass.getMethod("get", Int::class.javaPrimitiveType)
      val length = lengthMethod.invoke(trackGroups) as Int

      for (i in 0 until length) {
        val group = getMethod.invoke(trackGroups, i) ?: continue
        val groupClass = group.javaClass
        val groupLength = groupClass.getMethod("length").invoke(group) as Int
        val getFormat = groupClass.getMethod("getFormat", Int::class.javaPrimitiveType)
        for (j in 0 until groupLength) {
          val format = getFormat.invoke(group, j) ?: continue
          parseFormat(format, result)
        }
      }
    } catch (_: Exception) {
      // swallow - legacy path will fill
    }
  }

  private fun parseFormat(format: Any, result: MutableMap<String, Any?>) {
    try {
      val formatClass = format.javaClass

      // Use reflection to avoid hard compile dep on Format fields that move between versions
      fun <T> getField(name: String): T? {
        return try {
          val f = formatClass.getDeclaredField(name)
          f.isAccessible = true
          @Suppress("UNCHECKED_CAST")
          f.get(format) as? T
        } catch (_: Exception) {
          try {
            val m = formatClass.getMethod("get" + name.replaceFirstChar { it.uppercase() })
            @Suppress("UNCHECKED_CAST")
            m.invoke(format) as? T
          } catch (_: Exception) { null }
        }
      }

      val sampleMimeType: String? = getField("sampleMimeType") ?: getField("sampleMimeType2")
      val mimeType = sampleMimeType ?: (try { formatClass.getMethod("getSampleMimeType").invoke(format) as? String } catch (_: Exception) { null })
      val bitrate: Int? = try { formatClass.getMethod("getBitrate").invoke(format) as? Int } catch (_: Exception) { null }
      val sampleRate: Int? = try { formatClass.getMethod("getSampleRate").invoke(format) as? Int } catch (_: Exception) { null }
      val channelCount: Int? = try { formatClass.getMethod("getChannelCount").invoke(format) as? Int } catch (_: Exception) { null }
      val width: Int? = try { formatClass.getMethod("getWidth").invoke(format) as? Int } catch (_: Exception) { null }
      val height: Int? = try { formatClass.getMethod("getHeight").invoke(format) as? Int } catch (_: Exception) { null }
      val rotation: Int? = try { formatClass.getMethod("getRotationDegrees").invoke(format) as? Int } catch (_: Exception) { null }

      mimeType?.let { mime ->
        if (mime.startsWith("audio/")) {
          val audio = result.getOrPut("audio") { mutableMapOf<String, Any?>() } as MutableMap<String, Any?>
          if (audio["codecMime"] == null) audio["codecMime"] = mime
          if (audio["codec"] == null) audio["codec"] = normalizeAudioCodec(mime)
          bitrate?.let { if (audio["bitrate"] == null) audio["bitrate"] = it }
          sampleRate?.let { if (audio["sampleRate"] == null) audio["sampleRate"] = it }
          channelCount?.let { audio["channels"] = it; audio["channelLayout"] = channelLayout(it) }
        } else if (mime.startsWith("video/")) {
          val video = result.getOrPut("video") { mutableMapOf<String, Any?>() } as MutableMap<String, Any?>
          if (video["codecMime"] == null) video["codecMime"] = mime
          if (video["codec"] == null) video["codec"] = normalizeVideoCodec(mime)
          bitrate?.let { if (result["bitrate"] == null) result["bitrate"] = it }
          width?.let { if (result["width"] == null) result["width"] = it }
          height?.let { if (result["height"] == null) result["height"] = it }
          rotation?.let { if (it != 0 && result["rotation"] == null) result["rotation"] = it }
          if (video["width"] == null) width?.let { video["width"] = it }
          if (video["height"] == null) height?.let { video["height"] = it }
        }
      }
    } catch (_: Exception) {}
  }

  private fun normalizeAudioCodec(mime: String): String = when (mime) {
    "audio/mpeg" -> "mp3"
    "audio/mp4a-latm" -> "aac"
    "audio/opus" -> "opus"
    "audio/flac" -> "flac"
    "audio/vorbis" -> "vorbis"
    "audio/alac" -> "alac"
    else -> mime.substringAfter("/").substringBefore(";")
  }

  private fun normalizeVideoCodec(mime: String): String = when (mime) {
    "video/avc" -> "h264"
    "video/hevc" -> "hevc"
    "video/x-vnd.on2.vp9" -> "vp9"
    "video/av01" -> "av1"
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
