export interface AudioItem {
  id: string;
  uri: string;
  title: string;
  artist: string;
  album: string;
  albumId: string;
  genre: string | null;
  duration: number;
  size: number;
  trackNumber: number;
  discNumber: number;
  year: number;
  dateAdded: number;
  dateModified: number;
  composer: string | null;
  lyrics: string | null;
  albumArtist: string | null;
  isFavorite: boolean;
  playCount: number;
  lastPlayed: number;
  bookmark: number;
  bitrate: number | null;
  sampleRate: number | null;
  channels: number | null;
  encoding: string | null;
  mimeType: string;
  fileExtension: string;
  relativePath: string;
  displayName: string;
  contentUri: string;
  /** Catalog flags available from MediaStore without opening the file. */
  writer?: string | null;
  isMusic?: boolean;
  isPodcast?: boolean;
  isRingtone?: boolean;
  isAlarm?: boolean;
  isNotification?: boolean;
  cdTrackNumber?: number | null;
  numTracks?: number | null;
  customMetadata?: Record<string, unknown>;
}

export interface VideoItem {
  id: string;
  uri: string;
  title: string;
  duration: number;
  width: number;
  height: number;
  frameRate: number | null;
  rotation: number;
  size: number;
  mimeType: string;
  relativePath: string;
  displayName: string;
  dateAdded: number;
  dateModified: number;
  resolution: string;
  orientation: number;
  /** Catalog fields available from MediaStore without opening the file. */
  colorStandard?: string | null;
  colorTransfer?: string | null;
  videoCodec?: string | null;
  bucketId?: string | null;
  bucketDisplayName?: string | null;
  dateTaken?: number | null;
  customMetadata?: Record<string, unknown>;
}

export interface ImageItem {
  id: string;
  uri: string;
  title: string;
  width: number;
  height: number;
  orientation: number;
  cameraMake: string | null;
  cameraModel: string | null;
  dateTaken: number;
  gpsLatitude: number | null;
  gpsLongitude: number | null;
  mimeType: string;
  size: number;
  relativePath: string;
  displayName: string;
  dateAdded: number;
  dateModified: number;
  /** Catalog fields available from MediaStore without opening the file. */
  bucketId?: string | null;
  bucketDisplayName?: string | null;
  description?: string | null;
  customMetadata?: Record<string, unknown>;
}

export interface DocumentItem {
  id: string;
  uri: string;
  name: string;
  size: number;
  mimeType: string;
  extension: string;
  relativePath: string;
  dateAdded: number;
  dateModified: number;
  /** Catalog fields available from MediaStore without opening the file. */
  title?: string | null;
  isFavorite?: boolean;
  customMetadata?: Record<string, unknown>;
}

export interface Album {
  id: string;
  title: string;
  artist: string;
  songCount: number;
  duration: number;
  artworkUri: string | null;
  dateAdded: number;
  year: number | null;
}

export interface Artist {
  id: string;
  name: string;
  albumCount: number;
  songCount: number;
  duration: number;
  dateAdded: number;
}

export interface Genre {
  id: string;
  name: string;
  songCount: number;
  duration: number;
}

export interface Playlist {
  id: string;
  name: string;
  songCount: number;
  duration: number;
  dateAdded: number;
  dateModified: number;
}

export interface Folder {
  id: string;
  name: string;
  path: string;
  fileCount: number;
  totalSize: number;
}

export interface SearchResult {
  audio: AudioItem[];
  videos: VideoItem[];
  images: ImageItem[];
  documents: DocumentItem[];
  totalCount: number;
  query: string;
}

export enum SortOrder {
  Ascending = "asc",
  Descending = "desc",
}

export enum SortField {
  Name = "name",
  DateAdded = "dateAdded",
  DateModified = "dateModified",
  Duration = "duration",
  Artist = "artist",
  Album = "album",
  Year = "year",
  FileSize = "fileSize",
  Resolution = "resolution",
  Width = "width",
  Height = "height",
}

export interface SortOptions {
  field: SortField;
  order: SortOrder;
}

export interface FilterOptions {
  mimeTypes?: string[];
  extensions?: string[];
  folder?: string;
  album?: string;
  artist?: string;
  minDuration?: number;
  maxDuration?: number;
  minSize?: number;
  maxSize?: number;
  minResolution?: number;
  maxResolution?: number;
  startDate?: number;
  endDate?: number;
  includeHidden?: boolean;
  favoritesOnly?: boolean;
  playlistId?: string;
}

export interface PaginationOptions {
  limit?: number;
  offset?: number;
  cursor?: string;
}

export interface SearchOptions {
  query: string;
  types?: ("audio" | "video" | "image" | "document")[];
  sort?: SortOptions;
  filter?: FilterOptions;
  pagination?: PaginationOptions;
}

