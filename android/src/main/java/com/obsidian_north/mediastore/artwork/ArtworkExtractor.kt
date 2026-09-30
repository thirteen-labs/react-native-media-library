package com.obsidian_north.mediastore.artwork

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.security.MessageDigest

/**
 * Extracts embedded artwork (`MediaMetadataRetriever.embeddedPicture`) to a
 * cache file usable by React Native `<Image>`.
 *
 * Pipeline: audio → native layer → embedded bytes → local file → RN Image.
 */
object ArtworkExtractor {

  fun extract(context: Context, uriString: String): Map<String, Any?>? {
    val bytes = readEmbeddedBytes(context, uriString) ?: return null
    if (bytes.isEmpty()) return null
    val mime = detectMime(bytes, uriString)
    val ext = extensionFor(mime)
    val dir = File(context.cacheDir, "mediastore_artwork").apply { mkdirs() }
    val name = "art_${sha1(uriString)}.$ext"
    return try {
      val out = File(dir, name)
      // Reuse cached extraction when the source hasn't changed size.
      if (!out.exists() || out.length() != bytes.size.toLong()) {
        out.writeBytes(bytes)
      }
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
      mapOf(
        "uri" to out.toURI().toString(),
        "mimeType" to mime,
        "width" to bounds.outWidth.takeIf { it > 0 },
        "height" to bounds.outHeight.takeIf { it > 0 },
        "size" to bytes.size,
      )
    } catch (_: Exception) { null }
  }

  fun readEmbeddedBytes(context: Context, uriString: String): ByteArray? {
    var retriever: MediaMetadataRetriever? = null
    return try {
      retriever = MediaMetadataRetriever()
      if (uriString.startsWith("content://")) {
        retriever.setDataSource(context, Uri.parse(uriString))
      } else {
        val path = uriString.removePrefix("file://")
        val file = File(path)
        if (file.exists()) retriever.setDataSource(path)
        else retriever.setDataSource(context, Uri.parse(uriString))
      }
      retriever.embeddedPicture
    } catch (_: Exception) { null } finally {
      try { retriever?.release() } catch (_: Exception) {}
    }
  }

  fun hasEmbeddedArtwork(context: Context, uriString: String): Boolean {
    return try { readEmbeddedBytes(context, uriString)?.isNotEmpty() == true } catch (_: Exception) { false }
  }

  internal fun detectMime(bytes: ByteArray, fallbackUri: String): String {
    if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) return "image/jpeg"
    if (bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return "image/png"
    if (bytes.size >= 12 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
      bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() && bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()
    ) return "image/webp"
    if (bytes.size >= 6 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()) return "image/gif"
    if (bytes.size >= 2 && bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) return "image/bmp"
    val ext = fallbackUri.substringAfterLast('.', "").lowercase().substringBefore('?')
    return when (ext) {
      "jpg", "jpeg" -> "image/jpeg"
      "png" -> "image/png"
      "webp" -> "image/webp"
      "gif" -> "image/gif"
      "bmp" -> "image/bmp"
      "heic", "heif" -> "image/heic"
      else -> "image/jpeg"
    }
  }

  internal fun extensionFor(mime: String): String = when (mime) {
    "image/png" -> "png"
    "image/webp" -> "webp"
    "image/gif" -> "gif"
    "image/bmp" -> "bmp"
    "image/heic" -> "heic"
    else -> "jpg"
  }

  private fun sha1(input: String): String {
    val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }.take(16)
  }
}
