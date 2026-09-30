# @obsidian_north/react-native-mediastore

**Universal high-performance media indexing library for Android and iOS.**

A pure React Native native module that provides fast, production-grade access to media and indexed documents on Android (via MediaStore) and iOS (via Photos Framework) — no recursive filesystem scanning.

<div align="center">

![react-native-mediastore](image.png)

</div>

<div align="center">

| Build | Lint | Type Check | Tests | Release |
|-------|------|------------|-------|---------|
| ✅ | ✅ | ✅ | ✅ | ✅ |

</div>

## Architecture

```
App
 │
 ▼
TypeScript SDK
 │
 ▼
React Native Native Module
 │
 ├── Permission Manager
 ├── Cache Manager (LRU + TTL)
 ├── Search Engine
 ├── Observer (ContentObserver / PHPhotoLibraryChangeObserver)
 ├── Repository
 │
 ├─── Android ──→ MediaStore (SQLite Index) ──→ ContentResolver
 │                                              │
 │                                              ▼
 │                                         Cursor → Mapper → Domain Models → JSON
 │
  │                                         Metadata Subsystem
  │                                         ├── MetadataService (orchestrator + Media3 enrichment)
  │                                         ├── MetadataCache (LRU + generation tracking)
  │                                         ├── MetadataQueue (bounded concurrent extraction)
  │                                         ├── Media3InspectorExtractor (MetadataRetriever + FrameExtractor + MediaExtractorCompat) + Audio/Video legacy fallback
  │                                         ├── ImageMetadataExtractor (ExifInterface 1.4.2 + BitmapFactory)
  │                                         └── MetadataNormalizer (unified schema)
 │
 └─── iOS ─────→ Photos Framework (PHAsset) ──→ PHImageManager / PHAssetFetchRequest
                                                  │
                                                  ▼
                                             PHAsset → Domain Models → JSON
```

### Query Flow

```
getAudio()
 │
 ▼
Permission Check ─── DENIED ──→ PermissionDenied error
 │
 ▼ (granted)
Cache Lookup ─── HIT ──→ Return cached result
 │
 ▼ (miss)
Native Query (ContentResolver / PHAsset)
 │
 ▼
Object Mapping (typed projection / PHAsset properties)
 │
 ▼
JSON Serialization
 │
 ▼
React Native
```

### Thread Model

```
JS Thread ──→ async/await Promise
 │
 ▼
Native Module Thread (coroutine dispatcher / DispatchQueue)
 │
 ▼
IO Dispatcher (Dispatchers.IO) / Background Queue
 │
 ▼
MediaStore / Photos Framework
 │
 ▼
Back to JS (Promise resolved)
```

All queries execute on background dispatchers — the UI thread is never blocked.

### Memory Usage

```
100,000 songs
 │
 ▼
Cursor / PHFetchResult (lazy, not loaded entirely)
 │
 ▼
Stream rows individually
 │
 ▼
Map each row → JSON object
 │
 ▼
Collect results
 │
 ▼
Dispose Cursor / Release PHFetchResult
```

No entire library is loaded into memory. Each row is mapped and collected incrementally, then the cursor is closed in a `use` block.

---

## Features

- **Cross-platform** — Android (MediaStore) and iOS (Photos Framework) with a unified API
- **Blazing fast** — queries the native media database directly, no recursive directory scans
- **All media types** — audio, video, images, and documents (Android only; PDF, DOC/DOCX, XLS/XLSX, PPT/PPTX, TXT, EPUB, RTF, CSV, JSON, XML, ZIP, RAR, 7Z)
- **Rich metadata** — duration, resolution, bitrate, EXIF, GPS, album art, and more
- **Sorted & filtered queries** — sort by name, date, size, duration, artist, etc. Filter by MIME, extension, folder, date range, size range, and more
- **Full-text search** — prefix, partial, case-insensitive, multi-keyword, unicode-aware
- **Pagination** — limit/offset and cursor-based pagination on every API
- **Real-time change observation** — `ContentObserver` (Android) / `PHPhotoLibraryChangeObserver` (iOS) fires events when files are added, removed, or modified
- **Permissions-aware** — scoped `READ_MEDIA_*` permissions on Android 13+, `PHPhotoLibrary` authorization on iOS, automatic fallback
- **LRU caching** — optional in-memory cache with configurable TTL, auto-invalidated on changes
- **Fully typed** — complete TypeScript definitions with a strongly typed native module spec (concrete return types, no `any`)
- **Reactive** — React hook `useMediaChangeEvent` for real-time updates
- **Batch queries** — `getLibrary()` returns all media types in one native call
- **Thumbnail/artwork** — helper methods for album art and video/image thumbnails
- **Comprehensive deep metadata** — `getMetadata()` / `getDetailedMetadata()` / `getDetailedMetadataByUri()` open the file to extract true technical metadata: codec, bitrate, sample rate, channels, color space (audio/video), and full EXIF (aperture, ISO, focal length, GPS, flash, white balance…) for images; best-effort page/word counts for documents
- **Structured metadata extraction** — dedicated audio/video/image extractors with `MediaMetadataRetriever` id3 tag extraction, `MediaExtractor` format analysis, and `ExifInterface` camera/GPS data
- **Metadata levels** — `basic` / `standard` / `full` extraction depth control for performance tuning
- **Metadata caching** — deep metadata LRU cache with generation-based staleness detection
- **Bounded concurrent extraction** — metadata queue with configurable worker pool and cancellation support
- **GPS redaction awareness** — distinguishes "no GPS" from "GPS redacted by Android" for privacy-safe handling
- **Structured metadata errors** — typed `MetadataErrorCode` (`PERMISSION_DENIED`, `UNSUPPORTED_FORMAT`, `CORRUPTED_FILE`, `MEDIA_REDACTED`, etc.)
- **Metadata diagnostics** — `inspectMetadata()` reveals which extraction sources succeeded and per-field provenance
- **Robust album artwork** — Android extracts embedded album art via `MediaMetadataRetriever` (works on Android 10+ scoped storage); iOS uses the album's representative asset
- **Unified audio metadata (vNext)** — `getAudioMetadata()` / `getAudioMetadataBatch()` return one normalized `MediaStoreAudioMetadata` per file: standard tags, technical fields, artwork descriptor, and ReplayGain
- **R128 + ReplayGain 1.0** — native head-scan for `R128_*_GAIN` / `REPLAYGAIN_*` tags (FLAC/Vorbis/Opus, ID3v2 TXXX, MP4 freeform); R128 Q8.8 normalized to dB natively, `source: "r128" | "replaygain"` with R128 priority
- **Artwork engine** — `extractArtwork()` (embedded → cache file) and `saveArtwork()` with format preservation by default (no lossy recompression unless explicitly requested)
- **Capability detection** — `getCapabilities()` so callers never assume per-platform support
- **Folder statistics** — size histograms and per-type breakdowns for folders
- **Incremental indexing** — delta-only refresh tracking added, modified, and removed items
- **Plugin hooks** — extensible metadata system via JS-side plugin registration
- **Improved batch queries** — per-type pagination, selective type fetching, and query timing
- **File system CRUD** — recursive directory scanning, file creation, reading, writing, renaming, moving, copying, deleting, and statistics (Android & iOS)
- **MIME type detection** — auto-detect MIME types from file extensions
- **File extension detection** — get file extension from path

---

## Performance Benchmarks

| Library | 50k songs |
|---------|----------:|
| Recursive FS | 14 sec |
| **MediaStore** | **280 ms** |

| Operation | Time |
|-----------|-----:|
| `getAudio` | 120 ms |
| `getVideos` | 90 ms |
| `getImages` | 95 ms |
| `getDocuments` | 70 ms |
| `search` | 25 ms |
| `getStatistics` | 15 ms |

