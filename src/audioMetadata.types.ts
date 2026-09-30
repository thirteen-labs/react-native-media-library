/**
 * MediaStore vNext — first-class native audio metadata + artwork engine.
 *
 * Replaces the need for `@missingcore` (`saveArtwork` / `getR128Gain`) by
 * exposing artwork preservation/processing and R128 / ReplayGain extraction
 * as unified native functionality.
 */

/** Normalized gain source. R128 wins over ReplayGain 1.0 when both exist. */
export type ReplayGainSource = "r128" | "replaygain";

/** Unified ReplayGain view. All gains are normalized dB floats, peaks linear. */
export interface MediaStoreReplayGain {
  trackGain: number | null;
  albumGain: number | null;
  trackPeak: number | null;
  albumPeak: number | null;
  source: ReplayGainSource | null;
}

/** Which gain the caller wants when resolving playback gain. */
export type ReplayGainMode = "track" | "album" | "off";

/** Embedded / extracted artwork descriptor. */
export interface MediaStoreArtwork {
  uri: string;
  mimeType: string | null;
  width: number | null;
  height: number | null;
  size: number | null;
}

/** Unified per-file audio metadata. Native layer owns all normalization. */
export interface MediaStoreAudioMetadata {
  uri: string;
  title: string | null;
  artist: string | null;
  album: string | null;
  albumArtist: string | null;
  genre: string | null;
  year: number | null;
  trackNumber: number | null;
  discNumber: number | null;
  duration: number | null;
  bitrate: number | null;
  sampleRate: number | null;
  channels: number | null;
  composer: string | null;
  comment: string | null;
  artwork: MediaStoreArtwork | null;
  replayGain: MediaStoreReplayGain | null;
}

export type ArtworkSaveFormat = "original" | "jpeg" | "png" | "webp";

export interface ArtworkSaveOptions {
  format?: ArtworkSaveFormat;
  quality?: number;
  preserveFormat?: boolean;
}

export interface MediaStoreCapabilities {
  /** "android" (MediaStore) or "ios" (Photos Framework). */
  platform: "android" | "ios";
  metadata: boolean;
  artwork: boolean;
  replayGain: boolean;
  r128: boolean;
  batchMetadata: boolean;
  /** True only where the backing store is Android MediaStore. */
  mediaStore: boolean;
}

export const DEFAULT_ARTWORK_SAVE_OPTIONS: Required<ArtworkSaveOptions> = {
  format: "original",
  quality: 0.85,
  preserveFormat: true,
};

/**
 * Resolve the effective playback gain (dB) for a file.
 *
 * Hierarchy:
 *  1. R128 track gain / ReplayGain track gain (track mode)
 *  2. Album gain when `mode === "album"`
 *  3. Preamp
 *  4. 0 dB
 *
 * Never silently mixes track and album gain — the caller picks the mode.
 */
export function resolveReplayGainDb(
  replayGain: MediaStoreReplayGain | null | undefined,
  mode: ReplayGainMode = "track",
  preampDb = 0
): number {
  if (mode === "off") return preampDb;
  if (replayGain == null) return preampDb;
  const gain =
    mode === "album"
      ? replayGain.albumGain ?? replayGain.trackGain ?? 0
      : replayGain.trackGain ?? 0;
  return gain + preampDb;
}

/**
 * Convert a raw R128 tag value to dB.
 *
 * R128 tags (`R128_TRACK_GAIN`, `R128_ALBUM_GAIN`) are stored as Q8.8
 * fixed-point integers where 256 = 1 dB. Accepts raw integers ("-512"),
 * suffixed strings ("-512 dB"), or already-normalized floats ("-2.0").
 */
export function r128RawToDb(raw: string | number | null | undefined): number | null {
  if (raw == null) return null;
  if (typeof raw === "number") {
    if (!Number.isFinite(raw)) return null;
    if (Number.isInteger(raw) && Math.abs(raw) >= 64) return raw / 256;
    return raw;
  }
  const trimmed = raw.trim();
  if (trimmed.length === 0) return null;
  const withoutUnit = trimmed.replace(/\s*dB\s*$/i, "").trim();
  const asFloat = Number(withoutUnit);
  if (!Number.isFinite(asFloat)) return null;
  if (!withoutUnit.includes(".") && Math.abs(asFloat) >= 64) return asFloat / 256;
  return asFloat;
}

