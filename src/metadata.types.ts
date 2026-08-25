export type MediaMetaType = "audio" | "video" | "image" | "document";

export type MetadataLevel = "basic" | "standard" | "full" | "raw";

/**
 * Rich, deep metadata for a single media item. Returned by `getMetadata`,
 * `getDetailedMetadata`, and `getDetailedMetadataByUri`. Technical/Capture
 * metadata is extracted by opening the file (MediaExtractor / ExifInterface
 * on Android, AVAsset / CGImageSource on iOS), so it is only populated on
 * demand — not in bulk queries.
 */
export interface DetailedMetadata {
  mediaType: MediaMetaType;
  mimeType: string;
  fileSize: number;
  /** Container/wrapper format, e.g. "mp4", "mpeg-4", "matroska". */
  containerFormat?: string;
  /** Present for audio and video. Duration in milliseconds. */
  durationMs?: number;
  audio?: AudioFormatMetadata;
  video?: VideoFormatMetadata;
  image?: ImageFormatMetadata;
  document?: DocumentFormatMetadata;
  artwork?: ArtworkMetadata;
  /** Raw provider-specific payload (level="raw") — intentionally open-ended. */
  raw?: Record<string, unknown>;
  /** Allow future extension without breaking existing code. */
  [key: string]: unknown;
}

export interface AudioFormatMetadata {
  /** Normalized short codec name, e.g. "mp3", "aac", "opus", "flac". */
  codec?: string;
  /** Raw codec identifier / MIME, e.g. "audio/mpeg", "mp4a.40.2". */
  codecMime?: string;
  /** Codec profile, e.g. "HE-AAC", "LC", "HD". */
  codecProfile?: string;
  /** Bits per second. */
  bitrate?: number;
  /** Sample rate in Hz. */
  sampleRate?: number;
  /** Number of channels. */
  channels?: number;
  /** Human readable channel layout, e.g. "mono", "stereo", "5.1". */
  channelLayout?: string;
  /** Bits per sample (PCM depth). */
  bitsPerSample?: number;
  durationMs?: number;
  /** RFC-5646 language tag, e.g. "en". */
  language?: string;

  // Identity tags (from MediaMetadataRetriever)
  title?: string;
  artist?: string;
  album?: string;
  albumArtist?: string;
  composer?: string;
  genre?: string;
  author?: string;
  writer?: string;
  /** Track number (may contain "/" separator for totalTracks). */
  trackNumber?: number;
  totalTracks?: number;
  discNumber?: number;
  totalDiscs?: number;
  year?: number;
}

export interface VideoFormatMetadata {
  /** Normalized short codec name, e.g. "h264", "hevc", "vp9", "av1". */
  codec?: string;
  codecMime?: string;
  /** Codec profile, e.g. "High", "Baseline", "Main". */
  profile?: string;
  /** Codec level, e.g. "4.0". */
  level?: string;
  /** Video bitrate in bits per second. */
  bitrate?: number;
  width?: number;
  height?: number;
  /** Frames per second. */
  frameRate?: number;
  /** Display rotation in degrees. */
  rotation?: number;
  /** Capture framerate (slow-mo indicator). */
  captureFrameRate?: number;
  /** Total frame count. */
  frameCount?: number;
  /** Colour space, e.g. "BT.709", "BT.601", "BT.2020". */
  colorSpace?: string;
  colorStandard?: string;
  colorTransfer?: string;
  colorRange?: string;
  hasBFrames?: boolean;
  durationMs?: number;
  language?: string;
  /** Audio track information (if video has an audio track). */
  audioTrack?: {
    codecMime?: string;
    channels?: number;
    sampleRate?: number;
  };
}

export interface ImageFormatMetadata {
  /** Normalized short format, e.g. "jpeg", "png", "heic", "gif", "webp", "bmp", "tiff". */
  format?: string;
  width?: number;
  height?: number;
  bitsPerSample?: number;
  /** e.g. "sRGB", "AdobeRGB", "Uncalibrated". */
  colorSpace?: string;
  exif?: ExifMetadata;
  /** GPS location metadata with redaction awareness. */
  location?: ImageLocation;
}

export interface ImageLocation {
  latitude: number | null;
  longitude: number | null;
  altitude?: number | null;
  /** Whether GPS data exists at all. */
  available: boolean;
  /** Whether Android redacted the GPS data (requires ACCESS_MEDIA_LOCATION). */
  redacted: boolean;
}

