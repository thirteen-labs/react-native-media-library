package com.obsidian_north.mediastore

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.obsidian_north.mediastore.metadata.MetadataService
import com.obsidian_north.mediastore.models.*
import com.obsidian_north.mediastore.utils.CursorUtils
import com.obsidian_north.mediastore.utils.MimeUtils

class MediaStoreRepository(private val context: Context) {
  private val contentResolver get() = context.contentResolver
  val metadataService by lazy { MetadataService(context) }

  fun queryAudio(projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? =
    contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, sortOrder)

  fun queryVideo(projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? =
    contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, sortOrder)

  fun queryImages(projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? =
    contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, sortOrder)

  fun queryDocuments(projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? =
    contentResolver.query(MediaStore.Files.getContentUri("external"), projection, selection, selectionArgs, sortOrder)

  fun queryAlbums(projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? =
    contentResolver.query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, sortOrder)

  fun queryArtists(projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? =
    contentResolver.query(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, sortOrder)

  fun queryGenres(projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? =
    contentResolver.query(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, sortOrder)

  fun queryPlaylists(projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? =
    contentResolver.query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, sortOrder)

  fun queryFolders(selection: String?, sortOrder: String?): Cursor? {
    val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.PARENT, MediaStore.Files.FileColumns.MIME_TYPE, MediaStore.Files.FileColumns.TITLE, MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.RELATIVE_PATH)
    return contentResolver.query(MediaStore.Files.getContentUri("external"), projection, selection, null, sortOrder)
  }

  fun queryFolderStatistics(folderPath: String?): Cursor? {
    val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MIME_TYPE, MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.RELATIVE_PATH)
    val selection = if (folderPath != null) "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE '${MediaStoreQueryBuilder().escapeLike(folderPath)}%'" else "${MediaStore.Files.FileColumns.SIZE} > 0"
    return contentResolver.query(MediaStore.Files.getContentUri("external"), projection, selection, null, null)
  }

  fun queryIncremental(sinceTimestamp: Long, projection: Array<String>?, type: String): Cursor? {
    val selection = "${MediaStore.MediaColumns.DATE_MODIFIED} > ?"
    val selectionArgs = arrayOf((sinceTimestamp / 1000).toString())
    val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} ASC"
    return when (type) { "audio" -> queryAudio(projection, selection, selectionArgs, sortOrder); "video" -> queryVideo(projection, selection, selectionArgs, sortOrder); "image" -> queryImages(projection, selection, selectionArgs, sortOrder); "document" -> queryDocuments(projection, selection, selectionArgs, sortOrder); else -> null }
  }

  fun queryByUri(uriString: String): Cursor? = contentResolver.query(Uri.parse(uriString), null, null, null, null)

  fun getDetailedMetadata(mediaType: String, id: String): Map<String, Any?>? {
    return metadataService.extractMetadata(mediaType, id).metadata.ifEmpty { null }
  }

  fun getDetailedMetadataByUri(uri: String): Map<String, Any?>? {
    return metadataService.extractMetadataByUri(uri).metadata.ifEmpty { null }
  }

  fun getPathByUri(uriString: String): String? {
    try {
      val uri = Uri.parse(uriString)
      val mediaType = detectMediaTypeFromUri(uri)
      val projection = arrayOf(MediaStore.MediaColumns.DATA)
      val cursor: Cursor? = when (mediaType) {
        "audio" -> queryAudio(projection, "${MediaStore.Audio.Media._ID} = ?", null, null)
        "video" -> queryVideo(projection, "${MediaStore.Video.Media._ID} = ?", null, null)
        "image" -> queryImages(projection, "${MediaStore.Images.Media._ID} = ?", null, null)
        "document" -> {
          val docProjection = arrayOf(MediaStore.Files.FileColumns.DATA)
          queryDocuments(docProjection, "${MediaStore.Files.FileColumns._ID} = ?", null, null)
        }
        else -> null
      }
      return cursor?.use { if (it.moveToFirst()) CursorUtils.getString(it, MediaStore.MediaColumns.DATA) else null }
    } catch (e: Exception) { return null }
  }

