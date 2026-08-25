import type { TurboModule } from "react-native";
import { TurboModuleRegistry } from "react-native";
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
  FolderStatistics,
  SearchResult,
  SearchOptions,
  SortOptions,
  FilterOptions,
  PaginationOptions,
  MediaStoreStatistics,
  DuplicateItem,
  PermissionStatus,
  LibraryResult,
  IncrementalChanges,
  MetadataResult,
  ArtworkUriResult,
  ArtworkBytesResult,
  MetadataInspectionResult,
} from "./MediaStoreModule.types";
import type { DetailedMetadata, MetadataOptions } from "./metadata.types";

export interface Spec extends TurboModule {
  getAudio(sort: SortOptions | null, filter: FilterOptions | null, pagination: PaginationOptions | null): Promise<AudioItem[]>;
  getVideos(sort: SortOptions | null, filter: FilterOptions | null, pagination: PaginationOptions | null): Promise<VideoItem[]>;
  getImages(sort: SortOptions | null, filter: FilterOptions | null, pagination: PaginationOptions | null): Promise<ImageItem[]>;
  getDocuments(sort: SortOptions | null, filter: FilterOptions | null, pagination: PaginationOptions | null): Promise<DocumentItem[]>;
  getAlbums(sort: SortOptions | null, filter: FilterOptions | null, pagination: PaginationOptions | null): Promise<Album[]>;
  getArtists(sort: SortOptions | null, pagination: PaginationOptions | null): Promise<Artist[]>;
  getGenres(sort: SortOptions | null, pagination: PaginationOptions | null): Promise<Genre[]>;
  getPlaylists(sort: SortOptions | null, pagination: PaginationOptions | null): Promise<Playlist[]>;
  getFolders(sort: SortOptions | null, filter: FilterOptions | null, pagination: PaginationOptions | null): Promise<Folder[]>;
  getFolderStatistics(folderPath: string | null): Promise<FolderStatistics[]>;
  getStatistics(): Promise<MediaStoreStatistics>;
  search(options: SearchOptions): Promise<SearchResult>;
  getById(mediaType: string, id: string): Promise<AudioItem | VideoItem | ImageItem | DocumentItem | null>;
  getByUri(uri: string): Promise<AudioItem | VideoItem | ImageItem | DocumentItem | null>;
  getRecent(mediaType: string | null, limit: number | null): Promise<(AudioItem | VideoItem | ImageItem | DocumentItem)[]>;
  getFavorites(mediaType: string | null, sort: SortOptions | null, pagination: PaginationOptions | null): Promise<(AudioItem | VideoItem | ImageItem | DocumentItem)[]>;
  getLargestFiles(mediaType: string | null, limit: number | null): Promise<(AudioItem | VideoItem | ImageItem | DocumentItem)[]>;
  getDuplicates(mediaType: string | null): Promise<DuplicateItem[]>;
  refresh(): Promise<void>;
  refreshIncremental(lastTimestamp: number | null): Promise<IncrementalChanges>;
  getLastRefreshTimestamp(): Promise<number>;
  checkPermissions(): Promise<PermissionStatus>;
  requestPermissions(): Promise<PermissionStatus>;
  getAlbumArtwork(albumId: string): Promise<string | null>;
  getVideoThumbnail(videoId: string, width: number | null, height: number | null): Promise<string | null>;
  getImageThumbnail(imageId: string, width: number | null, height: number | null): Promise<string | null>;
  getLibrary(sort: SortOptions | null, filter: FilterOptions | null, pagination: PaginationOptions | null): Promise<LibraryResult>;
  getDetailedMetadata(mediaType: string, id: string): Promise<DetailedMetadata | null>;
  getDetailedMetadataByUri(uri: string): Promise<DetailedMetadata | null>;
  // --- Deep metadata / artwork / diagnostics ---
  getMetadata(uri: string, options: MetadataOptions | null): Promise<MetadataResult>;
  getArtworkUri(albumId: string): Promise<ArtworkUriResult>;
  getArtworkBytes(albumId: string): Promise<ArtworkBytesResult>;
  inspectMetadata(uri: string): Promise<MetadataInspectionResult>;
  cancelMetadataExtraction(jobId: string): Promise<boolean>;
  cancelAllMetadataExtraction(): Promise<boolean>;
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>("MediaStore");
