package com.obsidian_north.mediastore.metadata.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.obsidian_north.mediastore.artwork.ArtworkExtractor
import java.io.File

/**
 * vNext metadata pipeline:
 *
 * ```
 * URI → resolve file descriptor → container/format → reader
 *   ├── standard tags · artwork · ReplayGain · R128
 *   └── normalize → AudioMetadata → React Native
 * ```
 *
 * Normalization owns differing tag names/representations (notably R128
 * Q8.8 → dB) so JS just reads `metadata.replayGain.trackGain`.
 */
object UnifiedAudioMetadataReader {

  fun read(context: Context, uriString: String, includeArtworkFile: Boolean = true): Map<String, Any?>? {
    val tags = readStandardTags(context, uriString)
    // Bail out (null) only when we truly know nothing about the file.
    val fileExists = fileExists(context, uriString)
    if (tags.isEmpty() && !fileExists) return null

    val replayGain = try { ReplayGainReader.read(context, uriString) } catch (_: Exception) {
      mapOf("trackGain" to null, "albumGain" to null, "trackPeak" to null, "albumPeak" to null, "source" to null)
    }

    val artwork: Map<String, Any?>? = try {
      if (includeArtworkFile) {
        ArtworkExtractor.extract(context, uriString)
      } else {
        val available = try { ArtworkExtractor.hasEmbeddedArtwork(context, uriString) } catch (_: Exception) { false }
        if (available) mapOf(
          "uri" to uriString,
          "mimeType" to null,
          "width" to null,
          "height" to null,
          "size" to null,
        ) else null
      }
    } catch (_: Exception) { null }

    return mapOf(
      "uri" to uriString,
      "title" to tags["title"],
      "artist" to tags["artist"],
      "album" to tags["album"],
      "albumArtist" to tags["albumArtist"],
      "genre" to tags["genre"],
      "year" to tags["year"],
      "trackNumber" to tags["trackNumber"],
      "discNumber" to tags["discNumber"],
      "duration" to tags["duration"],
      "bitrate" to tags["bitrate"],
      "sampleRate" to tags["sampleRate"],
      "channels" to tags["channels"],
      "composer" to tags["composer"],
      "comment" to null,
      "artwork" to artwork,
      "replayGain" to replayGain,
    )
  }

  fun readBatch(context: Context, uris: List<String>): List<Map<String, Any?>?> {
    // Batch keeps artwork lightweight (availability only) for library scans.
    return uris.map { uri ->
      try { read(context, uri, includeArtworkFile = false) } catch (_: Exception) { null }
    }
  }

  private fun fileExists(context: Context, uriString: String): Boolean {
    return try {
      if (uriString.startsWith("content://")) {
        context.contentResolver.openInputStream(Uri.parse(uriString))?.use { true } ?: false
      } else {
        File(uriString.removePrefix("file://")).exists()
      }
    } catch (_: Exception) { false }
  }

  private fun readStandardTags(context: Context, uriString: String): Map<String, Any?> {
    val out = mutableMapOf<String, Any?>()
    var retriever: MediaMetadataRetriever? = null
    try {
      retriever = MediaMetadataRetriever()
      if (uriString.startsWith("content://")) {
        retriever.setDataSource(context, Uri.parse(uriString))
      } else {
        val path = uriString.removePrefix("file://")
        if (File(path).exists()) retriever.setDataSource(path)
        else retriever.setDataSource(context, Uri.parse(uriString))
      }
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { out["title"] = it }
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { out["artist"] = it }
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { out["album"] = it }
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.let { out["albumArtist"] = it }
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.let { out["genre"] = it }
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)?.let { out["composer"] = it }
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.let { raw ->
        raw.split("/").firstOrNull()?.trim()?.toIntOrNull()?.let { out["trackNumber"] = it }
      }
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)?.let { raw ->
        raw.split("/").firstOrNull()?.trim()?.toIntOrNull()?.let { out["discNumber"] = it }
      }
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.let { raw ->
        // YEAR may be "2021" or a full date "2021-01-01".
        raw.trim().take(4).toIntOrNull()?.let { out["year"] = it }
      }
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.let { raw ->
        raw.toLongOrNull()?.let { out["duration"] = it }
      }
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.let { raw ->
        raw.toIntOrNull()?.let { out["bitrate"] = it }
      }
    } catch (_: Exception) {
    } finally {
      try { retriever?.release() } catch (_: Exception) {}
    }

    // Technical enrichment via MediaExtractor when a file path is available.
    tryEnrichViaExtractor(context, uriString, out)
    return out
  }

  private fun tryEnrichViaExtractor(context: Context, uriString: String, out: MutableMap<String, Any?>) {
    var extractor: MediaExtractor? = null
    try {
      extractor = MediaExtractor()
      if (uriString.startsWith("content://")) {
        extractor.setDataSource(context, Uri.parse(uriString), null)
      } else {
        val path = uriString.removePrefix("file://")
        if (!File(path).exists()) return
        extractor.setDataSource(path)
      }
      for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = try { format.getString(MediaFormat.KEY_MIME) } catch (_: Exception) { null } ?: continue
        if (!mime.startsWith("audio/")) continue
        if (!out.containsKey("bitrate") && format.containsKey(MediaFormat.KEY_BIT_RATE)) {
          out["bitrate"] = format.getInteger(MediaFormat.KEY_BIT_RATE)
        }
        if (!out.containsKey("sampleRate") && format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
          out["sampleRate"] = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        }
        if (!out.containsKey("channels") && format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
          out["channels"] = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        }
        if (!out.containsKey("duration") && format.containsKey(MediaFormat.KEY_DURATION)) {
          out["duration"] = format.getLong(MediaFormat.KEY_DURATION) / 1000L
        }
        break
      }
    } catch (_: Exception) {
    } finally {
      try { extractor?.release() } catch (_: Exception) {}
    }
  }
}
