package com.obsidian_north.mediastore.metadata.common

enum class MetadataErrorCode(val jsCode: String) {
  PERMISSION_DENIED("PERMISSION_DENIED"),
  FILE_NOT_FOUND("FILE_NOT_FOUND"),
  URI_UNAVAILABLE("URI_UNAVAILABLE"),
  UNSUPPORTED_FORMAT("UNSUPPORTED_FORMAT"),
  CORRUPTED_FILE("CORRUPTED_FILE"),
  EXTRACTION_FAILED("EXTRACTION_FAILED"),
  METADATA_UNAVAILABLE("METADATA_UNAVAILABLE"),
  API_NOT_SUPPORTED("API_NOT_SUPPORTED"),
  MEDIA_REDACTED("MEDIA_REDACTED"),
  TIMEOUT("TIMEOUT"),
  CANCELLED("CANCELLED"),
  UNKNOWN("UNKNOWN_ERROR");

  companion object {
    fun fromException(e: Exception): MetadataErrorCode = when {
      e is SecurityException -> PERMISSION_DENIED
      e is java.io.FileNotFoundException -> FILE_NOT_FOUND
      e is IllegalArgumentException -> UNSUPPORTED_FORMAT
      e.message?.contains("timeout", true) == true -> TIMEOUT
      e.message?.contains("cancel", true) == true -> CANCELLED
      e.message?.contains("corrupt", true) == true -> CORRUPTED_FILE
      else -> EXTRACTION_FAILED
    }
  }
}

data class MetadataExtractionResult(
  val metadata: Map<String, Any?>,
  val status: ExtractionStatus = ExtractionStatus.COMPLETE,
  val warnings: List<String> = emptyList(),
  val errorCode: MetadataErrorCode? = null,
)

enum class ExtractionStatus(val jsValue: String) {
  COMPLETE("complete"),
  PARTIAL("partial"),
  FAILED("failed"),
  CANCELLED("cancelled");
}

data class MetadataField<T>(
  val value: T?,
  val source: MetadataSource,
)

enum class MetadataSource(val jsValue: String) {
  MEDIASTORE("mediastore"),
  EXIF("exif"),
  MEDIA_METADATA_RETRIEVER("retriever"),
  MEDIA_EXTRACTOR("extractor"),
  EMBEDDED("embedded"),
  FILENAME("filename"),
  FALLBACK("fallback"),
  UNKNOWN("unknown");
}
