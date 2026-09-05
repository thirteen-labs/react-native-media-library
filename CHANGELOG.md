# Changelog

## 3.5.4 (2026-09-05)

### Bug Fixes

- **Kotlin `mapOf` syntax** `android/.../MediaStoreModule.kt:733`: fixed `getDirectoryStatistics` using JS syntax `"key": value` inside `mapOf` — changed to `"key" to value` (`fileCount`, `totalSize`, `folderCount`, `histogram` + inner histogram). Failure was `Cannot infer type for this parameter` / `Argument type mismatch: Pair<K,V> expected` on `:obsidian_north_react-native-mediastore:compileReleaseKotlin` (Gradle 9.3.1, Kotlin 2.x).
- **Version sync** `package.json:3` / `android/build.gradle.kts:12` now `3.5.4` via `npm run sync-version`

## 3.5.3 (2026-09-04)

### Bug Fixes

- **Kotlin coroutine returns** `android/.../MediaStoreModule.kt:634`: removed explicit `return` inside `coroutineMethod {}` lambdas (`listFiles`, `recursiveList`, `fileLength`, `deleteFile`, `getDirectoryStatistics`) — labeled `return@coroutineMethod` required; bare `return` escaped `ReactMethod` and broke compilation / `Unit` inference
- **Directory statistics accumulation** `android/.../MediaStoreModule.kt:730`: fixed pass-by-value bug where `recursiveStats(dir, fileCount, totalSize, folderCount, histogram)` never accumulated — primitives copied on each recursion, always returned zeros; replaced with `DirectoryStats` data class holding mutable state
- **Null-safety** `android/.../MediaStoreRepository.kt:68`: `uri.authority.contains("media")` → `uri.authority?.contains("media") == true` to avoid NPE on non-media URIs; `repository.queryX` → `queryX` (class already is the repository) and `catch` now `return null` explicitly
- **MimeUtils API** `android/.../MediaStoreModule.kt:780`: `MimeUtils.getMimeType(ext)` → `getMimeFromExtension(ext)` to match actual util signature; added missing `MimeUtils` + `CursorUtils` imports
- **Version sync** `package.json:3` / `android/build.gradle.kts:12` now `3.5.3` via `npm run sync-version`

## 3.5.2 (2026-09-04)

### Chores

- **Version bump only** `package.json:3` / `android/build.gradle.kts:12` `3.5.1` → `3.5.2`; `scripts/sync-version.js:11` now syncs both `?: "x.y.z"` and `} else "x.y.z"` fallbacks with global replace

## 3.5.1 (2026-09-04)

### Bug Fixes

- **Gradle 9 / AGP 7.x compatibility** `android/build.gradle.kts:34`: removed unconditional `androidResources { isNonTransitiveRClass = true }` (introduced in AGP 8.3) that broke `moon-player` on Gradle 9.3.1 (`Unresolved reference 'isNonTransitiveRClass'`). Replaced with conditional comment; `consumerProguardFiles("consumer-rules.pro")` now added.
- **Guava bloat** `android/build.gradle.kts:86`: `guava:33.4.8-android` → `listenablefuture:1.0` + `kotlinx-coroutines-guava` (Inspector only needs `ListenableFuture`), added `android/consumer-rules.pro` for R8 keeps.
- **iOS private KVC** `ios/MediaStoreRepository.swift:11`: replaced `resource.value(forKey:"URL")` / `asset.value(forKey:"filename")` with public `PHAssetResource.originalFilename` + `PHContentEditingInput.fullSizeImageURL` async helper (`asyncAssetFileURL(for:)`) with sync KVC fallback for iOS 13-15 only; added `filename(for:)` helper. `ios/MediaStorePermissions.swift:5` now returns `{granted,status,limited}` handling `.limited` properly.
- **Version sync** `package.json:3` / `android/build.gradle.kts:7` now single-sourced from `package.json` via runtime read; added `scripts/sync-version.js` and `npm run sync-version`.
- **Docs** `README.md:195` corrected `API 21+` → `API 24+` (Media3 Inspector) and `iOS 16+ async` note; updated architecture diagram to `Media3InspectorExtractor`.
- **Build hygiene** `android/.../utils/MediaStoreMetadataExtractor.kt:12` marked `@Deprecated` (use `MetadataService`); `Media3InspectorExtractor.kt:4` now logs enrichment timeouts/failures via `Log.w`.