Measurements taken on a Pixel 7 (Android 14) with 50k audio, 2k video, 10k images. Results cached after first query.

---

## Installation

```bash
npm install @obsidian_north/react-native-mediastore
```

> Compatible with Expo development builds — install with `npx expo install @obsidian_north/react-native-mediastore` after running `npx expo prebuild`.

---

## Prerequisites

- React Native 0.76+
- **Android**: API 24+ (Android 7.0) — required for Media3 Inspector (`media3-inspector:1.11.0` + `media3-inspector-frame:1.11.0`); `ExifInterface 1.4.2` also requires 24+
  - Android 13+ (API 33): granular media permissions are requested automatically
  - Android 12 and below: `READ_EXTERNAL_STORAGE` permission is required
- **iOS**: iOS 13.0+ ( Photos Framework ), iOS 16+ uses fast async `AVAsyncProperty` path (`await asset.load(.duration/.tracks)`); iOS 13-15 falls back to sync legacy path

### Permissions Matrix

| Platform | Version | Permission |
|----------|---------|-----------|
| iOS | 13.0+ | `PHPhotoLibrary` (read/write authorization) |
| Android 15 | 35 | `READ_MEDIA_AUDIO`, `READ_MEDIA_VIDEO`, `READ_MEDIA_IMAGES` |
| Android 14 | 34 | `READ_MEDIA_AUDIO`, `READ_MEDIA_VIDEO`, `READ_MEDIA_IMAGES` |
| Android 13 | 33 | `READ_MEDIA_AUDIO`, `READ_MEDIA_VIDEO`, `READ_MEDIA_IMAGES` |
| Android 12 | 32 | `READ_EXTERNAL_STORAGE` |
| Android 11 | 30–31 | `READ_EXTERNAL_STORAGE` |
| Android 10 | 29 | `READ_EXTERNAL_STORAGE` |
| Android 5–9 | 21–28 | `READ_EXTERNAL_STORAGE` |

Call `requestPermissions()` before querying media on first launch.

### Compatibility Matrix

| React Native | AGP | Kotlin | Gradle | Status |
|-------------|-----|--------|--------|--------|
| 0.76 | 8.7 | 2.0.21 | 8.11 | ✅ |
| 0.85 | 9.0 | 2.1.20 | 9.3 | ✅ |

---

## Comparison

| Feature | **react-native-mediastore** | expo-file-system | react-native-fs |
|---------|:---------------------------:|:----------------:|:----------------:|
| Android | ✅ | ✅ | ✅ |
| iOS | ✅ | ✅ | ✅ |
| Audio (music) | ✅ | ❌ | ⚠️ |
| Video | ✅ | ❌ | ⚠️ |
| Images | ✅ | ❌ | ⚠️ |
| Documents | ✅ (Android) | ❌ | ⚠️ |
| Rich metadata | ✅ | ❌ | ❌ |
| Album art | ✅ | ❌ | ❌ |
| EXIF/GPS | ✅ | ❌ | ❌ |
| Full-text search | ✅ | ❌ | ❌ |
| Watch changes | ✅ | ❌ | ❌ |
| Pagination | ✅ | ❌ | ❌ |
| Sorting | ✅ | ❌ | ❌ |
| Filters | ✅ | ❌ | ❌ |
| Batch query | ✅ | ❌ | ❌ |
| Duplicate detection | ✅ | ❌ | ❌ |
| Statistics | ✅ | ❌ | ❌ |
| Favorites | ✅ | ❌ | ❌ |
| Folder histograms | ✅ | ❌ | ❌ |
| Incremental indexing | ✅ | ❌ | ❌ |
| Plugin metadata | ✅ | ❌ | ❌ |

⚠️ = partial support (no metadata, no filtering)

---

## Quick Start

```typescript
import { getAudio, getImages, useMediaChangeEvent } from "@obsidian_north/react-native-mediastore";

// Fetch all audio tracks
const songs = await getAudio(
  { field: "dateAdded", order: "desc" },
  { minDuration: 30_000 }
);

// Fetch recent images with pagination
const photos = await getImages(
  { field: "dateAdded", order: "desc" },
  null,
  { limit: 20, offset: 0 }
);

// Batch query — audio, video, images, documents in one call
const library = await getLibrary(
  { field: "dateAdded", order: "desc" }
);

// Fetch album artwork
const artworkUri = await getAlbumArtwork(albumId);

// Listen for MediaStore changes
function MediaWatcher() {
  const event = useMediaChangeEvent((e) => {
    switch (e.type) {
      case "added":
        console.log(`New ${e.mediaType}: ${e.uri}`);
        break;
      case "removed":
        console.log(`${e.mediaType} removed: ${e.itemId}`);
        break;
      case "modified":
        console.log(`${e.mediaType} modified: ${e.itemId}`);
        break;
    }
  });
  return null;
}
```

---

## API Reference

### Media Queries

| Function | Returns | Description |
|----------|---------|-------------|
| `getAudio(sort?, filter?, pagination?)` | `AudioItem[]` | Fetch audio tracks |
| `getVideos(sort?, filter?, pagination?)` | `VideoItem[]` | Fetch video files |
| `getImages(sort?, filter?, pagination?)` | `ImageItem[]` | Fetch images |
| `getDocuments(sort?, filter?, pagination?)` | `DocumentItem[]` | Fetch documents (Android only) |
| `getAlbums(sort?, filter?, pagination?)` | `Album[]` | Fetch audio albums |
| `getArtists(sort?, pagination?)` | `Artist[]` | Fetch artists |
| `getGenres(sort?, pagination?)` | `Genre[]` | Fetch genres |
| `getPlaylists(sort?, pagination?)` | `Playlist[]` | Fetch playlists |
| `getFolders(sort?, filter?, pagination?)` | `Folder[]` | Aggregate files by folder (Android only) |

### Search & Lookup

| Function | Returns | Description |
|----------|---------|-------------|
| `search(options)` | `SearchResult` | Full-text search across media types |
| `getById(mediaType, id)` | `AudioItem \| VideoItem \| ImageItem \| DocumentItem \| null` | Lookup by database ID |
| `getByUri(uri)` | `AudioItem \| VideoItem \| ImageItem \| DocumentItem \| null` | Lookup by content URI |

### Utility Queries

| Function | Returns | Description |
|----------|---------|-------------|
| `getRecent(mediaType?, limit?)` | `(AudioItem \| VideoItem \| ImageItem \| DocumentItem)[]` | Most recently added items |
| `getFavorites(mediaType?, sort?, pagination?)` | `(AudioItem \| VideoItem \| ImageItem \| DocumentItem)[]` | Starred/favorited items |
| `getLargestFiles(mediaType?, limit?)` | `(AudioItem \| VideoItem \| ImageItem \| DocumentItem)[]` | Largest files by size |
| `getDuplicates(mediaType?)` | `DuplicateItem[]` | Detect duplicate files (Android only) |
| `getStatistics()` | `MediaStoreStatistics` | Aggregate counts and sizes |
| `getLibrary(sort?, filter?, pagination?)` | `LibraryResult` | Audio, video, images, docs in one batch |
| `getLibraryQuery(options?)` | `LibraryQueryResult` | Improved batch query with per-type pagination and statistics |
| `getFolderStatistics(folderPath?)` | `FolderStatistics[]` | Size histograms and type breakdowns per folder |
| `refreshIncremental(lastTimestamp?)` | `IncrementalChanges` | Delta-only refresh tracking changes since timestamp |
| `getLastRefreshTimestamp()` | `number` | Get last refresh timestamp |

### Plugin System

