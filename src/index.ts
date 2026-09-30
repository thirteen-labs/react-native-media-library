import React from "react";
import { NativeEventEmitter, Platform } from "react-native";
import NativeModule from "./MediaStoreModule";
import type {
  AudioItem,
  VideoItem,
  ImageItem,
  DocumentItem,
  Album,
  Artist,
  Genre,
  Playlist,
  Folder,
  SearchResult,
  SortOptions,
  FilterOptions,
  PaginationOptions,
  SearchOptions,
  MediaStoreStatistics,
  DuplicateItem,
  MediaChangeEvent,
  PermissionStatus,
  MediaStoreError,
  LibraryResult,
  ArtworkResult,
  ThumbnailOptions,
  SizeHistogram,
  FolderStatistics,
  IncrementalChanges,
  MetadataPlugin,
  LibraryQueryOptions,
  LibraryQueryResult,
} from "./MediaStoreModule.types";
import type {
  DetailedMetadata,
  MediaMetaType,
  AudioFormatMetadata,
  VideoFormatMetadata,
  ImageFormatMetadata,
  ExifMetadata,
  DocumentFormatMetadata,
  MetadataLevel,
  ExtractionStatus,
  MetadataErrorCode,
  MetadataExtractionResult,
  MetadataSource,
  MetadataField,
  MetadataInspection,
  MetadataOptions,
  ArtworkMetadata,
  ImageLocation,
} from "./metadata.types";
import type {
  MetadataResult,
  ArtworkUriResult,
  ArtworkBytesResult,
  MetadataInspectionResult,
} from "./MediaStoreModule.types";
import type {
  ArtworkSaveFormat,
  ArtworkSaveOptions,
  MediaStoreArtwork,
  MediaStoreAudioMetadata,
  MediaStoreCapabilities,
  MediaStoreReplayGain,
  ReplayGainMode,
  ReplayGainSource,
} from "./audioMetadata.types";
import {
  DEFAULT_ARTWORK_SAVE_OPTIONS,
  parseGainTagsFromHead,
  parseReplayGainDb,
  parseReplayGainPeak,
  r128RawToDb,
  resolveReplayGainDb,
} from "./audioMetadata.types";

export type {
  AudioItem,
  VideoItem,
  ImageItem,
  DocumentItem,
  Album,
  Artist,
  Genre,
  Playlist,
  Folder,
  SearchResult,
  SortOptions,
  FilterOptions,
  PaginationOptions,
  SearchOptions,
  MediaStoreStatistics,
  DuplicateItem,
  MediaChangeEvent,
  PermissionStatus,
  MediaStoreError,
  LibraryResult,
  ArtworkResult,
  ThumbnailOptions,
  SizeHistogram,
  FolderStatistics,
  IncrementalChanges,
  MetadataPlugin,
  LibraryQueryOptions,
  LibraryQueryResult,
};

export type {
  DetailedMetadata,
  MediaMetaType,
  AudioFormatMetadata,
  VideoFormatMetadata,
  ImageFormatMetadata,
  ExifMetadata,
  DocumentFormatMetadata,
  MetadataLevel,
  ExtractionStatus,
  MetadataErrorCode,
  MetadataExtractionResult,
  MetadataSource,
  MetadataField,
  MetadataInspection,
  MetadataOptions,
  ArtworkMetadata,
  ImageLocation,
  MetadataResult,
  ArtworkUriResult,
  ArtworkBytesResult,
  MetadataInspectionResult,
};

export type {
  ArtworkSaveFormat,
  ArtworkSaveOptions,
  MediaStoreArtwork,
  MediaStoreAudioMetadata,
  MediaStoreCapabilities,
  MediaStoreReplayGain,
  ReplayGainMode,
  ReplayGainSource,
};

export {
  DEFAULT_ARTWORK_SAVE_OPTIONS,
  parseGainTagsFromHead,
  parseReplayGainDb,
  parseReplayGainPeak,
  r128RawToDb,
  resolveReplayGainDb,
};

export { SortOrder, SortField } from "./MediaStoreModule.types";

