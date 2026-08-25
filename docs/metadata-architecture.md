# Metadata Architecture — react-native-mediastore

**Target version:** v2.1.4 → v2.2
**Status:** Design specification
**Last updated:** 2026-08-17

---

## 1. The core idea: don't treat "metadata" as one source

The biggest architectural improvement is recognizing that Android has **multiple metadata layers**.

Instead of:

```
MediaStore
   ↓
metadata
   ↓
JS
```

build:

```
                    ┌─────────────────────┐
                    │   MediaStore Index  │
                    │ fast catalog data   │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │ Metadata Normalizer │
                    └──────────┬──────────┘
                               │
              ┌────────────────┼────────────────┐
              ▼                ▼                ▼
       Audio extractor   Video extractor   Image extractor
              │                │                │
              ▼                ▼                ▼
       MediaMetadata      MediaMetadata      ExifInterface
       Retriever          Retriever
              │                │
              └────────────────┼────────────────┘
                               ▼
                    ┌─────────────────────┐
                    │ Deep Metadata Layer │
                    │ optional/on-demand  │
                    └──────────┬──────────┘
                               ▼
                    ┌─────────────────────┐
                    │ Unified JS Object   │
                    └─────────────────────┘
```

That gives us **fast metadata for listing thousands of files**, while still allowing **deep metadata for an individual file**.

Android itself describes `MediaStore` as an indexed collection of common media types, while the `Files` collection provides a broader view across media.

---

## 2. Separate "indexed metadata" from "extracted metadata"

This is probably the single most important improvement.

### Layer A — MediaStore metadata

This should be extremely fast.

For example:

```
id
uri
displayName
mimeType
size
dateAdded
dateModified
duration
width
height
orientation
album
albumArtist
artist
genre
title
track
discNumber
bitrate
bucket
relativePath
```

Android already exposes many of these through `MediaStore.MediaColumns` and its audio/video/image collections. For example, Android's indexed columns include artist, album, bitrate, capture framerate, duration, height, orientation, genre, etc.

This should be the **default path**.

If the app asks:

> "Give me all 4,000 songs."

we should **not open 4,000 individual files**.

Instead:

```
MediaStore query
        ↓
Cursor
        ↓
4,000 lightweight records
        ↓
JS
```

That should be very fast.

---

## 3. Deep extraction should be optional

Then expose something like:

```ts
MediaStore.getMetadata(uri)
```

for one item.

That could perform:

```
URI
 ↓
determine MIME
 ↓
determine media type
 ↓
select extractor
 ↓
extract deeper metadata
 ↓
normalize
 ↓
return
```

For example:

```ts
const metadata = await MediaStore.getMetadata(uri);
```

could return:

```ts
{
  id: "...",
  uri: "...",
  type: "audio",

  basic: {
    name: "song.flac",
    mimeType: "audio/flac",
    size: 28473921,
    duration: 238421
  },

  audio: {
    title: "Darkside",
    artist: "Example Artist",
    album: "Example Album",
    albumArtist: "Example Artist",
    genre: "Electronic",
    track: 4,
    disc: 1,
    year: 2025,

    codec: "FLAC",
    sampleRate: 96000,
    bitsPerSample: 24,
    channels: 2,
    bitrate: 2840000
  }
}
```

This distinction gives the package a much better performance profile.

---

## 4. Audio metadata deserves its own extractor

For audio, create:

```
AudioMetadataExtractor.kt
```

Its job would be to extract things such as:

### Identity

```
title
artist
album
albumArtist
composer
genre
comment
grouping
```

### Track information

```
trackNumber
discNumber
totalTracks
totalDiscs
year
compilation
```

### Technical information

```
duration
bitrate
sampleRate
bitsPerSample
channels
codec
mimeType
```

### Artwork

```
hasArtwork
artwork
artworkMimeType
```

`MediaMetadataRetriever` provides many of these metadata keys, including bitrate, duration, album, artist, sample rate, bits-per-sample, MIME type, number of tracks, etc.

But there is an important compatibility issue.

`METADATA_KEY_BITS_PER_SAMPLE` and `METADATA_KEY_SAMPLERATE` were added in **API 31**.

So we should **never blindly reference them on older Android versions**.