| Function | Returns | Description |
|----------|---------|-------------|
| `registerPlugin(plugin)` | `void` | Register a metadata extractor plugin |
| `unregisterPlugin(pluginId)` | `boolean` | Remove a plugin by ID |
| `getRegisteredPlugins()` | `MetadataPlugin[]` | List all registered plugins |

### Detailed Metadata

`DetailedMetadata` performs **deep, on-demand extraction** — it opens the file (Android: `MediaExtractor` / `ExifInterface`; iOS: `AVAsset` / `CGImageSource`) to read true technical and capture metadata that is not available from the media index. Use it per-item (e.g. when a user opens a detail screen); do **not** call it inside bulk loops, as each call does file I/O.

| Function | Returns | Description |
|----------|---------|-------------|
| `getMetadata(uri, options?)` | `MetadataResult` | Deep metadata with level control (`basic`/`standard`/`full`) |
| `getDetailedMetadata(mediaType, id)` | `MetadataResult` | Deep metadata for a media item by type + database ID |
| `getDetailedMetadataByUri(uri)` | `MetadataResult` | Deep metadata for a media item by content URI or file path |
| `inspectMetadata(uri)` | `MetadataInspectionResult` | Diagnostics: sources used, per-field provenance, warnings |

```typescript
import { getMetadata, getDetailedMetadata } from "@obsidian_north/react-native-mediastore";

// Fast basic metadata (no file I/O for indexed fields)
const basic = await getMetadata(uri, { level: "basic" });

// Full deep extraction
const meta = await getMetadata(uri, { level: "full" });
console.log(meta.metadata?.audio?.codec);        // "flac"
console.log(meta.metadata?.audio?.sampleRate);   // 96000
console.log(meta.metadata?.audio?.bitsPerSample); // 24
console.log(meta.metadata?.image?.exif?.iso);    // 400

// Inspect which sources contributed data
const inspection = await inspectMetadata(uri);
console.log(inspection.sources.mediaStore);           // true
console.log(inspection.sources.mediaMetadataRetriever); // true
console.log(inspection.fields.sampleRate.source);    // "retriever"
```

The `MetadataResult` returned by all metadata methods includes:

```typescript
{
  metadata: DetailedMetadata;   // The extracted metadata
  status: "complete" | "partial" | "failed" | "cancelled";
  warnings: string[];           // Non-fatal extraction warnings
  errorCode: MetadataErrorCode | null;  // Structured error if failed
}
```

Bulk queries (`getAudio`, `getVideos`, …) are also enriched with **cheap catalog columns** (read from the media index, no file I/O). New optional fields:

- `AudioItem`: `writer`, `isMusic`, `isPodcast`, `isRingtone`, `isAlarm`, `isNotification`, `cdTrackNumber`, `numTracks`
- `VideoItem`: `colorStandard`, `colorTransfer`, `videoCodec`, `bucketId`, `bucketDisplayName`, `dateTaken`
- `ImageItem`: `bucketId`, `bucketDisplayName`, `description`
- `DocumentItem`: `title`, `isFavorite`

### DetailedMetadata Types

```typescript
interface DetailedMetadata {
  mediaType: "audio" | "video" | "image" | "document";
  mimeType: string;
  fileSize: number;
  containerFormat?: string;     // "mp4", "mkv", "mp3" ...
  durationMs?: number;          // audio/video
  audio?: AudioFormatMetadata;
  video?: VideoFormatMetadata;
  image?: ImageFormatMetadata;
  document?: DocumentFormatMetadata;
  artwork?: { available: boolean; uri?: string };
}

interface AudioFormatMetadata {
  // Technical
  codec?: string; codecMime?: string; codecProfile?: string;
  bitrate?: number; sampleRate?: number; channels?: number;
  channelLayout?: string; bitsPerSample?: number; durationMs?: number; language?: string;
  // Identity (from MediaMetadataRetriever)
  title?: string; artist?: string; album?: string; albumArtist?: string;
  composer?: string; genre?: string; author?: string; writer?: string;
  trackNumber?: number; totalTracks?: number; discNumber?: number;
  totalDiscs?: number; year?: number;
}

interface VideoFormatMetadata {
  codec?: string; codecMime?: string; profile?: string; level?: string;
  bitrate?: number; width?: number; height?: number; frameRate?: number;
  rotation?: number; captureFrameRate?: number; frameCount?: number;
  colorSpace?: string; colorStandard?: string;
  colorTransfer?: string; colorRange?: string;
  hasBFrames?: boolean; durationMs?: number; language?: string;
  audioTrack?: { codecMime?: string; channels?: number; sampleRate?: number };
}

interface ImageFormatMetadata {
  format?: string; width?: number; height?: number;
  bitsPerSample?: number; colorSpace?: string; exif?: ExifMetadata;
  location?: { latitude: number | null; longitude: number | null;
    available: boolean; redacted: boolean };
}

interface ExifMetadata {
  make?: string; model?: string; software?: string; lensMake?: string; lensModel?: string;
  imageDescription?: string; artist?: string; copyright?: string;
  dateTimeOriginal?: number; dateTimeDigitized?: number; dateTime?: number;
  orientation?: number;
  aperture?: number; iso?: number; shutterSpeed?: number; exposureTime?: number;
  exposureProgram?: string; exposureBias?: number; meteringMode?: string;
  flash?: boolean; flashMode?: string; whiteBalance?: string;
  focalLength?: number; focalLength35mm?: number; sceneCaptureType?: string;
  contrast?: string; saturation?: string; sharpness?: string; digitalZoomRatio?: number;
  compressedBitsPerPixel?: number; gpsLatitude?: number; gpsLongitude?: number;
  gpsAltitude?: number; gpsTimestamp?: number; gpsProcessingMethod?: string;
  colorSpace?: string; pixelXDimension?: number; pixelYDimension?: number;
}

interface DocumentFormatMetadata {
  format?: string; pageCount?: number; wordCount?: number;
  characterCount?: number; lineCount?: number; title?: string;
  author?: string; creator?: string; producer?: string; subject?: string;
  keywords?: string[]; language?: string; isEncrypted?: boolean;
  creationDate?: number; modificationDate?: number;
}

type MetadataLevel = "basic" | "standard" | "full" | "raw";
type ExtractionStatus = "complete" | "partial" | "failed" | "cancelled";
type MetadataErrorCode = "PERMISSION_DENIED" | "FILE_NOT_FOUND" | "URI_UNAVAILABLE"
  | "UNSUPPORTED_FORMAT" | "CORRUPTED_FILE" | "EXTRACTION_FAILED"
  | "METADATA_UNAVAILABLE" | "API_NOT_SUPPORTED" | "MEDIA_REDACTED"
  | "TIMEOUT" | "CANCELLED" | "UNKNOWN_ERROR";

interface MetadataResult {
  metadata: DetailedMetadata;
  status: ExtractionStatus;
  warnings: string[];
  errorCode: MetadataErrorCode | null;
}

interface MetadataInspectionResult {
  uri: string;
  sources: { mediaStore: boolean; mediaMetadataRetriever: boolean; exif: boolean };
  fields: Record<string, { value: unknown; source: string }>;
  warnings: string[];
  status: ExtractionStatus;
}
```

### Artwork & Thumbnails

| Function | Returns | Description |
|----------|---------|-------------|
| `getAlbumArtwork(albumId)` | `string \| null` | Album art content URI |
| `getArtworkUri(albumId)` | `ArtworkUriResult` | Album artwork content URI via metadata service |
| `getArtworkBytes(albumId)` | `ArtworkBytesResult` | Album artwork as cached file with size |
| `getVideoThumbnail(videoId, width?, height?)` | `string \| null` | Video thumbnail URI |
| `getImageThumbnail(imageId, width?, height?)` | `string \| null` | Image thumbnail URI |