## 3.5.0 (2026-09-04)

### New Features

- **Android Media3 Inspector**: Added modern metadata extraction via `androidx.media3:media3-inspector:1.11.0` + `media3-inspector-frame:1.11.0` (`Media3InspectorExtractor`) as primary path for audio/video — replaces `MediaMetadataRetriever`/`MediaExtractor` with `MetadataRetriever` (`retrieveDurationUs`/`retrieveTrackGroups`) + `MediaExtractorCompat`/`FrameExtractor`. HDR-aware thumbnail pipeline (`Presentation` downscale, `setExtractHdrFrames`/`setEnableUltraHdr` on API34) in `MediaStoreModule.kt:536` with fallback to `ContentResolver.loadThumbnail`. `MetadataService.kt:15` now takes `Context` to enable enrichment; `MediaStoreRepository` wired to `MetadataService(context)`. (Fixed: `1.8.0` never published — first `media3-inspector` is `1.9.0-alpha01`; `FrameExtractor` moved to `media3-inspector-frame` at `1.10.0`.)
- **iOS async AVFoundation**: Migrated `MediaStoreRepository.swift:971` to `AVAsyncProperty` (`await asset.load(.duration/.tracks/.naturalSize)`, `track.load(.formatDescriptions/.languageCode/.nominalFrameRate/.preferredTransform)`) behind `@available(iOS 16,*)`. Keeps sync legacy fallback for iOS 13-15. `MediaStoreModule.swift:163` and `getMetadata` now dispatch via `Task` on iOS 16+ to avoid blocking deprecations (`AVAsset-deprecated-symbols`).

### Improvements

- **ExifInterface 1.3.7 → 1.4.2** in `android/build.gradle.kts:69` (Dec 2025 stable, bugfixes).
- **Version alignment**: `package.json:3` and `android/build.gradle.kts:7` bumped to `3.5.0` (`ios/RNMediaStore.podspec` reads from `package.json`).

## 3.4.0 (2026-09-03)

### Improvements

- Audited latest metadata extraction APIs vs current package (no code changes) — documented Media3 Inspector vs Stagefright and iOS async `load(_:)` vs sync `asset.duration` deprecations.

## 3.3.1 (2026-08-25)

### Bug Fixes

- **Fix `npm run prepare` build failure**: Added missing `Spec` declarations for `getMetadata`, `getArtworkUri`, `getArtworkBytes`, `inspectMetadata`, `cancelMetadataExtraction`, `cancelAllMetadataExtraction` in `src/MediaStoreModule.ts:31` — `tsc` now passes (`Spec` now mirrors native `MediaStoreModule.kt` and `MediaStoreModule.swift`)
- **iOS parity**: Implemented deep-metadata bridges in `ios/MediaStoreModule.swift:263` (`getMetadata` with `basic`/`standard`/`full` level filtering, `getArtworkUri`/`getArtworkBytes`, `inspectMetadata`, `cancel*`) so the JS API no longer crashes on iOS
- **JS resilience**: `src/index.ts:237` now unwraps Android's `MetadataResult` wrapper for `getDetailedMetadata`/`getDetailedMetadataByUri` to support both Android (wrapper) and iOS (plain `DetailedMetadata`)
- **Type safety**: `src/metadata.types.ts:12` adds `raw` field + index signature to `DetailedMetadata` so `__tests__/metadata.test.ts` passes with strict `tsc`

### Chores

