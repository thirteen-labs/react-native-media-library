import {
  DEFAULT_ARTWORK_SAVE_OPTIONS,
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
      metadata: true,
      artwork: true,
      replayGain: true,
      r128: true,
      batchMetadata: true,
      mediaStore: true,
    };
    expect(caps.r128).toBe(true);
  });

  it("defaults artwork save to format preservation", () => {
    expect(DEFAULT_ARTWORK_SAVE_OPTIONS.preserveFormat).toBe(true);
    expect(DEFAULT_ARTWORK_SAVE_OPTIONS.format).toBe("original");
  });
});