### Unified Audio Metadata & Artwork Engine (vNext)

First-class native replacement for `@missingcore`-style helpers (`saveArtwork` / `getR128Gain`).
MediaStore answers **"what is inside this media file?"** — tags, artwork, R128/ReplayGain —
while your audio engine owns **"how should I play it?"** (preamp, EQ, DSP).

| Function | Returns | Description |
|----------|---------|-------------|
| `getAudioMetadata(uri)` | `MediaStoreAudioMetadata \| null` | Unified tags + technical fields + artwork + normalized `replayGain` |
| `getAudioMetadataBatch(uris)` | `(MediaStoreAudioMetadata \| null)[]` | One bridge hop for library scans (artwork is availability-only in batch) |
| `extractArtwork(audioUri)` | `MediaStoreArtwork \| null` | Embedded artwork → cache file usable by `<Image>` |
| `saveArtwork(sourceUri, destUri?, options?)` | `MediaStoreArtwork \| null` | Persist artwork; `{ preserveFormat: true }` by default |
| `getCapabilities()` | `MediaStoreCapabilities` | `{ platform, metadata, artwork, replayGain, r128, batchMetadata, mediaStore, photosLibrary }` |
| `getPlaybackGainDb(uri, mode?, preampDb?)` | `number` | Effective playback gain without silent track/album mixing |

```typescript
import {
  getAudioMetadata, getAudioMetadataBatch, extractArtwork,
  saveArtwork, getCapabilities, resolveReplayGainDb,
} from "@obsidian_north/react-native-mediastore";

const metadata = await getAudioMetadata(uri);
console.log(metadata?.replayGain);
// { trackGain: -5.42, albumGain: -6.18, trackPeak: 0.98, albumPeak: 0.99, source: "r128" }

// Playback: caller picks track/album — never silently mixed
const gain = resolveReplayGainDb(metadata?.replayGain, "track", preampDb);
const finalGain = gain + preampDb; // or: await getPlaybackGainDb(uri, "track", preampDb)

// Library scan: one native call instead of N
const tracks = await getAudioMetadataBatch(files);

// Artwork: extract embedded art, or re-save with explicit compression
const art = await extractArtwork(uri); // { uri, mimeType, width, height, size }
await saveArtwork(uri);                                  // PNG → PNG, JPEG → JPEG (default)
await saveArtwork(uri, null, { format: "jpeg", quality: 0.85 }); // opt-in compression

if (!(await getCapabilities()).r128) { /* fall back */ }
```

Gain resolution hierarchy: R128 track gain → ReplayGain track gain → album gain (only when
`mode === "album"`) → preamp → `0 dB`. R128 integers are Q8.8 (`256 = 1 dB`) and are normalized
to dB floats in native code, so JS never parses raw tag encodings.

```typescript
const caps = await getCapabilities();
caps.platform;       // "android" | "ios"  <- use this
caps.photosLibrary;  // true only on iOS   <- iOS-specific check
// caps.mediaStore is deprecated. It stays `true` on both platforms so existing
// gates keep working, so it cannot tell you the backing store. Use `platform`.
```

```typescript
interface MediaStoreAudioMetadata {
  uri: string;
  title: string | null; artist: string | null;
  album: string | null; albumArtist: string | null;
  genre: string | null; year: number | null;
  trackNumber: number | null; discNumber: number | null;
  duration: number | null;             // ms
  bitrate: number | null; sampleRate: number | null; channels: number | null;
  composer: string | null; comment: string | null;
  artwork: MediaStoreArtwork | null;
  replayGain: MediaStoreReplayGain | null;
}

interface MediaStoreReplayGain {
  trackGain: number | null; albumGain: number | null;   // dB
  trackPeak: number | null; albumPeak: number | null;   // linear
  source: "r128" | "replaygain" | null;
}

interface MediaStoreArtwork {
  uri: string; mimeType: string | null;
  width: number | null; height: number | null; size: number | null;
}

interface ArtworkSaveOptions {
  format?: "original" | "jpeg" | "png" | "webp";
  quality?: number;            // 0–1, default 0.85 (only used when transcoding)
  preserveFormat?: boolean;    // default true
}

type ReplayGainMode = "track" | "album" | "off";
```

### System

| Function | Returns | Description |
|----------|---------|-------------|
| `refresh()` | `void` | Invalidate all caches |
| `checkPermissions()` | `PermissionStatus` | Check current permission state |
| `requestPermissions()` | `PermissionStatus` | Request media permissions |
| `useMediaChangeEvent(callback?)` | `MediaChangeEvent \| null` | React hook for change events |
| `cancelMetadataExtraction(jobId)` | `boolean` | Cancel a queued/running metadata extraction job |
| `cancelAllMetadataExtraction()` | `boolean` | Cancel all pending metadata extraction jobs |

---

## Error Codes

Every thrown error has a structured `MediaStoreError` with a typed `code`:

| Code | Meaning |
|------|---------|
| `PERMISSION_DENIED` | Required media permissions not granted |
| `QUERY_FAILED` | MediaStore / Photos query failed (database error) |
| `INVALID_ARGUMENTS` | Invalid sort field, MIME type, or filter option |
| `INVALID_SORT_FIELD` | The requested sort field is not valid for this media type |
| `INVALID_MIME_TYPE` | The MIME type filter does not match any known type |
| `UNSUPPORTED_ANDROID_VERSION` | Android version below minimum SDK (21) |
| `FILE_UNAVAILABLE` | File not found or inaccessible |
| `CURSOR_CLOSED` | Cursor was closed before iteration completed |
| `CACHE_FAILURE` | Cache operation failed |
| `FILE_NOT_FOUND` | File does not exist at the given path |
| `URI_UNAVAILABLE` | Content URI could not be queried |
| `UNSUPPORTED_FORMAT` | Media format not supported for deep extraction |
| `CORRUPTED_FILE` | File is corrupted or unreadable |
| `EXTRACTION_FAILED` | Metadata extraction failed (generic) |
| `METADATA_UNAVAILABLE` | Requested metadata not available for this file |
| `API_NOT_SUPPORTED` | Required API not available on this Android version |
| `MEDIA_REDACTED` | Metadata redacted by Android (e.g. GPS requires ACCESS_MEDIA_LOCATION) |
| `TIMEOUT` | Metadata extraction timed out |
| `CANCELLED` | Metadata extraction was cancelled |
| `UNKNOWN_ERROR` | Unexpected error |

```typescript
try {
  const songs = await getAudio();
} catch (error) {
  if (error.code === "PERMISSION_DENIED") {
    // Handle permission flow
  }
}
```

---

## Event System

The module uses Android's `ContentObserver` and iOS's `PHPhotoLibraryChangeObserver` to monitor media changes and emit events to JavaScript.

### Events

| Event | Payload | Description |
|-------|---------|-------------|
| `onMediaChange` | `{ type: "added" \| "removed" \| "modified", mediaType, itemId, uri }` | Media file indexed, deleted, or modified |

### React Hook

```typescript
import { useMediaChangeEvent } from "@obsidian_north/react-native-mediastore";

function MyComponent() {
  const lastEvent = useMediaChangeEvent((event) => {
    // React to every change
  });

  return <Text>Last change: {lastEvent?.type}</Text>;
}
```

---

## Platform Support

### Android

`getFolders()` aggregates files into folders by their `relativePath`.

```typescript
interface Folder {
  id: string;
  name: string;        // Display name (last segment)
  path: string;        // Full relative path
  fileCount: number;   // Total files in folder
  totalSize: number;   // Cumulative size in bytes
}
```

