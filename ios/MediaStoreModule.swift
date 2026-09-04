import Foundation
import Photos

@objc(MediaStore)
class MediaStoreModule: RCTEventEmitter {
  private var hasListeners = false

  override func supportedEvents() -> [String] {
    return ["onMediaChange"]
  }

  override func startObserving() {
    hasListeners = true
    let observer = MediaStoreObserver.shared
    observer.startListening { [weak self] event in
      self?.sendEvent(withName: "onMediaChange", body: event)
    }
  }

  override func stopObserving() {
    hasListeners = false
    MediaStoreObserver.shared.stopListening()
  }

  // MARK: - Audio

  @objc
  func getAudio(_ sort: NSDictionary?, filter: NSDictionary?, pagination: NSDictionary?,
                resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.getAudio(sort: sort, filter: filter, pagination: pagination))
  }

  // MARK: - Videos

  @objc
  func getVideos(_ sort: NSDictionary?, filter: NSDictionary?, pagination: NSDictionary?,
                resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.getVideos(sort: sort, filter: filter, pagination: pagination))
  }

  // MARK: - Images

  @objc
  func getImages(_ sort: NSDictionary?, filter: NSDictionary?, pagination: NSDictionary?,
                resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.getImages(sort: sort, filter: filter, pagination: pagination))
  }

  // MARK: - Documents

  @objc
  func getDocuments(_ sort: NSDictionary?, filter: NSDictionary?, pagination: NSDictionary?,
                   resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.getDocuments(sort: sort, filter: filter, pagination: pagination))
  }

  // MARK: - Albums

  @objc
  func getAlbums(_ sort: NSDictionary?, filter: NSDictionary?, pagination: NSDictionary?,
                resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.getAlbums(sort: sort, filter: filter, pagination: pagination))
  }

  // MARK: - Artists

  @objc
  func getArtists(_ sort: NSDictionary?, pagination: NSDictionary?,
                 resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.getArtists(sort: sort, pagination: pagination))
  }

  // MARK: - Genres

  @objc
  func getGenres(_ sort: NSDictionary?, pagination: NSDictionary?,
                resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.getGenres(sort: sort, pagination: pagination))
  }

  // MARK: - Playlists

  @objc
  func getPlaylists(_ sort: NSDictionary?, pagination: NSDictionary?,
                   resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.getPlaylists(sort: sort, pagination: pagination))
  }

  // MARK: - Folders

  @objc
  func getFolders(_ sort: NSDictionary?, filter: NSDictionary?, pagination: NSDictionary?,
                 resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.getFolders(sort: sort, filter: filter, pagination: pagination))
  }

  // MARK: - Folder Statistics

  @objc
  func getFolderStatistics(_ folderPath: String?,
                          resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.getFolderStatistics(folderPath: folderPath))
  }

  // MARK: - Incremental Refresh

  @objc
  func refreshIncremental(_ lastTimestamp: Double?,
                         resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.refreshIncremental(lastTimestamp: lastTimestamp))
  }

  // MARK: - Last Refresh Timestamp

  @objc
  func getLastRefreshTimestamp(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    resolve(MediaStoreRepository.lastRefreshTimestamp)
  }

  // MARK: - Search

@objc
  func getByUri(_ uri: String,
               resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.getByUri(uri: uri))
  }

  @objc
  func getPathByUri(_ uri: String,
                   resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.getPathByUri(uri: uri))
  }

  // MARK: - Lookups

  @objc
  func getById(_ mediaType: String, id: String,
              resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.getById(mediaType: mediaType, id: id))
  }

  @objc
  func getByUri(_ uri: String,
               resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.getByUri(uri: uri))
  }

  @objc
  func getDetailedMetadata(_ mediaType: String, id: String,
                           resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    if #available(iOS 16.0, *) {
      Task {
        let repository = MediaStoreRepository()
        let result = await repository.getDetailedMetadataAsync(mediaType: mediaType, id: id)
        resolve(result)
      }
    } else {
      let repository = MediaStoreRepository()
      resolve(repository.getDetailedMetadata(mediaType: mediaType, id: id))
    }
  }

