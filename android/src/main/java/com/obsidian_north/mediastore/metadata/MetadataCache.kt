package com.obsidian_north.mediastore.metadata

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.util.concurrent.ConcurrentHashMap

data class CacheEntry(
  val metadata: Map<String, Any?>,
  val fileSize: Long,
  val lastModified: Long,
  val generationModified: Long?,
  val updatedAt: Long,
)

class MetadataCache(private val maxSize: Int = 500) {

  private val cache = ConcurrentHashMap<String, CacheEntry>()
  private val accessOrder = mutableListOf<String>()

  fun get(uri: String): Map<String, Any?>? {
    val entry = cache[uri] ?: return null
    synchronized(accessOrder) {
      accessOrder.remove(uri)
      accessOrder.add(uri)
    }
    return entry.metadata
  }

  fun put(uri: String, metadata: Map<String, Any?>, fileSize: Long, lastModified: Long, generationModified: Long? = null) {
    if (cache.size >= maxSize) {
      evictOldest()
    }
    cache[uri] = CacheEntry(
      metadata = metadata,
      fileSize = fileSize,
      lastModified = lastModified,
      generationModified = generationModified,
      updatedAt = System.currentTimeMillis(),
    )
    synchronized(accessOrder) {
      accessOrder.remove(uri)
      accessOrder.add(uri)
    }
  }

  fun isStale(uri: String, currentLastModified: Long, currentGeneration: Long? = null): Boolean {
    val entry = cache[uri] ?: return true
    if (currentLastModified > entry.lastModified) return true
    if (currentGeneration != null && entry.generationModified != null) {
      if (currentGeneration > entry.generationModified) return true
    }
    return false
  }

  fun invalidate(uri: String) {
    cache.remove(uri)
    synchronized(accessOrder) { accessOrder.remove(uri) }
  }

  fun invalidateAll() {
    cache.clear()
    synchronized(accessOrder) { accessOrder.clear() }
  }

  fun size(): Int = cache.size

  private fun evictOldest() {
    synchronized(accessOrder) {
      if (accessOrder.isEmpty()) return
      val oldest = accessOrder.removeAt(0)
      cache.remove(oldest)
    }
  }

  fun getChangedUris(
    contentResolver: ContentResolver,
    sinceTimestamp: Long,
  ): Triple<List<String>, List<String>, List<String>> {
    val added = mutableListOf<String>()
    val modified = mutableListOf<String>()
    val removed = mutableListOf<String>()

    val cachedUris = cache.keys.toSet()
    val seenUris = mutableSetOf<String>()

    val uris = listOf(
      MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
      MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
      MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    )

    for (contentUri in uris) {
      val projection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DATA,
        MediaStore.MediaColumns.DATE_MODIFIED,
        MediaStore.MediaColumns.SIZE,
      )
      val selection = "${MediaStore.MediaColumns.DATE_MODIFIED} > ?"
      val selectionArgs = arrayOf((sinceTimestamp / 1000).toString())

      contentResolver.query(contentUri, projection, selection, selectionArgs, null)?.use { cursor ->
        val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
        val modIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
        val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)

        while (cursor.moveToNext()) {
          val path = if (dataIdx >= 0) cursor.getString(dataIdx) ?: continue else continue
          val mod = if (modIdx >= 0) cursor.getLong(modIdx) else 0L
          val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L

          seenUris.add(path)
          if (path in cachedUris) {
            modified.add(path)
          } else {
            added.add(path)
          }
        }
      }
    }

    for (cachedUri in cachedUris) {
      if (cachedUri !in seenUris) {
        removed.add(cachedUri)
      }
    }

    return Triple(added, modified, removed)
  }

  fun getStats(): Map<String, Any> = mapOf(
    "size" to cache.size,
    "maxSize" to maxSize,
    "entries" to cache.keys.toList(),
  )
}