- **Grouping**: Files are grouped by `relativePath` (e.g. `Music/Artist/Album`)
- **Counts**: `fileCount` is the number of files in that folder
- **Sorting**: Supports sort fields like `name`, `dateAdded`, `dateModified`, `fileSize`
- **Filtering**: Supports `mimeTypes`, `extensions`, `folder` (deep path prefix), `minSize`/`maxSize`
- **Statistics**: Use `getFolderStatistics()` for size histograms and per-type breakdowns per folder

### iOS

iOS uses Apple's Photos Framework (`PHAsset`) for media indexing:

- **Audio, video, image** queries with metadata via `PHAsset` properties
- **Album, artist, genre, playlist** aggregation via `PHAssetCollection`
- **Search** with `CONTAINS[cd]` matching (case/diacritic-insensitive)
- **Thumbnail generation** via `PHImageManager.requestImage()`
- **Real-time changes** via `PHPhotoLibraryChangeObserver`
- **Documents** return an empty array (no Photos Framework equivalent)
- **Folders** return an empty array (no equivalent aggregation)

---

## Advanced Search

```typescript
import { search } from "@obsidian_north/react-native-mediastore";

const result = await search({
  query: "beatles",
  types: ["audio", "video"],
  filter: {
    artist: "Queen",
    album: "Greatest Hits",
    mimeTypes: ["audio/*"],
    minDuration: 60_000,
  },
  sort: { field: "name", order: "asc" },
  pagination: { limit: 50, cursor: "..." },
});

console.log(`${result.totalCount} results`);
```

---

## Caching

```
Request
 │
 ▼
Cache Lookup ─── HIT (within TTL) ──→ Return cached
 │
 ▼ (miss)
MediaStore Query
 │
 ▼
Store in Cache (LRU eviction)
 │
 ▼
Return result
 │
 ▼ (on change)
Observer fires cacheInvalidated
 │
 ▼
Cache cleared → next query goes to native DB
```

- **TTL**: Configurable time-to-live per query (default: 30s)
- **Eviction**: LRU-based when cache reaches max entries (100)
- **Invalidation**: Automatic on media change events
- **Refresh**: Call `refresh()` to manually clear all caches

---

## Code Examples

### Music Player Library

```typescript
import { getAudio, getAlbums, getArtists, getAlbumArtwork } from "@obsidian_north/react-native-mediastore";

async function loadLibrary() {
  const [songs, albums, artists] = await Promise.all([
    getAudio({ field: "artist", order: "asc" }),
    getAlbums(),
    getArtists(),
  ]);

  const albumArt = albums.reduce((map, album) => {
    map[album.id] = getAlbumArtwork(album.id);
    return map;
  }, {} as Record<string, Promise<string | null>>);

  return { songs, albums, artists, albumArt };
}
```

### Gallery with Thumbnails

```typescript
import { getImages, getImageThumbnail } from "@obsidian_north/react-native-mediastore";

async function loadGallery() {
  const images = await getImages(
    { field: "dateAdded", order: "desc" },
    null,
    { limit: 100 }
  );

  return images.map((img) => ({
    id: img.id,
    uri: img.uri,
    thumbnail: getImageThumbnail(img.id, 320, 320),
    width: img.width,
    height: img.height,
  }));
}
```

### File Manager File List

```typescript
import { getDocuments, getFolders } from "@obsidian_north/react-native-mediastore";

async function loadFileManager(folder?: string) {
  const [files, folders] = await Promise.all([
    getDocuments(null, { folder }),
    getFolders(null, { folder }),
  ]);
  return { files, folders };
}
```

### Search Screen

```typescript
import { search, SearchResult } from "@obsidian_north/react-native-mediastore";
import { useState, useCallback } from "react";

function useSearch() {
  const [results, setResults] = useState<SearchResult | null>(null);
  const [loading, setLoading] = useState(false);

  const query = useCallback(async (text: string) => {
    setLoading(true);
    const res = await search({ query: text, types: ["audio", "video", "image", "document"] });
    setResults(res);
    setLoading(false);
  }, []);

  return { results, loading, query };
}
```

### Infinite Scroll (Cursor-based)

```typescript
import { getAudio } from "@obsidian_north/react-native-mediastore";
import { useState, useCallback } from "react";

const PAGE_SIZE = 30;

function useInfiniteScroll() {
  const [songs, setSongs] = useState<any[]>([]);
  const [cursor, setCursor] = useState<string | undefined>();

  const loadMore = useCallback(async () => {
    const page = await getAudio(
      { field: "name", order: "asc" },
      null,
      { limit: PAGE_SIZE, cursor }
    );
    setSongs(prev => [...prev, ...page]);
  }, [cursor]);

  return { songs, loadMore };
}
```

### Pagination (Offset-based)

```typescript
import { getVideos } from "@obsidian_north/react-native-mediastore";

async function getPage(page: number, pageSize: number = 20) {
  return getVideos(
    { field: "dateAdded", order: "desc" },
    null,
    { limit: pageSize, offset: page * pageSize }
  );
}
```

### Album Browser

```typescript
import { getAlbums, getAudio, getAlbumArtwork } from "@obsidian_north/react-native-mediastore";

async function loadAlbumBrowser() {
  const albums = await getAlbums({ field: "year", order: "desc" });

  const albumDetails = await Promise.all(
    albums.map(async (album) => ({
      ...album,
      songs: await getAudio(null, { album: album.title }),
      artwork: await getAlbumArtwork(album.id),
    }))
  );

  return albumDetails;
}
```

### Playlist Browser

```typescript
import { getPlaylists, getAudio } from "@obsidian_north/react-native-mediastore";

async function loadPlaylistSongs(playlistId: string) {
  return getAudio(null, { playlistId });
}
```

### Reactive Auto-refresh

```typescript
import { getAudio, useMediaChangeEvent } from "@obsidian_north/react-native-mediastore";
import { useState, useEffect } from "react";

function useReactiveAudio() {
  const [songs, setSongs] = useState<any[]>([]);
  const refresh = async () => { setSongs(await getAudio()); };
  useEffect(() => { refresh(); }, []);
  useMediaChangeEvent(refresh);
  return songs;
}
```

### Folder Statistics

```typescript
import { getFolderStatistics } from "@obsidian_north/react-native-mediastore";

async function analyzeStorage() {
  const folders = await getFolderStatistics();

  for (const folder of folders) {
    console.log(`${folder.path}: ${folder.fileCount} files, ${folder.totalSize} bytes`);
    console.log(`  <1MB: ${folder.histogram.lessThan1MB}`);
    console.log(`  1-10MB: ${folder.histogram.from1to10MB}`);
    console.log(`  10-100MB: ${folder.histogram.from10to100MB}`);
    console.log(`  Avg size: ${folder.averageFileSize} bytes`);
    console.log(`  Audio: ${folder.mediaTypeBreakdown.audio}, Video: ${folder.mediaTypeBreakdown.video}`);
  }
}
```

### Incremental Indexing

```typescript
import { refreshIncremental, getLastRefreshTimestamp } from "@obsidian_north/react-native-mediastore";

async function syncLibrary() {
  const lastSync = await getLastRefreshTimestamp();
  const changes = await refreshIncremental(lastSync);

  console.log(`${changes.added} new items`);
  console.log(`${changes.modified} modified items`);
  console.log(`${changes.removed} removed items`);
}
```

### Plugin System

```typescript
import { registerPlugin, getAudio, unregisterPlugin } from "@obsidian_north/react-native-mediastore";

// Register a plugin that adds star ratings based on duration
registerPlugin({
  id: "duration-rating",
  name: "Duration Rating",
  version: "1.0.0",
  extract: (item) => {
    if ("duration" in item) {
      const duration = (item as any).duration;
      const rating = duration > 300000 ? "long" : duration > 60000 ? "medium" : "short";
      return { durationRating: rating };
    }
    return {};
  },
});

// All queries now include plugin metadata
const songs = await getAudio();
// songs[0].customMetadata?.durationRating === "long" | "medium" | "short"

unregisterPlugin("duration-rating");
```

