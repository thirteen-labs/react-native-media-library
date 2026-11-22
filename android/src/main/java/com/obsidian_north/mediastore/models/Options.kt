package com.obsidian_north.mediastore.models

// Data classes generate toString()/hashCode(), which MediaStoreModule uses to
// build query cache keys. Plain classes used identity hashes here, so the
// cache never hit.
data class SortOptionsRecord(
  var field: String = "",
  var order: String = "asc",
) {
  fun toMap(): Map<String, Any?> = mapOf("field" to field, "order" to order)
}

data class FilterOptionsRecord(
  var mimeTypes: List<String> = emptyList(),
  var extensions: List<String> = emptyList(),
  var folder: String? = null,
  var album: String? = null,
  var artist: String? = null,
  var minDuration: Long? = null,
  var maxDuration: Long? = null,
  var minSize: Long? = null,
  var maxSize: Long? = null,
  var minResolution: Int? = null,
  var maxResolution: Int? = null,
  var startDate: Long? = null,
  var endDate: Long? = null,
  var includeHidden: Boolean? = null,
  var favoritesOnly: Boolean? = null,
  var playlistId: String? = null,
) {
  fun toMap(): Map<String, Any?> = mapOf(
    "mimeTypes" to mimeTypes, "extensions" to extensions,
    "folder" to folder, "album" to album, "artist" to artist,
    "minDuration" to minDuration, "maxDuration" to maxDuration,
    "minSize" to minSize, "maxSize" to maxSize,
    "minResolution" to minResolution, "maxResolution" to maxResolution,
    "startDate" to startDate, "endDate" to endDate,
    "includeHidden" to includeHidden, "favoritesOnly" to favoritesOnly,
    "playlistId" to playlistId
  )
}

data class PaginationOptionsRecord(
  var limit: Int? = null,
  var offset: Int? = null,
  var cursor: String? = null,
) {
  fun toMap(): Map<String, Any?> = mapOf("limit" to limit, "offset" to offset, "cursor" to cursor)
}

data class SearchOptionsRecord(
  var query: String = "",
  var types: List<String>? = null,
  var sort: SortOptionsRecord? = null,
  var filter: FilterOptionsRecord? = null,
  var pagination: PaginationOptionsRecord? = null,
) {
  fun toMap(): Map<String, Any?> = mapOf(
    "query" to query, "types" to types,
    "sort" to sort?.toMap(), "filter" to filter?.toMap(),
    "pagination" to pagination?.toMap()
  )
}