Instead:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    ...
}
```

or use a compatibility abstraction.

---

## 5. Don't use one giant metadata extractor

Avoid having:

```
MediaStoreMetadataExtractor.kt
```

become a 1,000+ line monster.

Instead:

```
metadata/
├── MetadataExtractor.kt
├── MetadataNormalizer.kt
├── MetadataConstants.kt
│
├── audio/
│   ├── AudioMetadataExtractor.kt
│   ├── AudioMetadataMapper.kt
│   └── AudioArtworkExtractor.kt
│
├── video/
│   ├── VideoMetadataExtractor.kt
│   ├── VideoMetadataMapper.kt
│   └── VideoFrameExtractor.kt
│
├── image/
│   ├── ImageMetadataExtractor.kt
│   ├── ExifMetadataExtractor.kt
│   └── ImageDimensionExtractor.kt
│
└── common/
    ├── CursorUtils.kt
    ├── UriUtils.kt
    ├── MimeTypeUtils.kt
    └── MetadataValueUtils.kt
```

This also directly addresses the current `Unresolved reference: CursorUtils` error. If `CursorUtils` is supposed to be shared infrastructure, it should be an explicit utility in the package rather than an implicit dependency.

---

## 6. Fix the CursorUtils situation properly

Make a reusable utility:

```
CursorUtils
```

with safe methods such as:

```kotlin
getStringOrNull()
getIntOrNull()
getLongOrNull()
getFloatOrNull()
getDoubleOrNull()
getBooleanOrNull()
getBlobOrNull()
hasColumn()
```

Conceptually:

```kotlin
cursor.getStringOrNull(MediaStore.MediaColumns.DISPLAY_NAME)
```

instead of:

```kotlin
cursor.getString(cursor.getColumnIndex(...))
```

Why?

Because Android/OEM MediaStore implementations can differ.

A missing column should produce:

```
null
```

rather than:

```
-1
```

followed by:

```
CursorIndexOutOfBoundsException
```

This also gives us a single place to handle:

```
missing column
null value
wrong column type
empty string
invalid numeric value
```

---

## 7. Build a safe metadata value parser

Introduce:

```
MetadataValueUtils.kt
```

This becomes important because metadata isn't clean.

For example:

```
"44100"
"44100.0"
"44.1 kHz"
null
""
"unknown"
"-1"
```

shouldn't crash the extractor.

Instead:

```kotlin
parseIntOrNull()
parseLongOrNull()
parseFloatOrNull()
parseDoubleOrNull()
parseBooleanOrNull()
parseDateOrNull()
```

And then normalize.

For example:

```
"44100"
        ↓
sampleRate = 44100
```

and:

```
"44100.0"
        ↓
sampleRate = 44100
```

---

## 8. The current getDouble error is a design warning

The current build has:

```
Unresolved reference 'getDouble'
```

at the metadata extraction layer.

Rather than simply changing that one line, determine **what object you're calling `getDouble()` on**.

If it's a `Cursor`, use the cursor's typed API.

If it's metadata returned as a `String`, parse it.

If it's a `Bundle`, use the appropriate typed getter.

The extractor shouldn't assume that all metadata behaves like a generic map.

Create one abstraction:

```
MetadataReader
```

so the rest of the package doesn't care where the value came from.

---

## 9. Video metadata can become much richer

For videos target:

```
title
duration
width
height
rotation
frameRate
captureFrameRate
frameCount
bitrate
mimeType
codec
hasAudio
hasVideo
audioTracks
videoTracks
```

Android's `MediaMetadataRetriever` supports duration, video width/height, rotation, frame count, capture framerate and related metadata.

Then optionally:

```
HDR
colorSpace
colorRange
colorTransfer
profile
level
```

where supported.

The important thing is that these should be **optional fields**.

Don't make this:

```ts
frameRate: number
```

when Android doesn't know the value.

Use:

```ts
frameRate: number | null
```

instead.

---

## 10. Image metadata should use EXIF, not just MediaStore

For images, create a dedicated EXIF pipeline.

Something like:

```
MediaStore
     ↓
basic image record
     ↓
ExifInterface
     ↓
EXIF metadata
```

Extract:

```
width
height
orientation

make
model
lensModel
software

dateTime
dateTimeOriginal
dateTimeDigitized

latitude
longitude

focalLength
fNumber
iso
exposureTime
flash