const registeredPlugins: Map<string, MetadataPlugin> = new Map();

const eventEmitter = new NativeEventEmitter(NativeModule);

export async function getAudio(
  sort?: SortOptions,
  filter?: FilterOptions,
  pagination?: PaginationOptions
): Promise<AudioItem[]> {
  const result = await NativeModule.getAudio(
    sort ?? null,
    filter ?? null,
    pagination ?? null
  );
  return applyPlugins(result);
}

export async function getVideos(
  sort?: SortOptions,
  filter?: FilterOptions,
  pagination?: PaginationOptions
): Promise<VideoItem[]> {
  const result = await NativeModule.getVideos(
    sort ?? null,
    filter ?? null,
    pagination ?? null
  );
  return applyPlugins(result);
}

export async function getImages(
  sort?: SortOptions,
  filter?: FilterOptions,
  pagination?: PaginationOptions
): Promise<ImageItem[]> {
  const result = await NativeModule.getImages(
    sort ?? null,
    filter ?? null,
    pagination ?? null
  );
  return applyPlugins(result);
}

export async function getDocuments(
  sort?: SortOptions,
  filter?: FilterOptions,
  pagination?: PaginationOptions
): Promise<DocumentItem[]> {
  const result = await NativeModule.getDocuments(
    sort ?? null,
    filter ?? null,
    pagination ?? null
  );
  return applyPlugins(result);
}

export async function getAlbums(
  sort?: SortOptions,
  filter?: FilterOptions,
  pagination?: PaginationOptions
): Promise<Album[]> {
  return NativeModule.getAlbums(sort ?? null, filter ?? null, pagination ?? null);
}

export async function getArtists(
  sort?: SortOptions,
  pagination?: PaginationOptions
): Promise<Artist[]> {
  return NativeModule.getArtists(sort ?? null, pagination ?? null);
}

export async function getGenres(
  sort?: SortOptions,
  pagination?: PaginationOptions
): Promise<Genre[]> {
  return NativeModule.getGenres(sort ?? null, pagination ?? null);
}

export async function getPlaylists(
  sort?: SortOptions,
  pagination?: PaginationOptions
): Promise<Playlist[]> {
  return NativeModule.getPlaylists(sort ?? null, pagination ?? null);
}

export async function getFolders(
  sort?: SortOptions,
  filter?: FilterOptions,
  pagination?: PaginationOptions
): Promise<Folder[]> {
  return NativeModule.getFolders(sort ?? null, filter ?? null, pagination ?? null);
}

export async function search(
  options: SearchOptions
): Promise<SearchResult> {
  const result = await NativeModule.search(options);
  return {
    audio: applyPlugins(result.audio),
    videos: applyPlugins(result.videos),
    images: applyPlugins(result.images),
    documents: applyPlugins(result.documents),
    totalCount: result.totalCount,
    query: result.query,
  };
}

export async function getById(
  mediaType: "audio" | "video" | "image" | "document",
  id: string
): Promise<AudioItem | VideoItem | ImageItem | DocumentItem | null> {
  const result = await NativeModule.getById(mediaType, id);
  return result ? applyPlugins([result])[0] : null;
}

export async function getByUri(uri: string): Promise<AudioItem | VideoItem | ImageItem | DocumentItem | null> {
  const result = await NativeModule.getByUri(uri);
  return result ? applyPlugins([result])[0] : null;
}

export async function getDetailedMetadata(
  mediaType: MediaMetaType,
  id: string
): Promise<DetailedMetadata | null> {
  const result: unknown = await NativeModule.getDetailedMetadata(mediaType, id);
  return unwrapDetailedMetadata(result);
}

export async function getDetailedMetadataByUri(
  uri: string
): Promise<DetailedMetadata | null> {
  const result: unknown = await NativeModule.getDetailedMetadataByUri(uri);
  return unwrapDetailedMetadata(result);
}