### Improved Batch Query

```typescript
import { getLibraryQuery } from "@obsidian_north/react-native-mediastore";

async function loadLibrary() {
  const result = await getLibraryQuery({
    types: ["audio", "video"],
    typePagination: {
      audio: { limit: 100, offset: 0 },
      video: { limit: 20, offset: 0 },
    },
    includeStatistics: true,
  });

  console.log(`Total: ${result.totalCount} items (${result.totalSize} bytes)`);
  console.log(`Query time: ${result.queryTime}ms`);
  console.log(`Audio: ${result.perTypeStatistics?.audio.count} items`);
  console.log(`Video: ${result.perTypeStatistics?.video.count} items`);
}
```

---

## Types

### AudioItem

```typescript
interface AudioItem {
  id: string; uri: string; title: string;
  artist: string; album: string; albumId: string;
  genre: string | null; duration: number; size: number;
  trackNumber: number; discNumber: number; year: number;
  dateAdded: number; dateModified: number;
  composer: string | null; lyrics: string | null;
  albumArtist: string | null; isFavorite: boolean;
  playCount: number; lastPlayed: number; bookmark: number;
  bitrate: number | null; sampleRate: number | null;
  channels: number | null; encoding: string | null;
  mimeType: string; fileExtension: string; relativePath: string;
  displayName: string; contentUri: string;
}
```

### VideoItem

```typescript
interface VideoItem {
  id: string; uri: string; title: string; duration: number;
  width: number; height: number; frameRate: number | null;
  rotation: number; size: number; mimeType: string;
  relativePath: string; displayName: string; dateAdded: number;
  dateModified: number; resolution: string; orientation: number;
}
```

### ImageItem

```typescript
interface ImageItem {
  id: string; uri: string; title: string; width: number;
  height: number; orientation: number; cameraMake: string | null;
  cameraModel: string | null; dateTaken: number;
  gpsLatitude: number | null; gpsLongitude: number | null;
  mimeType: string; size: number; relativePath: string;
  displayName: string; dateAdded: number; dateModified: number;
}
```

### DocumentItem

```typescript
interface DocumentItem {
  id: string; uri: string; name: string; size: number;
  mimeType: string; extension: string; relativePath: string;
  dateAdded: number; dateModified: number;
}
```

### Album

```typescript
interface Album {
  id: string; title: string; artist: string;
  songCount: number; duration: number;
  artworkUri: string | null; dateAdded: number; year: number | null;
}
```

### Artist

```typescript
interface Artist {
  id: string; name: string;
  albumCount: number; songCount: number;
  duration: number; dateAdded: number;
}
```

### Genre

```typescript
interface Genre {
  id: string; name: string;
  songCount: number; duration: number;
}
```

### Playlist

```typescript
interface Playlist {
  id: string; name: string;
  songCount: number; duration: number;
  dateAdded: number; dateModified: number;
}
```

### Folder

```typescript
interface Folder {
  id: string; name: string; path: string;
  fileCount: number; totalSize: number;
}
```

### SearchResult

```typescript
interface SearchResult {
  audio: AudioItem[]; videos: VideoItem[];
  images: ImageItem[]; documents: DocumentItem[];
  totalCount: number; query: string;
}
```

### LibraryResult

```typescript
interface LibraryResult {
  audio: AudioItem[]; videos: VideoItem[];
  images: ImageItem[]; documents: DocumentItem[];
  totalCount: number;
}
```

### MediaStoreStatistics

```typescript
interface MediaStoreStatistics {
  totalAudio: number; totalVideo: number;
  totalImages: number; totalDocuments: number;
  totalSize: number; totalDuration: number;
}
```

### DuplicateItem

```typescript
interface DuplicateItem {
  fileHash: string; count: number;
  items: AudioItem[]; totalSize: number;
}
```

### SortOptions

```typescript
interface SortOptions { field: SortField; order: SortOrder; }
enum SortField { Name = "name", DateAdded = "dateAdded", DateModified = "dateModified", Duration = "duration", Artist = "artist", Album = "album", Year = "year", FileSize = "fileSize", Resolution = "resolution", Width = "width", Height = "height" }
enum SortOrder { Ascending = "asc", Descending = "desc" }
```

### FilterOptions

```typescript
interface FilterOptions {
  mimeTypes?: string[]; extensions?: string[]; folder?: string;
  album?: string; artist?: string; minDuration?: number;
  maxDuration?: number; minSize?: number; maxSize?: number;
  minResolution?: number; maxResolution?: number; startDate?: number;
  endDate?: number; includeHidden?: boolean; favoritesOnly?: boolean;
  playlistId?: string;
}
```

### PaginationOptions

```typescript
interface PaginationOptions { limit?: number; offset?: number; cursor?: string; }
```

### SearchOptions

```typescript
interface SearchOptions {
  query: string; types?: ("audio" | "video" | "image" | "document")[];
  sort?: SortOptions; filter?: FilterOptions; pagination?: PaginationOptions;
}
```

### MediaChangeEvent

```typescript
interface MediaChangeEvent {
  type: "added" | "removed" | "modified";
  mediaType: "audio" | "video" | "image" | "document";
  itemId: string; uri: string;
}
```

### PermissionStatus

```typescript
interface PermissionStatus { granted: boolean; audio: boolean; video: boolean; images: boolean; }
```

### MediaStoreError

```typescript
type ErrorCode =
  | "PERMISSION_DENIED" | "QUERY_FAILED" | "INVALID_ARGUMENTS"
  | "INVALID_SORT_FIELD" | "INVALID_MIME_TYPE"
  | "UNSUPPORTED_ANDROID_VERSION" | "FILE_UNAVAILABLE"
  | "CURSOR_CLOSED" | "CACHE_FAILURE"
  | "FILE_NOT_FOUND" | "URI_UNAVAILABLE" | "UNSUPPORTED_FORMAT"
  | "CORRUPTED_FILE" | "EXTRACTION_FAILED" | "METADATA_UNAVAILABLE"
  | "API_NOT_SUPPORTED" | "MEDIA_REDACTED" | "TIMEOUT" | "CANCELLED"
  | "UNKNOWN_ERROR";
interface MediaStoreError { code: ErrorCode; message: string; details?: string; }
```

### SizeHistogram

```typescript
interface SizeHistogram {
  lessThan1MB: number;
  from1to10MB: number;
  from10to100MB: number;
  from100MBto1GB: number;
  greaterThan1GB: number;
}
```

### FolderStatistics

```typescript
interface FolderStatistics {
  id: string; name: string; path: string;
  fileCount: number; totalSize: number;
  histogram: SizeHistogram;
  mediaTypeBreakdown: MediaTypeBreakdown;
  averageFileSize: number;
}
interface MediaTypeBreakdown {
  audio: number; video: number; image: number; document: number;
}
```

### IncrementalChanges

```typescript
interface IncrementalChanges {
  added: number; modified: number;
  removed: number; timestamp: number;
}
```

### MetadataPlugin

```typescript
interface MetadataPlugin {
  id: string; name: string; version: string;
  extract: (item: AudioItem | VideoItem | ImageItem | DocumentItem)
    => Record<string, unknown> | Promise<Record<string, unknown>>;
}
```

### LibraryQueryOptions

```typescript
interface LibraryQueryOptions {
  sort?: SortOptions; filter?: FilterOptions;
  pagination?: PaginationOptions;
  types?: ("audio" | "video" | "image" | "document")[];
  typePagination?: {
    audio?: PaginationOptions; video?: PaginationOptions;
    image?: PaginationOptions; document?: PaginationOptions;
  };
  includeStatistics?: boolean;
}
```