whiteBalance
colorSpace
```

This is much better than trying to force everything through `MediaMetadataRetriever`.

Android explicitly recommends `ExifInterface` for image properties such as height, width and rotation, and notes that `MetadataRetriever` is not intended as a general-purpose extractor for standalone images.

---

## 11. Be careful with GPS metadata

This is an important privacy/API issue.

For Android 10+, accessing unredacted photo EXIF location requires `ACCESS_MEDIA_LOCATION` and runtime permission.

So the package should return something like:

```ts
location: {
  latitude: number | null;
  longitude: number | null;
  available: boolean;
  redacted: boolean;
}
```

rather than silently returning:

```
latitude: null
longitude: null
```

The caller should be able to distinguish:

```
no GPS exists
```

from:

```
GPS exists but Android redacted it
```

That's a much more professional API.

---

## 12. Documents should not pretend to be MediaStore metadata

This is another architectural distinction.

`react-native-mediastore` can handle:

```
Photos
Audio
Video
Media files
```

but generic documents such as:

```
PDF
DOCX
XLSX
PPTX
EPUB
TXT
ZIP
```

belong more naturally to Android's Storage Access Framework rather than MediaStore. Android's documentation explicitly separates media content handled by MediaStore from documents/other files handled through SAF.

So if the package exposes a generic `getFiles()` API, distinguish:

```ts
type: "image"
type: "video"
type: "audio"
type: "document"
type: "other"
```

and make it clear that deep metadata support varies by type.

---

## 13. Metadata extraction should have levels

Introduce:

```ts
metadataLevel
```

### `basic`

Extremely fast:

```
id
uri
name
mimeType
size
dateAdded
dateModified
type
```

### `standard`

For library views:

```
basic +
duration
dimensions
orientation
artist
album
title
genre
track
albumArt availability
```

### `full`

Deep extraction:

```
standard +
codec
bitrate
sampleRate
bitsPerSample
channels
frameRate
EXIF
camera information
GPS
embedded artwork
technical metadata
```

### `raw`

Potentially return provider/extractor-specific information.

This gives users control over performance.

---

## 14. This is especially important for large libraries

Imagine an app has:

```
15,000 songs
3,000 videos
12,000 photos
```

We absolutely don't want:

```
15,000 × MediaMetadataRetriever
```

during startup.

Instead:

```
STARTUP
   ↓
MediaStore query
   ↓
basic/standard records
   ↓
SQLite
   ↓
UI immediately available
```

Then:

```
USER OPENS SONG
        ↓
deep metadata request
        ↓
MediaMetadataRetriever
        ↓
cache
```

That's a huge architectural improvement.

---

## 15. Add a metadata cache

Cache deep metadata.

For example:

```
metadata_cache
```

with:

```
uri
size
modified
generationModified
metadataHash
metadataJson
updatedAt
```

Then:

```
request metadata
      ↓
cached?
   /       \
 yes       no
 ↓          ↓
return    extract
            ↓
          cache
```

But Android gives us something even better for synchronization.

On API 30+, MediaStore exposes:

```
GENERATION_ADDED
GENERATION_MODIFIED
```

and Android specifically says generation numbers are more robust for detecting media changes than relying solely on `DATE_ADDED` or `DATE_MODIFIED`.

That means we can build:

```
MediaStore generation
        ↓
detect changes
        ↓
only re-extract changed files
```

instead of rescanning everything.

That's a **major upgrade** for any media-library application.

---

## 16. Build a two-stage indexing system

Make this the core architecture.

### Stage 1 — Index

Fast:

```
MediaStore
 ↓
Cursor
 ↓
normalize
 ↓
SQLite
```

### Stage 2 — Enrich

Slow/expensive:

```
SQLite records
 ↓
metadata queue
 ↓
deep extractor
 ↓
update SQLite
```

For example:

```
Song 1   ██████████ complete
Song 2   ██████████ complete
Song 3   ███░░░░░░░ pending
Song 4   ██████████ complete
```

The UI doesn't have to wait.

---

## 17. Metadata extraction should be concurrent — but bounded

Don't do:

```
for every file
    launch coroutine
```

with thousands of concurrent retrievers.

Instead:

```
MetadataQueue
       ↓
worker pool
       ↓
