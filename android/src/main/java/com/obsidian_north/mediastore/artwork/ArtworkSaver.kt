package com.obsidian_north.mediastore.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.security.MessageDigest

/**
 * Persists artwork with format preservation by default:
 * PNG → PNG, JPEG → JPEG, WebP → WebP — no lossy recompression unless the
 * caller explicitly passes `{ format: "jpeg", quality: 0.85 }`.
 */
object ArtworkSaver {

  data class SaveOptions(
    val format: String = "original",
    val quality: Double = 0.85,
    val preserveFormat: Boolean = true,
  )

  fun parseOptions(map: Map<String, Any?>?): SaveOptions {
    if (map == null) return SaveOptions()
    val format = (map["format"] as? String)?.lowercase() ?: "original"
    val quality = (map["quality"] as? Number)?.toDouble() ?: 0.85
    val preserve = (map["preserveFormat"] as? Boolean) ?: true
    return SaveOptions(
      format = format,
      quality = quality.coerceIn(0.0, 1.0),
      preserveFormat = preserve,
    )
  }

  /**
   * @param sourceUri artwork image (file://, content://) or an audio file
   *   with embedded artwork.
   * @param destUri optional explicit destination file path/uri. Defaults to
   *   the module artwork cache.
   */
  fun save(context: Context, sourceUri: String, destUri: String?, options: SaveOptions): Map<String, Any?>? {
    val bytes = loadSourceBytes(context, sourceUri) ?: return null
    if (bytes.isEmpty()) return null
    val sourceMime = ArtworkExtractor.detectMime(bytes, sourceUri)
    val targetMime = resolveTargetMime(sourceMime, options)
    val targetExt = ArtworkExtractor.extensionFor(targetMime)

    val outFile = resolveDestFile(context, sourceUri, destUri, targetExt) ?: return null
    return try {
      val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
      if (decoded == null) {
        // Non-decodable (e.g. HEIC on old devices): byte-copy, transcode skipped.
        outFile.writeBytes(bytes)
      } else {
        try {
          val compressFormat = when (targetMime) {
            "image/png" -> Bitmap.CompressFormat.PNG
            "image/webp" -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
              Bitmap.CompressFormat.WEBP_LOSSY
            } else {
              @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            }
            else -> Bitmap.CompressFormat.JPEG
          }
          // Preserve-bytes fast path: same container → byte copy, zero loss.
          val sameContainer = targetMime == sourceMime
          if (sameContainer && options.preserveFormat) {
            outFile.writeBytes(bytes)
          } else {
            val qualityInt = (options.quality * 100).toInt().coerceIn(0, 100)
            outFile.outputStream().use { out -> decoded.compress(compressFormat, qualityInt, out) }
          }
        } finally {
          try { if (!decoded.isRecycled) decoded.recycle() } catch (_: Exception) {}
        }
      }
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(outFile.absolutePath, bounds)
      mapOf(
        "uri" to outFile.toURI().toString(),
        "mimeType" to targetMime,
        "width" to bounds.outWidth.takeIf { it > 0 },
        "height" to bounds.outHeight.takeIf { it > 0 },
        "size" to outFile.length().toInt(),
      )
    } catch (_: Exception) { null }
  }

  private fun resolveTargetMime(sourceMime: String, options: SaveOptions): String {
    if (options.preserveFormat && (options.format == "original" || options.format.isBlank())) {
      return sourceMime
    }
    return when (options.format.lowercase()) {
      "original" -> sourceMime
      "jpeg", "jpg" -> "image/jpeg"
      "png" -> "image/png"
      "webp" -> "image/webp"
      else -> sourceMime
    }
  }

  private fun loadSourceBytes(context: Context, sourceUri: String): ByteArray? {
    // 1) Direct image/container file.
    try {
      if (!sourceUri.startsWith("content://")) {
        val file = File(sourceUri.removePrefix("file://"))
        if (file.exists() && file.isFile) {
          val bytes = file.readBytes()
          if (isImageBytes(bytes)) return bytes
          // May be an audio file → try embedded artwork below.
        }
      } else {
        context.contentResolver.openInputStream(Uri.parse(sourceUri))?.use { input ->
          val bytes = input.readBytes()
          if (bytes.isNotEmpty() && isImageBytes(bytes)) return bytes
        }
      }
    } catch (_: Exception) {}
    // 2) Audio file with embedded artwork.
    return try { ArtworkExtractor.readEmbeddedBytes(context, sourceUri) } catch (_: Exception) { null }
  }

  private fun isImageBytes(bytes: ByteArray): Boolean {
    if (bytes.size < 4) return false
    return detectImageMime(bytes) != null
  }

  private fun detectImageMime(bytes: ByteArray): String? {
    if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "image/jpeg"
    if (bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return "image/png"
    if (bytes.size >= 12 && bytes[0] == 0x52.toByte() && bytes[8] == 0x57.toByte()) return "image/webp"
    if (bytes.size >= 6 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()) return "image/gif"
    if (bytes.size >= 2 && bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) return "image/bmp"
    return null
  }

  private fun resolveDestFile(context: Context, sourceUri: String, destUri: String?, ext: String): File? {
    if (!destUri.isNullOrBlank()) {
      return try {
        val clean = destUri.removePrefix("file://")
        val file = File(clean)
        file.parentFile?.mkdirs()
        if (file.extension.isEmpty()) File(file.absolutePath + ".$ext") else file
      } catch (_: Exception) { null }
    }
    val dir = File(context.cacheDir, "mediastore_artwork").apply { mkdirs() }
    // Include options-independent stable name; collisions overwrite (cache).
    val digest = MessageDigest.getInstance("SHA-1").digest(sourceUri.toByteArray())
    val name = "saved_" + digest.joinToString("") { "%02x".format(it) }.take(16) + ".$ext"
    return File(dir, name)
  }

  @Suppress("unused")
  fun bytesToDescriptor(bytes: ByteArray, uri: String, mime: String): Map<String, Any?> {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val w: Int? = bounds.outWidth.takeIf { it > 0 }
    val h: Int? = bounds.outHeight.takeIf { it > 0 }
    return mapOf(
      "uri" to uri,
      "mimeType" to mime,
      "width" to w,
      "height" to h,
      "size" to bytes.size,
    )
  }
}