### LibraryQueryResult

```typescript
interface LibraryQueryResult {
  audio: AudioItem[]; videos: VideoItem[];
  images: ImageItem[]; documents: DocumentItem[];
  totalCount: number; totalSize: number;
  perTypeStatistics?: LibraryPerTypeStatistics;
  queryTime: number;
}
interface LibraryPerTypeStatistics {
  audio: { count: number; totalSize: number; totalDuration: number };
  video: { count: number; totalSize: number; totalDuration: number };
  image: { count: number; totalSize: number };
  document: { count: number; totalSize: number };
}
```

---

## Supported Document Types

| Format | MIME Type |
|--------|-----------|
| PDF | `application/pdf` |
| DOC | `application/msword` |
| DOCX | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |
| XLS | `application/vnd.ms-excel` |
| XLSX | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| PPT | `application/vnd.ms-powerpoint` |
| PPTX | `application/vnd.openxmlformats-officedocument.presentationml.presentation` |
| ODT | `application/vnd.oasis.opendocument.text` |
| ODS | `application/vnd.oasis.opendocument.spreadsheet` |
| ODP | `application/vnd.oasis.opendocument.presentation` |
| ODG | `application/vnd.oasis.opendocument.graphics` |
| PAGES | `application/x-iwork-pages-sffpages` |
| NUMBERS | `application/x-iwork-numbers-sffnumbers` |
| KEY | `application/x-iwork-keynote-sffkey` |
| TXT | `text/plain` |
| MD | `text/markdown` |
| HTML | `text/html` |
| EPUB | `application/epub+zip` |
| RTF | `application/rtf` |
| CSV | `text/csv` |
| JSON | `application/json` |
| XML | `application/xml`, `text/xml` |
| TAR | `application/x-tar` |
| GZ | `application/gzip` |
| BZ2 | `application/x-bzip2` |
| XZ | `application/x-xz` |
| ZST | `application/zstd` |
| ZIP | `application/zip` |
| RAR | `application/x-rar-compressed` |
| 7Z | `application/x-7z-compressed` |

Document queries are Android-only. iOS returns an empty array.

## File System Operations

The module now includes comprehensive file system CRUD operations, eliminating the need for external packages like `react-native-fs` or `expo-file-system` for basic file management:

### Android & iOS Capabilities

| Operation | Description |
|-----------|-------------|
| `fileExists(filePath)` | Check if a file or directory exists |
| `readDirectory(dirPath)` | List immediate children of a directory |
| `readDirectoryRecursive(dirPath)` | List all files recursively in a directory |
| `fileSize(filePath)` | Get file size in bytes |
| `createFile(filePath)` | Create a new empty file |
| `renameFile(oldPath, newPath)` | Rename/move a file |
| `deleteFile(filePath)` | Delete a file |
| `copyFile(srcPath, dstPath)` | Copy a file |
| `moveFile(srcPath, dstPath)` | Move/rename a file |
| `getDirectoryStatistics(dirPath)` | Get folder statistics: file count, total size, histogram |
| `getMimeType(filePath)` | Auto-detect MIME type from file extension |
| `getFileExtension(filePath)` | Get file extension from path |
| `readFileContents(filePath)` | Read file contents as string |
| `writeFileContents(filePath, data)` | Write data to file |

### Usage Example

```typescript
import { 
  getAudio, 
  getPathByUri,
  readDirectory,
  fileSize,
  createFile,
  renameFile,
  deleteFile,
  copyFile,
  getDirectoryStatistics,
  getMimeType,
  getFileExtension,
  readFileContents,
  writeFileContents
} from "@obsidian_north/react-native-mediastore";

// Discover media
const audio = await getAudio();

// Get file path from URI
const path = await getPathByUri(audio[0].uri);

// File system operations
await fileExists(path);              // true/false
const items = await readDirectory(path);  // [{name, path, isDirectory}]
const allItems = await readDirectoryRecursive(path);  // all files recursively
const size = await fileSize(path);       // bytes in number
await createFile(newPath);             // creates empty file
await renameFile(old, new);            // rename/move
await deleteFile(path);                // delete
await copyFile(src, dst);              // copy
await moveFile(src, dst);              // move
const stats = await getDirectoryStatistics(path);
// { fileCount, totalSize, folderCount, histogram: {lessThan1MB, from1to10MB, ...} }
const mime = await getMimeType(path);  // "audio/mpeg"
const ext = await getFileExtension(path); // "mp3"
const contents = await readFileContents(path); // string | null
await writeFileContents(path, "Hello world"); // write data
```

### Integration with Media Discovery

The file system operations work seamlessly with MediaStore queries:

```typescript
// Get all audio, then get their paths and sizes
const songs = await getAudio();
const songPaths = await Promise.all(songs.map(s => getPathByUri(s.uri)));
const songSizes = await Promise.all(songs.map(s => fileSize(s.uri)));

// Or list a specific directory
const musicDir = await readDirectoryRecursive('/storage/emulated/0/Music');
```

---

## FAQ

**Q: Can I delete files?**
**A:** Yes! Use `deleteFile(filePath)` — the module now supports file deletion on both Android and iOS.

**Q: Can I rename files?**
**A:** Yes! Use `renameFile(oldPath, newPath)` — the module now supports file renaming on both Android and iOS.

**Q: Can I create new files?**
**A:** Yes! Use `createFile(filePath)` — creates an empty file.

**Q: Can I read/write file contents?**
**A:** Yes! Use `readFileContents(filePath)` to read as string, and `writeFileContents(filePath, data)` to write data.

**Q: Can I list directory contents?**
**A:** Yes! Use `readDirectory(dirPath)` for immediate children, or `readDirectoryRecursive(dirPath)` for recursive listing.

**Q: Can I get file size and statistics?**
**A:** Yes! Use `fileSize(filePath)` for individual file size, or `getDirectoryStatistics(dirPath)` for folder-level statistics (file count, total size, histogram).

**Q: Can I detect MIME types from files?**
**A:** Yes! Use `getMimeType(filePath)` to auto-detect MIME types from file extensions.

**Q: Can I get file extensions?**
**A:** Yes! Use `getFileExtension(filePath)` to get the file extension.

---

## FAQ

**Q: Does it work in Expo Go?**
**A:** No. Requires native module support (Expo Dev Build or bare React Native).

**Q: Does it work with Expo Dev Build?**
**A:** Yes.

**Q: Does it work on iOS?**
**A:** Yes. iOS 13.0+ is supported via the Photos Framework (`PHAsset`). Document queries return an empty array on iOS.

**Q: Can I delete files?**
**A:** Yes! Use `deleteFile(filePath)` — the module now supports file deletion on both Android and iOS.

**Q: Can I rename files?**
**A:** Yes! Use `renameFile(oldPath, newPath)` — the module now supports file renaming on both Android and iOS.

**Q: Can I create new files?**
**A:** Yes! Use `createFile(filePath)` — creates an empty file.

**Q: Can I read/write file contents?**
**A:** Yes! Use `readFileContents(filePath)` to read as string, and `writeFileContents(filePath, data)` to write data.

**Q: Can I list directory contents?**
**A:** Yes! Use `readDirectory(dirPath)` for immediate children, or `readDirectoryRecursive(dirPath)` for recursive listing.

**Q: Can I get file size and statistics?**
**A:** Yes! Use `fileSize(filePath)` for individual file size, or `getDirectoryStatistics(dirPath)` for folder-level statistics (file count, total size, histogram).

**Q: Can I detect MIME types from files?**
**A:** Yes! Use `getMimeType(filePath)` to auto-detect MIME types from file extensions.

