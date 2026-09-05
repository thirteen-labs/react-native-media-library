package com.obsidian_north.mediastore

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.obsidian_north.mediastore.models.*
import com.obsidian_north.mediastore.utils.CursorUtils
import com.obsidian_north.mediastore.utils.MimeUtils
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class MediaStoreModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
  private val moduleScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
  private val context: Context get() = reactApplicationContext

  private val repository by lazy { MediaStoreRepository(context) }
  private val mapper by lazy { MediaStoreMapper() }
  private val queryBuilder by lazy { MediaStoreQueryBuilder() }
  private val permissions by lazy { MediaStorePermissions(context, reactApplicationContext) }
  private val observer by lazy { MediaStoreObserver(context) }
  private val cache by lazy { MediaStoreCache() }
  private var isObserving = false

  override fun getName(): String = "MediaStore"

  // --- ReadableMap parsing helpers ---

  private fun parseSort(map: ReadableMap?): SortOptionsRecord? {
    if (map == null) return null
    return SortOptionsRecord().apply { field = map.getString("field") ?: "name"; order = map.getString("order") ?: "asc" }
  }

  private fun parseFilter(map: ReadableMap?): FilterOptionsRecord? {
    if (map == null) return null
    return FilterOptionsRecord().apply {
      if (map.hasKey("mimeTypes")) { val a = map.getArray("mimeTypes"); mimeTypes = (0 until (a?.size() ?: 0)).map { a?.getString(it) ?: "" } }
      if (map.hasKey("extensions")) { val a = map.getArray("extensions"); extensions = (0 until (a?.size() ?: 0)).map { a?.getString(it) ?: "" } }
      if (map.hasKey("folder")) folder = map.getString("folder")
      if (map.hasKey("album")) album = map.getString("album")
      if (map.hasKey("artist")) artist = map.getString("artist")
      if (map.hasKey("minDuration")) minDuration = map.getDouble("minDuration").toLong()
      if (map.hasKey("maxDuration")) maxDuration = map.getDouble("maxDuration").toLong()
      if (map.hasKey("minSize")) minSize = map.getDouble("minSize").toLong()
      if (map.hasKey("maxSize")) maxSize = map.getDouble("maxSize").toLong()
      if (map.hasKey("minResolution")) minResolution = map.getInt("minResolution")
      if (map.hasKey("maxResolution")) maxResolution = map.getInt("maxResolution")
      if (map.hasKey("startDate")) startDate = map.getDouble("startDate").toLong()
      if (map.hasKey("endDate")) endDate = map.getDouble("endDate").toLong()
      if (map.hasKey("includeHidden")) includeHidden = map.getBoolean("includeHidden")
      if (map.hasKey("favoritesOnly")) favoritesOnly = map.getBoolean("favoritesOnly")
      if (map.hasKey("playlistId")) playlistId = map.getString("playlistId")
    }
  }

  private fun parsePagination(map: ReadableMap?): PaginationOptionsRecord? {
    if (map == null) return null
    return PaginationOptionsRecord().apply {
      if (map.hasKey("limit")) limit = map.getInt("limit")
      if (map.hasKey("offset")) offset = map.getInt("offset")
      if (map.hasKey("cursor")) cursor = map.getString("cursor")
    }
  }

  private fun parseSearchOptions(map: ReadableMap): SearchOptionsRecord {
    return SearchOptionsRecord().apply {
      query = map.getString("query") ?: ""
      if (map.hasKey("types")) { val a = map.getArray("types"); types = (0 until (a?.size() ?: 0)).map { a?.getString(it) ?: "" } }
      if (map.hasKey("sort")) sort = parseSort(map.getMap("sort"))
      if (map.hasKey("filter")) filter = parseFilter(map.getMap("filter"))
      if (map.hasKey("pagination")) pagination = parsePagination(map.getMap("pagination"))
    }
  }

  // --- Result conversion helpers ---

  private fun <T> List<T>.toWritableArray(transform: (T) -> Map<String, Any?>): WritableArray {
    return Arguments.createArray().apply { this@toWritableArray.forEach { pushMap(Arguments.makeNativeMap(transform(it))) } }
  }

  private fun Map<String, Any?>.toWritableMap(): WritableMap = Arguments.makeNativeMap(this)

  // --- Module Methods ---

  private fun <T> coroutineMethod(block: suspend CoroutineScope.() -> T, promise: Promise) {
    moduleScope.launch(Dispatchers.IO) {
      try { val result = block(); withContext(Dispatchers.Main) { promise.resolve(result) } }
      catch (e: Exception) { withContext(Dispatchers.Main) { promise.reject("QUERY_FAILED", e.message, e) } }
    }
  }

  @ReactMethod
  fun getAudio(sort: ReadableMap?, filter: ReadableMap?, pagination: ReadableMap?, promise: Promise) {
    coroutineMethod({
      val s = parseSort(sort); val f = parseFilter(filter); val p = parsePagination(pagination)
      val cacheKey = "audio:${s}:${f}:${p}"
      cache.get(cacheKey)?.let { return@coroutineMethod it }
      val sortOrder = queryBuilder.applyPagination(queryBuilder.buildSortOrder(s), p)
      val cursor = repository.queryAudio(queryBuilder.buildAudioProjection(), queryBuilder.buildFilter(f), null, sortOrder)
      val result = cursor?.use { mapper.mapAudio(it).map { r -> r.toMap() } } ?: emptyList<Map<String, Any?>>()
      cache.put(cacheKey, result)
      result
    }, promise)
  }

  @ReactMethod
  fun getVideos(sort: ReadableMap?, filter: ReadableMap?, pagination: ReadableMap?, promise: Promise) {
    coroutineMethod({
      val s = parseSort(sort); val f = parseFilter(filter); val p = parsePagination(pagination)
      val cacheKey = "videos:${s}:${f}:${p}"
      cache.get(cacheKey)?.let { return@coroutineMethod it }
      val sortOrder = queryBuilder.applyPagination(queryBuilder.buildSortOrder(s), p)
      val cursor = repository.queryVideo(queryBuilder.buildVideoProjection(), queryBuilder.buildFilter(f), null, sortOrder)
      val result = cursor?.use { mapper.mapVideo(it).map { r -> r.toMap() } } ?: emptyList<Map<String, Any?>>()
      cache.put(cacheKey, result)
      result
    }, promise)
  }

  @ReactMethod
  fun getImages(sort: ReadableMap?, filter: ReadableMap?, pagination: ReadableMap?, promise: Promise) {
    coroutineMethod({
      val s = parseSort(sort); val f = parseFilter(filter); val p = parsePagination(pagination)
      val cacheKey = "images:${s}:${f}:${p}"
      cache.get(cacheKey)?.let { return@coroutineMethod it }
      val sortOrder = queryBuilder.applyPagination(queryBuilder.buildSortOrder(s), p)
      val cursor = repository.queryImages(queryBuilder.buildImageProjection(), queryBuilder.buildFilter(f), null, sortOrder)
      val result = cursor?.use { mapper.mapImages(it).map { r -> r.toMap() } } ?: emptyList<Map<String, Any?>>()
      cache.put(cacheKey, result)
      result
    }, promise)
  }

  @ReactMethod
  fun getDocuments(sort: ReadableMap?, filter: ReadableMap?, pagination: ReadableMap?, promise: Promise) {
    coroutineMethod({
      val s = parseSort(sort); val f = parseFilter(filter); val p = parsePagination(pagination)
      val cacheKey = "documents:${s}:${f}:${p}"
      cache.get(cacheKey)?.let { return@coroutineMethod it }
      val sortOrder = queryBuilder.applyPagination(queryBuilder.buildSortOrder(s), p)
      val cursor = repository.queryDocuments(queryBuilder.buildDocumentProjection(), queryBuilder.buildDocumentFilter(f), null, sortOrder)
      val result = cursor?.use { mapper.mapDocuments(it).map { r -> r.toMap() } } ?: emptyList<Map<String, Any?>>()
      cache.put(cacheKey, result)
      result
    }, promise)
  }

  @ReactMethod
  fun getAlbums(sort: ReadableMap?, filter: ReadableMap?, pagination: ReadableMap?, promise: Promise) {
    coroutineMethod({
      val s = parseSort(sort); val f = parseFilter(filter); val p = parsePagination(pagination)
      val cacheKey = "albums:${s}:${f}:${p}"
      cache.get(cacheKey)?.let { return@coroutineMethod it }
      val sortOrder = queryBuilder.applyPagination(queryBuilder.buildSortOrder(s), p)
      val cursor = repository.queryAlbums(queryBuilder.buildAlbumProjection(), queryBuilder.buildAlbumFilter(f), null, sortOrder)
      val result = cursor?.use { mapper.mapAlbums(it).map { r -> r.toMap() } } ?: emptyList<Map<String, Any?>>()
      cache.put(cacheKey, result)
      result
    }, promise)
  }

  @ReactMethod
  fun getArtists(sort: ReadableMap?, pagination: ReadableMap?, promise: Promise) {
    coroutineMethod({
      val s = parseSort(sort); val p = parsePagination(pagination)
      val cacheKey = "artists:${s}:${p}"
      cache.get(cacheKey)?.let { return@coroutineMethod it }
      val sortOrder = queryBuilder.applyPagination(queryBuilder.buildSortOrder(s), p)
      val cursor = repository.queryArtists(queryBuilder.buildArtistProjection(), null, null, sortOrder)
      val result = cursor?.use { mapper.mapArtists(it).map { r -> r.toMap() } } ?: emptyList<Map<String, Any?>>()
      cache.put(cacheKey, result)
      result
    }, promise)
  }

  @ReactMethod
  fun getGenres(sort: ReadableMap?, pagination: ReadableMap?, promise: Promise) {
    coroutineMethod({
      val s = parseSort(sort); val p = parsePagination(pagination)
      val cacheKey = "genres:${s}:${p}"
      cache.get(cacheKey)?.let { return@coroutineMethod it }
      val sortOrder = queryBuilder.applyPagination(queryBuilder.buildSortOrder(s), p)
      val cursor = repository.queryGenres(queryBuilder.buildGenreProjection(), null, null, sortOrder)
      val result = cursor?.use { mapper.mapGenres(it).map { r -> r.toMap() } } ?: emptyList<Map<String, Any?>>()
      cache.put(cacheKey, result)
      result
    }, promise)
  }

  @ReactMethod
  fun getPlaylists(sort: ReadableMap?, pagination: ReadableMap?, promise: Promise) {
    coroutineMethod({
      val s = parseSort(sort); val p = parsePagination(pagination)
      val cacheKey = "playlists:${s}:${p}"
      cache.get(cacheKey)?.let { return@coroutineMethod it }
      val sortOrder = queryBuilder.applyPagination(queryBuilder.buildSortOrder(s), p)
      val cursor = repository.queryPlaylists(queryBuilder.buildPlaylistProjection(), null, null, sortOrder)
      val result = cursor?.use { mapper.mapPlaylists(it).map { r -> r.toMap() } } ?: emptyList<Map<String, Any?>>()
      cache.put(cacheKey, result)
      result
    }, promise)
  }

  @ReactMethod
  fun getFolders(sort: ReadableMap?, filter: ReadableMap?, pagination: ReadableMap?, promise: Promise) {
    coroutineMethod({
      val s = parseSort(sort); val f = parseFilter(filter); val p = parsePagination(pagination)
      val cacheKey = "folders:${s}:${f}:${p}"
      cache.get(cacheKey)?.let { return@coroutineMethod it }
      val sortOrder = queryBuilder.applyPagination(queryBuilder.buildSortOrder(s), p)
      val cursor = repository.queryFolders(queryBuilder.buildDocumentFilter(f), sortOrder)
      val result = cursor?.use { mapper.mapFolders(it).map { r -> r.toMap() } } ?: emptyList<Map<String, Any?>>()
      cache.put(cacheKey, result)
      result
    }, promise)
  }

  @ReactMethod
  fun getFolderStatistics(folderPath: String?, promise: Promise) {
    coroutineMethod({
      val cacheKey = "folderStats:${folderPath}"
      cache.get(cacheKey)?.let { return@coroutineMethod it }
      val cursor = repository.queryFolderStatistics(folderPath)
      val result = cursor?.use { mapper.mapFolderStatistics(it).map { r -> r.toMap() } } ?: emptyList<Map<String, Any?>>()
      cache.put(cacheKey, result)
      result
    }, promise)
  }

  @ReactMethod
  fun refreshIncremental(lastTimestamp: Double?, promise: Promise) {
    coroutineMethod({
      val since = lastTimestamp?.toLong() ?: cache.getLastRefreshTimestamp()
      var addedCount = 0
      for (type in listOf("audio", "video", "image", "document")) {
        val projection = when (type) { "audio" -> queryBuilder.buildAudioProjection(); "video" -> queryBuilder.buildVideoProjection(); "image" -> queryBuilder.buildImageProjection(); "document" -> queryBuilder.buildDocumentProjection(); else -> continue }
        val cursor = repository.queryIncremental(since, projection, type)
        cursor?.use { addedCount += it.count }
      }
      val removedItems = cache.getAndClearRemovedItems()
      cache.setLastRefreshTimestamp(System.currentTimeMillis())
      IncrementalChangesRecord().apply { added = addedCount; removed = removedItems.size; timestamp = System.currentTimeMillis() }.toMap()
    }, promise)
  }

  @ReactMethod
  fun getLastRefreshTimestamp(promise: Promise) {
    promise.resolve(cache.getLastRefreshTimestamp().toDouble())
  }

  @ReactMethod
  fun search(options: ReadableMap, promise: Promise) {
    coroutineMethod({ repository.search(parseSearchOptions(options)).toMap() }, promise)
  }

  @ReactMethod
  fun getById(mediaType: String, id: String, promise: Promise) {
    coroutineMethod({
      val cursor = when (mediaType) { "audio" -> repository.queryAudio(queryBuilder.buildAudioProjection(), "${MediaStore.Audio.Media._ID} = ?", arrayOf(id), null); "video" -> repository.queryVideo(queryBuilder.buildVideoProjection(), "${MediaStore.Video.Media._ID} = ?", arrayOf(id), null); "image" -> repository.queryImages(queryBuilder.buildImageProjection(), "${MediaStore.Images.Media._ID} = ?", arrayOf(id), null); "document" -> repository.queryDocuments(queryBuilder.buildDocumentProjection(), "${MediaStore.Files.FileColumns._ID} = ?", arrayOf(id), null); else -> null }
      if (cursor != null) cursor.use { val item = mapper.mapSingle(it, mediaType); when (item) { is AudioRecord -> item.toMap(); is VideoRecord -> item.toMap(); is ImageRecord -> item.toMap(); is DocumentRecord -> item.toMap(); else -> null } } else null
    }, promise)
  }

  @ReactMethod
  fun getByUri(uri: String, promise: Promise) {
    coroutineMethod({
      val mediaType = mapper.detectMediaType(uri)
      val cursor = repository.queryByUri(uri)
      if (cursor != null) cursor.use { val item = mapper.mapSingle(it, mediaType); when (item) { is AudioRecord -> item.toMap(); is VideoRecord -> item.toMap(); is ImageRecord -> item.toMap(); is DocumentRecord -> item.toMap(); else -> null } } else null
    }, promise)
  }

  @ReactMethod
  fun getDetailedMetadata(mediaType: String, id: String, promise: Promise) {
    coroutineMethod({
      val result = repository.metadataService.extractMetadata(mediaType, id)
      mapOf(
        "metadata" to result.metadata,
        "status" to result.status.jsValue,
        "warnings" to result.warnings,
        "errorCode" to (result.errorCode?.jsCode),
      )
    }, promise)
  }

  @ReactMethod
  fun getDetailedMetadataByUri(uri: String, promise: Promise) {
    coroutineMethod({
      val result = repository.metadataService.extractMetadataByUri(uri)
      mapOf(
        "metadata" to result.metadata,
        "status" to result.status.jsValue,
        "warnings" to result.warnings,
        "errorCode" to (result.errorCode?.jsCode),
      )
    }, promise)
  }

  @ReactMethod
  fun getMetadata(uri: String, options: ReadableMap?, promise: Promise) {
    coroutineMethod({
      val level = options?.getString("level") ?: "full"
      val result = repository.metadataService.extractMetadataByUri(uri)
      val metadata = result.metadata

      val filtered = when (level) {
        "basic" -> filterMetadataBasic(metadata)
        "standard" -> filterMetadataStandard(metadata)
        else -> metadata
      }

      mapOf(
        "metadata" to filtered,
        "status" to result.status.jsValue,
        "warnings" to result.warnings,
        "errorCode" to (result.errorCode?.jsCode),
      )
    }, promise)
  }

  @ReactMethod
  fun getArtworkUri(albumId: String, promise: Promise) {
    coroutineMethod({
      val uri = repository.metadataService.getArtworkUri(albumId)
      mapOf("uri" to uri?.toString())
    }, promise)
  }

  @ReactMethod
  fun getArtworkBytes(albumId: String, promise: Promise) {
    coroutineMethod({
      val bytes = repository.metadataService.getArtworkBytes(albumId)
      if (bytes != null) {
        val artworkDir = File(context.cacheDir, "mediastore_artwork")
        artworkDir.mkdirs()
        val artworkFile = File(artworkDir, "album_$albumId.jpg")
        artworkFile.writeBytes(bytes)
        mapOf("uri" to artworkFile.toURI().toString(), "size" to bytes.size)
      } else {
        mapOf("uri" to null, "size" to 0)
      }
    }, promise)
  }

  @ReactMethod
  fun inspectMetadata(uri: String, promise: Promise) {
    coroutineMethod({ repository.metadataService.inspectMetadata(uri) }, promise)
  }

  @ReactMethod
  fun cancelMetadataExtraction(jobId: String, promise: Promise) {
    repository.metadataService.getQueue()?.cancel(jobId)
    promise.resolve(true)
  }

  @ReactMethod
  fun cancelAllMetadataExtraction(promise: Promise) {
    repository.metadataService.getQueue()?.cancelAll()
    promise.resolve(true)
  }

  @ReactMethod
  fun getRecent(mediaType: String?, limit: Int?, promise: Promise) {
    coroutineMethod({
      val actualLimit = limit ?: 50
      val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC LIMIT $actualLimit"
      val results = mutableListOf<Map<String, Any?>>()
      val types = if (mediaType != null) listOf(mediaType) else listOf("audio", "video", "image", "document")
      for (type in types) {
        val cursor = when (type) { "audio" -> repository.queryAudio(queryBuilder.buildAudioProjection(), null, null, sortOrder); "video" -> repository.queryVideo(queryBuilder.buildVideoProjection(), null, null, sortOrder); "image" -> repository.queryImages(queryBuilder.buildImageProjection(), null, null, sortOrder); "document" -> repository.queryDocuments(queryBuilder.buildDocumentProjection(), null, null, sortOrder); else -> null }
        if (cursor != null) { when (type) { "audio" -> cursor.use { results.addAll(mapper.mapGenericToAudio(it).map { r -> r.toMap() }) }; "video" -> cursor.use { results.addAll(mapper.mapGenericToVideo(it).map { r -> r.toMap() }) }; "image" -> cursor.use { results.addAll(mapper.mapGenericToImage(it).map { r -> r.toMap() }) }; "document" -> cursor.use { results.addAll(mapper.mapGenericToDocument(it).map { r -> r.toMap() }) } } }
      }
      results
    }, promise)
  }

  @ReactMethod
  fun getFavorites(mediaType: String?, sort: ReadableMap?, pagination: ReadableMap?, promise: Promise) {
    coroutineMethod({
      val s = parseSort(sort); val p = parsePagination(pagination)
      val favoriteSelection = "${MediaStore.MediaColumns.IS_FAVORITE} = 1"
      val sortOrder = queryBuilder.applyPagination(queryBuilder.buildSortOrder(s), p)
      val results = mutableListOf<Map<String, Any?>>()
      val types = if (mediaType != null) listOf(mediaType) else listOf("audio", "video", "image", "document")
      for (type in types) {
        val cursor = when (type) { "audio" -> repository.queryAudio(queryBuilder.buildAudioProjection(), favoriteSelection, null, sortOrder); "video" -> repository.queryVideo(queryBuilder.buildVideoProjection(), favoriteSelection, null, sortOrder); "image" -> repository.queryImages(queryBuilder.buildImageProjection(), favoriteSelection, null, sortOrder); "document" -> repository.queryDocuments(queryBuilder.buildDocumentProjection(), favoriteSelection, null, sortOrder); else -> null }
        if (cursor != null) { when (type) { "audio" -> cursor.use { results.addAll(mapper.mapGenericToAudio(it).map { r -> r.toMap() }) }; "video" -> cursor.use { results.addAll(mapper.mapGenericToVideo(it).map { r -> r.toMap() }) }; "image" -> cursor.use { results.addAll(mapper.mapGenericToImage(it).map { r -> r.toMap() }) }; "document" -> cursor.use { results.addAll(mapper.mapGenericToDocument(it).map { r -> r.toMap() }) } } }
      }
      results
    }, promise)
  }

  @ReactMethod
  fun getLargestFiles(mediaType: String?, limit: Int?, promise: Promise) {
    coroutineMethod({
      val actualLimit = limit ?: 50
      val sortOrder = "${MediaStore.MediaColumns.SIZE} DESC LIMIT $actualLimit"
      val results = mutableListOf<Map<String, Any?>>()
      val types = if (mediaType != null) listOf(mediaType) else listOf("audio", "video", "image", "document")
      for (type in types) {
        val cursor = when (type) { "audio" -> repository.queryAudio(queryBuilder.buildAudioProjection(), null, null, sortOrder); "video" -> repository.queryVideo(queryBuilder.buildVideoProjection(), null, null, sortOrder); "image" -> repository.queryImages(queryBuilder.buildImageProjection(), null, null, sortOrder); "document" -> repository.queryDocuments(queryBuilder.buildDocumentProjection(), null, null, sortOrder); else -> null }
        if (cursor != null) { when (type) { "audio" -> cursor.use { results.addAll(mapper.mapGenericToAudio(it).map { r -> r.toMap() }) }; "video" -> cursor.use { results.addAll(mapper.mapGenericToVideo(it).map { r -> r.toMap() }) }; "image" -> cursor.use { results.addAll(mapper.mapGenericToImage(it).map { r -> r.toMap() }) }; "document" -> cursor.use { results.addAll(mapper.mapGenericToDocument(it).map { r -> r.toMap() }) } } }
      }
      results
    }, promise)
  }

  @ReactMethod
  fun getDuplicates(mediaType: String?, promise: Promise) {
    coroutineMethod({ findDuplicates(mediaType).map { it.toMap() } }, promise)
  }

  @ReactMethod
  fun getStatistics(promise: Promise) {
    coroutineMethod({ repository.getStatistics().toMap() }, promise)
  }

  @ReactMethod
  fun refresh(promise: Promise) {
    cache.invalidate()
    repository.metadataService.getCache().invalidateAll()
    promise.resolve(null)
  }

  @ReactMethod
  fun getPathByUri(uri: String, promise: Promise) {
    coroutineMethod({ repository.getPathByUri(uri) }, promise)
  }

  @ReactMethod
  fun checkPermissions(promise: Promise) {
    promise.resolve(Arguments.makeNativeMap(permissions.checkStatus()))
  }

  @ReactMethod
  fun requestPermissions(promise: Promise) {
    moduleScope.launch(Dispatchers.Main) {
      try { val result = withContext(Dispatchers.IO) { permissions.request() }; promise.resolve(Arguments.makeNativeMap(result)) }
      catch (e: Exception) { promise.reject("PERMISSION_DENIED", e.message, e) }
    }
  }

  @ReactMethod
  fun getAlbumArtwork(albumId: String?, promise: Promise) {
    coroutineMethod({ extractAlbumArtwork(albumId.orEmpty()) }, promise)
  }

  @ReactMethod
  fun getVideoThumbnail(videoId: String, width: Int?, height: Int?, promise: Promise) {
    coroutineMethod({ generateThumbnail(videoId, "video", width, height) }, promise)
  }

  @ReactMethod
  fun getImageThumbnail(imageId: String, width: Int?, height: Int?, promise: Promise) {
    coroutineMethod({ generateThumbnail(imageId, "image", width, height) }, promise)
  }

  // --- Event System ---

  @ReactMethod
  fun addListener(eventName: String) {
    if (eventName == "onMediaChange" && !isObserving) {
      isObserving = true
      observer.startListening { event ->
        reactApplicationContext
          .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
          .emit("onMediaChange", Arguments.makeNativeMap(mapOf(
            "type" to event.type, "mediaType" to event.mediaType,
            "itemId" to event.itemId, "uri" to event.uri
          )))
        if (event.type == "removed") cache.trackRemoved(event.itemId)
        cache.invalidate()
      }
    }
  }

  @ReactMethod
  fun removeListeners(count: Int) {
    if (isObserving) {
      isObserving = false
      observer.stopListening()
    }
  }

  // --- Internal Methods ---

  private fun filterMetadataBasic(metadata: Map<String, Any?>): Map<String, Any?> {
    return mapOf(
      "mediaType" to metadata["mediaType"],
      "mimeType" to metadata["mimeType"],
      "fileSize" to metadata["fileSize"],
      "durationMs" to metadata["durationMs"],
      "containerFormat" to metadata["containerFormat"],
    )
  }

  private fun filterMetadataStandard(metadata: Map<String, Any?>): Map<String, Any?> {
    val result = mutableMapOf<String, Any?>()
    for (key in listOf("mediaType", "mimeType", "fileSize", "durationMs", "containerFormat")) {
      metadata[key]?.let { result[key] = it }
    }
    val audio = metadata["audio"] as? Map<*, *>
    if (audio != null) {
      val standardAudio = mutableMapOf<String, Any?>()
      for (key in listOf("title", "artist", "album", "albumArtist", "genre", "trackNumber", "discNumber", "year")) {
        audio[key]?.let { standardAudio[key] = it }
      }
      if (standardAudio.isNotEmpty()) result["audio"] = standardAudio
    }
    val video = metadata["video"] as? Map<*, *>
    if (video != null) {
      val standardVideo = mutableMapOf<String, Any?>()
      for (key in listOf("width", "height", "rotation", "codec")) {
        video[key]?.let { standardVideo[key] = it }
      }
      if (standardVideo.isNotEmpty()) result["video"] = standardVideo
    }
    val image = metadata["image"] as? Map<*, *>
    if (image != null) {
      val standardImage = mutableMapOf<String, Any?>()
      for (key in listOf("width", "height", "format")) {
        image[key]?.let { standardImage[key] = it }
      }
      if (standardImage.isNotEmpty()) result["image"] = standardImage
    }
    metadata["artwork"]?.let { result["artwork"] = it }
    return result
  }

  private suspend fun generateThumbnail(mediaId: String, mediaType: String, width: Int?, height: Int?): String? {
    return withContext(Dispatchers.IO) {
      try {
        val contentUri = when (mediaType) { "video" -> Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId); "image" -> Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId); else -> return@withContext null }
        val targetWidth = width ?: 320; val targetHeight = height ?: 240

        // 1) Try Media3 FrameExtractor (modern, HDR-aware, effect-driven downscale).
        // Falls back to platform loadThumbnail if Media3 not present or fails.
        var bitmap: Bitmap? = null
        try {
          bitmap = com.obsidian_north.mediastore.metadata.Media3InspectorExtractor.extractFrame(
            context, contentUri, 0L, targetWidth, targetHeight
          )
        } catch (_: Exception) { bitmap = null }

        // 2) Legacy platform path
        if (bitmap == null) {
          bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(contentUri, android.util.Size(targetWidth, targetHeight), CancellationSignal())
          } else {
            val idLong = mediaId.toLongOrNull() ?: return@withContext null
            @Suppress("DEPRECATION")
            when (mediaType) { "video" -> MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, idLong, MediaStore.Video.Thumbnails.MINI_KIND, null)
              else -> MediaStore.Images.Thumbnails.getThumbnail(context.contentResolver, idLong, MediaStore.Images.Thumbnails.MINI_KIND, null) }
          }
        }
        if (bitmap != null) {
          val thumbDir = File(context.cacheDir, "mediastore_thumbnails"); thumbDir.mkdirs()
          val thumbFile = File(thumbDir, "${mediaType}_${mediaId}_${targetWidth}x${targetHeight}.jpg")
          FileOutputStream(thumbFile).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
          bitmap.recycle()
          thumbFile.toURI().toString()
        } else null
      } catch (e: Exception) { null }
    }
  }

  private suspend fun extractAlbumArtwork(albumId: String): String? = withContext(Dispatchers.IO) {
    try {
      val projection = arrayOf(MediaStore.Audio.Media.DATA)
      val selection = "${MediaStore.Audio.Media.ALBUM_ID} = ?"
      val cursor = repository.queryAudio(projection, selection, arrayOf(albumId), null)
      var path: String? = null
      cursor?.use { if (it.moveToFirst()) path = CursorUtils.getString(it, MediaStore.Audio.Media.DATA) }
      val filePath = path ?: return@withContext null

      val retriever = MediaMetadataRetriever()
      retriever.setDataSource(filePath)
      val picture = retriever.embeddedPicture
      retriever.release()
      if (picture == null) return@withContext null

      val artworkDir = File(context.cacheDir, "mediastore_artwork"); artworkDir.mkdirs()
      val artworkFile = File(artworkDir, "album_$albumId.jpg")
      artworkFile.writeBytes(picture)
      artworkFile.toURI().toString()
    } catch (e: Exception) { null }
  }

  private suspend fun findDuplicates(mediaType: String?): List<DuplicateRecord> {
    return withContext(Dispatchers.IO) {
      val types = if (mediaType != null) listOf(mediaType) else listOf("audio", "video", "image")
      val allFiles = mutableListOf<Triple<String, Long, String>>()
      for (type in types) {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.SIZE)
        val cursor = when (type) { "audio" -> repository.queryAudio(projection, "${MediaStore.MediaColumns.SIZE} > 0", null, null); "video" -> repository.queryVideo(projection, "${MediaStore.MediaColumns.SIZE} > 0", null, null); "image" -> repository.queryImages(projection, "${MediaStore.MediaColumns.SIZE} > 0", null, null); else -> null }
        cursor?.use { while (it.moveToNext()) { val id = it.getLong(0).toString(); val path = it.getString(1) ?: continue; val size = it.getLong(2); allFiles.add(Triple(id, size, path)) } }
      }
      val sizeGroups = allFiles.groupBy { it.second }
      val duplicates = mutableListOf<DuplicateRecord>()
      for ((size, files) in sizeGroups) {
        if (files.size < 2) continue
        val hashGroups = mutableMapOf<String, MutableList<Triple<String, Long, String>>>()
        for (file in files) { try { val hash = computeFileHash(file.third); hashGroups.getOrPut(hash) { mutableListOf() }.add(file) } catch (_: Exception) {} }
        for ((hash, group) in hashGroups) { if (group.size < 2) continue; duplicates.add(DuplicateRecord().apply { fileHash = hash; count = group.size; totalSize = size * group.size }) }
      }
      duplicates.sortedByDescending { it.count }
    }
  }

  private fun computeFileHash(filePath: String): String {
    val file = File(filePath); if (!file.exists() || !file.canRead()) return ""
    val digest = MessageDigest.getInstance("MD5"); val buffer = ByteArray(8192)
    try { file.inputStream().use { input -> var bytesRead: Int; while (input.read(buffer).also { bytesRead = it } != -1) digest.update(buffer, 0, bytesRead) } } catch (_: Exception) { return "" }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  // --- File System Operations ---

  @ReactMethod
  fun fileExists(filePath: String, promise: Promise) {
    coroutineMethod({ File(filePath).exists() }, promise)
  }

  @ReactMethod
  fun readDirectory(dirPath: String, promise: Promise) {
    coroutineMethod({
      val dir = File(dirPath)
      if (!dir.exists() || !dir.isDirectory) return@coroutineMethod emptyList<String>()
      dir.listFiles()?.map { it.absolutePath } ?: emptyList<String>()
    }, promise)
  }

  @ReactMethod
  fun readDirectoryRecursive(dirPath: String, promise: Promise) {
    coroutineMethod({
      val dir = File(dirPath)
      if (!dir.exists() || !dir.isDirectory) return@coroutineMethod emptyList<String>()
      val result = mutableListOf<String>()
      recursiveListFiles(dir, result)
      result
    }, promise)
  }

  private suspend fun recursiveListFiles(dir: File, result: MutableList<String>) {
    val files = dir.listFiles() ?: return
    for (file in files) {
      if (file.isDirectory) {
        recursiveListFiles(file, result)
      } else {
        result.add(file.absolutePath)
      }
    }
  }

  @ReactMethod
  fun fileSize(filePath: String, promise: Promise) {
    coroutineMethod({
      val file = File(filePath)
      if (!file.exists()) return@coroutineMethod 0L
      file.length()
    }, promise)
  }

  @ReactMethod
  fun createFile(filePath: String, promise: Promise) {
    coroutineMethod({
      val file = File(filePath)
      file.parentFile?.mkdirs()
      if (file.createNewFile()) {
        file.toURI().toString()
      } else {
        ""
      }
    }, promise)
  }

  @ReactMethod
  fun renameFile(oldPath: String, newPath: String, promise: Promise) {
    coroutineMethod({
      val file = File(oldPath)
      if (file.renameTo(File(newPath))) {
        newPath
      } else {
        ""
      }
    }, promise)
  }

  @ReactMethod
  fun deleteFile(filePath: String, promise: Promise) {
    coroutineMethod({
      val file = File(filePath)
      file.delete()
    }, promise)
  }

  @ReactMethod
  fun copyFile(srcPath: String, dstPath: String, promise: Promise) {
    coroutineMethod({
      val src = File(srcPath)
      val dst = File(dstPath)
      dst.parentFile?.mkdirs()
      if (src.exists()) {
        dst.writeBytes(src.readBytes())
        dst.absolutePath
      } else ""
    }, promise)
  }

  @ReactMethod
  fun moveFile(srcPath: String, dstPath: String, promise: Promise) {
    coroutineMethod({
      val src = File(srcPath)
      val dst = File(dstPath)
      dst.parentFile?.mkdirs()
      if (src.renameTo(dst)) {
        dst.absolutePath
      } else ""
    }, promise)
  }

  @ReactMethod
  fun getDirectoryStatistics(dirPath: String, promise: Promise) {
    coroutineMethod({
      val dir = File(dirPath)
      if (!dir.exists() || !dir.isDirectory) return@coroutineMethod mapOf(
        "fileCount" to 0, "totalSize" to 0L, "folderCount" to 0,
        "histogram" to mapOf("lessThan1MB" to 0, "from1to10MB" to 0, "from10to100MB" to 0, "from100MBto1GB" to 0, "greaterThan1GB" to 0)
      )
      val stats = DirectoryStats()
      recursiveStats(dir, stats)
      mapOf(
        "fileCount" to stats.fileCount, "totalSize" to stats.totalSize, "folderCount" to stats.folderCount,
        "histogram" to stats.histogram
      )
    }, promise)
  }

  private class DirectoryStats(
    var fileCount: Long = 0L,
    var totalSize: Long = 0L,
    var folderCount: Int = 0,
    val histogram: MutableMap<String, Int> = mutableMapOf(
      "lessThan1MB" to 0, "from1to10MB" to 0, "from10to100MB" to 0, "from100MBto1GB" to 0, "greaterThan1GB" to 0
    )
  )

  private suspend fun recursiveStats(dir: File, stats: DirectoryStats) {
    val files = dir.listFiles() ?: return
    stats.folderCount++
    for (file in files) {
      if (file.isDirectory) {
        recursiveStats(file, stats)
      } else {
        stats.fileCount++
        stats.totalSize += file.length()
        val sizeMB = file.length() / (1024 * 1024)
        if (sizeMB < 1) { stats.histogram["lessThan1MB"] = (stats.histogram["lessThan1MB"] ?: 0) + 1 }
        else if (sizeMB < 10) { stats.histogram["from1to10MB"] = (stats.histogram["from1to10MB"] ?: 0) + 1 }
        else if (sizeMB < 100) { stats.histogram["from10to100MB"] = (stats.histogram["from10to100MB"] ?: 0) + 1 }
        else if (sizeMB < 1024) { stats.histogram["from100MBto1GB"] = (stats.histogram["from100MBto1GB"] ?: 0) + 1 }
        else { stats.histogram["greaterThan1GB"] = (stats.histogram["greaterThan1GB"] ?: 0) + 1 }
      }
    }
  }

  // --- Mime Type Detection ---

  @ReactMethod
  fun getMimeType(filePath: String, promise: Promise) {
    coroutineMethod({
      val ext = File(filePath).extension
      MimeUtils.getMimeFromExtension(ext)
    }, promise)
  }

  @ReactMethod
  fun getFileExtension(filePath: String, promise: Promise) {
    coroutineMethod({
      File(filePath).extension
    }, promise)
  }
}