2–4 extraction workers
       ↓
cache
```

Something like:

```
Queue
 ├── Worker 1
 ├── Worker 2
 ├── Worker 3
 └── Worker 4
```

The exact number should be tuned, but the principle is important.

Media extraction can involve actual file I/O and decoding, and `MediaMetadataRetriever.setDataSource()` itself may be time-consuming.

---

## 18. Add cancellation

If the user opens:

```
All Songs
```

and then immediately navigates to:

```
Videos
```

we shouldn't continue unnecessary metadata work.

Expose cancellation internally:

```
MetadataJob
 ├── queued
 ├── running
 ├── completed
 ├── failed
 └── cancelled
```

Android's content-provider query APIs support cancellation through `CancellationSignal`, which is exactly the sort of mechanism we should use for expensive queries.

---

## 19. Metadata extraction should never crash the entire scan

This is crucial.

Bad:

```
file 1 ✓
file 2 ✓
file 3 ✓
file 4 ❌
        ↓
entire scan crashes
```

Good:

```
file 1 ✓
file 2 ✓
file 3 ✓
file 4 ⚠️ partial
file 5 ✓
file 6 ✓
```

Return:

```ts
{
  metadata: {...},
  extraction: {
    status: "partial",
    warnings: [...]
  }
}
```

For example:

```ts
{
  extraction: {
    status: "partial",
    warnings: [
      "EXIF metadata unavailable"
    ]
  }
}
```

---

## 20. Define extraction errors properly

Create:

```ts
MetadataErrorCode
```

such as:

```
PERMISSION_DENIED
FILE_NOT_FOUND
URI_UNAVAILABLE
UNSUPPORTED_FORMAT
CORRUPTED_FILE
EXTRACTION_FAILED
METADATA_UNAVAILABLE
API_NOT_SUPPORTED
MEDIA_REDACTED
TIMEOUT
CANCELLED
```

Then JS gets structured errors rather than:

```
java.lang.IllegalStateException...
```

---

## 21. Normalize all metadata into one schema

This is extremely important for cross-platform React Native.

For example:

```ts
interface MediaMetadata {
  id: string;
  uri: string;
  type: MediaType;

  name: string | null;
  mimeType: string | null;
  size: number | null;

  dates: {
    added: number | null;
    modified: number | null;
    created: number | null;
    taken: number | null;
  };

  dimensions?: {
    width: number | null;
    height: number | null;
    orientation: number | null;
  };

  audio?: {
    title: string | null;
    artist: string | null;
    album: string | null;
    albumArtist: string | null;
    genre: string | null;

    track: number | null;
    disc: number | null;
    year: number | null;

    duration: number | null;
    bitrate: number | null;
    sampleRate: number | null;
    bitsPerSample: number | null;
    channels: number | null;
    codec: string | null;
  };

  video?: {
    duration: number | null;
    width: number | null;
    height: number | null;
    rotation: number | null;

    frameRate: number | null;
    bitrate: number | null;
    codec: string | null;

    hasAudio: boolean | null;
    hasVideo: boolean | null;
  };

  image?: {
    cameraMake: string | null;
    cameraModel: string | null;
    lensModel: string | null;

    iso: number | null;
    exposureTime: number | null;
    focalLength: number | null;
    aperture: number | null;

    latitude: number | null;
    longitude: number | null;
  };

  artwork?: {
    available: boolean;
    uri?: string;
  };
}
```

The key principle:

**missing metadata = `null`, not fake defaults.**

Don't turn:

```
unknown sample rate
```

into:

```
sampleRate: 0
```

because `0` means something different.

---

## 22. Add metadata provenance

This would make the package considerably more advanced.

Every metadata value could theoretically have a source:

```ts
{
  bitrate: {
    value: 320000,
    source: "mediastore"
  }
}
```

or:

```ts
{
  bitrate: {
    value: 319842,
    source: "retriever"
  }
}
```

or:

```ts
{
  artist: {
    value: "Juice WRLD",
    source: "embedded"
  }
}
```

This helps when sources disagree.

We could define:

```
MEDIASTORE
EXIF
MEDIA_METADATA_RETRIEVER
EMBEDDED
FILENAME
FALLBACK
```

---

## 23. Use confidence/fallback rules

Suppose:

```
MediaStore artist = null
embedded artist = "Artist X"
filename = "Artist X - Song.mp3"
```

The normalizer can use:

```
Embedded metadata
        ↓