/**
 * Parse a ReplayGain 1.0 gain string ("-5.42 dB", "-5.42") to dB float.
 */
export function parseReplayGainDb(raw: string | number | null | undefined): number | null {
  if (raw == null) return null;
  if (typeof raw === "number") return Number.isFinite(raw) ? raw : null;
  const cleaned = raw.trim().replace(/\s*dB\s*$/i, "").trim();
  if (cleaned.length === 0) return null;
  const value = Number(cleaned);
  return Number.isFinite(value) ? value : null;
}

/** Parse a ReplayGain peak string ("0.98") to linear float. */
export function parseReplayGainPeak(raw: string | number | null | undefined): number | null {
  if (raw == null) return null;
  if (typeof raw === "number") return raw >= 0 && Number.isFinite(raw) ? raw : null;
  const value = Number(raw.trim());
  return Number.isFinite(value) && value >= 0 ? value : null;
}

/**
 * Reference implementation of the native container tag scan in
 * `ReplayGainReader.kt` and `MediaStoreRepository.swift`.
 *
 * KEEP IN SYNC with both native implementations. It lives here so the
 * separator logic is unit-testable without a device.
 *
 * The separator between a tag name and its value is not always `=`:
 * - Vorbis comments (FLAC / Ogg / Opus): `KEY=value`
 * - ID3v2 TXXX frames:                   `KEY\0<encoding byte>value`
 * - MP4 freeform `----` atoms:           `KEY<4 flag bytes>value`
 *
 * A single `\s*=\s*` therefore matched only the Vorbis case and silently
 * returned empty gain for MP3 and MP4. The alternation below accepts `=` or a
 * short run of non-printable bytes, and the value is anchored to a numeric
 * shape so binary padding cannot be mistaken for a gain.
 */
const GAIN_TAG_PATTERN =
  /(R128_TRACK_GAIN|R128_ALBUM_GAIN|REPLAYGAIN_TRACK_GAIN|REPLAYGAIN_ALBUM_GAIN|REPLAYGAIN_TRACK_PEAK|REPLAYGAIN_ALBUM_PEAK)(?:[ \t]*=[ \t]*|[^\x20-\x7E]{0,8})([+-]?[0-9]+(?:\.[0-9]+)?)/gi;

/**
 * Extract R128 / ReplayGain tags from a Latin-1 decoded container head or tail.
 * R128 wins over ReplayGain 1.0 when both are present.
 */
export function parseGainTagsFromHead(head: string): MediaStoreReplayGain {
  let r128Track: number | null = null;
  let r128Album: number | null = null;
  let rgTrack: number | null = null;
  let rgAlbum: number | null = null;
  let trackPeak: number | null = null;
  let albumPeak: number | null = null;

  GAIN_TAG_PATTERN.lastIndex = 0;
  for (const match of head.matchAll(GAIN_TAG_PATTERN)) {
    const key = match[1].toUpperCase();
    const raw = match[2];
    switch (key) {
      case "R128_TRACK_GAIN":
        r128Track ??= r128RawToDb(raw);
        break;
      case "R128_ALBUM_GAIN":
        r128Album ??= r128RawToDb(raw);
        break;
      case "REPLAYGAIN_TRACK_GAIN":
        rgTrack ??= parseReplayGainDb(raw);
        break;
      case "REPLAYGAIN_ALBUM_GAIN":
        rgAlbum ??= parseReplayGainDb(raw);
        break;
      case "REPLAYGAIN_TRACK_PEAK":
        trackPeak ??= parseReplayGainPeak(raw);
        break;
      case "REPLAYGAIN_ALBUM_PEAK":
        albumPeak ??= parseReplayGainPeak(raw);
        break;
    }
  }

  const hasR128 = r128Track !== null || r128Album !== null;
  const hasRg = rgTrack !== null || rgAlbum !== null || trackPeak !== null || albumPeak !== null;
  if (!hasR128 && !hasRg) {
    return { trackGain: null, albumGain: null, trackPeak: null, albumPeak: null, source: null };
  }
  return {
    trackGain: r128Track ?? rgTrack,
    albumGain: r128Album ?? rgAlbum,
    trackPeak,
    albumPeak,
    source: hasR128 ? "r128" : "replaygain",
  };
}
