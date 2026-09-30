import {
  DEFAULT_ARTWORK_SAVE_OPTIONS,
  parseGainTagsFromHead,
  parseReplayGainDb,
  parseReplayGainPeak,
  r128RawToDb,
  resolveReplayGainDb,
} from "../src/audioMetadata.types";
import type {
  MediaStoreAudioMetadata,
  MediaStoreCapabilities,
} from "../src/audioMetadata.types";

describe("vNext gain normalization (native owns conversion)", () => {
  it("converts R128 Q8.8 integers to dB (256 = 1 dB)", () => {
    expect(r128RawToDb("-512")).toBeCloseTo(-2);
    expect(r128RawToDb(256)).toBeCloseTo(1);
    expect(r128RawToDb("-1383")).toBeCloseTo(-1383 / 256);
    expect(r128RawToDb("0")).toBe(0);
  });

  it("passes through already-normalized R128 floats", () => {
    expect(r128RawToDb("-2.0")).toBeCloseTo(-2);
    expect(r128RawToDb(-5.42)).toBeCloseTo(-5.42);
  });

  it("strips dB suffixes", () => {
    expect(r128RawToDb("-512 dB")).toBeCloseTo(-2);
    expect(parseReplayGainDb("-5.42 dB")).toBeCloseTo(-5.42);
    expect(parseReplayGainDb("-6.18")).toBeCloseTo(-6.18);
  });

  it("returns null for missing/invalid gain", () => {
    expect(r128RawToDb(null)).toBeNull();
    expect(r128RawToDb("")).toBeNull();
    expect(r128RawToDb("not-a-gain")).toBeNull();
    expect(parseReplayGainDb(null)).toBeNull();
  });

  it("parses peaks as linear floats", () => {
    expect(parseReplayGainPeak("0.98")).toBeCloseTo(0.98);
    expect(parseReplayGainPeak(0.99)).toBeCloseTo(0.99);
    expect(parseReplayGainPeak("-1")).toBeNull();
    expect(parseReplayGainPeak("abc")).toBeNull();
  });
});

describe("replayGain fallback hierarchy (no silent track/album mixing)", () => {
  const rg = {
    trackGain: -5.42,
    albumGain: -6.18,
    trackPeak: 0.98,
    albumPeak: 0.99,
    source: "r128" as const,
  };

  it("prefers track gain in track mode", () => {
    expect(resolveReplayGainDb(rg, "track", 0)).toBeCloseTo(-5.42);
  });

  it("uses album gain in album mode", () => {
    expect(resolveReplayGainDb(rg, "album", 0)).toBeCloseTo(-6.18);
  });

  it("applies preamp on top", () => {
    expect(resolveReplayGainDb(rg, "track", 3)).toBeCloseTo(-2.42);
  });

  it("returns preamp/0dB when gain missing or off", () => {
    expect(resolveReplayGainDb(null, "track", 0)).toBe(0);
    expect(resolveReplayGainDb(rg, "off", 2)).toBe(2);
    expect(resolveReplayGainDb({ trackGain: null, albumGain: null, trackPeak: null, albumPeak: null, source: null }, "track", 0)).toBe(0);
  });
});

describe("vNext shapes", () => {
  it("type-checks unified AudioMetadata", () => {
    const meta: MediaStoreAudioMetadata = {
      uri: "file:///music/track.flac",
      title: "Track",
      artist: "Artist",
      album: "Album",
      albumArtist: null,
      genre: null,
      year: 2024,
      trackNumber: 1,
      discNumber: 1,
      duration: 180000,
      bitrate: 320000,
      sampleRate: 44100,
      channels: 2,
      composer: null,
      comment: null,
      artwork: {
        uri: "file:///cache/art.jpg",
        mimeType: "image/jpeg",
        width: 1000,
        height: 1000,
        size: 182341,
      },
      replayGain: {
        trackGain: -5.42,
        albumGain: -6.18,
        trackPeak: 0.98,
        albumPeak: 0.99,
        source: "r128",
      },
    };
    expect(meta.replayGain?.trackGain).toBeCloseTo(-5.42);
    expect(meta.artwork?.mimeType).toBe("image/jpeg");
  });

  it("type-checks capabilities", () => {
    const caps: MediaStoreCapabilities = {
      platform: "android",
      metadata: true,
      artwork: true,
      replayGain: true,
      r128: true,
      batchMetadata: true,
      mediaStore: true,
      photosLibrary: false,
    };
    expect(caps.r128).toBe(true);
  });

  it("keeps the pre-3.6.1 capability shape assignable", () => {
    // Consumers written against 3.6.0 must keep compiling: `platform` and
    // `photosLibrary` are both optional, and `mediaStore` is still required
    // and still means what it meant before.
    const legacy: MediaStoreCapabilities = {
      metadata: true,
      artwork: true,
      replayGain: true,
      r128: true,
      batchMetadata: true,
      mediaStore: true,
    };
    expect(legacy.mediaStore).toBe(true);
    expect(legacy.platform).toBeUndefined();
    expect(legacy.photosLibrary).toBeUndefined();
  });

  it("distinguishes the backing store without changing mediaStore", () => {
    // mediaStore stays `true` on both platforms for backward compatibility;
    // photosLibrary is the accurate iOS signal.
    const ios: MediaStoreCapabilities = {
      platform: "ios",
      metadata: true,
      artwork: true,
      replayGain: true,
      r128: true,
      batchMetadata: true,
      mediaStore: true,
      photosLibrary: true,
    };
    expect(ios.photosLibrary).toBe(true);
    expect(ios.platform).toBe("ios");
    // The compatibility promise: the deprecated flag does not flip.
    expect(ios.mediaStore).toBe(true);
  });

  it("defaults artwork save to format preservation", () => {
    expect(DEFAULT_ARTWORK_SAVE_OPTIONS.preserveFormat).toBe(true);
    expect(DEFAULT_ARTWORK_SAVE_OPTIONS.format).toBe("original");
  });
});