function unwrapDetailedMetadata(value: unknown): DetailedMetadata | null {
  if (value == null) return null;
  if (typeof value === "object" && value !== null && "metadata" in (value as Record<string, unknown>)) {
    const wrapper = value as { metadata?: unknown };
    return (wrapper.metadata as DetailedMetadata) ?? null;
  }
  return value as DetailedMetadata;
}

export async function getMetadata(
  uri: string,
  options?: MetadataOptions
): Promise<MetadataResult> {
  return NativeModule.getMetadata(uri, options ?? null);
}

export async function getArtworkUri(
  albumId: string
): Promise<ArtworkUriResult> {
  return NativeModule.getArtworkUri(albumId);
}

export async function getArtworkBytes(
  albumId: string
): Promise<ArtworkBytesResult> {
  return NativeModule.getArtworkBytes(albumId);
}

export async function inspectMetadata(
  uri: string
): Promise<MetadataInspectionResult> {
  return NativeModule.inspectMetadata(uri);
}

// ---------------------------------------------------------------------------
// vNext: unified audio metadata + artwork engine
// (replaces `@missingcore` saveArtwork / getR128Gain)
// ---------------------------------------------------------------------------

const VNEXT_CAPABILITIES_FALLBACK: MediaStoreCapabilities = {
  platform: Platform.OS === "ios" ? "ios" : "android",
  metadata: true,
  artwork: false,
  replayGain: false,
  r128: false,
  batchMetadata: false,
  mediaStore: true,
  photosLibrary: Platform.OS === "ios",
};

function normalizeAudioMetadata(value: unknown, uri: string): MediaStoreAudioMetadata | null {
  if (value == null) return null;
  const raw = value as Record<string, unknown>;
  const replayGainRaw = raw.replayGain as Record<string, unknown> | null | undefined;
  const artworkRaw = raw.artwork as Record<string, unknown> | null | undefined;
  const numOrNull = (v: unknown): number | null =>
    typeof v === "number" && Number.isFinite(v) ? v : null;
  const strOrNull = (v: unknown): string | null =>
    typeof v === "string" ? v : v == null ? null : String(v);
  return {
    uri: typeof raw.uri === "string" ? raw.uri : uri,
    title: strOrNull(raw.title),
    artist: strOrNull(raw.artist),
    album: strOrNull(raw.album),
    albumArtist: strOrNull(raw.albumArtist),
    genre: strOrNull(raw.genre),
    year: numOrNull(raw.year),
    trackNumber: numOrNull(raw.trackNumber),
    discNumber: numOrNull(raw.discNumber),
    duration: numOrNull(raw.duration),
    bitrate: numOrNull(raw.bitrate),
    sampleRate: numOrNull(raw.sampleRate),
    channels: numOrNull(raw.channels),
    composer: strOrNull(raw.composer),
    comment: strOrNull((raw as Record<string, unknown>).comment),
    artwork:
      artworkRaw == null
        ? null
        : {
            uri: typeof artworkRaw.uri === "string" ? (artworkRaw.uri as string) : uri,
            mimeType: strOrNull(artworkRaw.mimeType),
            width: numOrNull(artworkRaw.width),
            height: numOrNull(artworkRaw.height),
            size: numOrNull(artworkRaw.size),
          },
    replayGain:
      replayGainRaw == null
        ? null
        : {
            trackGain: numOrNull(replayGainRaw.trackGain),
            albumGain: numOrNull(replayGainRaw.albumGain),
            trackPeak: numOrNull(replayGainRaw.trackPeak),
            albumPeak: numOrNull(replayGainRaw.albumPeak),
            source:
              replayGainRaw.source === "r128" || replayGainRaw.source === "replaygain"
                ? replayGainRaw.source
                : null,
          },
  };
}

/**
 * Unified per-file metadata. Native layer owns tag reading + R128/Q8.8
 * normalization, so callers just use `metadata.replayGain?.trackGain`.
 */