- Bumped `package.json:4` and `android/build.gradle.kts:7` to `3.3.1` (`ios/RNMediaStore.podspec:7` reads from `package.json`)

## 3.3.0 (2026-08-17)

### New Features

- **Metadata subsystem architecture**: Refactored monolithic `MediaStoreMetadataExtractor` into a dedicated `metadata/` package with separate extractors, normalizer, cache, and queue.
  - `MetadataService` — orchestrator with caching, artwork, and diagnostics
  - `AudioMetadataExtractor` — `MediaMetadataRetriever` id3 tags (title, artist, album, genre, track, disc, year, composer, albumArtist) + `MediaExtractor` format analysis (codec, bitrate, sampleRate, channels, bitsPerSample)
  - `VideoMetadataExtractor` — dimensions, rotation, frameRate, captureFrameRate, frameCount, hasAudio, hasVideo, audioTrack info
  - `ImageMetadataExtractor` + `ExifMetadataExtractor` — full EXIF pipeline with GPS redaction awareness (`location.available` / `location.redacted`)
  - `MetadataNormalizer` — unified schema across all extraction sources
  - `MetadataCache` — deep metadata LRU cache with generation-based staleness detection
  - `MetadataQueue` — bounded concurrent extraction (configurable worker pool) with cancellation support
  - `MetadataValueUtils` — safe parsers for clean/dirty metadata values (parseIntOrNull, parseDateOrNull, normalizeSampleRate, normalizeBitrate, etc.)
  - `MetadataReader` — sealed class abstraction over Map/Cursor/MediaFormat
  - `MetadataErrorCode` — structured error codes (`PERMISSION_DENIED`, `UNSUPPORTED_FORMAT`, `CORRUPTED_FILE`, `MEDIA_REDACTED`, `TIMEOUT`, `CANCELLED`, etc.)
- **Metadata levels**: `getMetadata(uri, { level })` supports `"basic"`, `"standard"`, `"full"`, and `"raw"` extraction depths
- **Metadata diagnostics**: `inspectMetadata(uri)` reveals which extraction sources succeeded and per-field provenance
- **Artwork subsystem**: `getArtworkUri(albumId)` and `getArtworkBytes(albumId)` for structured artwork access
- **Cancellation API**: `cancelMetadataExtraction(jobId)` and `cancelAllMetadataExtraction()` for stopping unnecessary metadata work
- **GPS redaction awareness**: Image metadata distinguishes "no GPS exists" from "GPS redacted by Android" (requires `ACCESS_MEDIA_LOCATION`)
- **Audio identity tags**: Deep metadata now extracts title, artist, album, albumArtist, composer, genre, trackNumber, discNumber, year from embedded id3 tags
- **Metadata architecture document**: `docs/metadata-architecture.md` — full design specification for the metadata subsystem

### Improvements

- `MediaStoreRepository` now delegates to `MetadataService` instead of the monolithic `MediaStoreMetadataExtractor`
- `getDetailedMetadata` and `getDetailedMetadataByUri` now return `MetadataResult` with `status`, `warnings`, and `errorCode`
- TypeScript types updated with `MetadataLevel`, `ExtractionStatus`, `MetadataErrorCode`, `ImageLocation`, `ArtworkMetadata`, `MetadataOptions`, `MetadataInspection`
- Extended `ErrorCode` union with metadata-specific error codes
- Updated README with metadata subsystem architecture, new API methods, and complete type documentation

## 3.2.1 (2026-08-15)

### Bug Fixes

- **Android build fix**: resolved Kotlin compilation failures in `MediaStoreModule.kt` and `MediaStoreMetadataExtractor.kt` (`:compileReleaseKotlin`):
  - Added the missing `CursorUtils` import.
  - Replaced the non-existent `MediaFormat.KEY_BITS_PER_SAMPLE` / `KEY_CAPTURE_FRAMERATE` / `getDouble()` references with the raw format keys `"bits-per-sample"` / `"capture-framerate"` and a type-safe `getNumberAsDouble` helper.
  - Replaced the non-existent `ExifInterface.TAG_ISO` with `TAG_ISO_SPEED_RATINGS`.