/**
 * Container-level tag scanning. The value-level parsers above were always
 * covered; the separator logic between a tag name and its value was not, which
 * is why MP3 and MP4 silently produced empty gain.
 */
describe("gain tag container scan (mirrors native ReplayGainReader)", () => {
  const NO_GAIN = {
    trackGain: null,
    albumGain: null,
    trackPeak: null,
    albumPeak: null,
    source: null,
  };

  it("reads Vorbis comments (FLAC / Ogg / Opus) via KEY=value", () => {
    const flac = "fLaC\x00\x00\x00" + "R128_TRACK_GAIN=-512\x00REPLAYGAIN_TRACK_PEAK=0.988\x00";
    const rg = parseGainTagsFromHead(flac);
    expect(rg.trackGain).toBeCloseTo(-2); // Q8.8: -512 / 256
    expect(rg.trackPeak).toBeCloseTo(0.988);
    expect(rg.source).toBe("r128");
  });

  it("reads ID3v2 TXXX frames (KEY NUL encoding-byte value)", () => {
    const mp3 = "ID3\x03\x00\x00" + "TXXXREPLAYGAIN_TRACK_GAIN\x00\x03-5.42 dB";
    const rg = parseGainTagsFromHead(mp3);
    expect(rg.trackGain).toBeCloseTo(-5.42);
    expect(rg.source).toBe("replaygain");
  });

  it("reads MP4 freeform ---- atoms (KEY + 4 flag bytes + value)", () => {
    const mp4 =
      "\x00\x00\x00\x30----com.apple.iTunes\x00\x0d" +
      "REPLAYGAIN_TRACK_GAIN\x00\x00\x00\x00-6.18 dB";
    const rg = parseGainTagsFromHead(mp4);
    expect(rg.trackGain).toBeCloseTo(-6.18);
    expect(rg.source).toBe("replaygain");
  });

  it("keeps R128 priority when both tag families are present", () => {
    const mixed =
      "R128_TRACK_GAIN=-512\x00REPLAYGAIN_TRACK_GAIN=-5.42\x00" +
      "R128_ALBUM_GAIN=-768\x00REPLAYGAIN_ALBUM_GAIN=-6.18\x00";
    const rg = parseGainTagsFromHead(mixed);
    expect(rg.trackGain).toBeCloseTo(-2);
    expect(rg.albumGain).toBeCloseTo(-3);
    expect(rg.source).toBe("r128");
  });

  it("returns empty gain for untagged media", () => {
    expect(parseGainTagsFromHead("fLaC\x00\x00\x00\x22\x00\x00\x00TITLE\x08Something")).toEqual(NO_GAIN);
  });

  it("does not invent a gain from binary padding after a tag name", () => {
    // Tag name present but the following bytes are not a number.
    const garbage = "TXXXREPLAYGAIN_TRACK_GAIN\x00\x03\xff\xfe\xfd";
    expect(parseGainTagsFromHead(garbage)).toEqual(NO_GAIN);
  });

  it("is stateless across repeated scans", () => {
    const flac = "R128_TRACK_GAIN=-512\x00";
    expect(parseGainTagsFromHead(flac).trackGain).toBeCloseTo(-2);
    // A shared /g regex would carry lastIndex between calls without this.
    expect(parseGainTagsFromHead(flac).trackGain).toBeCloseTo(-2);
  });
});
