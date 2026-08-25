package com.obsidian_north.mediastore.metadata

import android.content.ContentResolver
import android.net.Uri
import android.os.CancellationSignal
import android.provider.MediaStore
import com.obsidian_north.mediastore.metadata.audio.AudioMetadataExtractor
import com.obsidian_north.mediastore.metadata.common.*
import com.obsidian_north.mediastore.metadata.image.ImageMetadataExtractor
import com.obsidian_north.mediastore.metadata.video.VideoMetadataExtractor
import com.obsidian_north.mediastore.utils.MimeUtils
import kotlinx.coroutines.*
import java.io.File

class MetadataService(private val contentResolver: ContentResolver) {

  private val cache = MetadataCache()
  private var queue: MetadataQueue? = null

  companion object {
    // Hoisted to avoid re-allocating these on every metadata call.
    private val RESOLVE_PROJECTION = arrayOf(
      MediaStore.MediaColumns._ID,
      MediaStore.MediaColumns.DATA,
      MediaStore.MediaColumns.MIME_TYPE,
      MediaStore.MediaColumns.SIZE,
      MediaStore.MediaColumns.DATE_MODIFIED,
    )
    private const val ID_SELECTION = "${MediaStore.MediaColumns._ID} = ?"
  }

  fun initialize(scope: CoroutineScope, maxConcurrent: Int = 3) {
    queue = MetadataQueue(maxConcurrent, scope)
  }

  fun extractMetadata(
    mediaType: String,
    id: String,
    cancellationSignal: CancellationSignal? = null,
  ): MetadataExtractionResult {
    val tableUri = when (mediaType) {
      "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
      "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
      "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
      "document" -> MediaStore.Files.getContentUri("external")
      else -> return MetadataExtractionResult(
        metadata = emptyMap(),
        status = ExtractionStatus.FAILED,
        errorCode = MetadataErrorCode.UNSUPPORTED_FORMAT,
      )
    }

    val contentUri = Uri.withAppendedPath(tableUri, id).toString()

    // Cache-first: skip the MediaStore round-trip entirely on a hit.
    // Cache is invalidated by refresh() / process restart; media files are
    // treated as immutable between refreshes.
    val cached = cache.get(contentUri)
    if (cached != null) {
      return MetadataExtractionResult(metadata = cached, status = ExtractionStatus.COMPLETE)
    }

    val cursor = contentResolver.query(tableUri, RESOLVE_PROJECTION, ID_SELECTION, arrayOf(id), null)
      ?: return MetadataExtractionResult(
        metadata = emptyMap(),
        status = ExtractionStatus.FAILED,
        errorCode = MetadataErrorCode.URI_UNAVAILABLE,
      )

    cursor.use {
      if (!it.moveToFirst()) return MetadataExtractionResult(
        metadata = emptyMap(),
        status = ExtractionStatus.FAILED,
        errorCode = MetadataErrorCode.FILE_NOT_FOUND,
      )

      val filePath = it.getString(it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)) ?: ""
      val mimeType = it.getString(it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)) ?: ""
      val fileSize = it.getLong(it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
      val lastModified = it.getLong(it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)) * 1000L

