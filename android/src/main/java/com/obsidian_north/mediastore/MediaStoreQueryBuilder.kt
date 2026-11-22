package com.obsidian_north.mediastore

import android.os.Build
import android.provider.MediaStore
import com.obsidian_north.mediastore.models.FilterOptionsRecord
import com.obsidian_north.mediastore.models.PaginationOptionsRecord
import com.obsidian_north.mediastore.models.SortOptionsRecord
import com.obsidian_north.mediastore.utils.MimeUtils

class MediaStoreQueryBuilder {
  fun buildAudioProjection(): Array<String> {
    val columns = mutableListOf(
      MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
      MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
      MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
      MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.TRACK,
      MediaStore.Audio.Media.YEAR,
      MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.DATE_MODIFIED,
      MediaStore.Audio.Media.BITRATE, MediaStore.Audio.Media.MIME_TYPE,
      MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DISPLAY_NAME,
      MediaStore.Audio.Media.COMPOSER, MediaStore.Audio.Media.BOOKMARK,
      MediaStore.Audio.Media.IS_MUSIC, MediaStore.Audio.Media.IS_PODCAST,
      MediaStore.Audio.Media.IS_RINGTONE, MediaStore.Audio.Media.IS_ALARM,
      MediaStore.Audio.Media.IS_NOTIFICATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      columns.add(MediaStore.Audio.Media.RELATIVE_PATH)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      columns.add(MediaStore.Audio.Media.WRITER)
      columns.add(MediaStore.Audio.Media.ALBUM_ARTIST)
      columns.add(MediaStore.Audio.Media.DISC_NUMBER)
      columns.add(MediaStore.Audio.Media.IS_FAVORITE)
      columns.add(MediaStore.Audio.Media.CD_TRACK_NUMBER)
      columns.add(MediaStore.Audio.Media.NUM_TRACKS)
    }
    return columns.toTypedArray()
  }

  fun buildVideoProjection(): Array<String> {
    val columns = mutableListOf(
      MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE,
      MediaStore.Video.Media.DURATION, MediaStore.Video.Media.WIDTH,
      MediaStore.Video.Media.HEIGHT, MediaStore.Video.Media.SIZE,
      MediaStore.Video.Media.MIME_TYPE, MediaStore.Video.Media.DATA,
      MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DATE_ADDED,
      MediaStore.Video.Media.DATE_MODIFIED, MediaStore.Video.Media.RESOLUTION,
      MediaStore.Video.Media.ORIENTATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      columns.add(MediaStore.Video.Media.DESCRIPTION)
      columns.add(MediaStore.Video.Media.TAGS)
      columns.add(MediaStore.Video.Media.RELATIVE_PATH)
      columns.add(MediaStore.Video.Media.BUCKET_ID)
      columns.add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      columns.add(MediaStore.Video.Media.COLOR_STANDARD)
      columns.add(MediaStore.Video.Media.COLOR_TRANSFER)
    }
    return columns.toTypedArray()
  }