export async function getAudioMetadata(uri: string): Promise<MediaStoreAudioMetadata | null> {
  const fn = NativeModule.getAudioMetadata;
  if (typeof fn !== "function") {
    const detailed = await getDetailedMetadataByUri(uri);
    if (detailed == null) return null;
    const audio = (detailed.audio ?? {}) as Record<string, unknown>;
    return normalizeAudioMetadata(
      {
        uri,
        title: audio.title ?? null,
        artist: audio.artist ?? null,
        album: audio.album ?? null,
        albumArtist: audio.albumArtist ?? null,
        genre: audio.genre ?? null,
        year: audio.year ?? null,
        trackNumber: audio.trackNumber ?? null,
        discNumber: audio.discNumber ?? null,
        duration:
          detailed.durationMs != null ? Math.round(Number(detailed.durationMs)) : null,
        bitrate: audio.bitrate ?? null,
        sampleRate: audio.sampleRate ?? null,
        channels: audio.channels ?? null,
        composer: audio.composer ?? null,
        comment: null,
        artwork: detailed.artwork
          ? {
              uri: (detailed.artwork as { uri?: string }).uri ?? uri,
              mimeType: null,
              width: null,
              height: null,
              size: null,
            }
          : null,
        replayGain: null,
      },
      uri
    );
  }
  return normalizeAudioMetadata(await fn.call(NativeModule, uri), uri);
}

/** Batch variant for library scans — one bridge hop instead of N. */
export async function getAudioMetadataBatch(
  uris: string[]
): Promise<(MediaStoreAudioMetadata | null)[]> {
  const fn = NativeModule.getAudioMetadataBatch;
  if (typeof fn !== "function") {
    const out: (MediaStoreAudioMetadata | null)[] = [];
    for (const uri of uris) out.push(await getAudioMetadata(uri));
    return out;
  }
  const results = await fn.call(NativeModule, uris);
  return (results ?? []).map((r: unknown, i: number) =>
    normalizeAudioMetadata(r, uris[i] ?? "")
  );
}

/** Extract embedded artwork to a cache file usable by `<Image>`. */
export async function extractArtwork(audioUri: string): Promise<MediaStoreArtwork | null> {
  const fn = NativeModule.extractArtwork;
  if (typeof fn !== "function") return null;
  const raw = (await fn.call(NativeModule, audioUri)) as Record<string, unknown> | null;
  if (raw == null) return null;
  return {
    uri: typeof raw.uri === "string" ? raw.uri : audioUri,
    mimeType: typeof raw.mimeType === "string" ? raw.mimeType : null,
    width: typeof raw.width === "number" ? raw.width : null,
    height: typeof raw.height === "number" ? raw.height : null,
    size: typeof raw.size === "number" ? raw.size : null,
  };
}

export type SaveArtworkArgs =
  | [sourceUri: string, options?: ArtworkSaveOptions]
  | [sourceUri: string, destUri: string | null, options?: ArtworkSaveOptions];

/**
 * Persist artwork with format preservation by default
 * (`{ preserveFormat: true }`), avoiding lossy JPEG 0.85 recompression
 * unless explicitly requested via `{ format: "jpeg", quality: 0.85 }`.
 */
export async function saveArtwork(
  sourceUri: string,
  destOrOptions?: string | ArtworkSaveOptions | null,
  maybeOptions?: ArtworkSaveOptions
): Promise<MediaStoreArtwork | null> {
  const fn = NativeModule.saveArtwork;
  if (typeof fn !== "function") return null;
  let destUri: string | null = null;
  let options: ArtworkSaveOptions = {};
  if (typeof destOrOptions === "string") {
    destUri = destOrOptions;
    options = maybeOptions ?? {};
  } else if (destOrOptions != null) {
    options = destOrOptions;
    if (typeof maybeOptions?.format === "string") options = maybeOptions;
  }
  const merged: ArtworkSaveOptions = {
    preserveFormat: true,
    ...options,
    ...(options.format == null ? { format: "original" as const } : {}),
  };
  const raw = (await fn.call(
    NativeModule,
    sourceUri,
    destUri,
    merged
  )) as Record<string, unknown> | null;
  if (raw == null) return null;
  return {
    uri: typeof raw.uri === "string" ? raw.uri : sourceUri,
    mimeType: typeof raw.mimeType === "string" ? raw.mimeType : null,
    width: typeof raw.width === "number" ? raw.width : null,
    height: typeof raw.height === "number" ? raw.height : null,
    size: typeof raw.size === "number" ? raw.size : null,
  };
}

