package com.obsidian_north.mediastore.metadata.audio

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream

/**
 * Best-effort R128 / ReplayGain 1.0 tag reader.
 *
 * `MediaMetadataRetriever` does not expose Vorbis comments / ID3 TXXX
 * gain tags, so this reader scans the container head for ASCII tag keys:
 *
 * ```
 * R128_TRACK_GAIN / R128_ALBUM_GAIN
 * REPLAYGAIN_TRACK_GAIN / REPLAYGAIN_ALBUM_GAIN
 * REPLAYGAIN_TRACK_PEAK / REPLAYGAIN_ALBUM_PEAK
 * ```
 *
 * This covers FLAC/Vorbis/Opus comments, ID3v2 TXXX frames, and MP4
 * freeform (`----:com.apple.iTunes:REPLAYGAIN_*`) atoms without pulling in
 * a full tag library. Returns normalized dB gains + linear peaks with a
 * unified `source` flag (`r128` wins over `replaygain`).
 */
object ReplayGainReader {

  private const val HEAD_BYTES_CONTENT_URI = 2 * 1024 * 1024L
  private const val HEAD_BYTES_FILE = 8 * 1024 * 1024L

  private val GAIN_PATTERN = Regex(
    "(?i)(R128_TRACK_GAIN|R128_ALBUM_GAIN|REPLAYGAIN_TRACK_GAIN|REPLAYGAIN_ALBUM_GAIN|REPLAYGAIN_TRACK_PEAK|REPLAYGAIN_ALBUM_PEAK)\\s*=\\s*([^\\u0000\\r\\n;]{1,32})"
  )

  fun read(context: Context, uriString: String): Map<String, Any?> {
    val head = readHead(context, uriString) ?: return emptyGain()
    return parse(head)
  }

  fun readFile(file: File): Map<String, Any?> {
    if (!file.exists() || !file.canRead()) return emptyGain()
    return try {
      val len = minOf(file.length(), HEAD_BYTES_FILE).toInt()
      if (len <= 0) return emptyGain()
      val buf = ByteArray(len)
      file.inputStream().use { input ->
        var off = 0
        while (off < len) {
          val n = input.read(buf, off, len - off)
          if (n <= 0) break
          off += n
        }
      }
      parse(String(buf, Charsets.ISO_8859_1))
    } catch (_: Exception) {
      emptyGain()
    }
  }

  private fun readHead(context: Context, uriString: String): String? {
    return try {
      if (uriString.startsWith("content://")) {
        val uri = Uri.parse(uriString)
        context.contentResolver.openInputStream(uri)?.use { input ->
          readHeadFromStream(input, HEAD_BYTES_CONTENT_URI)
        }?.let { String(it, Charsets.ISO_8859_1) }
      } else {
        val path = uriString.removePrefix("file://")
        val file = File(path)
        if (file.exists() && file.canRead()) {
          val len = minOf(file.length(), HEAD_BYTES_FILE).toInt()
          if (len <= 0) return null
          val buf = ByteArray(len)
          file.inputStream().use { input ->
            var off = 0
            while (off < len) {
              val n = input.read(buf, off, len - off)
              if (n <= 0) break
              off += n
            }
          }
          String(buf, Charsets.ISO_8859_1)
        } else {
          // Fall back to resolving as a generic Uri (file provider, etc.)
          try {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
              readHeadFromStream(input, HEAD_BYTES_CONTENT_URI)
            }?.let { String(it, Charsets.ISO_8859_1) }
          } catch (_: Exception) { null }
        }
      }
    } catch (_: Exception) { null }
  }

  private fun readHeadFromStream(input: InputStream, maxBytes: Long): ByteArray {
    val cap = maxBytes.toInt()
    val buf = ByteArray(cap)
    var off = 0
    while (off < cap) {
      val n = input.read(buf, off, cap - off)
      if (n <= 0) break
      off += n
    }
    return if (off == cap) buf else buf.copyOf(off)
  }

  fun parse(headLatin1: String): Map<String, Any?> {
    var r128Track: Double? = null
    var r128Album: Double? = null
    var rgTrack: Double? = null
    var rgAlbum: Double? = null
    var trackPeak: Double? = null
    var albumPeak: Double? = null

    for (match in GAIN_PATTERN.findAll(headLatin1)) {
      val key = match.groupValues[1].uppercase()
      val raw = match.groupValues[2].trim().trim('\u0000', '"', '\'')
      when (key) {
        "R128_TRACK_GAIN" -> if (r128Track == null) R128Parser.r128RawToDb(raw)?.let { r128Track = it }
        "R128_ALBUM_GAIN" -> if (r128Album == null) R128Parser.r128RawToDb(raw)?.let { r128Album = it }
        "REPLAYGAIN_TRACK_GAIN" -> if (rgTrack == null) R128Parser.parseReplayGainDb(raw)?.let { rgTrack = it }
        "REPLAYGAIN_ALBUM_GAIN" -> if (rgAlbum == null) R128Parser.parseReplayGainDb(raw)?.let { rgAlbum = it }
        "REPLAYGAIN_TRACK_PEAK" -> if (trackPeak == null) R128Parser.parsePeak(raw)?.let { trackPeak = it }
        "REPLAYGAIN_ALBUM_PEAK" -> if (albumPeak == null) R128Parser.parsePeak(raw)?.let { albumPeak = it }
      }
    }

    val hasR128 = r128Track != null || r128Album != null
    val hasRg = rgTrack != null || rgAlbum != null || trackPeak != null || albumPeak != null
    if (!hasR128 && !hasRg) return emptyGain()

    val source = if (hasR128) "r128" else "replaygain"
    return mapOf(
      "trackGain" to (r128Track ?: rgTrack),
      "albumGain" to (r128Album ?: rgAlbum),
      "trackPeak" to trackPeak,
      "albumPeak" to albumPeak,
      "source" to source,
    )
  }

  private fun emptyGain(): Map<String, Any?> = mapOf(
    "trackGain" to null,
    "albumGain" to null,
    "trackPeak" to null,
    "albumPeak" to null,
    "source" to null,
  )

  /** ContentResolver overload for callers that already hold a resolver. */
  fun read(resolver: ContentResolver, uri: Uri): Map<String, Any?> {
    return try {
      resolver.openInputStream(uri)?.use { input ->
        parse(String(readHeadFromStream(input, HEAD_BYTES_CONTENT_URI), Charsets.ISO_8859_1))
      } ?: emptyGain()
    } catch (_: Exception) { emptyGain() }
  }
}
