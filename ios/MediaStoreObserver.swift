import Photos

class MediaStoreObserver: NSObject, PHPhotoLibraryChangeObserver {
  static let shared = MediaStoreObserver()

  private var onChange: (([String: String]) -> Void)?
  private var fetchResults: [PHFetchResult<PHAsset>] = []
  private var isListening = false

  func startListening(onChange: @escaping ([String: String]) -> Void) {
    guard !isListening else { return }
    self.onChange = onChange
    // PHChange.changeDetails(for:) needs the fetch result we want diffed.
    // Keep one per media type so all Photos-backed types are observed.
    fetchResults = [
      PHAsset.fetchAssets(with: .audio, options: nil),
      PHAsset.fetchAssets(with: .video, options: nil),
      PHAsset.fetchAssets(with: .image, options: nil),
    ]
    PHPhotoLibrary.shared().register(self)
    isListening = true
  }

  func stopListening() {
    guard isListening else { return }
    PHPhotoLibrary.shared().unregisterChangeObserver(self)
    onChange = nil
    fetchResults = []
    isListening = false
  }

  func photoLibraryDidChange(_ changeInstance: PHChange) {
    guard let onChange = onChange else { return }

    var updated: [PHFetchResult<PHAsset>] = []
    for result in fetchResults {
      guard let details = changeInstance.changeDetails(for: result) else {
        updated.append(result)
        continue
      }
      updated.append(details.fetchResultAfterChanges)

      for asset in details.insertedAssets {
        onChange(event("added", for: asset))
      }
      for asset in details.removedAssets {
        onChange(event("removed", for: asset))
      }
      for asset in details.changedAssets {
        onChange(event("modified", for: asset))
      }
    }
    fetchResults = updated
  }

  private func event(_ type: String, for asset: PHAsset) -> [String: String] {
    return [
      "type": type,
      "mediaType": mapMediaType(asset.mediaType),
      "itemId": asset.localIdentifier,
      "uri": asset.localIdentifier,
    ]
  }

  private func mapMediaType(_ type: PHAssetMediaType) -> String {
    switch type {
    case .audio: return "audio"
    case .video: return "video"
    case .image: return "image"
    default: return "document"
    }
  }
}
