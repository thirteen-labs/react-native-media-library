package com.obsidian_north.mediastore

import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import androidx.exifinterface.media.ExifInterface
import com.obsidian_north.mediastore.models.*
import com.obsidian_north.mediastore.utils.ArtworkUtils
import com.obsidian_north.mediastore.utils.CursorUtils
import com.obsidian_north.mediastore.utils.MimeUtils
import java.io.File

class MediaStoreMapper {
  fun mapAudio(cursor: Cursor): List<AudioRecord> {
    val items = mutableListOf<AudioRecord>()
    while (cursor.moveToNext()) items.add(mapAudioRow(cursor))
    return items
  }

  fun mapAudioRow(cursor: Cursor): AudioRecord {
    val data = CursorUtils.getString(cursor, MediaStore.Audio.Media.DATA)
    return AudioRecord().apply {
      id = CursorUtils.getLong(cursor, MediaStore.Audio.Media._ID).toString()
      uri = data
      title = CursorUtils.getString(cursor, MediaStore.Audio.Media.TITLE)
      artist = CursorUtils.getString(cursor, MediaStore.Audio.Media.ARTIST)
      album = CursorUtils.getString(cursor, MediaStore.Audio.Media.ALBUM)
      albumId = CursorUtils.getLong(cursor, MediaStore.Audio.Media.ALBUM_ID).toString()
      duration = CursorUtils.getLong(cursor, MediaStore.Audio.Media.DURATION)
      size = CursorUtils.getLong(cursor, MediaStore.Audio.Media.SIZE)
      trackNumber = CursorUtils.getInt(cursor, MediaStore.Audio.Media.TRACK)
      discNumber = CursorUtils.getInt(cursor, MediaStore.Audio.Media.DISC_NUMBER)
      year = CursorUtils.getInt(cursor, MediaStore.Audio.Media.YEAR)
      dateAdded = CursorUtils.getLong(cursor, MediaStore.Audio.Media.DATE_ADDED) * 1000L
      dateModified = CursorUtils.getLong(cursor, MediaStore.Audio.Media.DATE_MODIFIED) * 1000L
      composer = CursorUtils.getStringOrNull(cursor, MediaStore.Audio.Media.COMPOSER)
      albumArtist = CursorUtils.getStringOrNull(cursor, MediaStore.Audio.Media.ALBUM_ARTIST)
      isFavorite = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) CursorUtils.getInt(cursor, MediaStore.Audio.Media.IS_FAVORITE) == 1 else false
      bookmark = CursorUtils.getLong(cursor, MediaStore.Audio.Media.BOOKMARK)
      bitrate = CursorUtils.getIntOrNull(cursor, MediaStore.Audio.Media.BITRATE)
      writer = CursorUtils.getStringOrNull(cursor, MediaStore.Audio.Media.WRITER)
      isMusic = CursorUtils.getBooleanOrNull(cursor, MediaStore.Audio.Media.IS_MUSIC)
      isPodcast = CursorUtils.getBooleanOrNull(cursor, MediaStore.Audio.Media.IS_PODCAST)
      isRingtone = CursorUtils.getBooleanOrNull(cursor, MediaStore.Audio.Media.IS_RINGTONE)
      isAlarm = CursorUtils.getBooleanOrNull(cursor, MediaStore.Audio.Media.IS_ALARM)
      isNotification = CursorUtils.getBooleanOrNull(cursor, MediaStore.Audio.Media.IS_NOTIFICATION)
      cdTrackNumber = CursorUtils.getIntOrNull(cursor, MediaStore.Audio.Media.CD_TRACK_NUMBER)
      numTracks = CursorUtils.getIntOrNull(cursor, MediaStore.Audio.Media.NUM_TRACKS)
      mimeType = CursorUtils.getString(cursor, MediaStore.Audio.Media.MIME_TYPE)
      fileExtension = data.substringAfterLast('.', "")
      relativePath = CursorUtils.getString(cursor, MediaStore.Audio.Media.RELATIVE_PATH)
      displayName = CursorUtils.getString(cursor, MediaStore.Audio.Media.DISPLAY_NAME)
      contentUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
    }
  }

  fun mapVideo(cursor: Cursor): List<VideoRecord> {
    val items = mutableListOf<VideoRecord>()
    while (cursor.moveToNext()) items.add(mapVideoRow(cursor))
    return items
  }

  fun mapVideoRow(cursor: Cursor): VideoRecord {
    val data = CursorUtils.getString(cursor, MediaStore.Video.Media.DATA)
    val w = CursorUtils.getInt(cursor, MediaStore.Video.Media.WIDTH)
    val h = CursorUtils.getInt(cursor, MediaStore.Video.Media.HEIGHT)
    return VideoRecord().apply {
      id = CursorUtils.getLong(cursor, MediaStore.Video.Media._ID).toString()
      uri = data
      title = CursorUtils.getString(cursor, MediaStore.Video.Media.TITLE)
      duration = CursorUtils.getLong(cursor, MediaStore.Video.Media.DURATION)
      width = w; height = h
      rotation = CursorUtils.getInt(cursor, MediaStore.Video.Media.ORIENTATION)
      size = CursorUtils.getLong(cursor, MediaStore.Video.Media.SIZE)
      mimeType = CursorUtils.getString(cursor, MediaStore.Video.Media.MIME_TYPE)
      relativePath = CursorUtils.getString(cursor, MediaStore.Video.Media.RELATIVE_PATH)
      displayName = CursorUtils.getString(cursor, MediaStore.Video.Media.DISPLAY_NAME)
      dateAdded = CursorUtils.getLong(cursor, MediaStore.Video.Media.DATE_ADDED) * 1000L
      dateModified = CursorUtils.getLong(cursor, MediaStore.Video.Media.DATE_MODIFIED) * 1000L
      resolution = "${w}x${h}"
      orientation = CursorUtils.getInt(cursor, MediaStore.Video.Media.ORIENTATION)
      colorStandard = CursorUtils.getStringOrNull(cursor, MediaStore.Video.Media.COLOR_STANDARD)
      colorTransfer = CursorUtils.getStringOrNull(cursor, MediaStore.Video.Media.COLOR_TRANSFER)
      bucketId = CursorUtils.getStringOrNull(cursor, MediaStore.Video.Media.BUCKET_ID)
      bucketDisplayName = CursorUtils.getStringOrNull(cursor, MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
    }
  }

  fun mapImages(cursor: Cursor, includeCameraDetails: Boolean = false): List<ImageRecord> {
    val items = mutableListOf<ImageRecord>()
    while (cursor.moveToNext()) items.add(mapImageRow(cursor, includeCameraDetails))
    return items
  }

  /**
   * Bulk queries keep [includeCameraDetails] false: opening ExifInterface for
   * every row is prohibitively slow on large libraries. Single-item lookups
   * opt in; full EXIF is always available via getMetadata()/getDetailedMetadata().
   */
  fun mapImageRow(cursor: Cursor, includeCameraDetails: Boolean = false): ImageRecord {
    val data = CursorUtils.getString(cursor, MediaStore.Images.Media.DATA)
    var cameraMake: String? = null; var cameraModel: String? = null
    if (includeCameraDetails && data.isNotEmpty()) {
      try { val file = File(data); if (file.exists()) { val exif = ExifInterface(data); cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE); cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL) } } catch (_: Exception) {}
    }
    return ImageRecord().apply {
      id = CursorUtils.getLong(cursor, MediaStore.Images.Media._ID).toString()
      uri = data
      title = CursorUtils.getString(cursor, MediaStore.Images.Media.TITLE)
      width = CursorUtils.getInt(cursor, MediaStore.Images.Media.WIDTH)
      height = CursorUtils.getInt(cursor, MediaStore.Images.Media.HEIGHT)
      orientation = CursorUtils.getInt(cursor, MediaStore.Images.Media.ORIENTATION)
      this.cameraMake = cameraMake; this.cameraModel = cameraModel
      // MediaStore stores DATE_TAKEN in seconds; the JS API uses milliseconds.
      dateTaken = CursorUtils.getLong(cursor, MediaStore.Images.Media.DATE_TAKEN) * 1000L
      gpsLatitude = CursorUtils.getDoubleOrNull(cursor, MediaStore.Images.Media.LATITUDE)
      gpsLongitude = CursorUtils.getDoubleOrNull(cursor, MediaStore.Images.Media.LONGITUDE)
      mimeType = CursorUtils.getString(cursor, MediaStore.Images.Media.MIME_TYPE)
      size = CursorUtils.getLong(cursor, MediaStore.Images.Media.SIZE)
      relativePath = CursorUtils.getString(cursor, MediaStore.Images.Media.RELATIVE_PATH)
      displayName = CursorUtils.getString(cursor, MediaStore.Images.Media.DISPLAY_NAME)
      dateAdded = CursorUtils.getLong(cursor, MediaStore.Images.Media.DATE_ADDED) * 1000L
      dateModified = CursorUtils.getLong(cursor, MediaStore.Images.Media.DATE_MODIFIED) * 1000L
      bucketId = CursorUtils.getStringOrNull(cursor, MediaStore.Images.Media.BUCKET_ID)
      bucketDisplayName = CursorUtils.getStringOrNull(cursor, MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
    }
  }

  fun mapDocuments(cursor: Cursor): List<DocumentRecord> {
    val items = mutableListOf<DocumentRecord>()
    while (cursor.moveToNext()) items.add(mapDocumentRow(cursor))
    return items
  }

  fun mapDocumentRow(cursor: Cursor): DocumentRecord {
    val data = CursorUtils.getString(cursor, FileColumns.DATA)
    val mimeType = CursorUtils.getString(cursor, FileColumns.MIME_TYPE)
    return DocumentRecord().apply {
      id = CursorUtils.getLong(cursor, FileColumns._ID).toString()
      uri = data
      name = CursorUtils.getString(cursor, FileColumns.TITLE)
      size = CursorUtils.getLong(cursor, FileColumns.SIZE)
      this.mimeType = mimeType
      title = CursorUtils.getStringOrNull(cursor, FileColumns.TITLE)
      extension = MimeUtils.getExtensionFromMime(mimeType).ifEmpty { data.substringAfterLast('.', "") }
      relativePath = CursorUtils.getString(cursor, FileColumns.RELATIVE_PATH)
      dateAdded = CursorUtils.getLong(cursor, FileColumns.DATE_ADDED) * 1000L
      dateModified = CursorUtils.getLong(cursor, FileColumns.DATE_MODIFIED) * 1000L
    }
  }

  fun mapAlbums(cursor: Cursor): List<AlbumRecord> {
    val items = mutableListOf<AlbumRecord>()
    while (cursor.moveToNext()) {
      val albumId = CursorUtils.getLong(cursor, MediaStore.Audio.Albums._ID)
      items.add(AlbumRecord().apply {
        id = albumId.toString()
        title = CursorUtils.getString(cursor, MediaStore.Audio.Albums.ALBUM)
        artist = CursorUtils.getString(cursor, MediaStore.Audio.Albums.ARTIST)
        songCount = CursorUtils.getInt(cursor, MediaStore.Audio.Albums.NUMBER_OF_SONGS)
        artworkUri = ArtworkUtils.getAlbumArtworkUri(albumId).toString()
        year = CursorUtils.getIntOrNull(cursor, MediaStore.Audio.Albums.FIRST_YEAR)
      })
    }
    return items
  }

  fun mapArtists(cursor: Cursor): List<ArtistRecord> {
    val items = mutableListOf<ArtistRecord>()
    while (cursor.moveToNext()) {
      items.add(ArtistRecord().apply {
        id = CursorUtils.getLong(cursor, MediaStore.Audio.Artists._ID).toString()
        name = CursorUtils.getString(cursor, MediaStore.Audio.Artists.ARTIST)
        albumCount = CursorUtils.getInt(cursor, MediaStore.Audio.Artists.NUMBER_OF_ALBUMS)
        songCount = CursorUtils.getInt(cursor, MediaStore.Audio.Artists.NUMBER_OF_TRACKS)
      })
    }
    return items
  }

  fun mapGenres(cursor: Cursor): List<GenreRecord> {
    val items = mutableListOf<GenreRecord>()
    while (cursor.moveToNext()) {
      items.add(GenreRecord().apply {
        id = CursorUtils.getLong(cursor, MediaStore.Audio.Genres._ID).toString()
        name = CursorUtils.getString(cursor, MediaStore.Audio.Genres.NAME)
      })
    }
    return items
  }

  fun mapPlaylists(cursor: Cursor): List<PlaylistRecord> {
    val items = mutableListOf<PlaylistRecord>()
    while (cursor.moveToNext()) {
      items.add(PlaylistRecord().apply {
        id = CursorUtils.getLong(cursor, MediaStore.Audio.Playlists._ID).toString()
        name = CursorUtils.getString(cursor, MediaStore.Audio.Playlists.NAME)
        dateAdded = CursorUtils.getLong(cursor, MediaStore.Audio.Playlists.DATE_ADDED) * 1000L
        dateModified = CursorUtils.getLong(cursor, MediaStore.Audio.Playlists.DATE_MODIFIED) * 1000L
      })
    }
    return items
  }

  fun mapFolders(cursor: Cursor): List<FolderRecord> {
    val folderMap = mutableMapOf<String, FolderRecord>()
    while (cursor.moveToNext()) {
      val path = CursorUtils.getString(cursor, FileColumns.RELATIVE_PATH)
        .ifEmpty { CursorUtils.getString(cursor, FileColumns.PARENT) }
      if (path.isNotEmpty()) {
        val folder = folderMap.getOrPut(path) {
          FolderRecord().apply {
            id = path.hashCode().toString()
            name = path.trimEnd('/').substringAfterLast('/').ifEmpty { path.trimEnd('/') }
            this.path = path
          }
        }
        folder.fileCount++
        folder.totalSize += CursorUtils.getLong(cursor, FileColumns.SIZE)
      }
    }
    return folderMap.values.toList()
  }

  fun mapFolderStatistics(cursor: Cursor): List<FolderStatisticsRecord> {
    val folderMap = mutableMapOf<String, FolderStatisticsRecord>()
    while (cursor.moveToNext()) {
      val path = CursorUtils.getString(cursor, FileColumns.RELATIVE_PATH)
        .ifEmpty { CursorUtils.getString(cursor, FileColumns.PARENT) }
      val size = CursorUtils.getLong(cursor, FileColumns.SIZE)
      val mimeType = CursorUtils.getString(cursor, FileColumns.MIME_TYPE)
      if (path.isNotEmpty()) {
        val folder = folderMap.getOrPut(path) {
          FolderStatisticsRecord().apply {
            id = path.hashCode().toString()
            name = path.trimEnd('/').substringAfterLast('/').ifEmpty { path.trimEnd('/') }
            this.path = path
          }
        }
        folder.fileCount++; folder.totalSize += size
        when { size < 1_048_576L -> folder.histogram.lessThan1MB++
          size < 10_485_760L -> folder.histogram.from1to10MB++
          size < 104_857_600L -> folder.histogram.from10to100MB++
          size < 1_073_741_824L -> folder.histogram.from100MBto1GB++
          else -> folder.histogram.greaterThan1GB++ }
        when { mimeType.startsWith("audio/") -> folder.mediaTypeBreakdown.audio++
          mimeType.startsWith("video/") -> folder.mediaTypeBreakdown.video++
          mimeType.startsWith("image/") -> folder.mediaTypeBreakdown.image++
          else -> folder.mediaTypeBreakdown.document++ }
      }
    }
    return folderMap.values.map { folder -> folder.averageFileSize = if (folder.fileCount > 0) folder.totalSize.toDouble() / folder.fileCount else 0.0; folder }
  }

  fun mapSingle(cursor: Cursor, mediaType: String): Any? {
    if (!cursor.moveToFirst()) return null
    return when (mediaType) { "audio" -> mapAudioRow(cursor); "video" -> mapVideoRow(cursor); "image" -> mapImageRow(cursor, includeCameraDetails = true); "document" -> mapDocumentRow(cursor); else -> null }
  }

  fun mapGenericToAudio(cursor: Cursor): List<AudioRecord> { val items = mutableListOf<AudioRecord>(); while (cursor.moveToNext()) items.add(mapAudioRow(cursor)); return items }
  fun mapGenericToVideo(cursor: Cursor): List<VideoRecord> { val items = mutableListOf<VideoRecord>(); while (cursor.moveToNext()) items.add(mapVideoRow(cursor)); return items }
  fun mapGenericToImage(cursor: Cursor): List<ImageRecord> { val items = mutableListOf<ImageRecord>(); while (cursor.moveToNext()) items.add(mapImageRow(cursor)); return items }
  fun mapGenericToDocument(cursor: Cursor): List<DocumentRecord> { val items = mutableListOf<DocumentRecord>(); while (cursor.moveToNext()) items.add(mapDocumentRow(cursor)); return items }

  fun detectMediaType(uri: String): String = when { uri.contains("audio") -> "audio"; uri.contains("video") -> "video"; uri.contains("images") -> "image"; else -> "document" }
}