MediaStore
        ↓
Filename inference
```

But we should **not silently overwrite real metadata**.

Instead:

```
artist = "Artist X"
artistSource = "embedded"
```

This becomes especially useful for poorly tagged music libraries.

---

## 24. Artwork should be its own subsystem

For music applications, album artwork is huge.

Instead of returning massive blobs inside the normal metadata object:

```
artwork: base64...
```

avoid that.

Return:

```ts
artwork: {
  available: true,
  uri: "content://..."
}
```

Then provide:

```ts
MediaStore.getArtwork(uri)
```

or:

```ts
MediaStore.getArtworkUri(uri)
```

Potentially with:

```ts
size: "small"
size: "medium"
size: "large"
```

This prevents huge JS bridge payloads.

---

## 25. Don't send unnecessary data across the RN bridge

This is another major performance optimization.

Bad:

```
Android
 ↓
100 MB image/artwork
 ↓
JS bridge
```

Good:

```
Android
 ↓
content:// URI
 ↓
React Native Image
```

Similarly, don't send raw EXIF blobs unless requested.

---

## 26. Metadata should be lazy

The API could support:

```ts
getPhotos({
  metadata: "basic"
});
```

or:

```ts
getPhotos({
  metadata: "standard"
});
```

and:

```ts
getMetadata(uri, {
  level: "full"
});
```

This is much cleaner than forcing one expensive behavior on everyone.

---

## 27. Query projections should be optimized

Don't use:

```kotlin
projection = null
```

for everything.

Request only what you need.

For example:

```
PHOTO_PROJECTION
AUDIO_PROJECTION
VIDEO_PROJECTION
FILE_PROJECTION
```

This reduces cursor data and makes the query layer predictable.

Android's `ContentResolver` query API explicitly allows specifying a projection and supports cancellation.

---

## 28. Don't rely exclusively on DATA

For modern Android, make the canonical identifier:

```
content://...
```

not:

```
/storage/emulated/0/...
```

Android documentation notes that while `DATA` can still be used when accessing an existing media file, apps shouldn't assume the path is always available and should be prepared for file I/O failures.

So:

```ts
uri: "content://media/..."
```

should be primary.

Then:

```ts
path?: string | null
```

can be secondary.

---

## 29. Android API compatibility needs to be designed into the extractor

This is particularly relevant to current compile errors.

For example:

| Metadata                       | Android API |
| ------------------------------ | ----------: |
| Basic MediaStore               |      API 1+ |
| Capture framerate              |     API 23+ |
| Image dimensions via retriever |     API 28+ |
| Bits per sample                |     API 31+ |
| Sample rate metadata key       |     API 31+ |
| MediaStore generation          |     API 30+ |
| EXIF location permission       |     API 29+ |

`METADATA_KEY_BITS_PER_SAMPLE` and `METADATA_KEY_SAMPLERATE` specifically require API 31+.

So the code should have explicit compatibility guards.

---

## 30. The current errors should become a redesign trigger

Current failures:

```
CursorUtils
KEY_BITS_PER_SAMPLE
KEY_CAPTURE_FRAMERATE
getDouble
TAG_ISO
```

suggest that the extractor currently mixes several different metadata systems.

Refactor rather than patch:

```
OLD

MediaStoreMetadataExtractor
 ├── Cursor
 ├── MediaMetadataRetriever
 ├── EXIF
 ├── parsing
 ├── normalization
 └── output
```

into:

```
NEW

MetadataService
       │
       ├── MediaStoreIndexer
       │
       ├── MetadataResolver
       │       │
       │       ├── AudioMetadataExtractor
       │       ├── VideoMetadataExtractor
       │       └── ImageMetadataExtractor
       │
       ├── MetadataNormalizer
       │
       ├── MetadataCache
       │
       └── MetadataQueue