export interface MediaStoreStatistics {
  totalAudio: number;
  totalVideo: number;
  totalImages: number;
  totalDocuments: number;
  totalSize: number;
  totalDuration: number;
}

export interface DuplicateItem {
  fileHash: string;
  count: number;
  items: AudioItem[];
  totalSize: number;
}

export interface MediaChangeEvent {
  type: "added" | "removed" | "modified";
  mediaType: "audio" | "video" | "image" | "document";
  itemId: string;
  uri: string;
}

export interface PermissionStatus {
  granted: boolean;
  audio: boolean;
  video: boolean;
  images: boolean;
}

export type ErrorCode =
  | "PERMISSION_DENIED"
  | "QUERY_FAILED"
  | "INVALID_ARGUMENTS"
  | "INVALID_SORT_FIELD"
  | "INVALID_MIME_TYPE"
  | "UNSUPPORTED_ANDROID_VERSION"
  | "FILE_UNAVAILABLE"
  | "CURSOR_CLOSED"
  | "CACHE_FAILURE"
  | "UNKNOWN_ERROR"
  | "FILE_NOT_FOUND"
  | "URI_UNAVAILABLE"
  | "UNSUPPORTED_FORMAT"
  | "CORRUPTED_FILE"
  | "EXTRACTION_FAILED"
  | "METADATA_UNAVAILABLE"
  | "API_NOT_SUPPORTED"
  | "MEDIA_REDACTED"
  | "TIMEOUT"
  | "CANCELLED";

export interface MediaStoreError {
  code: ErrorCode;
  message: string;
  details?: string;
}

export interface LibraryResult {
  audio: AudioItem[];
  videos: VideoItem[];
  images: ImageItem[];
  documents: DocumentItem[];
  totalCount: number;
}

export interface ArtworkResult {
  uri: string | null;
  mimeType: string | null;
}

export interface ThumbnailOptions {
  width?: number;
  height?: number;
  kind?: "MINI_KIND" | "FULL_SCREEN_KIND" | "MICRO_KIND";
}

export interface SizeHistogram {
  lessThan1MB: number;
  from1to10MB: number;
  from10to100MB: number;
  from100MBto1GB: number;
  greaterThan1GB: number;
}

export interface MediaTypeBreakdown {
  audio: number;
  video: number;
  image: number;
  document: number;
}

export interface FolderStatistics {
  id: string;
  name: string;
  path: string;
  fileCount: number;
  totalSize: number;
  histogram: SizeHistogram;
  mediaTypeBreakdown: MediaTypeBreakdown;
  averageFileSize: number;
}

export interface IncrementalChanges {
  added: number;
  modified: number;
  removed: number;
  timestamp: number;
}

export interface MetadataPlugin {
  id: string;
  name: string;
  version: string;
  extract: (
    item: AudioItem | VideoItem | ImageItem | DocumentItem
  ) => Record<string, unknown> | Promise<Record<string, unknown>>;
}

export interface LibraryPerTypeStatistics {
  audio: { count: number; totalSize: number; totalDuration: number };
  video: { count: number; totalSize: number; totalDuration: number };
  image: { count: number; totalSize: number };
  document: { count: number; totalSize: number };
}

export interface LibraryQueryOptions {
  sort?: SortOptions;
  filter?: FilterOptions;
  pagination?: PaginationOptions;
  types?: ("audio" | "video" | "image" | "document")[];
  typePagination?: {
    audio?: PaginationOptions;
    video?: PaginationOptions;
    image?: PaginationOptions;
    document?: PaginationOptions;
  };
  includeStatistics?: boolean;
}

export interface LibraryQueryResult {
  audio: AudioItem[];
  videos: VideoItem[];
  images: ImageItem[];
  documents: DocumentItem[];
  totalCount: number;
  totalSize: number;
  perTypeStatistics?: LibraryPerTypeStatistics;
  queryTime: number;
}

export interface MetadataResult {
  metadata: Record<string, unknown>;
  status: "complete" | "partial" | "failed" | "cancelled";
  warnings: string[];
  errorCode: string | null;
}

export interface ArtworkUriResult {
  uri: string | null;
}

export interface ArtworkBytesResult {
  uri: string | null;
  size: number;
}

export interface MetadataInspectionResult {
  uri: string;
  mimeType?: string;
  sources: {
    mediaStore: boolean;
    mediaMetadataRetriever: boolean;
    exif: boolean;
  };
  fields: Record<string, { value: unknown; source: string }>;
  warnings: string[];
  status: string;
}

export type MetadataLevel = "basic" | "standard" | "full" | "raw";
