\# Android MediaStore Expo Module



\## Universal High-Performance Media Indexing Library



\*\*Version:\*\* 3.3.1



\---



\# Overview



The Android MediaStore Module is a reusable Expo Module designed to provide fast, reliable, and production-grade access to media and indexed documents on Android devices. It should serve as the primary storage indexing layer for all applications developed within the ecosystem.



Rather than recursively scanning directories using filesystem APIs, the module leverages Android's native MediaStore database, providing near-instant indexing, lower battery usage, and metadata-rich results.



The module is intended to be imported into any Expo Development Build or React Native application without requiring changes to the application codebase.



Applications include:



\* Music Players

\* Video Players

\* Gallery Apps

\* File Managers

\* Document Readers

\* Podcast Apps

\* Audio Book Apps

\* Offline Media Libraries

\* AI File Search

\* Backup Utilities



\---



\# Design Goals



The module must be:



\* Extremely fast

\* Memory efficient

\* Thread-safe

\* Production ready

\* Fully typed

\* Reusable

\* Modular

\* Future-proof

\* Easy to extend

\* Compatible with Expo Modules

\* Compatible with React Native New Architecture



\---



\# Core Philosophy



The module is \*\*not\*\* a file explorer.



It is an indexing engine built on Android's MediaStore database.



Filesystem operations are intentionally separated from indexing.



Responsibilities include:



\* Discover media

\* Read metadata

\* Search

\* Sort

\* Filter

\* Watch changes

\* Cache results

\* Generate lightweight models



Responsibilities excluded:



\* Delete files

\* Rename files

\* Copy files

\* Move files

\* Download files

\* Write files



Those belong in a separate filesystem module.



\---



\# Architecture



```text

React Native / Expo

&#x20;       │

&#x20;       ▼

TypeScript API

&#x20;       │

&#x20;       ▼

Expo Module

&#x20;       │

&#x20;       ▼

Kotlin

&#x20;       │

&#x20;       ▼

Repository Layer

&#x20;       │

&#x20;       ▼

MediaStore Queries

&#x20;       │

&#x20;       ▼

Cursor Mapper

&#x20;       │

&#x20;       ▼

Domain Models

&#x20;       │

&#x20;       ▼

JSON

```



\---



\# Internal Package Structure



```text

expo-mediastore/



android/



src/



MediaStoreModule.kt



MediaStoreRepository.kt



MediaStoreQuery.kt



MediaStoreMapper.kt



MediaStoreObserver.kt



MediaStorePermissions.kt



MediaStoreCache.kt



models/



Audio.kt



Video.kt



Image.kt



Document.kt



Album.kt



Artist.kt



Playlist.kt



Folder.kt



SearchResult.kt



SortOptions.kt



FilterOptions.kt



utils/



MimeUtils.kt



DurationUtils.kt



ArtworkUtils.kt



CursorUtils.kt



extensions/



CursorExtensions.kt



UriExtensions.kt



ContentResolverExtensions.kt

```



\---



\# Design Principles



Each class should have a single responsibility.



Repository



\* Handles MediaStore queries.



Mapper



\* Converts Cursor to Kotlin models.



Observer



\* Watches for MediaStore changes.



Cache



\* Stores lightweight indexes.



Permission Manager



\* Handles Android permissions.



Search Engine



\* Filters cached results.



No class should exceed a few hundred lines without clear justification. Favor composition over large utility classes.



\---



\# Public API



```ts

getAudio()



getVideos()



getImages()



getDocuments()



getAlbums()



getArtists()



getGenres()



getPlaylists()



getFolders()



search()



watch()



refresh()



getById()



getByUri()



getRecent()



getFavorites()



getLargestFiles()



getDuplicates()



getStatistics()

```



\---



\# Audio Metadata



Each song should include:



\* ID

\* URI

\* Title

\* Artist

\* Album

\* Album ID

\* Genre (when available)

\* Duration

\* Size

\* Track Number

\* Disc Number

\* Year

\* Date Added

\* Date Modified

\* Bitrate (if available)

\* MIME Type

\* File Extension

\* Relative Path

\* Display Name

\* Content URI



\---



\# Video Metadata



Include:



\* ID

\* URI

\* Duration

\* Width

\* Height

\* Frame Rate (when available)

\* Rotation

\* Size

\* MIME Type

\* Relative Path

\* Display Name

\* Date Added

\* Date Modified

\* Resolution

\* Orientation



\---



\# Image Metadata



Include:



\* Width

\* Height

\* Orientation