/** Capability detection so callers never assume per-platform support. */
export async function getCapabilities(): Promise<MediaStoreCapabilities> {
  const fn = NativeModule.getCapabilities;
  if (typeof fn !== "function") return { ...VNEXT_CAPABILITIES_FALLBACK };
  try {
    const caps = (await fn.call(NativeModule)) as Partial<MediaStoreCapabilities>;
    const platform = caps.platform ?? (Platform.OS === "ios" ? "ios" : "android");
    return {
      platform,
      metadata: caps.metadata ?? true,
      artwork: caps.artwork ?? false,
      replayGain: caps.replayGain ?? false,
      r128: caps.r128 ?? false,
      batchMetadata: caps.batchMetadata ?? false,
      // Intentionally `true` on both platforms. See the deprecation note on
      // MediaStoreCapabilities.mediaStore -- flipping this to false on iOS
      // would break callers that gate on it. `photosLibrary` is the accurate
      // iOS signal, and `platform` is the general one.
      mediaStore: caps.mediaStore ?? true,
      photosLibrary: caps.photosLibrary ?? platform === "ios",
    };
  } catch (_e) {
    return { ...VNEXT_CAPABILITIES_FALLBACK };
  }
}

/**
 * Convenience: effective playback gain for an audio URI.
 * `mode` selects track/album without silently mixing them.
 */
export async function getPlaybackGainDb(
  uri: string,
  mode: ReplayGainMode = "track",
  preampDb = 0
): Promise<number> {
  const metadata = await getAudioMetadata(uri);
  return resolveReplayGainDb(metadata?.replayGain, mode, preampDb);
}

export async function cancelMetadataExtraction(jobId: string): Promise<boolean> {
  return NativeModule.cancelMetadataExtraction(jobId);
}

export async function cancelAllMetadataExtraction(): Promise<boolean> {
  return NativeModule.cancelAllMetadataExtraction();
}

export async function getRecent(
  mediaType?: "audio" | "video" | "image" | "document",
  limit?: number
): Promise<(AudioItem | VideoItem | ImageItem | DocumentItem)[]> {
  const result = await NativeModule.getRecent(mediaType ?? null, limit ?? null);
  return applyPlugins(result);
}

export async function getFavorites(
  mediaType?: "audio" | "video" | "image" | "document",
  sort?: SortOptions,
  pagination?: PaginationOptions
): Promise<(AudioItem | VideoItem | ImageItem | DocumentItem)[]> {
  const result = await NativeModule.getFavorites(
    mediaType ?? null,
    sort ?? null,
    pagination ?? null
  );
  return applyPlugins(result);
}

export async function getLargestFiles(
  mediaType?: "audio" | "video" | "image" | "document",
  limit?: number
): Promise<(AudioItem | VideoItem | ImageItem | DocumentItem)[]> {
  const result = await NativeModule.getLargestFiles(mediaType ?? null, limit ?? null);
  return applyPlugins(result);
}

export async function getDuplicates(
  mediaType?: "audio" | "video" | "image" | "document"
): Promise<DuplicateItem[]> {
  return NativeModule.getDuplicates(mediaType ?? null);
}

export async function getStatistics(): Promise<MediaStoreStatistics> {
  return NativeModule.getStatistics();
}

export async function refresh(): Promise<void> {
  return NativeModule.refresh();
}

export async function checkPermissions(): Promise<PermissionStatus> {
  return NativeModule.checkPermissions();
}

export async function requestPermissions(): Promise<PermissionStatus> {
  return NativeModule.requestPermissions();
}