## 3.2.0 (2026-08-13)

### New Features

- **Comprehensive deep metadata**: Added `getDetailedMetadata(mediaType, id)` and `getDetailedMetadataByUri(uri)` returning a new `DetailedMetadata` model.
  - Audio/Video: codec (normalized), codec MIME, profile/level, bitrate, sample rate, channels, channel layout, bits-per-sample, frame rate, rotation, color space/standard/transfer (via `MediaExtractor` / `AVAsset` + `CMFormatDescription`).
  - Images: format, dimensions, bit depth, color space, and a full `ExifMetadata` block (make/model, aperture, ISO, focal length, GPS, flash, white balance, scene capture, dates, …) via `ExifInterface` / `CGImageSource`.
  - Documents: format, page count (PDF), word/character/line counts (text), encryption flag, creation/modification dates.
- **Rich catalog columns on bulk queries** (read from the media index, no file I/O): `AudioItem` (`writer`, `isMusic`, `isPodcast`, `isRingtone`, `isAlarm`, `isNotification`, `cdTrackNumber`, `numTracks`), `VideoItem` (`colorStandard`, `colorTransfer`, `videoCodec`, `bucketId`, `bucketDisplayName`, `dateTaken`), `ImageItem` (`bucketId`, `bucketDisplayName`, `description`), `DocumentItem` (`title`, `isFavorite`).

### Bug Fixes

- **Android album artwork** now extracts the embedded picture via `MediaMetadataRetriever.getEmbeddedPicture()` and writes a cached file, fixing silent failures on Android 10+ (scoped storage removed the legacy `albumart` content provider).
- **Android thumbnail URIs** now use `File.toURI()` instead of `Uri.fromFile(...)` to avoid `FileUriExposedException` under StrictMode; pre-Q thumbnail path hardened with `toLongOrNull()`.

## 3.1.2 (2026-08-01)

### Bug Fixes

- **Android permissions compile fix**: Added missing parentheses around `&&` chain in `MediaStorePermissions.kt` — Kotlin `to` infix operator binds tighter than `&&`, causing a `Pair<String, Boolean>` vs `Boolean` type mismatch

## 3.1.1 (2026-08-01)

### Bug Fixes

- **Android compile fixes**: Removed non-portable `SAMPLE_RATE`/`CHANNEL_COUNT` column references from the audio query projection and mapper (`MediaStoreQueryBuilder.kt`, `MediaStoreMapper.kt`)
- **Fixed nullable argument**: `getAlbumArtwork` no longer passes a `String?` into `ArtworkUtils.getAlbumArtworkUri(albumId: String)` (`MediaStoreModule.kt`)

## 3.1.0 (2026-07-30)

### Improvements

- **Strongly typed TurboModule Spec**: `NativeMediaStore.ts` now uses concrete return types (`AudioItem[]`, `Album[]`, `FolderStatistics[]`, `SearchResult`, etc.) instead of `Record<string, any>` — full type safety through the native bridge
- **Removed 17 unnecessary casts** from `index.ts` — the typed Spec eliminated all manual `as Promise<X>` assertions
- **Rebuilt missing Android model classes**: Created `android/.../models/` package (5 files) with properly typed toMap() methods for the RN bridge pattern

### Bug Fixes

- **Fixed `.npmignore`**: Changed `ios` → `ios/Pods` — was excluding all iOS source files from npm publish
- **No Expo traces**: Confirmed zero Expo imports, dependencies, or config in library native code

## 2.0.0 (2026-07-10)

### Breaking Changes