export interface ExifMetadata {
  make?: string;
  model?: string;
  software?: string;
  lensMake?: string;
  lensModel?: string;
  imageDescription?: string;
  artist?: string;
  copyright?: string;
  /** Epoch milliseconds. */
  dateTimeOriginal?: number;
  /** Epoch milliseconds. */
  dateTimeDigitized?: number;
  /** Epoch milliseconds. */
  dateTime?: number;
  orientation?: number;
  /** F-number (e.g. 2.8). */
  aperture?: number;
  iso?: number;
  /** APEX shutter speed. */
  shutterSpeed?: number;
  /** Seconds (e.g. 0.004). */
  exposureTime?: number;
  exposureProgram?: string;
  /** APEX exposure bias. */
  exposureBias?: number;
  meteringMode?: string;
  flash?: boolean;
  flashMode?: string;
  whiteBalance?: string;
  /** Focal length in mm. */
  focalLength?: number;
  /** 35mm-equivalent focal length in mm. */
  focalLength35mm?: number;
  sceneCaptureType?: string;
  contrast?: string;
  saturation?: string;
  sharpness?: string;
  digitalZoomRatio?: number;
  /** JPEG compression ratio (bits per pixel). */
  compressedBitsPerPixel?: number;
  gpsLatitude?: number;
  gpsLongitude?: number;
  gpsAltitude?: number;
  /** Epoch milliseconds of GPS timestamp. */
  gpsTimestamp?: number;
  gpsProcessingMethod?: string;
  /** e.g. "sRGB", "Adobe RGB", "Uncalibrated". */
  colorSpace?: string;
  pixelXDimension?: number;
  pixelYDimension?: number;
}

export interface DocumentFormatMetadata {
  /** Normalized short format, e.g. "pdf", "docx", "txt". */
  format?: string;
  pageCount?: number;
  wordCount?: number;
  characterCount?: number;
  lineCount?: number;
  title?: string;
  author?: string;
  creator?: string;
  producer?: string;
  subject?: string;
  keywords?: string[];
  language?: string;
  isEncrypted?: boolean;
  /** Epoch milliseconds. */
  creationDate?: number;
  /** Epoch milliseconds. */
  modificationDate?: number;
}

export interface ArtworkMetadata {
  /** Whether embedded artwork is available. */
  available: boolean;
  /** URI to retrieve artwork (content:// or file path). */
  uri?: string;
}

/** Extraction status for a metadata operation. */
export type ExtractionStatus = "complete" | "partial" | "failed" | "cancelled";

/** Structured metadata error codes. */
export type MetadataErrorCode =
  | "PERMISSION_DENIED"
  | "FILE_NOT_FOUND"
  | "URI_UNAVAILABLE"
  | "UNSUPPORTED_FORMAT"
  | "CORRUPTED_FILE"
  | "EXTRACTION_FAILED"
  | "METADATA_UNAVAILABLE"
  | "API_NOT_SUPPORTED"
  | "MEDIA_REDACTED"
  | "TIMEOUT"
  | "CANCELLED"
  | "UNKNOWN_ERROR";

/** Result of a metadata extraction operation. */
export interface MetadataExtractionResult {
  metadata: DetailedMetadata;
  status: ExtractionStatus;
  warnings: string[];
  errorCode: MetadataErrorCode | null;
}

/** Source from which a metadata value was extracted. */
export type MetadataSource =
  | "mediastore"
  | "exif"
  | "retriever"
  | "extractor"
  | "embedded"
  | "filename"
  | "fallback"
  | "unknown";

/** A metadata value with provenance information. */
export interface MetadataField<T = unknown> {
  value: T;
  source: MetadataSource;
}

/** Diagnostic result from inspectMetadata(). */
export interface MetadataInspection {
  uri: string;
  mimeType?: string;
  sources: {
    mediaStore: boolean;
    mediaMetadataRetriever: boolean;
    exif: boolean;
  };
  fields: Record<string, MetadataField>;
  warnings: string[];
  status: ExtractionStatus;
}

/** Options for the getMetadata() API call. */
export interface MetadataOptions {
  /** Extraction depth: "basic" (fast), "standard" (library views), "full" (deep). Default: "full". */
  level?: MetadataLevel;
}