export function useMediaChangeEvent(
  onMediaChange?: (event: MediaChangeEvent) => void
): MediaChangeEvent | null {
  const [event, setEvent] = React.useState<MediaChangeEvent | null>(null);

  React.useEffect(() => {
    const subscription = eventEmitter.addListener("onMediaChange", (e: MediaChangeEvent) => {
      setEvent(e);
      if (onMediaChange) {
        onMediaChange(e);
      }
    });
    return () => subscription.remove();
  }, [onMediaChange]);

  return event;
}

export async function getLibrary(
  sort?: SortOptions,
  filter?: FilterOptions,
  pagination?: PaginationOptions
): Promise<LibraryResult> {
  return NativeModule.getLibrary(
    sort ?? null,
    filter ?? null,
    pagination ?? null
  );
}

export async function getLibraryQuery(
  options?: LibraryQueryOptions
): Promise<LibraryQueryResult> {
  const startTime = Date.now();
  const opts = options ?? {};
  const types = opts.types ?? ["audio", "video", "image", "document"];
  const typePag = opts.typePagination ?? {};

  const results = await Promise.all([
    types.includes("audio")
      ? NativeModule.getAudio(
          opts.sort ?? null,
          opts.filter ?? null,
          typePag.audio ?? opts.pagination ?? null
        ).then((r) => applyPlugins(r))
      : Promise.resolve([] as AudioItem[]),
    types.includes("video")
      ? NativeModule.getVideos(
          opts.sort ?? null,
          opts.filter ?? null,
          typePag.video ?? opts.pagination ?? null
        ).then((r) => applyPlugins(r))
      : Promise.resolve([] as VideoItem[]),
    types.includes("image")
      ? NativeModule.getImages(
          opts.sort ?? null,
          opts.filter ?? null,
          typePag.image ?? opts.pagination ?? null
        ).then((r) => applyPlugins(r))
      : Promise.resolve([] as ImageItem[]),
    types.includes("document")
      ? NativeModule.getDocuments(
          opts.sort ?? null,
          opts.filter ?? null,
          typePag.document ?? opts.pagination ?? null
        ).then((r) => applyPlugins(r))
      : Promise.resolve([] as DocumentItem[]),
  ]);

  const [audio, videos, images, documents] = results;
  const totalCount = audio.length + videos.length + images.length + documents.length;
  const totalSize =
    audio.reduce((s: number, i: AudioItem) => s + i.size, 0) +
    videos.reduce((s: number, i: VideoItem) => s + i.size, 0) +
    images.reduce((s: number, i: ImageItem) => s + i.size, 0) +
    documents.reduce((s: number, i: DocumentItem) => s + i.size, 0);

  const result: LibraryQueryResult = {
    audio,
    videos,
    images,
    documents,
    totalCount,
    totalSize,
    queryTime: Date.now() - startTime,
  };

  if (opts.includeStatistics) {
    result.perTypeStatistics = {
      audio: {
        count: audio.length,
        totalSize: audio.reduce((s: number, i: AudioItem) => s + i.size, 0),
        totalDuration: audio.reduce((s: number, i: AudioItem) => s + i.duration, 0),
      },
      video: {
        count: videos.length,
        totalSize: videos.reduce((s: number, i: VideoItem) => s + i.size, 0),
        totalDuration: videos.reduce((s: number, i: VideoItem) => s + i.duration, 0),
      },
      image: {
        count: images.length,
        totalSize: images.reduce((s: number, i: ImageItem) => s + i.size, 0),
      },
      document: {
        count: documents.length,
        totalSize: documents.reduce((s: number, i: DocumentItem) => s + i.size, 0),
      },
    };
  }

  return result;
}

export async function getAlbumArtwork(
  albumId: string
): Promise<string | null> {
  return NativeModule.getAlbumArtwork(albumId);
}

export async function getVideoThumbnail(
  videoId: string,
  width?: number,
  height?: number
): Promise<string | null> {
  return NativeModule.getVideoThumbnail(
    videoId,
    width ?? null,
    height ?? null
  );
}

export async function getImageThumbnail(
  imageId: string,
  width?: number,
  height?: number
): Promise<string | null> {
  return NativeModule.getImageThumbnail(
    imageId,
    width ?? null,
    height ?? null
  );
}