```

That is much more maintainable.

---

## 31. Add a metadata diagnostics API

This could be incredibly useful when debugging weird devices.

```ts
MediaStore.inspectMetadata(uri)
```

returns:

```ts
{
  uri: "...",
  mimeType: "audio/flac",

  sources: {
    mediaStore: true,
    mediaMetadataRetriever: true,
    exif: false
  },

  fields: {
    title: {
      value: "Song",
      source: "mediastore"
    },

    sampleRate: {
      value: 96000,
      source: "retriever"
    },

    bitsPerSample: {
      value: 24,
      source: "retriever"
    }
  },

  warnings: []
}
```

For a library developer, this is **gold**.

---

## 32. Testing needs to become a first-class feature

Build a metadata test corpus.

### Audio

```
MP3
AAC
M4A
FLAC
OGG
OPUS
WAV
AIFF
ALAC
WMA
```

### Video

```
MP4
MKV
WEBM
MOV
AVI
3GP
```

### Images

```
JPEG
PNG
WEBP
HEIC
HEIF
GIF
AVIF
```

And deliberately test:

```
missing metadata
corrupt metadata
huge files
zero-byte files
permission denied
cloud-backed URI
content URI
OEM-specific media
API 24
API 28
API 29
API 30
API 31
API 33
API 35+
```

---

## 33. OEM testing matters

Samsung, Xiaomi, Huawei, Oppo, Vivo, Tecno, Infinix, Pixel, etc. can have differences in media databases and file providers.

So the extractor must follow:

```
Android contract
+
defensive cursor handling
+
nullable fields
+
fallback extractors
```

rather than assuming every MediaStore implementation behaves identically.

---

## 34. The ideal pipeline

For `react-native-mediastore`, aim for:

```
                   USER / APP
                       │
                       ▼
              React Native API
                       │
                       ▼
              MetadataService
                       │
             ┌─────────┴─────────┐
             │                   │
          BASIC              DEEP
             │                   │
             ▼                   ▼
       MediaStore          MIME detection
             │                   │
             │          ┌────────┼────────┐
             │          ▼        ▼        ▼
             │       AUDIO     VIDEO    IMAGE
             │          │        │        │
             │          ▼        ▼        ▼
             │       Retriever Retriever EXIF
             │          │        │        │
             └──────────┴────────┴────────┘
                       │
                       ▼
                MetadataNormalizer
                       │
                       ▼
                  MetadataCache
                       │
                       ▼
                 React Native
```

---

## 35. Recommended file structure

```text
android/src/main/java/com/obsidian_north/mediastore/

├── MediaStoreModule.kt
│
├── core/
│   ├── MediaStoreQuery.kt
│   ├── MediaTypeResolver.kt
│   ├── PermissionManager.kt
│   └── UriResolver.kt
│
├── metadata/
│   ├── MetadataService.kt
│   ├── MetadataNormalizer.kt
│   ├── MetadataCache.kt
│   ├── MetadataQueue.kt
│   ├── MetadataValueUtils.kt
│   │
│   ├── audio/
│   │   ├── AudioMetadataExtractor.kt
│   │   └── AudioArtworkExtractor.kt
│   │
│   ├── video/
│   │   ├── VideoMetadataExtractor.kt
│   │   └── VideoFrameExtractor.kt
│   │
│   └── image/
│       ├── ImageMetadataExtractor.kt
│       └── ExifMetadataExtractor.kt
│
└── utils/
    ├── CursorUtils.kt
    ├── MimeTypeUtils.kt
    └── DateUtils.kt
```

---

## 36. Recommended public JS API

```ts
MediaStore.getAudio()
MediaStore.getVideos()
MediaStore.getPhotos()
MediaStore.getFiles()

MediaStore.getMetadata(uri, {
  level: "full"
})

MediaStore.getArtwork(uri)

MediaStore.getThumbnail(uri, {
  width: 512,
  height: 512
})

MediaStore.getChanges({
  sinceGeneration: 1234
})
```

That would turn `react-native-mediastore` from **"a React Native wrapper around MediaStore"** into a genuinely strong **native media indexing and metadata engine**.

---

## 37. For large music apps specifically

```
MediaStore
   ↓
Fast scan
   ↓
SQLite
   ↓
Library immediately available
   ↓
background metadata enrichment
   ↓
album art / technical information
   ↓
cache
```

Then when the user opens a track:

```
SQLite
 ↓
cached metadata
 ↓
instant UI
```

rather than:

```
open track
 ↓
scan file
 ↓
extract metadata
 ↓
wait
 ↓
render
```

That makes the app feel dramatically faster.
