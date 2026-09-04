import React from "react";
import { NativeEventEmitter } from "react-native";
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