export async function getFolderStatistics(
  folderPath?: string
): Promise<FolderStatistics[]> {
  return NativeModule.getFolderStatistics(folderPath ?? null);
}

export async function refreshIncremental(
  lastTimestamp?: number
): Promise<IncrementalChanges> {
  return NativeModule.refreshIncremental(lastTimestamp ?? null);
}

export async function getLastRefreshTimestamp(): Promise<number> {
  return NativeModule.getLastRefreshTimestamp();
}

export async function getPathByUri(uri: string): Promise<string | null> {
  return NativeModule.getPathByUri(uri);
}

export async function fileExists(filePath: string): Promise<boolean> {
  return NativeModule.fileExists(filePath);
}

export async function readDirectory(dirPath: string): Promise<Array<{name: string, path: string, isDirectory: boolean}>> {
  return NativeModule.readDirectory(dirPath);
}

export async function readDirectoryRecursive(dirPath: string): Promise<Array<{name: string, path: string, isDirectory: boolean}>> {
  return NativeModule.readDirectoryRecursive(dirPath);
}

export async function fileSize(filePath: string): Promise<number> {
  return NativeModule.fileSize(filePath);
}

export async function createFile(filePath: string): Promise<string> {
  return NativeModule.createFile(filePath);
}

export async function renameFile(oldPath: string, newPath: string): Promise<string> {
  return NativeModule.renameFile(oldPath, newPath);
}

export async function deleteFile(filePath: string): Promise<boolean> {
  return NativeModule.deleteFile(filePath);
}

export async function copyFile(srcPath: string, dstPath: string): Promise<string> {
  return NativeModule.copyFile(srcPath, dstPath);
}

export async function moveFile(srcPath: string, dstPath: string): Promise<string> {
  return NativeModule.moveFile(srcPath, dstPath);
}

export async function getDirectoryStatistics(dirPath: string): Promise<{
  fileCount: number;
  totalSize: number;
  folderCount: number;
  histogram: {lessThan1MB: number, from1to10MB: number, from10to100MB: number, from100MBto1GB: number, greaterThan1GB: number}
}> {
  return NativeModule.getDirectoryStatistics(dirPath);
}

export async function getMimeType(filePath: string): Promise<string> {
  return NativeModule.getMimeType(filePath);
}

export async function getFileExtension(filePath: string): Promise<string> {
  return NativeModule.getFileExtension(filePath);
}

export async function readFileContents(filePath: string): Promise<string | null> {
  return NativeModule.readFileContents(filePath);
}

export async function writeFileContents(filePath: string, data: string): Promise<boolean> {
  return NativeModule.writeFileContents(data, filePath);
}

export function registerPlugin(plugin: MetadataPlugin): void {
  if (!plugin.id || !plugin.name || !plugin.version || typeof plugin.extract !== "function") {
    throw new Error("Invalid plugin: must have id, name, version, and extract function");
  }
  registeredPlugins.set(plugin.id, plugin);
}

export function unregisterPlugin(pluginId: string): boolean {
  return registeredPlugins.delete(pluginId);
}

export function getRegisteredPlugins(): MetadataPlugin[] {
  return Array.from(registeredPlugins.values());
}

function applyPlugins<T extends AudioItem | VideoItem | ImageItem | DocumentItem>(
  items: T[]
): T[] {
  if (registeredPlugins.size === 0) return items;

  return items.map((item) => {
    const copy = { ...item } as unknown as Record<string, unknown>;
    const custom: Record<string, unknown> = {};

    for (const plugin of registeredPlugins.values()) {
      try {
        const result = plugin.extract(item);
        if (result && typeof result === "object") {
          Object.assign(custom, result);
        }
      } catch (_e) {
      }
    }

    if (Object.keys(custom).length > 0) {
      const existing = copy.customMetadata as Record<string, unknown> | undefined;
      copy.customMetadata = { ...(existing ?? {}), ...custom };
    }

    return copy as unknown as T;
  });
}