**Q: Can I get file extensions?**
**A:** Yes! Use `getFileExtension(filePath)` to get the file extension.

---

## Project Structure

```
react-native-mediastore/
 ├── android/
 │   └── src/main/java/com/obsidian_north/mediastore/
 │       ├── MediaStoreModule.kt
 │       ├── MediaStorePackage.kt
 │       ├── MediaStoreRepository.kt
 │       ├── MediaStoreQueryBuilder.kt
 │       ├── MediaStoreMapper.kt
 │       ├── MediaStoreObserver.kt
 │       ├── MediaStorePermissions.kt
 │       ├── MediaStoreCache.kt
 │       ├── metadata/
 │       │   ├── MetadataService.kt
 │       │   ├── MetadataNormalizer.kt
 │       │   ├── MetadataCache.kt
 │       │   ├── MetadataQueue.kt
 │       │   ├── common/
 │       │   │   ├── MetadataValueUtils.kt
 │       │   │   ├── MetadataReader.kt
 │       │   │   └── MetadataErrorCode.kt
 │       │   ├── audio/
 │       │   │   └── AudioMetadataExtractor.kt
 │       │   ├── video/
 │       │   │   └── VideoMetadataExtractor.kt
 │       │   └── image/
 │       │       ├── ImageMetadataExtractor.kt
 │       │       └── ExifMetadataExtractor.kt
 │       ├── models/
 │       │   ├── Options.kt
 │       │   ├── MediaRecords.kt
 │       │   ├── CollectionRecords.kt
 │       │   ├── FolderRecords.kt
 │       │   └── ResultRecords.kt
 │       ├── utils/
 │       │   ├── CursorUtils.kt
 │       │   ├── MimeUtils.kt
 │       │   ├── DurationUtils.kt
 │       │   ├── ArtworkUtils.kt
 │       │   └── MediaStoreMetadataExtractor.kt
 │       └── extensions/
 │           ├── CursorExtensions.kt
 │           ├── ContentResolverExtensions.kt
 │           └── UriExtensions.kt
 ├── ios/
 │   ├── RNMediaStore.podspec
 │   ├── MediaStoreModule.swift
 │   ├── MediaStoreRepository.swift
 │   ├── MediaStoreObserver.swift
 │   └── MediaStorePermissions.swift
 ├── src/
 │   ├── index.ts
 │   ├── MediaStoreModule.ts
 │   ├── MediaStoreModule.types.ts
 │   └── metadata.types.ts
 ├── docs/
 │   └── metadata-architecture.md
 ├── build/
 ├── __tests__/
 │   └── types.test.ts
 ├── example/
 │   ├── app/ (Music, Gallery, Documents, Search tabs)
 │   └── package.json
 ├── .github/workflows/
 │   ├── ci.yml
 │   └── release.yml
 ├── react-native.config.js
 ├── package.json
 └── tsconfig.json
```

---

## Roadmap

```
2.0 — Expo Module (legacy)
  ✓ iOS support (Photos Framework: PHAsset, PHImageManager, PHPhotoLibraryChangeObserver)
  ✓ Thumbnail generation (video + image) with configurable dimensions
  ✓ Album artwork extraction via ContentResolver / PHImageManager
  ✓ Batch library query (getLibrary)
  ✓ Reactive subscriptions (useMediaChangeEvent hook)
  ✓ Duplicate detection via size + MD5 hash
  ✓ Document statistics in getStatistics
  ✓ Cache auto-invalidation on media change events
  ✓ Pagination actually applied (limit/offset/cursor)
  ✓ SQL injection prevention (escapeSql)
  ✓ ExifInterface for camera make/model metadata

2.1 — Expo Module (legacy)
  ✓ Folder statistics (size histograms)
  ✓ Incremental indexing (delta-only refresh)
  ✓ Plugin hooks for custom metadata
  ✓ Batch library query improvements

3.2
  ✓ Migrated from Expo Module to pure React Native native module
  ✓ No dependency on expo-modules-core
  ✓ Compatible with RN CLI, Expo prebuild, and EAS Build
  ✓ Strongly typed native module spec — concrete return types, no Record<string, any>
  ✓ Zero Expo references in native code
  ✓ Android model layer rebuilt for RN bridge pattern
  ✓ Cleaner public API — no unnecessary type casts
  ✓ Comprehensive deep metadata (getDetailedMetadata / getDetailedMetadataByUri)
  ✓ Rich catalog columns on bulk queries
  ✓ Robust album artwork — embedded picture extraction on Android 10+

3.3 (2026-08-17)
   ✓ Metadata subsystem architecture — dedicated metadata/ package with audio/video/image extractors
   ✓ AudioMetadataExtractor — MediaMetadataRetriever id3 tags + MediaExtractor format analysis
   ✓ VideoMetadataExtractor — dimensions, rotation, frameRate, captureFrameRate, hasAudio/hasVideo
   ✓ ImageMetadataExtractor + ExifMetadataExtractor — full EXIF pipeline with GPS redaction awareness
   ✓ MetadataNormalizer — unified schema across all extraction sources
   ✓ MetadataCache — deep metadata LRU cache with generation-based staleness detection
   ✓ MetadataQueue — bounded concurrent extraction with cancellation support
   ✓ MetadataValueUtils — safe parsers for clean/dirty metadata values
   ✓ MetadataReader abstraction — sealed class over Map/Cursor/MediaFormat
   ✓ MetadataErrorCode — structured error codes for metadata operations
   ✓ getMetadata(uri, { level }) — metadata level control (basic/standard/full)
   ✓ inspectMetadata(uri) — diagnostics with per-field provenance
   ✓ getArtworkUri / getArtworkBytes — artwork subsystem
   ✓ Cancellation API — cancelMetadataExtraction / cancelAllMetadataExtraction
   ✓ GPS location redaction awareness (ACCESS_MEDIA_LOCATION)
   ✓ Audio identity tags in deep metadata (title, artist, album, genre, track, disc, year)
   ✓ Metadata architecture document (docs/metadata-architecture.md)

3.3.1 (Current — patch)
   ✓ Fix `npm run prepare` — add missing `Spec` methods (`getMetadata`, `getArtworkUri`, `getArtworkBytes`, `inspectMetadata`, `cancelMetadataExtraction`, `cancelAllMetadataExtraction`) so `tsc --project tsconfig.json` passes
   ✓ iOS bridges for deep metadata / artwork / diagnostics (`MediaStoreModule.swift:263`)
   ✓ JS unwrap for `getDetailedMetadata` to handle Android wrapper vs iOS plain object (`index.ts:237`)
   ✓ `DetailedMetadata.raw` + index signature for strict type tests (`metadata.types.ts:12`)
   ✓ Version bump `package.json:4` + `android/build.gradle.kts:7` → `3.3.1` (`iOS` reads from `package.json`)
   ☐ AI semantic search
  ☐ Smart albums / auto-playlists
  ☐ EXIF utilities (editing GPS, date)
  ☐ Waveform extraction (audio)
  ☐ Face clustering (images)
  ☐ OCR indexing (documents)

4.0
  ☐ Desktop (Electron / Tauri)
  ☐ Cloud sync abstraction
  ☐ Cross-platform unified API
```

---

## Developers

Built and maintained by:

<div align="center">

<table>
  <tr>
    <td align="center" width="50%">
      <img src="./thirteen-labs.png" width="180" alt="Thirteen Labs" />
      <br />
      <strong>Thirteen Labs</strong>
    </td>
    <td align="center" width="50%">
      <img src="./obsidian-northern.png" width="180" alt="Obsidian Northern" />
      <br />
      <strong>Obsidian Northern</strong>
    </td>
  </tr>
</table>

</div>

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

---

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for release history.

---

## License

MIT