- **iOS Support**: Now works on both Android (MediaStore) and iOS (Photos Framework)
- **Typed Returns**: `getRecent`, `getFavorites`, `getLargestFiles` now return properly typed records instead of raw maps
- **Pagination Applied**: `limit` and `offset` in `PaginationOptions` now actually work (were ignored in v1)
- **Cache Wired**: LRU cache is now integrated into all query functions (was built but unused in v1)

### New Features

- **iOS MediaLibrary**: Full iOS support using `PHAsset` and `Photos.framework`
  - Audio, video, image queries with metadata
  - Album, artist, genre, playlist aggregation
  - Search with `CONTAINS[cd]` matching
  - Thumbnail generation via `PHImageManager`
  - Real-time change observation via `PHPhotoLibraryChangeObserver`
  - Permission handling via `PHPhotoLibrary.requestAuthorization`
- **Thumbnail Generation**: `getVideoThumbnail` and `getImageThumbnail` now return actual thumbnail file URIs
  - Android: Uses `ContentResolver.loadThumbnail()` (API 29+) with fallback to `MediaStore.Thumbnails`
  - iOS: Uses `PHImageManager.requestImage()` with configurable size
- **Duplicate Detection**: `getDuplicates` now groups files by size + MD5 hash
- **Document Statistics**: `getStatistics` now includes document counts and sizes
- **ExifInterface**: Image metadata now reads camera make/model from EXIF data

### Bug Fixes

- **SQL Injection**: Fixed unsafe string interpolation in query filters (now uses `escapeSql()`)
- **Broken Extensions Filter**: Fixed `LIKE '%.' || $quoted` syntax to proper `LIKE '%.ext'`
- **Hardcoded Mapper Values**: Audio `isFavorite` now reads `IS_FAVORITE` column (API 29+)
- **Video/Image Metadata**: `relativePath` now read from cursor instead of hardcoded empty string
- **Image GPS**: `gpsLatitude`/`gpsLongitude` now read from `MediaStore.Images.Media` columns
- **Permissions Request**: `requestPermissions()` now actually prompts the user (was a no-op)
- **Genre/Playlist Queries**: Now respect sort and pagination parameters
- **Video Rotation**: Now reads `ORIENTATION` column instead of hardcoded 0

### Improvements

- **Cache Auto-Invalidation**: Cache automatically invalidates on `onMediaChange` events
- **iOS Observers**: `PHPhotoLibraryChangeObserver` properly maps insert/update/remove events
- **Sort Support**: Genre and playlist queries now support sorting

## 1.0.0 (2025-07-10)

### Initial Release

- Audio queries (`getAudio`) with metadata (artist, album, genre, duration, bitrate, etc.)
- Video queries (`getVideos`) with metadata (resolution, frame rate, rotation, orientation, etc.)
- Image queries (`getImages`) with metadata (EXIF, GPS, orientation, camera info, etc.)
- Document queries (`getDocuments`) with support for PDF, DOC, DOCX, XLS, XLSX, PPT, PPTX, TXT, EPUB, RTF, CSV, JSON, XML, ZIP, RAR, 7Z
- Album, Artist, Genre, Playlist aggregation queries
- Folder aggregation (`getFolders`) with file counts and total sizes
- Full-text search engine with prefix, partial, case-insensitive, multi-keyword, unicode support
- Pagination (limit/offset and cursor-based) on every API
- Real-time change observation via `ContentObserver` (add, remove, modify events)
- Granular permissions for Android 13+ (`READ_MEDIA_AUDIO`, `READ_MEDIA_VIDEO`, `READ_MEDIA_IMAGES`)
- Legacy `READ_EXTERNAL_STORAGE` fallback for Android 12 and below
- LRU in-memory cache with configurable TTL and auto-invalidation
- Duplicate detection using file heuristics
- Media statistics (counts, total size, total duration)
- Favorites support
- React hook `useMediaChangeEvent`
- Structured error codes
- 100% TypeScript types
- Thread-safe background execution on `Dispatchers.IO`
- Android API 21+ support
- Expo Modules API compatibility
