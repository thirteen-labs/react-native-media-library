import Photos

class MediaStorePermissions {
  func checkStatus() -> [String: Any?] {
    let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
    let granted = status == .authorized || status == .limited
    return [
      "granted": granted,
      "status": string(from: status),
      "limited": status == .limited,
      "audio": granted,
      "video": granted,
      "images": granted
    ]
  }

  func request() async -> [String: Any?] {
    let currentStatus = PHPhotoLibrary.authorizationStatus(for: .readWrite)

    if currentStatus == .authorized || currentStatus == .limited {
      return checkStatus()
    }

    let status = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
    let granted = status == .authorized || status == .limited

    return [
      "granted": granted,
      "status": string(from: status),
      "limited": status == .limited,
      "audio": granted,
      "video": granted,
      "images": granted
    ]
  }

  private func string(from status: PHAuthorizationStatus) -> String {
    switch status {
    case .authorized: return "authorized"
    case .limited: return "limited"
    case .denied: return "denied"
    case .restricted: return "restricted"
    case .notDetermined: return "notDetermined"
    @unknown default: return "unknown"
    }
  }
}