@objc
  func getByUri(_ uri: String,
               resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.getByUri(uri: uri))
  }

  @objc
  func fileExists(atPath path: String,
                 resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.fileExists(atPath: path))
  }

  @objc
  func readDirectory(atPath path: String,
                     resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.readDirectory(atPath: path))
  }

  @objc
  func readDirectoryRecursive(atPath path: String,
                             resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.readDirectoryRecursive(atPath: path))
  }

  @objc
  func fileSize(atPath path: String,
                resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.fileSize(atPath: path))
  }

  @objc
  func createFile(atPath path: String,
                 resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.createFile(atPath: path))
  }

  @objc
  func renameFile(oldPath: String, newPath: String,
                 resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.renameItem(atPath: oldPath, to: newPath))
  }

  @objc
  func deleteFile(atPath path: String,
                 resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.deleteItem(atPath: path))
  }

  @objc
  func copyFile(srcPath: String, dstPath: String,
               resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.copyItem(atPath: srcPath, to: dstPath))
  }

  @objc
  func moveFile(srcPath: String, dstPath: String,
               resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.renameItem(atPath: srcPath, to: dstPath))
  }

  // MARK: - Recent

  @objc
  func getRecent(_ mediaType: String?, limit: Int?,
                resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.getRecent(mediaType: mediaType, limit: limit))
  }

  // MARK: - Favorites

  @objc
  func getFavorites(_ mediaType: String?, sort: NSDictionary?, pagination: NSDictionary?,
                   resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.getFavorites(mediaType: mediaType, sort: sort, pagination: pagination))
  }

  // MARK: - Largest Files

  @objc
  func getLargestFiles(_ mediaType: String?, limit: Int?,
                      resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.getLargestFiles(mediaType: mediaType, limit: limit))
  }

  // MARK: - Duplicates

  @objc
  func getDuplicates(_ mediaType: String?,
                    resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.getDuplicates(mediaType: mediaType))
  }

  // MARK: - Statistics

  @objc
  func getStatistics(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.getStatistics())
  }

  // MARK: - Refresh

  @objc
  func refresh(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    resolve(nil)
  }

  // MARK: - Permissions

  @objc
  func checkPermissions(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let permissions = MediaStorePermissions()
    resolve(permissions.checkStatus())
  }

  @objc
  func requestPermissions(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    Task {
      let permissions = MediaStorePermissions()
      let result = await permissions.request()
      resolve(result)
    }
  }

  // MARK: - Artwork

  @objc
  func getAlbumArtwork(_ albumId: String?,
                      resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.getAlbumArtwork(albumId: albumId ?? ""))
  }

  // MARK: - Thumbnails

  @objc
  func getVideoThumbnail(_ videoId: String, width: Int?, height: Int?,
                        resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.getThumbnail(assetId: videoId, mediaType: .video, width: width, height: height))
  }

  @objc
  func getImageThumbnail(_ imageId: String, width: Int?, height: Int?,
                        resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    resolve(repository.getThumbnail(assetId: imageId, mediaType: .photo, width: width, height: height))
  }

  // MARK: - Deep Metadata

  @objc
  func getMetadata(_ uri: String, options: NSDictionary?,
                 resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let level = (options?["level"] as? String) ?? "full"
    if #available(iOS 16.0, *) {
      Task {
        let repository = MediaStoreRepository()
        guard let detailed = await repository.getDetailedMetadataByUriAsync(uri: uri) else {
          resolve([
            "metadata": [:] as [String: Any],
            "status": "failed",
            "warnings": ["File not found or unsupported"],
            "errorCode": "FILE_NOT_FOUND"
          ] as [String: Any])
          return
        }
        var filtered = detailed
        if level == "basic" {
          let allowed: Set<String> = ["mediaType", "mimeType", "fileSize", "durationMs", "containerFormat"]
          filtered = filtered.filter { allowed.contains($0.key) }
        } else if level == "standard" {
          var out: [String: Any?] = [:]
          for key in ["mediaType", "mimeType", "fileSize", "durationMs", "containerFormat"] {
            if let v = detailed[key] { out[key] = v }
          }
          if let audio = detailed["audio"] as? [String: Any?] {
            let keep = ["title", "artist", "album", "albumArtist", "genre", "trackNumber", "discNumber", "year"]
            var sa: [String: Any?] = [:]
            for k in keep { if let v = audio[k] { sa[k] = v } }
            if !sa.isEmpty { out["audio"] = sa }
          }
          if let video = detailed["video"] as? [String: Any?] {
            let keep = ["width", "height", "rotation", "codec"]
            var sv: [String: Any?] = [:]
            for k in keep { if let v = video[k] { sv[k] = v } }
            if !sv.isEmpty { out["video"] = sv }
          }
          if let image = detailed["image"] as? [String: Any?] {
            let keep = ["width", "height", "format"]
            var si: [String: Any?] = [:]
            for k in keep { if let v = image[k] { si[k] = v } }
            if !si.isEmpty { out["image"] = si }
          }
          if let artwork = detailed["artwork"] { out["artwork"] = artwork }
          filtered = out
        }
        resolve([
          "metadata": filtered,
          "status": "complete",
          "warnings": [] as [String],
          "errorCode": NSNull()
        ] as [String: Any])
      }
    } else {
      let repository = MediaStoreRepository()
      guard let detailed = repository.getDetailedMetadataByUri(uri: uri) else {
        resolve([
          "metadata": [:] as [String: Any],
          "status": "failed",
          "warnings": ["File not found or unsupported"],
          "errorCode": "FILE_NOT_FOUND"
        ] as [String: Any])
        return
      }
      var filtered = detailed
      if level == "basic" {
        let allowed: Set<String> = ["mediaType", "mimeType", "fileSize", "durationMs", "containerFormat"]
        filtered = filtered.filter { allowed.contains($0.key) }
      } else if level == "standard" {
        var out: [String: Any?] = [:]
        for key in ["mediaType", "mimeType", "fileSize", "durationMs", "containerFormat"] {
          if let v = detailed[key] { out[key] = v }
        }
        if let audio = detailed["audio"] as? [String: Any?] {
          let keep = ["title", "artist", "album", "albumArtist", "genre", "trackNumber", "discNumber", "year"]
          var sa: [String: Any?] = [:]
          for k in keep { if let v = audio[k] { sa[k] = v } }
          if !sa.isEmpty { out["audio"] = sa }
        }
        if let video = detailed["video"] as? [String: Any?] {
          let keep = ["width", "height", "rotation", "codec"]
          var sv: [String: Any?] = [:]
          for k in keep { if let v = video[k] { sv[k] = v } }
          if !sv.isEmpty { out["video"] = sv }
        }
        if let image = detailed["image"] as? [String: Any?] {
          let keep = ["width", "height", "format"]
          var si: [String: Any?] = [:]
          for k in keep { if let v = image[k] { si[k] = v } }
          if !si.isEmpty { out["image"] = si }
        }
        if let artwork = detailed["artwork"] { out["artwork"] = artwork }
        filtered = out
      }
      resolve([
        "metadata": filtered,
        "status": "complete",
        "warnings": [] as [String],
        "errorCode": NSNull()
      ] as [String: Any])
    }
  }

  @objc
  func getArtworkUri(_ albumId: String,
                   resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    let uri = repository.getAlbumArtwork(albumId: albumId)
    resolve(["uri": uri as Any] as [String: Any])
  }

  @objc
  func getArtworkBytes(_ albumId: String,
                     resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    guard let uriString = repository.getAlbumArtwork(albumId: albumId),
          let url = URL(string: uriString),
          let data = try? Data(contentsOf: url) else {
      resolve(["uri": NSNull(), "size": 0] as [String: Any])
      return
    }
    resolve(["uri": uriString, "size": data.count] as [String: Any])
  }

  @objc
  func inspectMetadata(_ uri: String,
                     resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let repository = MediaStoreRepository()
    guard let detailed = repository.getDetailedMetadataByUri(uri: uri) else {
      resolve([
        "uri": uri,
        "sources": ["mediaStore": false, "mediaMetadataRetriever": false, "exif": false],
        "fields": [:] as [String: Any],
        "warnings": ["File not found"],
        "status": "failed"
      ] as [String: Any])
      return
    }
    var fields: [String: Any] = [:]
    for (key, value) in detailed {
      if let sub = value as? [String: Any?] {
        for (subKey, subValue) in sub {
          fields["\(key).\(subKey)"] = ["value": subValue as Any, "source": "extractor"]
        }
      } else {
        fields[key] = ["value": value as Any, "source": "mediastore"]
      }
    }
    let hasExif = (detailed["image"] as? [String: Any?])?["exif"] != nil
    resolve([
      "uri": uri,
      "mimeType": detailed["mimeType"] as Any,
      "sources": ["mediaStore": true, "mediaMetadataRetriever": true, "exif": hasExif],
      "fields": fields,
      "warnings": [] as [String],
      "status": "complete"
    ] as [String: Any])
  }

  @objc
  func cancelMetadataExtraction(_ jobId: String,
                             resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    resolve(true)
  }

  @objc
  func cancelAllMetadataExtraction(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    resolve(true)
  }

  // MARK: - Lifecycle

  override static func requiresMainQueueSetup() -> Bool {
    return false
  }

  deinit {
    if hasListeners {
      MediaStoreObserver.shared.stopListening()
    }
  }
}
