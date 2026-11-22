jest.mock("react-native", () => {
  class NativeEventEmitter {
    addListener() {
      return { remove() {} };
    }
  }
  return {
    NativeEventEmitter,
    Platform: { OS: "android" },
    TurboModuleRegistry: { getEnforcing: () => ({}) },
  };
});

jest.mock("../src/MediaStoreModule", () => ({
  __esModule: true,
  default: {
    getAudio: jest.fn(),
    getVideos: jest.fn(),
    getImages: jest.fn(),
    getDocuments: jest.fn(),
    writeFileContents: jest.fn(),
  },
}));

import NativeModule from "../src/MediaStoreModule";
import { getLibrary, SortField, SortOrder, writeFileContents } from "../src/index";

type MockNative = {
  getAudio: jest.Mock;
  getVideos: jest.Mock;
  getImages: jest.Mock;
  getDocuments: jest.Mock;
  writeFileContents: jest.Mock;
};

const native = NativeModule as unknown as MockNative;

describe("index helpers", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("passes filePath first and data second to writeFileContents", async () => {
    native.writeFileContents.mockResolvedValue(true);

    await expect(writeFileContents("/tmp/note.txt", "hello")).resolves.toBe(true);

    expect(native.writeFileContents).toHaveBeenCalledWith("/tmp/note.txt", "hello");
  });

  it("implements getLibrary as a batch of the four media queries", async () => {
    const audio = [{ id: "a", size: 1, duration: 2 }];
    const videos = [{ id: "v", size: 1, duration: 2 }];
    const images = [{ id: "i", size: 1 }];
    const documents = [{ id: "d", size: 1 }];

    native.getAudio.mockResolvedValue(audio);
    native.getVideos.mockResolvedValue(videos);
    native.getImages.mockResolvedValue(images);
    native.getDocuments.mockResolvedValue(documents);

    const sort = { field: SortField.Name, order: SortOrder.Ascending };
    const result = await getLibrary(sort, undefined, { limit: 10 });

    expect(native.getAudio).toHaveBeenCalledWith(sort, null, { limit: 10 });
    expect(native.getVideos).toHaveBeenCalledWith(sort, null, { limit: 10 });
    expect(native.getImages).toHaveBeenCalledWith(sort, null, { limit: 10 });
    expect(native.getDocuments).toHaveBeenCalledWith(sort, null, { limit: 10 });

    expect(result).toEqual({
      audio,
      videos,
      images,
      documents,
      totalCount: 4,
    });
  });
});