  fun buildImageProjection(): Array<String> {
    val columns = mutableListOf(
      MediaStore.Images.Media._ID, MediaStore.Images.Media.TITLE,
      MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT,
      MediaStore.Images.Media.ORIENTATION, MediaStore.Images.Media.SIZE,
      MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.DATA,
      MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED,
      MediaStore.Images.Media.DATE_MODIFIED, MediaStore.Images.Media.DATE_TAKEN,
      MediaStore.Images.Media.LATITUDE, MediaStore.Images.Media.LONGITUDE
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      columns.add(MediaStore.Images.Media.DESCRIPTION)
      columns.add(MediaStore.Images.Media.RELATIVE_PATH)
      columns.add(MediaStore.Images.Media.BUCKET_ID)
      columns.add(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
    }
    return columns.toTypedArray()
  }

  fun buildDocumentProjection(): Array<String> {
    val columns = mutableListOf(
      MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.TITLE,
      MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.MIME_TYPE,
      MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.DISPLAY_NAME,
      MediaStore.Files.FileColumns.DATE_ADDED, MediaStore.Files.FileColumns.DATE_MODIFIED
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      columns.add(MediaStore.Files.FileColumns.RELATIVE_PATH)
    }
    return columns.toTypedArray()
  }

  fun buildAlbumProjection() = arrayOf(
    MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM,
    MediaStore.Audio.Albums.ARTIST, MediaStore.Audio.Albums.NUMBER_OF_SONGS,
    MediaStore.Audio.Albums.FIRST_YEAR, MediaStore.Audio.Albums.LAST_YEAR
  )

  fun buildArtistProjection() = arrayOf(
    MediaStore.Audio.Artists._ID, MediaStore.Audio.Artists.ARTIST,
    MediaStore.Audio.Artists.NUMBER_OF_ALBUMS, MediaStore.Audio.Artists.NUMBER_OF_TRACKS
  )

  fun buildGenreProjection() = arrayOf(
    MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME
  )

  fun buildPlaylistProjection() = arrayOf(
    MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME,
    MediaStore.Audio.Playlists.DATE_ADDED, MediaStore.Audio.Playlists.DATE_MODIFIED
  )

  fun buildFilter(filter: FilterOptionsRecord?): String? {
    if (filter == null) return null
    val conditions = mutableListOf<String>()

    filter.mimeTypes?.let { types ->
      if (types.isNotEmpty()) {
        conditions.add("${MediaStore.MediaColumns.MIME_TYPE} IN (${types.joinToString(", ") { "'${escapeSql(it)}'" }})")
      }
    }
    filter.extensions?.let { exts ->
      if (exts.isNotEmpty()) {
        conditions.add("(${exts.joinToString(" OR ") { "${MediaStore.MediaColumns.DATA} LIKE '%.${escapeSql(it)}'" }})")
      }
    }
    filter.folder?.let { conditions.add("${MediaStore.MediaColumns.RELATIVE_PATH} LIKE '${escapeSql(escapeLike(it))}%'") }
    filter.album?.let { conditions.add("${MediaStore.Audio.Media.ALBUM} = '${escapeSql(it)}'") }
    filter.artist?.let { conditions.add("${MediaStore.Audio.Media.ARTIST} = '${escapeSql(it)}'") }
    filter.minDuration?.let { conditions.add("${MediaStore.Audio.Media.DURATION} >= $it") }
    filter.maxDuration?.let { conditions.add("${MediaStore.Audio.Media.DURATION} <= $it") }
    filter.minSize?.let { conditions.add("${MediaStore.MediaColumns.SIZE} >= $it") }
    filter.maxSize?.let { conditions.add("${MediaStore.MediaColumns.SIZE} <= $it") }
    filter.startDate?.let { conditions.add("${MediaStore.MediaColumns.DATE_ADDED} >= $it") }
    filter.endDate?.let { conditions.add("${MediaStore.MediaColumns.DATE_ADDED} <= $it") }
    filter.favoritesOnly?.let { if (it) conditions.add("${MediaStore.MediaColumns.IS_FAVORITE} = 1") }
    filter.minResolution?.let { conditions.add("(${MediaStore.Video.Media.WIDTH} * ${MediaStore.Video.Media.HEIGHT}) >= $it") }
    filter.maxResolution?.let { conditions.add("(${MediaStore.Video.Media.WIDTH} * ${MediaStore.Video.Media.HEIGHT}) <= $it") }
    // IS_TRASHED was added in API 30; older providers do not have the column.
    // It also is not a true "hidden" flag, so only apply it where it exists.
    filter.includeHidden?.let {
      if (!it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        conditions.add("${MediaStore.MediaColumns.IS_TRASHED} = 0")
      }
    }

    return if (conditions.isNotEmpty()) conditions.joinToString(" AND ") else null
  }

  fun buildAlbumFilter(filter: FilterOptionsRecord?): String? {
    if (filter == null) return null
    val conditions = mutableListOf<String>()
    filter.artist?.let { conditions.add("${MediaStore.Audio.Albums.ARTIST} = '${escapeSql(it)}'") }
    filter.startDate?.let { conditions.add("${MediaStore.Audio.Albums._ID} IN (SELECT DISTINCT ${MediaStore.Audio.Media.ALBUM_ID} FROM ${MediaStore.Audio.Media.EXTERNAL_CONTENT_URI} WHERE ${MediaStore.Audio.Media.DATE_ADDED} >= $it)") }
    filter.endDate?.let { conditions.add("${MediaStore.Audio.Albums._ID} IN (SELECT DISTINCT ${MediaStore.Audio.Media.ALBUM_ID} FROM ${MediaStore.Audio.Media.EXTERNAL_CONTENT_URI} WHERE ${MediaStore.Audio.Media.DATE_ADDED} <= $it)") }
    return if (conditions.isNotEmpty()) conditions.joinToString(" AND ") else null
  }

  fun buildDocumentFilter(filter: FilterOptionsRecord?): String {
    val conditions = mutableListOf<String>()
    val quotedMimes = MimeUtils.getDocumentMimeTypes().joinToString(", ") { "'$it'" }
    conditions.add("${MediaStore.MediaColumns.MIME_TYPE} IN ($quotedMimes)")

    if (filter != null) {
      filter.minSize?.let { conditions.add("${MediaStore.MediaColumns.SIZE} >= $it") }
      filter.maxSize?.let { conditions.add("${MediaStore.MediaColumns.SIZE} <= $it") }
      filter.startDate?.let { conditions.add("${MediaStore.MediaColumns.DATE_ADDED} >= $it") }
      filter.endDate?.let { conditions.add("${MediaStore.MediaColumns.DATE_ADDED} <= $it") }
      filter.folder?.let { conditions.add("${MediaStore.MediaColumns.RELATIVE_PATH} LIKE '${escapeSql(escapeLike(it))}%'") }
      filter.extensions?.let { exts ->
        if (exts.isNotEmpty()) {
          conditions.add("(${exts.joinToString(" OR ") { "${MediaStore.MediaColumns.DATA} LIKE '%.${escapeSql(it)}'" }})")
        }
      }
    }

    return conditions.joinToString(" AND ")
  }

  fun buildSortOrder(sort: SortOptionsRecord?): String? {
    if (sort == null) return null
    val direction = if (sort.order == "desc") "DESC" else "ASC"
    val column = when (sort.field) {
      "name" -> MediaStore.MediaColumns.TITLE
      "dateAdded" -> MediaStore.MediaColumns.DATE_ADDED
      "dateModified" -> MediaStore.MediaColumns.DATE_MODIFIED
      "duration" -> MediaStore.Audio.Media.DURATION
      "artist" -> MediaStore.Audio.Media.ARTIST
      "album" -> MediaStore.Audio.Media.ALBUM
      "year" -> MediaStore.Audio.Media.YEAR
      "fileSize" -> MediaStore.MediaColumns.SIZE
      "resolution" -> MediaStore.Video.Media.RESOLUTION
      "width" -> MediaStore.Video.Media.WIDTH
      "height" -> MediaStore.Video.Media.HEIGHT
      else -> MediaStore.MediaColumns.TITLE
    }
    return "$column $direction"
  }

  fun buildPagination(pagination: PaginationOptionsRecord?): String? {
    if (pagination == null) return null
    val parts = mutableListOf<String>()
    pagination.limit?.let { parts.add("LIMIT $it") }
    pagination.offset?.let { parts.add("OFFSET $it") }
    return if (parts.isNotEmpty()) parts.joinToString(" ") else null
  }

  fun applyPagination(sortOrder: String?, pagination: PaginationOptionsRecord?): String {
    val base = sortOrder ?: "${MediaStore.MediaColumns.TITLE} ASC"
    val paginationClause = buildPagination(pagination)
    return if (paginationClause != null) "$base $paginationClause" else base
  }

  fun escapeLike(value: String): String = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
  private fun escapeSql(value: String): String = value.replace("'", "''")
}