      return extractFromFile(mediaType, filePath, mimeType, fileSize, lastModified, contentUri, cancellationSignal)
    }
  }

  fun extractMetadataByUri(
    uri: String,
    cancellationSignal: CancellationSignal? = null,
  ): MetadataExtractionResult {
    if (uri.startsWith("content://")) {
      // Cache-first: avoid the MediaStore query on a hit.
      val cached = cache.get(uri)
      if (cached != null) {
        return MetadataExtractionResult(metadata = cached, status = ExtractionStatus.COMPLETE)
      }

      val cursor = contentResolver.query(Uri.parse(uri), RESOLVE_PROJECTION, null, null, null)
        ?: return MetadataExtractionResult(
          metadata = emptyMap(),
          status = ExtractionStatus.FAILED,
          errorCode = MetadataErrorCode.URI_UNAVAILABLE,
        )

      cursor.use {
        if (!it.moveToFirst()) return MetadataExtractionResult(
          metadata = emptyMap(),
          status = ExtractionStatus.FAILED,
          errorCode = MetadataErrorCode.FILE_NOT_FOUND,
        )

        val filePath = it.getString(it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)) ?: ""
        val mimeType = it.getString(it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)) ?: ""
        val fileSize = it.getLong(it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
        val lastModified = it.getLong(it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)) * 1000L
        val mediaType = inferMediaType(mimeType, filePath)

        return extractFromFile(mediaType, filePath, mimeType, fileSize, lastModified, uri, cancellationSignal)
      }
    } else {
      val file = File(uri)
      if (!file.exists()) return MetadataExtractionResult(
        metadata = emptyMap(),
        status = ExtractionStatus.FAILED,
        errorCode = MetadataErrorCode.FILE_NOT_FOUND,
      )

      val cached = cache.get(uri)
      if (cached != null) {
        return MetadataExtractionResult(metadata = cached, status = ExtractionStatus.COMPLETE)
      }

      val mimeType = MimeUtils.getMimeFromExtension(uri.substringAfterLast('.', ""))
      val mediaType = inferMediaType(mimeType, uri)
      val fileSize = file.length()
      val lastModified = file.lastModified()

      return extractFromFile(mediaType, uri, mimeType, fileSize, lastModified, uri, cancellationSignal)
    }
  }

  private fun extractFromFile(
    mediaType: String,
    filePath: String,
    mimeType: String,
    fileSize: Long,
    lastModified: Long,
    cacheUri: String,
    cancellationSignal: CancellationSignal?,
  ): MetadataExtractionResult {
    if (cancellationSignal?.isCanceled == true) {
      return MetadataExtractionResult(
        metadata = emptyMap(),
        status = ExtractionStatus.CANCELLED,
        errorCode = MetadataErrorCode.CANCELLED,
      )
    }

    val rawResult = when (mediaType) {
      "audio" -> AudioMetadataExtractor.extract(filePath, mimeType)
      "video" -> VideoMetadataExtractor.extract(filePath, mimeType)
      "image" -> ImageMetadataExtractor.extract(filePath, mimeType)
      else -> MetadataExtractionResult(
        metadata = mapOf("mediaType" to mediaType, "mimeType" to mimeType, "fileSize" to fileSize),
        status = ExtractionStatus.COMPLETE,
      )
    }

    val enriched = rawResult.metadata.toMutableMap()
    enriched["fileSize"] = fileSize
    enriched["mediaType"] = mediaType
    enriched["mimeType"] = mimeType

    val normalized = MetadataNormalizer.normalize(mediaType, enriched)

    cache.put(cacheUri, normalized, fileSize, lastModified)

    return rawResult.copy(metadata = normalized)
  }

  fun getArtworkUri(albumId: String): Uri? {
    return try {
      android.content.ContentUris.withAppendedId(
        Uri.parse("content://media/external/audio/albumart"),
        albumId.toLong(),
      )
    } catch (_: NumberFormatException) { null }
  }

  fun getArtworkBytes(albumId: String): ByteArray? {
    val artworkUri = getArtworkUri(albumId) ?: return null
    return try {
      contentResolver.openInputStream(artworkUri)?.use { it.readBytes() }
    } catch (_: Exception) { null }
  }

  fun inspectMetadata(uri: String): Map<String, Any?> {
    val result = mutableMapOf<String, Any?>()
    result["uri"] = uri

    val sources = mutableMapOf<String, Boolean>()
    val fields = mutableMapOf<String, Map<String, Any?>>()

    val extraction = extractMetadataByUri(uri)

    sources["mediaStore"] = true
    sources["mediaMetadataRetriever"] = extraction.status != ExtractionStatus.FAILED
    sources["exif"] = (extraction.metadata["image"] as? Map<*, *>)?.containsKey("exif") == true

    result["sources"] = sources
    result["fields"] = fields
    result["warnings"] = extraction.warnings
    result["status"] = extraction.status.jsValue

    for ((key, value) in extraction.metadata) {
      if (value is Map<*, *>) {
        for ((subKey, subValue) in value) {
          val fieldKey = if (value.containsKey("audio") || value.containsKey("video") || value.containsKey("image")) {
            "$key.${subKey}"
          } else {
            subKey?.toString() ?: key
          }
          fields[fieldKey] = mapOf(
            "value" to (subValue ?: "null"),
            "source" to "extractor",
          )
        }
      } else if (key != "mediaType" && key != "mimeType") {
        fields[key] = mapOf(
          "value" to (value ?: "null"),
          "source" to "mediastore",
        )
      }
    }

    return result
  }

  fun getQueue(): MetadataQueue? = queue
  fun getCache(): MetadataCache = cache

  private fun inferMediaType(mimeType: String, filePath: String): String {
    return when {
      mimeType.startsWith("audio/") -> "audio"
      mimeType.startsWith("video/") -> "video"
      mimeType.startsWith("image/") -> "image"
      mimeType.isNotEmpty() && mimeType != "application/octet-stream" -> MimeUtils.getMediaType(mimeType)
      else -> {
        val ext = filePath.substringAfterLast('.', "").lowercase()
        when (ext) {
          "pdf", "txt", "md", "csv", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "epub" -> "document"
          "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "tiff", "tif", "avif" -> "image"
          "mp3", "wav", "flac", "m4a", "aac", "ogg", "opus", "wma", "alac", "aiff" -> "audio"
          "mp4", "mkv", "mov", "avi", "webm", "m4v", "3gp" -> "video"
          else -> "document"
        }
      }
    }
  }
}
