package com.obsidian_north.mediastore.metadata

import android.os.CancellationSignal
import com.obsidian_north.mediastore.metadata.audio.AudioMetadataExtractor
import com.obsidian_north.mediastore.metadata.common.ExtractionStatus
import com.obsidian_north.mediastore.metadata.common.MetadataErrorCode
import com.obsidian_north.mediastore.metadata.common.MetadataExtractionResult
import com.obsidian_north.mediastore.metadata.image.ImageMetadataExtractor
import com.obsidian_north.mediastore.metadata.video.VideoMetadataExtractor
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

enum class MetadataJobStatus {
  QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED
}

data class MetadataJob(
  val id: String,
  val uri: String,
  val filePath: String,
  val mediaType: String,
  val mimeType: String,
  var status: MetadataJobStatus = MetadataJobStatus.QUEUED,
  var result: MetadataExtractionResult? = null,
  var error: Exception? = null,
  val cancellationSignal: CancellationSignal = CancellationSignal(),
)

class MetadataQueue(
  private val maxConcurrent: Int = 3,
  private val scope: CoroutineScope,
) {
  private val queue = ConcurrentLinkedQueue<MetadataJob>()
  private val activeJobs = AtomicInteger(0)
  private val isProcessing = AtomicBoolean(false)
  private val listeners = mutableListOf<(MetadataJob) -> Unit>()

  fun submit(
    uri: String,
    filePath: String,
    mediaType: String,
    mimeType: String,
  ): MetadataJob {
    val job = MetadataJob(
      id = generateJobId(),
      uri = uri,
      filePath = filePath,
      mediaType = mediaType,
      mimeType = mimeType,
    )
    queue.add(job)
    notifyListeners(job)
    processNext()
    return job
  }

  fun cancel(jobId: String) {
    queue.find { it.id == jobId && it.status == MetadataJobStatus.QUEUED }?.let { job ->
      job.status = MetadataJobStatus.CANCELLED
      job.cancellationSignal.cancel()
      queue.remove(job)
      notifyListeners(job)
    }
    queue.find { it.id == jobId && it.status == MetadataJobStatus.RUNNING }?.let { job ->
      job.status = MetadataJobStatus.CANCELLED
      job.cancellationSignal.cancel()
      notifyListeners(job)
    }
  }

  fun cancelAll() {
    for (job in queue) {
      job.status = MetadataJobStatus.CANCELLED
      job.cancellationSignal.cancel()
      notifyListeners(job)
    }
    queue.clear()
  }

  fun getJob(jobId: String): MetadataJob? {
    return queue.find { it.id == jobId }
  }

  fun pendingCount(): Int = queue.count { it.status == MetadataJobStatus.QUEUED }
  fun activeCount(): Int = activeJobs.get()
  fun isIdle(): Boolean = queue.isEmpty() && activeJobs.get() == 0

  fun onJobComplete(listener: (MetadataJob) -> Unit) {
    synchronized(listeners) { listeners.add(listener) }
  }

  private fun processNext() {
    if (activeJobs.get() >= maxConcurrent) return
    val next = queue.poll() ?: return
    if (next.status == MetadataJobStatus.CANCELLED) {
      processNext()
      return
    }

    activeJobs.incrementAndGet()
    next.status = MetadataJobStatus.RUNNING
    notifyListeners(next)

    scope.launch(Dispatchers.IO) {
      try {
        if (next.cancellationSignal.isCanceled) {
          next.status = MetadataJobStatus.CANCELLED
          notifyListeners(next)
          return@launch
        }

        val result = extractMetadata(next)
        if (next.cancellationSignal.isCanceled) {
          next.status = MetadataJobStatus.CANCELLED
          notifyListeners(next)
        } else {
          next.result = result
          next.status = MetadataJobStatus.COMPLETED
          notifyListeners(next)
        }
      } catch (e: CancellationException) {
        next.status = MetadataJobStatus.CANCELLED
        next.error = e
        notifyListeners(next)
      } catch (e: Exception) {
        next.status = MetadataJobStatus.FAILED
        next.error = e
        next.result = MetadataExtractionResult(
          metadata = emptyMap(),
          status = ExtractionStatus.FAILED,
          warnings = listOf(e.message ?: "Unknown error"),
          errorCode = MetadataErrorCode.fromException(e),
        )
        notifyListeners(next)
      } finally {
        activeJobs.decrementAndGet()
        processNext()
      }
    }
  }

  private fun extractMetadata(job: MetadataJob): MetadataExtractionResult {
    return when (job.mediaType) {
      "audio" -> AudioMetadataExtractor.extract(job.filePath, job.mimeType)
      "video" -> VideoMetadataExtractor.extract(job.filePath, job.mimeType)
      "image" -> ImageMetadataExtractor.extract(job.filePath, job.mimeType)
      else -> MetadataExtractionResult(
        metadata = emptyMap(),
        status = ExtractionStatus.FAILED,
        errorCode = MetadataErrorCode.UNSUPPORTED_FORMAT,
      )
    }
  }

  private fun notifyListeners(job: MetadataJob) {
    synchronized(listeners) {
      for (listener in listeners) {
        try { listener(job) } catch (_: Exception) {}
      }
    }
  }

  private val jobIdCounter = AtomicInteger(0)
  private fun generateJobId(): String = "meta_${jobIdCounter.incrementAndGet()}_${System.currentTimeMillis()}"
}