\* Camera Make

\* Camera Model

\* Date Taken

\* GPS (if accessible)

\* MIME Type

\* Size



\---



\# Document Metadata



Support:



\* PDF

\* DOC

\* DOCX

\* XLS

\* XLSX

\* PPT

\* PPTX

\* TXT

\* EPUB

\* RTF

\* CSV

\* JSON

\* XML

\* ZIP

\* RAR

\* 7Z



Metadata:



\* Name

\* Size

\* MIME

\* Extension

\* Relative Path

\* Date Added

\* Date Modified

\* URI



\---



\# Sorting



Support:



\* Name

\* Date Added

\* Date Modified

\* Duration

\* Artist

\* Album

\* Year

\* File Size

\* Resolution

\* Width

\* Height



Ascending and descending order should be available for every sortable field.



\---



\# Filtering



Support:



\* MIME Type

\* Extension

\* Folder

\* Album

\* Artist

\* Duration Range

\* Size Range

\* Resolution

\* Date Range

\* Hidden Files

\* Favorites

\* Playlists



\---



\# Search Engine



The search implementation should support:



\* Prefix search

\* Partial search

\* Case-insensitive matching

\* Multiple keywords

\* Unicode support

\* Accent-insensitive matching where practical



Search should first query cached indexes when possible to avoid repeated MediaStore scans.



\---



\# Pagination



Every API should support:



\* limit

\* offset

\* cursor-based pagination



Never load an entire library into memory if the caller only needs the first page.



\---



\# Observer



Use Android's ContentObserver to monitor:



\* New files

\* Deleted files

\* Updated metadata



Expose events to JavaScript such as:



\* mediaAdded

\* mediaRemoved

\* mediaChanged



Applications should be able to refresh incrementally.



\---



\# Album Artwork



Provide helper methods to retrieve artwork URIs for audio albums.



Artwork should be loaded lazily and cached.



\---



\# Performance Requirements



\* Never recursively scan storage.

\* Never block the UI thread.

\* Execute queries on background dispatchers.

\* Close every Cursor.

\* Reuse projections where possible.

\* Avoid duplicate allocations.

\* Return immutable collections.

\* Stream large result sets if appropriate.

\* Support libraries with hundreds of thousands of media items.



\---



\# Caching



Implement optional caching using:



\* In-memory LRU cache

\* SQLite

\* MMKV



Cache should be invalidated automatically when MediaStore changes are observed.



\---



\# Permissions



Support Android versions appropriately:



\* Android 13+: request media-specific permissions when required.

\* Documents outside media collections should rely on the Storage Access Framework when necessary.

\* Avoid requesting broad storage permissions unless the application explicitly requires them.



The permission layer should expose simple TypeScript methods such as:



\* checkPermissions()

\* requestPermissions()



\---



\# Error Handling



Return structured errors instead of generic exceptions.



Example categories:



\* PermissionDenied

\* QueryFailed

\* InvalidArguments

\* UnsupportedAndroidVersion

\* FileUnavailable

\* UnknownError



\---



\# Threading



All MediaStore access should occur off the main thread.



Expose asynchronous Promise-based APIs to JavaScript.



\---



\# TypeScript SDK



Provide fully typed interfaces.



Example:



\* AudioItem

\* VideoItem

\* ImageItem

\* DocumentItem

\* Album

\* Artist

\* Playlist

\* SearchOptions

\* SortOptions

\* FilterOptions



No use of `any`.



\---



\# Testing



Unit tests should cover:



\* Cursor mapping

\* MIME detection

\* Sorting

\* Filtering

\* Search

\* Pagination

\* Permission handling



Integration tests should validate behavior across supported Android versions.



\---



\# Future Roadmap



Potential extensions include:



\* Thumbnail generation

\* Waveform extraction

\* EXIF utilities

\* Duplicate detection

\* Smart collections

\* AI-powered semantic search

\* Face clustering (images)

\* OCR indexing

\* Cloud provider integration

\* Folder analytics

\* Storage statistics

\* Incremental indexing engine

\* Cross-platform abstraction for iOS

For a detailed metadata subsystem architecture, see [docs/metadata-architecture.md](docs/metadata-architecture.md).



\---



\# Intended Usage



This module should become the shared media indexing layer used across all Android applications. Each application should import it as a dependency and consume its typed API rather than implementing custom scanning logic.



By centralizing MediaStore access into a single, well-tested module, future applications gain consistent behavior, improved performance, easier maintenance, and a significantly reduced need for third-party media scanning libraries.