  private fun detectMediaTypeFromUri(uri: Uri): String {
    return when {
      uri.authority?.contains("media") == true && uri.pathSegments.firstOrNull() == "audio" -> "audio"
      uri.authority?.contains("media") == true && uri.pathSegments.firstOrNull() == "video" -> "video"
      uri.authority?.contains("media") == true && uri.pathSegments.firstOrNull() == "images" -> "image"
      else -> "document"
    }
  }

  fun search(options: SearchOptionsRecord): SearchResultRecord {
    val query = options.query.trim()
    val searchFilter = "${MediaStore.MediaColumns.TITLE} LIKE ? OR ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
    val searchArgs = arrayOf("%$query%", "%$query%")
    val types = options.types ?: listOf("audio", "video", "image", "document")
    val mapper = MediaStoreMapper()
    val queryBuilder = MediaStoreQueryBuilder()

    val audioItems = if (types.contains("audio")) queryAudio(queryBuilder.buildAudioProjection(), searchFilter, searchArgs, null)?.use { mapper.mapGenericToAudio(it) } ?: emptyList() else emptyList()
    val videoItems = if (types.contains("video")) queryVideo(queryBuilder.buildVideoProjection(), searchFilter, searchArgs, null)?.use { mapper.mapGenericToVideo(it) } ?: emptyList() else emptyList()
    val imageItems = if (types.contains("image")) queryImages(queryBuilder.buildImageProjection(), searchFilter, searchArgs, null)?.use { mapper.mapGenericToImage(it) } ?: emptyList() else emptyList()
    val documentItems = if (types.contains("document")) {
      val docMimeFilter = MimeUtils.getDocumentMimeFilter()
      queryDocuments(queryBuilder.buildDocumentProjection(), "($searchFilter) AND (${MediaStore.Files.FileColumns.MIME_TYPE} IN ($docMimeFilter))", searchArgs, null)?.use { mapper.mapGenericToDocument(it) } ?: emptyList()
    } else emptyList()

    val totalCount = audioItems.size + videoItems.size + imageItems.size + documentItems.size

    return SearchResultRecord().apply { this.audio = audioItems; this.videos = videoItems; this.images = imageItems; this.documents = documentItems; this.totalCount = totalCount; this.query = query }
  }

  fun getStatistics(): StatisticsRecord {
    var totalAudio = 0; var totalVideo = 0; var totalImages = 0; var totalDocuments = 0; var totalSize = 0L; var totalDuration = 0L

    queryAudio(arrayOf("COUNT(*)", "SUM(${MediaStore.Audio.Media.SIZE})", "SUM(${MediaStore.Audio.Media.DURATION})"), null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) { totalAudio = cursor.getInt(0); totalSize += cursor.getLong(1); totalDuration += cursor.getLong(2) }
    }
    queryVideo(arrayOf("COUNT(*)", "SUM(${MediaStore.Video.Media.SIZE})", "SUM(${MediaStore.Video.Media.DURATION})"), null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) { totalVideo = cursor.getInt(0); totalSize += cursor.getLong(1); totalDuration += cursor.getLong(2) }
    }
    queryImages(arrayOf("COUNT(*)", "SUM(${MediaStore.Images.Media.SIZE})"), null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) { totalImages = cursor.getInt(0); totalSize += cursor.getLong(1) }
    }
    val docMimeFilter = MimeUtils.getDocumentMimeFilter()
    queryDocuments(arrayOf("COUNT(*)", "SUM(${MediaStore.Files.FileColumns.SIZE})"), "${MediaStore.Files.FileColumns.MIME_TYPE} IN ($docMimeFilter)", null, null)?.use { cursor ->
      if (cursor.moveToFirst()) { totalDocuments = cursor.getInt(0); totalSize += cursor.getLong(1) }
    }

    return StatisticsRecord().apply { this.totalAudio = totalAudio; this.totalVideo = totalVideo; this.totalImages = totalImages; this.totalDocuments = totalDocuments; this.totalSize = totalSize; this.totalDuration = totalDuration }
  }
}
