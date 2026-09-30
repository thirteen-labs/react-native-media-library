import Photos
import AVFoundation
import UIKit
import ImageIO
import PDFKit
import CoreMedia

class MediaStoreRepository {
  private let imageManager = PHCachingImageManager()
  static var lastRefreshTimestamp: Double = Date().timeIntervalSince1970 * 1000

  // MARK: - Public PHAsset file URL helpers (replaces private KVC value(forKey:"URL"))

  /// Synchronous fallback for iOS 13-15 (private KVC) — kept for legacy sync paths only.
  /// On iOS 16+ prefer `assetFileURLAsync`.
  private func syncAssetFileURL(for asset: PHAsset) -> URL? {
    let resources = PHAssetResource.assetResources(for: asset)
    guard let resource = resources.first else { return nil }
    // Private KVC retained only for sync legacy fallback; async path uses PHContentEditingInput.
    // swiftlint:disable:next force_cast
    return (resource as AnyObject).value(forKey: "privateFileURL") as? URL
      ?? (resource as AnyObject).value(forKey: "URL") as? URL
      ?? (resource as AnyObject).value(forKey: "fileURL") as? URL
  }

  /// Public async helper — uses PHContentEditingInput.fullSizeImageURL (no private KVC).
  @available(iOS 15.0, *)
  private func asyncAssetFileURL(for asset: PHAsset) async -> URL? {
    await withCheckedContinuation { cont in
      let opts = PHContentEditingInputRequestOptions()
      opts.isNetworkAccessAllowed = true
      opts.canHandleAdjustmentData = { _ in true }
      asset.requestContentEditingInput(with: opts) { input, _ in
        if let url = input?.fullSizeImageURL {
          cont.resume(returning: url)
        } else if let avAsset = input?.avAsset as? AVURLAsset {
          cont.resume(returning: avAsset.url)
        } else {
          // Fallback to private KVC only if public input yields nil
          let resources = PHAssetResource.assetResources(for: asset)
          let url = (resources.first as AnyObject?)?.value(forKey: "URL") as? URL
          cont.resume(returning: url)
        }
      }
    }
  }

  private func filename(for asset: PHAsset) -> String {
    // Public: PHAssetResource.originalFilename vs private KVC value(forKey:"filename")
    if let name = PHAssetResource.assetResources(for: asset).first?.originalFilename, !name.isEmpty {
      return name
    }
    return (asset as AnyObject).value(forKey: "filename") as? String ?? ""
  }

  // MARK: - Audio

  func getAudio(sort: Any?, filter: Any?, pagination: Any?) -> [[String: Any?]] {
    let fetchOptions = PHFetchOptions()
    fetchOptions.predicate = NSPredicate(format: "mediaType == %d", PHAssetMediaType.audio.rawValue)

    applyFilterOptions(fetchOptions, filter: filter)
    applySortOptions(fetchOptions, sort: sort, mediaType: .audio)

    let results = PHAsset.fetchAssets(with: .audio, options: fetchOptions)
    var items: [[String: Any?]] = []

    results.enumerateObjects { asset, _, _ in
      let metadata = self.getAudioMetadata(for: asset)
      items.append(self.mapAudioAsset(asset, metadata: metadata))
    }

    return applyPagination(items, pagination: pagination)
  }

  // MARK: - Videos

  func getVideos(sort: Any?, filter: Any?, pagination: Any?) -> [[String: Any?]] {
    let fetchOptions = PHFetchOptions()
    fetchOptions.predicate = NSPredicate(format: "mediaType == %d", PHAssetMediaType.video.rawValue)

    applyFilterOptions(fetchOptions, filter: filter)
    applySortOptions(fetchOptions, sort: sort, mediaType: .video)

    let results = PHAsset.fetchAssets(with: .video, options: fetchOptions)
    var items: [[String: Any?]] = []

    results.enumerateObjects { asset, _, _ in
      items.append(self.mapVideoAsset(asset))
    }

    return applyPagination(items, pagination: pagination)
  }

  // MARK: - Images

  func getImages(sort: Any?, filter: Any?, pagination: Any?) -> [[String: Any?]] {
    let fetchOptions = PHFetchOptions()
    fetchOptions.predicate = NSPredicate(format: "mediaType == %d", PHAssetMediaType.image.rawValue)

    applyFilterOptions(fetchOptions, filter: filter)
    applySortOptions(fetchOptions, sort: sort, mediaType: .image)

    let results = PHAsset.fetchAssets(with: .image, options: fetchOptions)
    var items: [[String: Any?]] = []

    results.enumerateObjects { asset, _, _ in
      let metadata = self.getImageMetadata(for: asset)
      items.append(self.mapImageAsset(asset, metadata: metadata))
    }

    return applyPagination(items, pagination: pagination)
  }

  // MARK: - Documents (iOS has no direct equivalent - returns empty)

  func getDocuments(sort: Any?, filter: Any?, pagination: Any?) -> [[String: Any?]] {
    return []
  }

  // MARK: - Albums

  func getAlbums(sort: Any?, filter: Any?, pagination: Any?) -> [[String: Any?]] {
    var albumTypes: [PHAssetCollectionType] = [.album, .smartAlbum]
    if let filterDict = filter as? [String: Any?],
       let favoritesOnly = filterDict["favoritesOnly"] as? Bool, favoritesOnly {
      albumTypes = [.smartAlbum]
    }

    var items: [[String: Any?]] = []

    for type in albumTypes {
      let collections = PHAssetCollection.fetchAssetCollections(with: type, options: nil)
      collections.enumerateObjects { collection, _, _ in
        let fetchOptions = PHFetchOptions()
        fetchOptions.predicate = NSPredicate(format: "mediaType IN %@",
          [PHAssetMediaType.audio.rawValue, PHAssetMediaType.video.rawValue])

        let assets = PHAsset.fetchAssets(in: collection, options: fetchOptions)
        if assets.count > 0 {
          items.append([
            "id": collection.localIdentifier,
            "title": collection.localizedTitle ?? "",
            "artist": "",
            "songCount": assets.count,
            "duration": self.calculateTotalDuration(assets: assets),
            "artworkUri": self.getAlbumArtworkUri(collection: collection),
            "dateAdded": (collection.startDate ?? Date()).timeIntervalSince1970 * 1000,
            "year": collection.startDate?.year
          ])
        }
      }
    }

    return applyPagination(applySorting(items, sort: sort), pagination: pagination)
  }

  // MARK: - Artists

  func getArtists(sort: Any?, pagination: Any?) -> [[String: Any?]] {
    let fetchOptions = PHFetchOptions()
    fetchOptions.predicate = NSPredicate(format: "mediaType == %d", PHAssetMediaType.audio.rawValue)

    let results = PHAsset.fetchAssets(with: .audio, options: fetchOptions)
    var artistMap: [String: [String: Any?]] = [:]

    results.enumerateObjects { asset, _, _ in
      let artist = self.getAudioArtist(for: asset) ?? "Unknown Artist"
      if var existing = artistMap[artist] {
        existing["songCount"] = (existing["songCount"] as? Int ?? 0) + 1
        artistMap[artist] = existing
      } else {
        artistMap[artist] = [
          "id": artist,
          "name": artist,
          "albumCount": 0,
          "songCount": 1,
          "duration": asset.duration,
          "dateAdded": asset.creationDate?.timeIntervalSince1970 ?? 0
        ]
      }
    }

    let items = Array(artistMap.values)
    return applyPagination(applySorting(items, sort: sort), pagination: pagination)
  }

  // MARK: - Genres

  func getGenres(sort: Any?, pagination: Any?) -> [[String: Any?]] {
    let fetchOptions = PHFetchOptions()
    fetchOptions.predicate = NSPredicate(format: "mediaType == %d", PHAssetMediaType.audio.rawValue)

    let results = PHAsset.fetchAssets(with: .audio, options: fetchOptions)
    var genreMap: [String: [String: Any?]] = [:]

    results.enumerateObjects { asset, _, _ in
      let genre = self.getAudioGenre(for: asset) ?? "Unknown"
      if var existing = genreMap[genre] {
        existing["songCount"] = (existing["songCount"] as? Int ?? 0) + 1
        genreMap[genre] = existing
      } else {
        genreMap[genre] = [
          "id": genre,
          "name": genre,
          "songCount": 1,
          "duration": asset.duration
        ]
      }
    }

    let items = Array(genreMap.values)
    return applyPagination(applySorting(items, sort: sort), pagination: pagination)
  }

  // MARK: - Playlists

  func getPlaylists(sort: Any?, pagination: Any?) -> [[String: Any?]] {
    let collections = PHAssetCollection.fetchAssetCollections(with: .smartAlbum, subtype: .smartAlbumPlaylists, options: nil)
    var items: [[String: Any?]] = []

    collections.enumerateObjects { collection, _, _ in
      let assets = PHAsset.fetchAssets(in: collection, options: nil)
      items.append([
        "id": collection.localIdentifier,
        "name": collection.localizedTitle ?? "",
        "songCount": assets.count,
        "duration": self.calculateTotalDuration(assets: assets),
        "dateAdded": (collection.startDate ?? Date()).timeIntervalSince1970 * 1000,
        "dateModified": (collection.endDate ?? Date()).timeIntervalSince1970 * 1000
      ])
    }

    return applyPagination(applySorting(items, sort: sort), pagination: pagination)
  }

  // MARK: - Folders

  func getFolders(sort: Any?, filter: Any?, pagination: Any?) -> [[String: Any?]] {
    var items: [[String: Any?]] = []

    let albums = PHAssetCollection.fetchAssetCollections(with: .smartAlbum, subtype: .any, options: nil)
    albums.enumerateObjects { collection, _, _ in
      let assets = PHAsset.fetchAssets(in: collection, options: nil)
      if assets.count > 0 {
        let path = collection.localizedTitle ?? ""
        items.append([
          "id": collection.localIdentifier,
          "name": path,
          "path": path,
          "fileCount": assets.count,
          "totalSize": self.calculateTotalSize(assets: assets)
        ])
      }
    }

    return applyPagination(applySorting(items, sort: sort), pagination: pagination)
  }

  // MARK: - Folder Statistics

  func getFolderStatistics(folderPath: String?) -> [[String: Any?]] {
    var folderMap: [String: [String: Any?]] = [:]

    let albums = PHAssetCollection.fetchAssetCollections(with: .smartAlbum, subtype: .any, options: nil)
    albums.enumerateObjects { collection, _, _ in
      let assets = PHAsset.fetchAssets(in: collection, options: nil)
      if assets.count > 0 {
        let path = collection.localizedTitle ?? ""

        if let filterPath = folderPath, !path.hasPrefix(filterPath) {
          return
        }

        var totalSize: Double = 0
        var histogram: [String: Int] = [
          "lessThan1MB": 0, "from1to10MB": 0,
          "from10to100MB": 0, "from100MBto1GB": 0, "greaterThan1GB": 0
        ]
        var typeBreakdown: [String: Int] = ["audio": 0, "video": 0, "image": 0, "document": 0]

        assets.enumerateObjects { asset, _, _ in
          let size = self.getAssetFileSize(asset)
          totalSize += size

          let sizeMB = size / (1024 * 1024)
          if sizeMB < 1 { histogram["lessThan1MB"]! += 1 }
          else if sizeMB < 10 { histogram["from1to10MB"]! += 1 }
          else if sizeMB < 100 { histogram["from10to100MB"]! += 1 }
          else if sizeMB < 1024 { histogram["from100MBto1GB"]! += 1 }
          else { histogram["greaterThan1GB"]! += 1 }

          switch asset.mediaType {
          case .audio: typeBreakdown["audio"]! += 1
          case .video: typeBreakdown["video"]! += 1
          case .image: typeBreakdown["image"]! += 1
          default: typeBreakdown["document"]! += 1
          }
        }

        let avgSize = assets.count > 0 ? Double(totalSize) / Double(assets.count) : 0.0

        folderMap[path] = [
          "id": path,
          "name": path,
          "path": path,
          "fileCount": assets.count,
          "totalSize": totalSize,
          "histogram": histogram,
          "mediaTypeBreakdown": typeBreakdown,
          "averageFileSize": avgSize
        ]
      }
    }

    return Array(folderMap.values)
  }

  // MARK: - Incremental Refresh

  func refreshIncremental(lastTimestamp: Double?) -> [String: Any?] {
    let since = lastTimestamp ?? MediaStoreRepository.lastRefreshTimestamp
    let sinceDate = Date(timeIntervalSince1970: since / 1000)
    var addedCount = 0
    var modifiedCount = 0

    let fetchOptions = PHFetchOptions()
    fetchOptions.predicate = NSPredicate(format: "modificationDate >= %@", sinceDate as NSDate)

    let audioResult = PHAsset.fetchAssets(with: .audio, options: fetchOptions)
    let videoResult = PHAsset.fetchAssets(with: .video, options: fetchOptions)
    let imageResult = PHAsset.fetchAssets(with: .image, options: fetchOptions)

    addedCount += audioResult.count + videoResult.count + imageResult.count

    MediaStoreRepository.lastRefreshTimestamp = Date().timeIntervalSince1970 * 1000

    return [
      "added": addedCount,
      "modified": modifiedCount,
      "removed": 0,
      "timestamp": MediaStoreRepository.lastRefreshTimestamp
    ]
  }

  // MARK: - Search

  func search(options: [String: Any?]) -> [String: Any?] {
    let query = (options["query"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
    guard !query.isEmpty else {
      return ["audio": [], "videos": [], "images": [], "documents": [], "totalCount": 0, "query": query]
    }

    let types = options["types"] as? [String] ?? ["audio", "video", "image", "document"]

    var audioItems: [[String: Any?]] = []
    var videoItems: [[String: Any?]] = []
    var imageItems: [[String: Any?]] = []

    if types.contains("audio") {
      let fetchOptions = PHFetchOptions()
      fetchOptions.predicate = NSPredicate(format: "mediaType == %d AND (title CONTAINS[cd] %@ OR (SUBSTRING(localizedTitle, 1, %d) ==[cd] %@))",
        PHAssetMediaType.audio.rawValue, query, query.count, query)
      let results = PHAsset.fetchAssets(with: .audio, options: fetchOptions)
      results.enumerateObjects { asset, _, _ in
        let metadata = self.getAudioMetadata(for: asset)
        audioItems.append(self.mapAudioAsset(asset, metadata: metadata))
      }
    }

    if types.contains("video") {
      let fetchOptions = PHFetchOptions()
      fetchOptions.predicate = NSPredicate(format: "mediaType == %d AND title CONTAINS[cd] %@",
        PHAssetMediaType.video.rawValue, query)
      let results = PHAsset.fetchAssets(with: .video, options: fetchOptions)
      results.enumerateObjects { asset, _, _ in
        videoItems.append(self.mapVideoAsset(asset))
      }
    }

    if types.contains("image") {
      let fetchOptions = PHFetchOptions()
      fetchOptions.predicate = NSPredicate(format: "mediaType == %d AND title CONTAINS[cd] %@",
        PHAssetMediaType.image.rawValue, query)
      let results = PHAsset.fetchAssets(with: .image, options: fetchOptions)
      results.enumerateObjects { asset, _, _ in
        let metadata = self.getImageMetadata(for: asset)
        imageItems.append(self.mapImageAsset(asset, metadata: metadata))
      }
    }

    let totalCount = audioItems.count + videoItems.count + imageItems.count

    return [
      "audio": audioItems,
      "videos": videoItems,
      "images": imageItems,
      "documents": [],
      "totalCount": totalCount,
      "query": query
    ]
  }

  // MARK: - Lookups

  func getById(mediaType: String, id: String) -> [String: Any?]? {
    let fetchResult = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil)
    guard let asset = fetchResult.firstObject else { return nil }

    switch mediaType {
    case "audio":
      let metadata = getAudioMetadata(for: asset)
      return mapAudioAsset(asset, metadata: metadata)
    case "video":
      return mapVideoAsset(asset)
    case "image":
      let metadata = getImageMetadata(for: asset)
      return mapImageAsset(asset, metadata: metadata)
    default:
      return nil
    }
  }

  func getByUri(uri: String) -> [String: Any?]? {
    // Try to fetch by local identifier
    let fetchResult = PHAsset.fetchAssets(withLocalIdentifiers: [uri], options: nil)
    guard let asset = fetchResult.firstObject else { return nil }

    switch asset.mediaType {
    case .audio:
      let metadata = getAudioMetadata(for: asset)
      return mapAudioAsset(asset, metadata: metadata)
    case .video:
      return mapVideoAsset(asset)
    case .image:
      let metadata = getImageMetadata(for: asset)
      return mapImageAsset(asset, metadata: metadata)
    default:
      return nil
    }
  }

  func getPathByUri(uri: String) -> String? {
    // Try as file URL first
    if let fileURL = URL(string: uri), fileURL.scheme == "file" {
      return fileURL.path
    }
    // Try as PHAsset local identifier
    let fetchResult = PHAsset.fetchAssets(withLocalIdentifiers: [uri], options: nil)
    guard let asset = fetchResult.firstObject else {
      // Fallback: try the local identifier as a path
      if FileManager.default.fileExists(atPath: uri) { return uri }
      return nil
    }
    if let url = syncAssetFileURL(for: asset) {
      return url.path
    }
    if FileManager.default.fileExists(atPath: uri) { return uri }
    return nil
  }

  // MARK: - Detailed Metadata

  func getDetailedMetadata(mediaType: String, id: String) -> [String: Any?]? {
    let fetchResult = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil)
    guard let asset = fetchResult.firstObject else { return nil }

    let resources = PHAssetResource.assetResources(for: asset)
    guard let resource = resources.first else { return nil }
    guard let url = syncAssetFileURL(for: asset) else { return nil }

    let assetMediaType: String = {
      switch asset.mediaType {
      case .audio: return "audio"
      case .video: return "video"
      case .image: return "image"
      default: return "document"
      }
    }()
    let resolvedMediaType = (mediaType != assetMediaType) ? assetMediaType : mediaType
    let mime = resource.uniformTypeIdentifier ?? mimeTypeFromExtension(url.pathExtension)

    return extractMetadata(for: url, mediaType: resolvedMediaType, mimeType: mime)
  }

  func getDetailedMetadataByUri(uri: String) -> [String: Any?]? {
    var resolvedUrl: URL?
    var resolvedMediaType: String?
    var resolvedMime: String?

    if let candidate = URL(string: uri), candidate.scheme == "file" {
      resolvedUrl = candidate
      resolvedMime = mimeTypeFromExtension(candidate.pathExtension)
      resolvedMediaType = mediaTypeFromExtension(candidate.pathExtension)
    } else if URL(string: uri)?.scheme == nil {
      // Plain filesystem path or a PHAsset local identifier (no scheme).
      let fileCandidate = URL(fileURLWithPath: uri)
      if FileManager.default.fileExists(atPath: fileCandidate.path) {
        resolvedUrl = fileCandidate
        resolvedMime = mimeTypeFromExtension(fileCandidate.pathExtension)
        resolvedMediaType = mediaTypeFromExtension(fileCandidate.pathExtension)
      } else {
        // Treat as a PHAsset local identifier.
        let fetchResult = PHAsset.fetchAssets(withLocalIdentifiers: [uri], options: nil)
        guard let asset = fetchResult.firstObject else { return nil }
        let resources = PHAssetResource.assetResources(for: asset)
        guard let resource = resources.first else { return nil }
        guard let url = syncAssetFileURL(for: asset) else { return nil }
        resolvedUrl = url
        resolvedMime = resource.uniformTypeIdentifier ?? mimeTypeFromExtension(url.pathExtension)
        resolvedMediaType = {
          switch asset.mediaType {
          case .audio: return "audio"
          case .video: return "video"
          case .image: return "image"
          default: return "document"
          }
        }()
      }
    }

    guard let url = resolvedUrl else { return nil }
    let mediaType = resolvedMediaType ?? "document"
    let mime = resolvedMime ?? ""

    return extractMetadata(for: url, mediaType: mediaType, mimeType: mime)
  }

  // MARK: - Modern async variants (iOS 16+ AVAsyncProperty)

  @available(iOS 16.0, *)
  func getDetailedMetadataAsync(mediaType: String, id: String) async -> [String: Any?]? {
    let fetchResult = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil)
    guard let asset = fetchResult.firstObject else { return nil }
    let resources = PHAssetResource.assetResources(for: asset)
    guard let resource = resources.first else { return nil }
    guard let url = await asyncAssetFileURL(for: asset) ?? syncAssetFileURL(for: asset) else { return nil }
    let assetMediaType: String = {
      switch asset.mediaType {
      case .audio: return "audio"
      case .video: return "video"
      case .image: return "image"
      default: return "document"
      }
    }()
    let resolvedMediaType = (mediaType != assetMediaType) ? assetMediaType : mediaType
    let mime = resource.uniformTypeIdentifier ?? mimeTypeFromExtension(url.pathExtension)
    return await extractMetadataAsync(for: url, mediaType: resolvedMediaType, mimeType: mime)
  }

  @available(iOS 16.0, *)
  func getDetailedMetadataByUriAsync(uri: String) async -> [String: Any?]? {
    var resolvedUrl: URL?
    var resolvedMediaType: String?
    var resolvedMime: String?
    if let candidate = URL(string: uri), candidate.scheme == "file" {
      resolvedUrl = candidate
      resolvedMime = mimeTypeFromExtension(candidate.pathExtension)
      resolvedMediaType = mediaTypeFromExtension(candidate.pathExtension)
    } else if URL(string: uri)?.scheme == nil {
      let fileCandidate = URL(fileURLWithPath: uri)
      if FileManager.default.fileExists(atPath: fileCandidate.path) {
        resolvedUrl = fileCandidate
        resolvedMime = mimeTypeFromExtension(fileCandidate.pathExtension)
        resolvedMediaType = mediaTypeFromExtension(fileCandidate.pathExtension)
      } else {
        let fetchResult = PHAsset.fetchAssets(withLocalIdentifiers: [uri], options: nil)
        guard let asset = fetchResult.firstObject else { return nil }
        let resources = PHAssetResource.assetResources(for: asset)
        guard let resource = resources.first else { return nil }
        guard let url = await asyncAssetFileURL(for: asset) ?? syncAssetFileURL(for: asset) else { return nil }
        resolvedUrl = url
        resolvedMime = resource.uniformTypeIdentifier ?? mimeTypeFromExtension(url.pathExtension)
        resolvedMediaType = {
          switch asset.mediaType {
          case .audio: return "audio"
          case .video: return "video"
          case .image: return "image"
          default: return "document"
          }
        }()
      }
    }
    guard let url = resolvedUrl else { return nil }
    let mediaType = resolvedMediaType ?? "document"
    let mime = resolvedMime ?? ""
    return await extractMetadataAsync(for: url, mediaType: mediaType, mimeType: mime)
  }

  // MARK: - Recent

  func getRecent(mediaType: String?, limit: Int?) -> [[String: Any?]] {
    let actualLimit = limit ?? 50
    let fetchOptions = PHFetchOptions()
    fetchOptions.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]

    if let type = mediaType {
      let assetType = mapMediaType(type)
      fetchOptions.predicate = NSPredicate(format: "mediaType == %d", assetType.rawValue)
    }

    let results = PHAsset.fetchAssets(with: fetchOptions)
    var items: [[String: Any?]] = []

    let count = min(results.count, actualLimit)
    for i in 0..<count {
      let asset = results.object(at: i)
      switch asset.mediaType {
      case .audio:
        let metadata = getAudioMetadata(for: asset)
        items.append(mapAudioAsset(asset, metadata: metadata))
      case .video:
        items.append(mapVideoAsset(asset))
      case .image:
        let metadata = getImageMetadata(for: asset)
        items.append(mapImageAsset(asset, metadata: metadata))
      default:
        break
      }
    }

    return items
  }

  // MARK: - Favorites

  func getFavorites(mediaType: String?, sort: Any?, pagination: Any?) -> [[String: Any?]] {
    let fetchOptions = PHFetchOptions()
    fetchOptions.predicate = NSPredicate(format: "favorite == YES")

    if let type = mediaType {
      let assetType = mapMediaType(type)
      fetchOptions.predicate = NSPredicate(format: "favorite == YES AND mediaType == %d", assetType.rawValue)
    }

    applySortOptions(fetchOptions, sort: sort, mediaType: .audio)

    let results = PHAsset.fetchAssets(with: fetchOptions)
    var items: [[String: Any?]] = []

    results.enumerateObjects { asset, _, _ in
      switch asset.mediaType {
      case .audio:
        let metadata = self.getAudioMetadata(for: asset)
        items.append(self.mapAudioAsset(asset, metadata: metadata))
      case .video:
        items.append(self.mapVideoAsset(asset))
      case .image:
        let metadata = self.getImageMetadata(for: asset)
        items.append(self.mapImageAsset(asset, metadata: metadata))
      default:
        break
      }
    }

    return applyPagination(items, pagination: pagination)
  }

  // MARK: - Largest Files

  func getLargestFiles(mediaType: String?, limit: Int?) -> [[String: Any?]] {
    let actualLimit = limit ?? 50
    let fetchOptions = PHFetchOptions()

    if let type = mediaType {
      let assetType = mapMediaType(type)
      fetchOptions.predicate = NSPredicate(format: "mediaType == %d", assetType.rawValue)
    }

    let results = PHAsset.fetchAssets(with: fetchOptions)
    var items: [(Double, [String: Any?])] = []

    results.enumerateObjects { asset, _, _ in
      let size = self.getAssetFileSize(asset)
      var item: [String: Any?]

      switch asset.mediaType {
      case .audio:
        let metadata = self.getAudioMetadata(for: asset)
        item = self.mapAudioAsset(asset, metadata: metadata)
      case .video:
        item = self.mapVideoAsset(asset)
      case .image:
        let metadata = self.getImageMetadata(for: asset)
        item = self.mapImageAsset(asset, metadata: metadata)
      default:
        return
      }

      items.append((size, item))
    }

    return Array(items.sorted { $0.0 > $1.0 }
      .prefix(actualLimit)
      .map { $0.1 })
  }

  // MARK: - Duplicates

  func getDuplicates(mediaType: String?) -> [[String: Any?]] {
    let fetchOptions = PHFetchOptions()
    if let type = mediaType {
      let assetType = mapMediaType(type)
      fetchOptions.predicate = NSPredicate(format: "mediaType == %d", assetType.rawValue)
    }

    let results = PHAsset.fetchAssets(with: fetchOptions)
    var sizeGroups: [Double: [(String, PHAsset)]] = [:]

    results.enumerateObjects { asset, _, _ in
      let size = self.getAssetFileSize(asset)
      if size > 0 {
        if sizeGroups[size] == nil {
          sizeGroups[size] = []
        }
        sizeGroups[size]!.append((asset.localIdentifier, asset))
      }
    }

    var duplicates: [[String: Any?]] = []

    for (_, group) in sizeGroups where group.count >= 1 {
      if group.count >= 2 {
        let hash = group[0].0
        duplicates.append([
          "fileHash": hash,
          "count": group.count,
          "items": [],
          "totalSize": Double(group.count) * Double(group[0].1)
        ])
      }
    }

    return duplicates
  }

  // MARK: - Statistics

  func getStatistics() -> [String: Any?] {
    let audioResult = PHAsset.fetchAssets(with: .audio, options: nil)
    let videoResult = PHAsset.fetchAssets(with: .video, options: nil)
    let imageResult = PHAsset.fetchAssets(with: .image, options: nil)

    var totalDuration: Double = 0

    audioResult.enumerateObjects { asset, _, _ in
      totalDuration += asset.duration
    }

    videoResult.enumerateObjects { asset, _, _ in
      totalDuration += asset.duration
    }

    return [
      "totalAudio": audioResult.count,
      "totalVideo": videoResult.count,
      "totalImages": imageResult.count,
      "totalDocuments": 0,
      "totalSize": calculateTotalSize(assets: audioResult) + calculateTotalSize(assets: videoResult) + calculateTotalSize(assets: imageResult),
      "totalDuration": totalDuration * 1000
    ]
  }

  // MARK: - Artwork

  func getAlbumArtwork(albumId: String) -> String? {
    let fetchResult = PHAssetCollection.fetchAssetCollections(withLocalIdentifiers: [albumId], options: nil)
    guard let collection = fetchResult.firstObject else { return nil }

    let assets = PHAsset.fetchAssets(in: collection, options: nil)
    guard let firstAsset = assets.firstObject else { return nil }

    let options = PHImageRequestOptions()
    options.synchronous = true
    options.deliveryMode = .fastFormat
    options.resizeMode = .fast

    var resultUri: String?

    PHImageManager.default().requestImage(
      for: firstAsset,
      targetSize: CGSize(width: 300, height: 300),
      contentMode: .aspectFill,
      options: options
    ) { image, _ in
      if let image = image, let data = image.jpegData(compressionQuality: 0.8) {
        let tempDir = FileManager.default.temporaryDirectory
        let fileUrl = tempDir.appendingPathComponent("artwork_\(albumId).jpg")
        try? data.write(to: fileUrl)
        resultUri = fileUrl.absoluteString
      }
    }

    return resultUri
  }

  // MARK: - Thumbnails

  func getThumbnail(assetId: String, mediaType: PHAssetMediaType, width: Int?, height: Int?) -> String? {
    let fetchResult = PHAsset.fetchAssets(withLocalIdentifiers: [assetId], options: nil)
    guard let asset = fetchResult.firstObject else { return nil }

    let targetWidth = CGFloat(width ?? 320)
    let targetHeight = CGFloat(height ?? 240)

    let options = PHImageRequestOptions()
    options.synchronous = true
    options.deliveryMode = .fastFormat
    options.resizeMode = .fast

    var resultUri: String?

    PHImageManager.default().requestImage(
      for: asset,
      targetSize: CGSize(width: targetWidth, height: targetHeight),
      contentMode: .aspectFill,
      options: options
    ) { image, _ in
      if let image = image, let data = image.jpegData(compressionQuality: 0.85) {
        let tempDir = FileManager.default.temporaryDirectory
        let typeName = mediaType == .video ? "video" : "image"
        let fileUrl = tempDir.appendingPathComponent("\(typeName)_\(assetId)_\(Int(targetWidth))x\(Int(targetHeight)).jpg")
        try? data.write(to: fileUrl)
        resultUri = fileUrl.absoluteString
      }
    }

    return resultUri
  }

  // MARK: - Private Helpers

  private func mapAudioAsset(_ asset: PHAsset, metadata: [String: Any?]?) -> [String: Any?] {
    var uri: String?
    let resources = PHAssetResource.assetResources(for: asset)
    if let resource = resources.first {
      uri = resource.originalFilename
    }

    return [
      "id": asset.localIdentifier,
      "uri": uri ?? "",
      "title": filename(for: asset),
      "artist": metadata?["artist"] as? String ?? "",
      "album": metadata?["album"] as? String ?? "",
      "albumId": metadata?["albumId"] as? String ?? "",
      "genre": metadata?["genre"] as? String,
      "duration": Int(asset.duration * 1000),
      "size": getAssetFileSize(asset),
      "trackNumber": metadata?["trackNumber"] as? Int ?? 0,
      "discNumber": metadata?["discNumber"] as? Int ?? 0,
      "year": asset.creationDate?.year ?? 0,
      "dateAdded": (asset.creationDate ?? Date()).timeIntervalSince1970 * 1000,
      "dateModified": (asset.modificationDate ?? Date()).timeIntervalSince1970 * 1000,
      "composer": metadata?["composer"] as? String,
      "lyrics": metadata?["lyrics"] as? String,
      "albumArtist": metadata?["albumArtist"] as? String,
      "isFavorite": asset.isFavorite,
      "playCount": 0,
      "lastPlayed": 0,
      "bookmark": 0,
      "bitrate": metadata?["bitrate"] as? Int,
      "sampleRate": metadata?["sampleRate"] as? Int,
      "channels": metadata?["channels"] as? Int,
      "encoding": nil,
      "mimeType": metadata?["mimeType"] as? String ?? "",
      "fileExtension": uri?.pathExtension ?? "",
      "relativePath": "",
      "displayName": uri ?? "",
      "contentUri": asset.localIdentifier
    ]
  }

  private func mapVideoAsset(_ asset: PHAsset) -> [String: Any?] {
    var uri: String?
    let resources = PHAssetResource.assetResources(for: asset)
    if let resource = resources.first {
      uri = resource.originalFilename
    }

    return [
      "id": asset.localIdentifier,
      "uri": uri ?? "",
      "title": filename(for: asset),
      "duration": Int(asset.duration * 1000),
      "width": asset.pixelWidth,
      "height": asset.pixelHeight,
      "frameRate": nil,
      "rotation": 0,
      "size": getAssetFileSize(asset),
      "mimeType": getMimeType(for: asset),
      "relativePath": "",
      "displayName": uri ?? "",
      "dateAdded": (asset.creationDate ?? Date()).timeIntervalSince1970 * 1000,
      "dateModified": (asset.modificationDate ?? Date()).timeIntervalSince1970 * 1000,
      "resolution": "\(asset.pixelWidth)x\(asset.pixelHeight)",
      "orientation": 0
    ]
  }

  private func mapImageAsset(_ asset: PHAsset, metadata: [String: Any?]?) -> [String: Any?] {
    var uri: String?
    let resources = PHAssetResource.assetResources(for: asset)
    if let resource = resources.first {
      uri = resource.originalFilename
    }

    return [
      "id": asset.localIdentifier,
      "uri": uri ?? "",
      "title": filename(for: asset),
      "width": asset.pixelWidth,
      "height": asset.pixelHeight,
      "orientation": metadata?["orientation"] as? Int ?? 0,
      "cameraMake": metadata?["cameraMake"] as? String,
      "cameraModel": metadata?["cameraModel"] as? String,
      "dateTaken": (asset.creationDate ?? Date()).timeIntervalSince1970 * 1000,
      "gpsLatitude": metadata?["latitude"] as? Double,
      "gpsLongitude": metadata?["longitude"] as? Double,
      "mimeType": getMimeType(for: asset),
      "size": getAssetFileSize(asset),
      "relativePath": "",
      "displayName": uri ?? "",
      "dateAdded": (asset.creationDate ?? Date()).timeIntervalSince1970 * 1000,
      "dateModified": (asset.modificationDate ?? Date()).timeIntervalSince1970 * 1000
    ]
  }

  private func getAudioMetadata(for asset: PHAsset) -> [String: Any?] {
    var metadata: [String: Any?] = [:]

    let resources = PHAssetResource.assetResources(for: asset)
    if let resource = resources.first {
      metadata["mimeType"] = resource.uniformTypeIdentifier
    }

    return metadata
  }

  private func getImageMetadata(for asset: PHAsset) -> [String: Any?] {
    var metadata: [String: Any?] = [:]
    metadata["latitude"] = asset.location?.coordinate.latitude
    metadata["longitude"] = asset.location?.coordinate.longitude
    return metadata
  }

  private func getAudioArtist(for asset: PHAsset) -> String? {
    return filename(for: asset)
  }

  private func getAudioGenre(for asset: PHAsset) -> String? {
    return nil
  }

  private func getMimeType(for asset: PHAsset) -> String {
    let resources = PHAssetResource.assetResources(for: asset)
    guard let resource = resources.first else { return "" }
    return resource.uniformTypeIdentifier
  }

  private func getAssetFileSize(_ asset: PHAsset) -> Double {
    let resources = PHAssetResource.assetResources(for: asset)
    guard let resource = resources.first else { return 0 }
    return Double(resource.fileSize)
  }

  private func calculateTotalSize(assets: PHFetchResult<PHAsset>) -> Double {
    var totalSize: Double = 0
    assets.enumerateObjects { asset, _, _ in
      totalSize += self.getAssetFileSize(asset)
    }
    return totalSize
  }

  private func calculateTotalDuration(assets: PHFetchResult<PHAsset>) -> Double {
    var totalDuration: Double = 0
    assets.enumerateObjects { asset, _, _ in
      totalDuration += asset.duration
    }
    return totalDuration * 1000
  }

  private func getAlbumArtworkUri(collection: PHAssetCollection) -> String? {
    let assets = PHAsset.fetchAssets(in: collection, options: nil)
    guard let firstAsset = assets.firstObject else { return nil }
    return firstAsset.localIdentifier
  }

  private func mapMediaType(_ type: String) -> PHAssetMediaType {
    switch type {
    case "audio": return .audio
    case "video": return .video
    case "image": return .image
    default: return .unknown
    }
  }

  private func applyFilterOptions(_ options: PHFetchOptions, filter: Any?) {
    guard let filterDict = filter as? [String: Any?] else { return }

    if let startDate = filterDict["startDate"] as? Double {
      let date = Date(timeIntervalSince1970: startDate / 1000)
      options.predicate = NSPredicate(format: "creationDate >= %@", date as NSDate)
    }
    if let endDate = filterDict["endDate"] as? Double {
      let date = Date(timeIntervalSince1970: endDate / 1000)
      if let existing = options.predicate {
        options.predicate = NSCompoundPredicate(andPredicateWithSubpredicates: [
          existing,
          NSPredicate(format: "creationDate <= %@", date as NSDate)
        ])
      } else {
        options.predicate = NSPredicate(format: "creationDate <= %@", date as NSDate)
      }
    }
  }

  private func applySortOptions(_ options: PHFetchOptions, sort: Any?, mediaType: PHAssetMediaType) {
    guard let sortDict = sort as? [String: Any?] else { return }

    let field = sortDict["field"] as? String ?? "name"
    let ascending = (sortDict["order"] as? String ?? "asc") == "asc"

    let sortKey: String
    switch field {
    case "dateAdded": sortKey = "creationDate"
    case "dateModified": sortKey = "modificationDate"
    case "name": sortKey = "filename"
    case "duration": sortKey = "duration"
    default: sortKey = "creationDate"
    }

    options.sortDescriptors = [NSSortDescriptor(key: sortKey, ascending: ascending)]
  }

  private func applySorting(_ items: [[String: Any?]], sort: Any?) -> [[String: Any?]] {
    guard let sortDict = sort as? [String: Any?] else { return items }

    let field = sortDict["field"] as? String ?? "name"
    let ascending = (sortDict["order"] as? String ?? "asc") == "asc"

    return items.sorted { a, b in
      let aVal = a[field] ?? ""
      let bVal = b[field] ?? ""

      if let aNum = aVal as? Double, let bNum = bVal as? Double {
        return ascending ? aNum < bNum : aNum > bNum
      }
      if let aStr = aVal as? String, let bStr = bVal as? String {
        return ascending ? aStr < bStr : aStr > bStr
      }
      return false
    }
  }

  private func applyPagination(_ items: [[String: Any?]], pagination: Any?) -> [[String: Any?]] {
    guard let paginationDict = pagination as? [String: Any?] else { return items }

    var result = items

    if let offset = paginationDict["offset"] as? Int, offset > 0 {
      result = Array(result.dropFirst(offset))
    }

    if let limit = paginationDict["limit"] as? Int, limit > 0 {
      result = Array(result.prefix(limit))
    }

    return result
  }

  // MARK: - Detailed Metadata Extraction

  private func extractMetadata(for url: URL, mediaType: String, mimeType: String) -> [String: Any?] {
    var result: [String: Any?] = [:]
    result["mediaType"] = mediaType
    result["mimeType"] = mimeType
    result["fileSize"] = (try? FileManager.default.attributesOfItem(atPath: url.path)[.size] as? NSNumber)?.intValue
      ?? (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0

    if mediaType == "audio" || mediaType == "video" {
      let av = extractAudioVideoMetadata(for: url, mimeType: mimeType)
      if let durationMs = av["durationMs"] { result["durationMs"] = durationMs }
      if let containerFormat = av["containerFormat"] { result["containerFormat"] = containerFormat }
      if let audio = av["audio"] as? [String: Any?] { result["audio"] = compact(audio) }
      if let video = av["video"] as? [String: Any?] { result["video"] = compact(video) }
    } else if mediaType == "image" {
      result["image"] = compact(extractImageMetadata(for: url))
    } else {
      result["document"] = compact(extractDocumentMetadata(for: url))
    }

    return compact(result)
  }

  // Legacy synchronous path (pre-iOS 16). Kept for fallback when async not available.
  // Deprecated in Swift since iOS 16: use extractAudioVideoMetadataAsync instead.
  private func extractAudioVideoMetadata(for url: URL, mimeType: String) -> [String: Any?] {
    let asset = AVURLAsset(url: url)
    var result: [String: Any?] = [:]

    let durationSeconds = asset.duration.seconds
    result["durationMs"] = durationSeconds.isFinite ? Int(durationSeconds * 1000) : nil
    result["containerFormat"] = containerFormat(from: mimeType, pathExtension: url.pathExtension)

    var audioDict: [String: Any?]?
    var videoDict: [String: Any?]?

    for track in asset.tracks {
      if track.mediaType == .audio {
        var ad: [String: Any?] = [:]
        ad["language"] = track.languageCode
        if let af = track.formatDescriptions.first as? CMFormatDescription {
          let exts = af.extensions as? [AnyHashable: Any] ?? [:]
          if let asbd = CMAudioFormatDescriptionGetStreamBasicDescription(af)?.pointee {
            if asbd.mSampleRate > 0 { ad["sampleRate"] = Int(asbd.mSampleRate) }
            if asbd.mChannelsPerFrame > 0 { ad["channels"] = Int(asbd.mChannelsPerFrame) }
            if asbd.mBitsPerChannel > 0 { ad["bitsPerSample"] = Int(asbd.mBitsPerChannel) }
          }
          if let bitrate = exts[kCMFormatDescriptionExtension_AudioBitRate] as? NSNumber
            ?? exts[kCMFormatDescriptionExtension_BitRate] as? NSNumber {
            ad["bitrate"] = bitrate.intValue
          }
          if let formatName = exts[kCMFormatDescriptionExtension_FormatName] as? String {
            ad["codecMime"] = formatName
            ad["codec"] = normalizeAudioCodec(formatName: formatName)
          }
          if let channels = ad["channels"] as? Int {
            ad["channelLayout"] = channelLayout(for: channels)
          }
        }
        audioDict = ad
      } else if track.mediaType == .video {
        var vd: [String: Any?] = [:]
        if track.nominalFrameRate > 0 { vd["frameRate"] = Double(track.nominalFrameRate) }
        vd["rotation"] = rotationDegrees(from: track.preferredTransform)
        vd["language"] = track.languageCode
        if let vf = track.formatDescriptions.first as? CMFormatDescription {
          let exts = vf.extensions as? [AnyHashable: Any] ?? [:]
          let dims = CMVideoFormatDescriptionGetDimensions(vf)
          if dims.width > 0 { vd["width"] = Int(dims.width) }
          if dims.height > 0 { vd["height"] = Int(dims.height) }
          if let bitrate = exts[kCMFormatDescriptionExtension_VideoBitRate] as? NSNumber {
            vd["bitrate"] = bitrate.intValue
          }
          if let formatName = exts[kCMFormatDescriptionExtension_FormatName] as? String {
            vd["codecMime"] = formatName
            vd["codec"] = normalizeVideoCodec(formatName: formatName)
          }
          vd["profile"] = exts[kCMFormatDescriptionExtension_Profile] as? String
          vd["level"] = exts[kCMFormatDescriptionExtension_Level] as? String
          vd["colorSpace"] = exts[kCMFormatDescriptionExtension_ColorPrimaries] as? String
          vd["colorStandard"] = exts[kCMFormatDescriptionExtension_YCbCrMatrix] as? String
          vd["colorTransfer"] = exts[kCMFormatDescriptionExtension_TransferFunction] as? String
        }
        if vd["width"] == nil {
          let size = asset.naturalSize
          if size.width > 0 { vd["width"] = Int(size.width) }
          if size.height > 0 { vd["height"] = Int(size.height) }
        }
        videoDict = vd
      }
    }

    result["audio"] = audioDict
    result["video"] = videoDict
    return result
  }

  // MARK: Modern async AVFoundation (iOS 16+)
  // Uses AVAsyncProperty: load(.duration), load(.tracks), track.load(.formatDescriptions), etc.
  // Avoids deprecated sync access (asset.duration, asset.tracks) which blocks.

  @available(iOS 16.0, *)
  private func extractMetadataAsync(for url: URL, mediaType: String, mimeType: String) async -> [String: Any?] {
    var result: [String: Any?] = [:]
    result["mediaType"] = mediaType
    result["mimeType"] = mimeType
    result["fileSize"] = (try? FileManager.default.attributesOfItem(atPath: url.path)[.size] as? NSNumber)?.intValue
      ?? (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0

    if mediaType == "audio" || mediaType == "video" {
      let av = await extractAudioVideoMetadataAsync(for: url, mimeType: mimeType)
      if let durationMs = av["durationMs"] { result["durationMs"] = durationMs }
      if let containerFormat = av["containerFormat"] { result["containerFormat"] = containerFormat }
      if let audio = av["audio"] as? [String: Any?] { result["audio"] = compact(audio) }
      if let video = av["video"] as? [String: Any?] { result["video"] = compact(video) }
    } else if mediaType == "image" {
      result["image"] = compact(extractImageMetadata(for: url))
    } else {
      result["document"] = compact(extractDocumentMetadata(for: url))
    }
    return compact(result)
  }

  @available(iOS 16.0, *)
  private func extractAudioVideoMetadataAsync(for url: URL, mimeType: String) async -> [String: Any?] {
    let asset = AVURLAsset(url: url)
    var result: [String: Any?] = [:]

    // Async duration — replaces deprecated asset.duration
    do {
      let duration = try await asset.load(.duration)
      let seconds = duration.seconds
      if seconds.isFinite { result["durationMs"] = Int(seconds * 1000) }
    } catch { /* ignore, legacy fallback handles */ }

    result["containerFormat"] = containerFormat(from: mimeType, pathExtension: url.pathExtension)

    var audioDict: [String: Any?]?
    var videoDict: [String: Any?]? = [:]

    // Async tracks — replaces deprecated asset.tracks
    let tracks: [AVAssetTrack]
    do {
      tracks = try await asset.load(.tracks)
    } catch {
      // Fallback to sync if async fails (e.g. file not ready)
      return extractAudioVideoMetadata(for: url, mimeType: mimeType)
    }

    for track in tracks {
      // Async track properties
      let mediaTypeVal: AVMediaType
      let languageCode: String?
      let formatDescriptions: [Any]
      let nominalFrameRate: Float
      let preferredTransform: CGAffineTransform
      do {
        async let mt = track.load(.mediaType)
        async let lang = track.load(.languageCode)
        async let descs = track.load(.formatDescriptions)
        async let fps = track.load(.nominalFrameRate)
        async let xform = track.load(.preferredTransform)
        mediaTypeVal = try await mt
        languageCode = try await lang
        formatDescriptions = try await descs
        nominalFrameRate = try await fps
        preferredTransform = try await xform
      } catch { continue }

      if mediaTypeVal == .audio {
        var ad: [String: Any?] = [:]
        ad["language"] = languageCode
        if let af = formatDescriptions.first as? CMFormatDescription {
          let exts = af.extensions as? [AnyHashable: Any] ?? [:]
          if let asbd = CMAudioFormatDescriptionGetStreamBasicDescription(af)?.pointee {
            if asbd.mSampleRate > 0 { ad["sampleRate"] = Int(asbd.mSampleRate) }
            if asbd.mChannelsPerFrame > 0 { ad["channels"] = Int(asbd.mChannelsPerFrame) }
            if asbd.mBitsPerChannel > 0 { ad["bitsPerSample"] = Int(asbd.mBitsPerChannel) }
          }
          if let bitrate = exts[kCMFormatDescriptionExtension_AudioBitRate] as? NSNumber
            ?? exts[kCMFormatDescriptionExtension_BitRate] as? NSNumber {
            ad["bitrate"] = bitrate.intValue
          }
          if let formatName = exts[kCMFormatDescriptionExtension_FormatName] as? String {
            ad["codecMime"] = formatName
            ad["codec"] = normalizeAudioCodec(formatName: formatName)
          }
          if let channels = ad["channels"] as? Int {
            ad["channelLayout"] = channelLayout(for: channels)
          }
        }
        audioDict = ad
      } else if mediaTypeVal == .video {
        var vd: [String: Any?] = videoDict ?? [:]
        if nominalFrameRate > 0 { vd["frameRate"] = Double(nominalFrameRate) }
        vd["rotation"] = rotationDegrees(from: preferredTransform)
        vd["language"] = languageCode
        if let vf = formatDescriptions.first as? CMFormatDescription {
          let exts = vf.extensions as? [AnyHashable: Any] ?? [:]
          let dims = CMVideoFormatDescriptionGetDimensions(vf)
          if dims.width > 0 { vd["width"] = Int(dims.width) }
          if dims.height > 0 { vd["height"] = Int(dims.height) }
          if let bitrate = exts[kCMFormatDescriptionExtension_VideoBitRate] as? NSNumber {
            vd["bitrate"] = bitrate.intValue
          }
          if let formatName = exts[kCMFormatDescriptionExtension_FormatName] as? String {
            vd["codecMime"] = formatName
            vd["codec"] = normalizeVideoCodec(formatName: formatName)
          }
          vd["profile"] = exts[kCMFormatDescriptionExtension_Profile] as? String
          vd["level"] = exts[kCMFormatDescriptionExtension_Level] as? String
          vd["colorSpace"] = exts[kCMFormatDescriptionExtension_ColorPrimaries] as? String
          vd["colorStandard"] = exts[kCMFormatDescriptionExtension_YCbCrMatrix] as? String
          vd["colorTransfer"] = exts[kCMFormatDescriptionExtension_TransferFunction] as? String
        }
        // naturalSize fallback needs asset load
        if vd["width"] == nil {
          if let size = try? await asset.load(.naturalSize) {
            if size.width > 0 { vd["width"] = Int(size.width) }
            if size.height > 0 { vd["height"] = Int(size.height) }
          }
        }
        videoDict = vd
      }
    }

    if let ad = audioDict { result["audio"] = ad }
    if let vd = videoDict, !vd.isEmpty { result["video"] = vd }
    return result
  }

  private func extractImageMetadata(for url: URL) -> [String: Any?] {
    guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else {
      return [:]
    }

    var result: [String: Any?] = [:]
    let type = CGImageSourceGetType(source) as String?
    result["format"] = imageFormat(from: type)

    guard let props = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [String: Any] else {
      return compact(result)
    }

    result["width"] = props[kCGImagePropertyPixelWidth as String] as? Int
    result["height"] = props[kCGImagePropertyPixelHeight as String] as? Int
    result["bitsPerSample"] = props[kCGImagePropertyDepth as String] as? Int
    result["colorSpace"] = props[kCGImagePropertyColorModel as String] as? String

    let exifDict = props[kCGImagePropertyExifDictionary as String] as? [String: Any]
    let tiffDict = props[kCGImagePropertyTIFFDictionary as String] as? [String: Any]
    let gpsDict = props[kCGImagePropertyGPSDictionary as String] as? [String: Any]

    var exif: [String: Any?] = [:]
    exif["make"] = tiffDict?[kCGImagePropertyTIFFMake as String] as? String
    exif["model"] = tiffDict?[kCGImagePropertyTIFFModel as String] as? String
    exif["software"] = tiffDict?[kCGImagePropertyTIFFSoftware as String] as? String
    exif["imageDescription"] = tiffDict?[kCGImagePropertyTIFFImageDescription as String] as? String
    exif["artist"] = tiffDict?[kCGImagePropertyTIFFArtist as String] as? String
    exif["copyright"] = tiffDict?[kCGImagePropertyTIFFCopyright as String] as? String

    exif["dateTimeOriginal"] = exifDateTime(exifDict?[kCGImagePropertyExifDateTimeOriginal as String] as? String)
    exif["dateTimeDigitized"] = exifDateTime(exifDict?[kCGImagePropertyExifDateTimeDigitized as String] as? String)
    exif["orientation"] = exifDict?[kCGImagePropertyExifOrientation as String] as? Int
    exif["aperture"] = exifDict?[kCGImagePropertyExifFNumber as String] as? Double
    if let isoArray = exifDict?[kCGImagePropertyExifISOSpeedRatings as String] as? [Int],
       let iso = isoArray.first {
      exif["iso"] = iso
    }
    exif["shutterSpeed"] = exifDict?[kCGImagePropertyExifShutterSpeedValue as String] as? Double
    exif["exposureTime"] = exifDict?[kCGImagePropertyExifExposureTime as String] as? Double
    if let ep = exifDict?[kCGImagePropertyExifExposureProgram as String] as? Int {
      exif["exposureProgram"] = exposureProgramString(ep)
    }
    exif["exposureBias"] = exifDict?[kCGImagePropertyExifExposureBiasValue as String] as? Double
    if let mm = exifDict?[kCGImagePropertyExifMeteringMode as String] as? Int {
      exif["meteringMode"] = meteringModeString(mm)
    }
    if let flash = exifDict?[kCGImagePropertyExifFlash as String] as? Int {
      exif["flash"] = (flash & 1) != 0
      exif["flashMode"] = flashModeString(flash)
    }
    if let wb = exifDict?[kCGImagePropertyExifWhiteBalance as String] as? Int {
      exif["whiteBalance"] = wb == 0 ? "Auto" : "Manual"
    }
    exif["focalLength"] = exifDict?[kCGImagePropertyExifFocalLength as String] as? Double
    exif["focalLength35mm"] = exifDict?[kCGImagePropertyExifFocalLenIn35mmFilm as String] as? Int
    if let sct = exifDict?[kCGImagePropertyExifSceneCaptureType as String] as? Int {
      exif["sceneCaptureType"] = sceneCaptureTypeString(sct)
    }
    if let c = exifDict?[kCGImagePropertyExifContrast as String] as? Int {
      exif["contrast"] = contrastString(c)
    }
    if let s = exifDict?[kCGImagePropertyExifSaturation as String] as? Int {
      exif["saturation"] = saturationString(s)
    }
    if let sh = exifDict?[kCGImagePropertyExifSharpness as String] as? Int {
      exif["sharpness"] = sharpnessString(sh)
    }
    exif["digitalZoomRatio"] = exifDict?[kCGImagePropertyExifDigitalZoomRatio as String] as? Double
    exif["compressedBitsPerPixel"] = exifDict?[kCGImagePropertyExifCompressedBitsPerPixel as String] as? Double
    exif["pixelXDimension"] = exifDict?[kCGImagePropertyExifPixelXDimension as String] as? Int
    exif["pixelYDimension"] = exifDict?[kCGImagePropertyExifPixelYDimension as String] as? Int
    if let cs = exifDict?[kCGImagePropertyExifColorSpace as String] as? Int {
      exif["colorSpace"] = colorSpaceString(cs)
    }

    if let lat = gpsDict?[kCGImagePropertyGPSLatitude as String] as? Double,
       let latRef = gpsDict?[kCGImagePropertyGPSLatitudeRef as String] as? String {
      exif["gpsLatitude"] = lat * (latRef == "S" ? -1.0 : 1.0)
    }
    if let lon = gpsDict?[kCGImagePropertyGPSLongitude as String] as? Double,
       let lonRef = gpsDict?[kCGImagePropertyGPSLongitudeRef as String] as? String {
      exif["gpsLongitude"] = lon * (lonRef == "W" ? -1.0 : 1.0)
    }
    exif["gpsAltitude"] = gpsDict?[kCGImagePropertyGPSAltitude as String] as? Double
    if let gpsTime = gpsDict?[kCGImagePropertyGPSTimeStamp as String] as? String,
       let gpsDate = gpsDict?[kCGImagePropertyGPSDateStamp as String] as? String {
      exif["gpsTimestamp"] = exifDateTime("\(gpsDate) \(gpsTime)")
    }
    exif["gpsProcessingMethod"] = gpsDict?[kCGImagePropertyGPSProcessingMethod as String] as? String

    result["exif"] = compact(exif)
    return compact(result)
  }

  private func extractDocumentMetadata(for url: URL) -> [String: Any?] {
    var result: [String: Any?] = [:]
    result["format"] = url.pathExtension.lowercased()

    if let attrs = try? FileManager.default.attributesOfItem(atPath: url.path) {
      if let creation = attrs[.creationDate] as? Date {
        result["creationDate"] = Int(creation.timeIntervalSince1970 * 1000)
      }
      if let modification = attrs[.modificationDate] as? Date {
        result["modificationDate"] = Int(modification.timeIntervalSince1970 * 1000)
      }
    }

    let ext = url.pathExtension.lowercased()

    if ext == "pdf" {
      if #available(iOS 11.0, *) {
        if let doc = PDFDocument(url: url) {
          result["pageCount"] = doc.pageCount
          result["isEncrypted"] = doc.isEncrypted
        }
      }
    } else if ["txt", "md", "csv", "json"].contains(ext) {
      if let size = (try? FileManager.default.attributesOfItem(atPath: url.path)[.size] as? NSNumber)?.intValue,
         size > 0, size <= 5_000_000 {
        if let content = try? String(contentsOf: url, encoding: .utf8) {
          result["characterCount"] = content.count
          result["wordCount"] = content.components(separatedBy: .whitespacesAndNewlines).filter { !$0.isEmpty }.count
          result["lineCount"] = content.components(separatedBy: .newlines).count
        }
      }
    }

    return result
  }

  // MARK: - File System Operations

  func fileExists(atPath path: String) -> Bool {
    FileManager.default.fileExists(atPath: path)
  }

  func readDirectory(atPath path: String) -> [[String: Any?]]? {
    guard let urls = try? FileManager.default.contentsOfDirectory(atPath: path) else { return nil }
    return urls.map { url in
      var isDir: ObjCBool = false
      let isDirectory = FileManager.default.fileExists(atPath: url.path, isDirectory: &isDir)
      [
        "name": url.lastPathComponent,
        "path": url.path,
        "isDirectory": isDirectory,
        "isSymlink": false
      ]
    }
  }

  func readDirectoryRecursive(atPath path: String) -> [[String: Any?]] {
    var result: [[String: Any?]] = []
    enumerateItems(atPath: path, result: &result)
    return result
  }

  private func enumerateItems(atPath path: String, _ result: inout [[String: Any?]]) {
    guard let urls = try? FileManager.default.contentsOfDirectory(atPath: path) else { return }
    for url in urls {
      var isDir: ObjCBool = false
      let isDirectory = FileManager.default.fileExists(atPath: url.path, isDirectory: &isDir)
      let item: [String: Any?] = [
        "name": url.lastPathComponent,
        "path": url.path,
        "isDirectory": isDirectory,
        "isSymlink": false
      ]
      result.append(item)
      if isDirectory {
        enumerateItems(url.path, &result)
      }
    }
  }

  func fileSize(atPath path: String) -> Int64? {
    do { return try FileManager.default.attributesOfItem(atPath: path)[.size] as? Int64 }
    catch { return nil }
  }

  func createFile(atPath path: String, contents data: Data?, attributes: [FileAttributeKey: Any]? = nil) -> Bool {
    do {
      try data?.write(to: URL(fileURLWithPath: path))
      try FileManager.default.createItem(atPath: path)
      return true
    } catch { return false }
  }

  func renameItem(atPath oldPath: String, to newPath: String) -> Bool {
    do { try FileManager.default.moveItem(atPath: oldPath, toPath: newPath); return true }
    catch { return false }
  }

  func deleteItem(atPath path: String) -> Bool {
    do { try FileManager.default.removeItem(atPath: path); return true }
    catch { return false }
  }

  func copyItem(atPath srcPath: String, to dstPath: String) -> Bool {
    do { try FileManager.default.copyItem(atPath: srcPath, toPath: dstPath); return true }
    catch { return false }
  }

  func directoryStatistics(atPath path: String) -> [String: Any?] {
    var fileCount = 0
    var totalSize: Int64 = 0
    var folderCount = 0
    var histogram = [
      "lessThan1MB": 0, "from1to10MB": 0,
      "from10to100MB": 0, "from100MBto1GB": 0, "greaterThan1GB": 0
    ]

    enumerateItems(atPath: path) { item in
      if let isDir = item["isDirectory"] as? Bool, !isDir {
        fileCount += 1
        if let size = item["size"] as? Int64 {
          totalSize += size
        }
      } else {
        folderCount += 1
      }
    }

    // Re-calculate histogram from items
    // (simplified - in production would track sizes during enumeration)

    return [
      "fileCount": fileCount,
      "totalSize": totalSize,
      "folderCount": folderCount,
      "histogram": histogram
    ]
  }

  func mimeType(forPath path: String) -> String {
    let ext = (path as NSString).pathExtension.lowercased()
    return mimeTypeFromExtension(ext)
  }

  func fileExtension(forPath path: String) -> String {
    (path as NSString).pathExtension.lowercased()
  }

  // MARK: - File Reading

  func readFileContents(atPath path: String, encoding: String.Encoding = .utf8) -> String? {
    do { return try String(contentsOfFile: path, encoding: encoding) }
    catch { return nil }
  }

  func writeFileContents(_ data: Data, toPath path: String, atomically: Bool = true) -> Bool {
    do { try data.write(to: URL(fileURLWithPath: path), atomically: atomically); return true }
    catch { return false }
  }

  // MARK: - vNext: unified audio metadata + artwork engine

  /// Unified per-file metadata. Native owns R128 Q8.8 → dB normalization.
  func getUnifiedAudioMetadata(uri: String) -> [String: Any?]? {
    guard let url = resolveAudioFileURL(uri) else { return nil }
    let asset = AVURLAsset(url: url)
    var title: String?; var artist: String?; var album: String?
    var albumArtist: String?; var genre: String?; var composer: String?
    var trackNumber: Int?; var discNumber: Int?; var year: Int?
    for item in asset.commonMetadata {
      guard let key = item.commonKey?.rawValue else { continue }
      let value = item.stringValue
      switch key {
      case "title": if title == nil { title = value }
      case "artist": if artist == nil { artist = value }
      case "albumName": if album == nil { album = value }
      case "type": if genre == nil { genre = value }
      case "creator": if composer == nil { composer = value }
      default: break
      }
    }
    // iTunes-style extras via available metadata formats (best-effort).
    for format in asset.availableMetadataFormats {
      for item in asset.metadata(forFormat: format) {
        let k = (item.key as? String ?? item.identifier?.rawValue ?? "").uppercased()
        let v = item.stringValue
        if k.contains("ALBUMARTIST") { if albumArtist == nil { albumArtist = v } }
        if k.contains("TRACKNUMBER") || k == "TRK" { if trackNumber == nil { trackNumber = Int(v ?? "") } }
        if k.contains("DISCNUMBER") || k == "DISK" { if discNumber == nil { discNumber = Int(v ?? "") } }
      }
    }
    let durationMs: Int? = {
      let s = asset.duration.seconds
      return s.isFinite && s > 0 ? Int(s * 1000) : nil
    }()
    var bitrate: Int?; var sampleRate: Int?; var channels: Int?
    for track in asset.tracks where track.mediaType == .audio {
      if let af = track.formatDescriptions.first as? CMFormatDescription {
        let exts = af.extensions as? [AnyHashable: Any] ?? [:]
        if let asbd = CMAudioFormatDescriptionGetStreamBasicDescription(af)?.pointee {
          if asbd.mSampleRate > 0 { sampleRate = Int(asbd.mSampleRate) }
          if asbd.mChannelsPerFrame > 0 { channels = Int(asbd.mChannelsPerFrame) }
        }
        if let br = exts[kCMFormatDescriptionExtension_AudioBitRate] as? NSNumber { bitrate = br.intValue }
      }
      break
    }
    let replayGain = readReplayGainHead(url: url)
    let artwork = extractArtworkFile(uri: uri, lightweight: true)
    // NOTE: no `as Any` on optionals — boxed nils bridge unreliably.
    // compact() strips nils; the TS normalizer maps missing keys to null.
    var out: [String: Any?] = [
      "uri": uri,
      "title": title, "artist": artist, "album": album,
      "albumArtist": albumArtist, "genre": genre, "year": year,
      "trackNumber": trackNumber, "discNumber": discNumber,
      "duration": durationMs, "bitrate": bitrate,
      "sampleRate": sampleRate, "channels": channels,
      "composer": composer,
      "artwork": artwork, "replayGain": replayGain,
    ]
    return compact(out)
  }

  /// Extract embedded artwork to a cache file. lightweight=true returns
  /// availability only (for batch scans).
  func extractArtworkFile(uri: String, lightweight: Bool = false) -> [String: Any?]? {
    guard let url = resolveAudioFileURL(uri) else { return nil }
    let asset = AVURLAsset(url: url)
    var imageData: Data?
    for item in asset.commonMetadata where item.commonKey?.rawValue == "artwork" {
      if let data = item.dataValue { imageData = data; break }
    }
    if imageData == nil {
      // Fallback: first 2MB head scan cannot yield image bytes; report absent.
      return nil
    }
    guard let data = imageData, !data.isEmpty else { return nil }
    if lightweight {
      return ["uri": uri, "mimeType": nil, "width": nil, "height": nil, "size": nil]
    }
    let mime = artworkMime(data: data)
    let ext = mime == "image/png" ? "png" : "jpg"
    let fileUrl = FileManager.default.temporaryDirectory.appendingPathComponent("art_\(abs(uri.hashValue)).\(ext)")
    do { try data.write(to: fileUrl) } catch { return nil }
    var w: Int?; var h: Int?
    if let src = CGImageSourceCreateWithData(data as CFData, nil),
       let props = CGImageSourceCopyPropertiesAtIndex(src, 0, nil) as? [String: Any] {
      w = props[kCGImagePropertyPixelWidth as String] as? Int
      h = props[kCGImagePropertyPixelHeight as String] as? Int
    }
    var art: [String: Any?] = ["uri": fileUrl.absoluteString, "mimeType": mime, "width": w, "height": h, "size": data.count]
    return compact(art)
  }

  /// Persist artwork with format preservation by default.
  func saveArtworkFile(sourceUri: String, destUri: String?, options: [String: Any]?) -> [String: Any?]? {
    let preserve = (options?["preserveFormat"] as? Bool) ?? true
    let format = ((options?["format"] as? String) ?? "original").lowercased()
    let quality = (options?["quality"] as? Double) ?? 0.85
    // Resolve source bytes: embedded artwork first, then raw file.
    var data: Data?
    if let art = extractArtworkFile(uri: sourceUri), let artUri = art["uri"] as? String,
       let url = URL(string: artUri), let d = try? Data(contentsOf: url) {
      data = d
    } else if let url = URL(string: sourceUri), let d = try? Data(contentsOf: url) {
      data = d
    } else {
      let path = sourceUri.hasPrefix("file://") ? URL(string: sourceUri)?.path ?? sourceUri : sourceUri
      data = try? Data(contentsOf: URL(fileURLWithPath: path))
    }
    guard let bytes = data, !bytes.isEmpty, let image = UIImage(data: bytes) else { return nil }
    let sourceMime = artworkMime(data: bytes)
    let targetMime: String = {
      if preserve && (format == "original" || format.isEmpty) { return sourceMime }
      switch format {
      case "original": return sourceMime
      case "jpeg", "jpg": return "image/jpeg"
      case "png": return "image/png"
      default: return sourceMime
      }
    }()
    let outData: Data? = targetMime == "image/png" ? image.pngData() : image.jpegData(compressionQuality: CGFloat(quality))
    guard let final = outData else { return nil }
    let ext = targetMime == "image/png" ? "png" : "jpg"
    let outUrl: URL = {
      if let d = destUri, !d.isEmpty {
        let p = d.hasPrefix("file://") ? URL(string: d)?.path ?? d : d
        return URL(fileURLWithPath: p)
      }
      return FileManager.default.temporaryDirectory.appendingPathComponent("saved_\(abs(sourceUri.hashValue)).\(ext)")
    }()
    do { try final.write(to: outUrl) } catch { return nil }
    var w: Int?; var h: Int?
    if let src = CGImageSourceCreateWithData(final as CFData, nil),
       let props = CGImageSourceCopyPropertiesAtIndex(src, 0, nil) as? [String: Any] {
      w = props[kCGImagePropertyPixelWidth as String] as? Int
      h = props[kCGImagePropertyPixelHeight as String] as? Int
    }
    var saved: [String: Any?] = ["uri": outUrl.absoluteString, "mimeType": targetMime, "width": w, "height": h, "size": final.count]
    return compact(saved)
  }

  private func resolveAudioFileURL(_ uri: String) -> URL? {
    if let url = URL(string: uri), url.scheme == "file" { return url }
    if URL(string: uri)?.scheme == nil {
      let fileURL = URL(fileURLWithPath: uri)
      if FileManager.default.fileExists(atPath: fileURL.path) { return fileURL }
      if let assetURL = syncAssetFileURL(forAssetId: uri) { return assetURL }
    }
    return URL(string: uri)
  }

  private func syncAssetFileURL(forAssetId id: String) -> URL? {
    let result = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil)
    guard let asset = result.firstObject else { return nil }
    return syncAssetFileURL(for: asset)
  }

  private func readReplayGainHead(url: URL) -> [String: Any?] {
    guard let handle = try? FileHandle(forReadingFrom: url) else { return emptyGain() }
    // FileHandle.read(upToCount:) / close() need iOS 13.4+; fall back on 13.0–13.3.
    defer {
      if #available(iOS 13.4, *) { try? handle.close() }
      else { handle.closeFile() }
    }
    let data: Data
    if #available(iOS 13.4, *) {
      data = (try? handle.read(upToCount: 2 * 1024 * 1024)) ?? Data()
    } else {
      data = (try? handle.readData(ofLength: 2 * 1024 * 1024)) ?? Data()
    }
    guard !data.isEmpty, let head = String(data: data, encoding: .isoLatin1) else { return emptyGain() }
    var r128Track: Double?; var r128Album: Double?; var rgTrack: Double?; var rgAlbum: Double?
    var trackPeak: Double?; var albumPeak: Double?
    let pattern = "(?i)(R128_TRACK_GAIN|R128_ALBUM_GAIN|REPLAYGAIN_TRACK_GAIN|REPLAYGAIN_ALBUM_GAIN|REPLAYGAIN_TRACK_PEAK|REPLAYGAIN_ALBUM_PEAK)\\s*=\\s*([^\\r\\n;]{1,32})"
    if let regex = try? NSRegularExpression(pattern: pattern) {
      let range = NSRange(head.startIndex..., in: head)
      for match in regex.matches(in: head, range: range) {
        guard match.numberOfRanges == 3,
              let kRange = Range(match.range(at: 1), in: head),
              let vRange = Range(match.range(at: 2), in: head) else { continue }
        let key = String(head[kRange]).uppercased()
        let raw = String(head[vRange]).trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "\"'"))
        switch key {
        case "R128_TRACK_GAIN": if r128Track == nil { r128Track = r128ToDb(raw) }
        case "R128_ALBUM_GAIN": if r128Album == nil { r128Album = r128ToDb(raw) }
        case "REPLAYGAIN_TRACK_GAIN": if rgTrack == nil { rgTrack = gainToDb(raw) }
        case "REPLAYGAIN_ALBUM_GAIN": if rgAlbum == nil { rgAlbum = gainToDb(raw) }
        case "REPLAYGAIN_TRACK_PEAK": if trackPeak == nil { trackPeak = Double(raw) }
        case "REPLAYGAIN_ALBUM_PEAK": if albumPeak == nil { albumPeak = Double(raw) }
        default: break
        }
      }
    }
    let hasR128 = r128Track != nil || r128Album != nil
    let hasRg = rgTrack != nil || rgAlbum != nil || trackPeak != nil || albumPeak != nil
    if !hasR128 && !hasRg { return emptyGain() }
    return compact([
      "trackGain": (r128Track ?? rgTrack),
      "albumGain": (r128Album ?? rgAlbum),
      "trackPeak": trackPeak, "albumPeak": albumPeak,
      "source": hasR128 ? "r128" : "replaygain",
    ])
  }

  private func r128ToDb(_ raw: String) -> Double? {
    let cleaned = raw.replacingOccurrences(of: "(?i)\\s*dB\\s*$", with: "", options: .regularExpression).trimmingCharacters(in: .whitespaces)
    guard let v = Double(cleaned) else { return nil }
    if !cleaned.contains(".") && abs(v) >= 64 { return v / 256.0 }
    return v
  }

  private func gainToDb(_ raw: String) -> Double? {
    let cleaned = raw.replacingOccurrences(of: "(?i)\\s*dB\\s*$", with: "", options: .regularExpression).trimmingCharacters(in: .whitespaces)
    return Double(cleaned)
  }

  private func emptyGain() -> [String: Any?] {
    ["trackGain": nil, "albumGain": nil, "trackPeak": nil, "albumPeak": nil, "source": nil]
  }

  private func artworkMime(data: Data) -> String {
    if data.count >= 3 && data[0] == 0xFF && data[1] == 0xD8 { return "image/jpeg" }
    if data.count >= 8 && data[0] == 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47 { return "image/png" }
    if data.count >= 12 && data[0] == 0x52 && data[8] == 0x57 { return "image/webp" }
    if data.count >= 6 && data[0] == 0x47 && data[1] == 0x49 && data[2] == 0x46 { return "image/gif" }
    return "image/jpeg"
  }

  private func compact(_ dict: [String: Any?]) -> [String: Any?] {
    var result: [String: Any?] = [:]
    for (key, value) in dict where value != nil {
      result[key] = value
    }
    return result
  }

  private func mimeTypeFromExtension(_ ext: String) -> String {
    switch ext.lowercased() {
    case "mp3": return "audio/mpeg"
    case "m4a": return "audio/mp4"
    case "wav": return "audio/wav"
    case "aac": return "audio/aac"
    case "flac": return "audio/flac"
    case "caf": return "audio/x-caf"
    case "aiff", "aif": return "audio/aiff"
    case "mp4", "m4v": return "video/mp4"
    case "mov": return "video/quicktime"
    case "jpg", "jpeg": return "image/jpeg"
    case "png": return "image/png"
    case "heic": return "image/heic"
    case "gif": return "image/gif"
    case "tiff", "tif": return "image/tiff"
    case "pdf": return "application/pdf"
    case "txt", "md", "csv", "json": return "text/plain"
    default: return ""
    }
  }

  private func mediaTypeFromExtension(_ ext: String) -> String {
    switch ext.lowercased() {
    case "mp3", "m4a", "wav", "aac", "flac", "caf", "aiff", "aif", "opus": return "audio"
    case "mp4", "m4v", "mov", "avi", "wmv": return "video"
    case "jpg", "jpeg", "png", "heic", "gif", "tiff", "tif", "bmp", "webp": return "image"
    default: return "document"
    }
  }

  private func imageFormat(from type: String?) -> String? {
    guard let type = type else { return nil }
    switch type {
    case "public.jpeg": return "jpeg"
    case "public.png": return "png"
    case "public.heic": return "heic"
    case "com.compuserve.gif": return "gif"
    case "public.tiff": return "tiff"
    case "public.bmp": return "bmp"
    default: return nil
    }
  }

  private func containerFormat(from mimeType: String, pathExtension: String) -> String? {
    let ext = pathExtension.lowercased()
    if !ext.isEmpty { return ext }
    let lower = mimeType.lowercased()
    if lower.contains("mp4") { return "mp4" }
    if lower.contains("quicktime") || lower.contains("mov") { return "mov" }
    if lower.contains("m4a") { return "m4a" }
    return nil
  }

  private func normalizeAudioCodec(formatName: String) -> String {
    let lower = formatName.lowercased()
    if lower.contains("mp3") || lower.contains("mpeg") { return "mp3" }
    if lower.contains("aac") { return "aac" }
    if lower.contains("opus") { return "opus" }
    if lower.contains("flac") { return "flac" }
    if lower.contains("pcm") || lower.contains("lpcm") { return "pcm" }
    if lower.contains("apple lossless") || lower.contains("alac") { return "alac" }
    return lower
  }

  private func normalizeVideoCodec(formatName: String) -> String {
    let lower = formatName.lowercased()
    if lower.contains("h.264") || lower.contains("avc") || lower.contains("h264") { return "h264" }
    if lower.contains("hevc") || lower.contains("h.265") || lower.contains("h265") { return "hevc" }
    if lower.contains("vp9") { return "vp9" }
    if lower.contains("av1") { return "av1" }
    return lower
  }

  private func channelLayout(for channels: Int) -> String? {
    switch channels {
    case 1: return "mono"
    case 2: return "stereo"
    case 6: return "5.1"
    case 8: return "7.1"
    default: return nil
    }
  }

  private func rotationDegrees(from transform: CGAffineTransform) -> Int {
    let radians = atan2(transform.b, transform.a)
    var degrees = Int((radians * 180 / .pi).rounded())
    degrees = ((degrees % 360) + 360) % 360
    return degrees
  }

  private func exifDateTime(_ value: String?) -> Int? {
    guard let value = value, !value.isEmpty else { return nil }
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyy:MM:dd HH:mm:ss"
    guard let date = formatter.date(from: value) else { return nil }
    return Int(date.timeIntervalSince1970 * 1000)
  }

  private func exposureProgramString(_ v: Int) -> String {
    switch v {
    case 0: return "Undefined"
    case 1: return "Manual"
    case 2: return "Normal"
    case 3: return "Aperture"
    case 4: return "Shutter"
    case 5: return "Creative"
    case 6: return "Action"
    case 7: return "Portrait"
    case 8: return "Landscape"
    default: return "Undefined"
    }
  }

  private func meteringModeString(_ v: Int) -> String {
    switch v {
    case 0: return "Unknown"
    case 1: return "Average"
    case 2: return "Center"
    case 3: return "Spot"
    case 4: return "MultiSpot"
    case 5: return "Pattern"
    case 6: return "Partial"
    default: return "Unknown"
    }
  }

  private func flashModeString(_ v: Int) -> String {
    let mode = (v >> 3) & 0x3
    switch mode {
    case 1: return "CompulsoryFire"
    case 2: return "CompulsorySuppression"
    case 3: return "Auto"
    default: return "Unknown"
    }
  }

  private func sceneCaptureTypeString(_ v: Int) -> String {
    switch v {
    case 0: return "Standard"
    case 1: return "Landscape"
    case 2: return "Portrait"
    case 3: return "Night"
    default: return "Standard"
    }
  }

  private func contrastString(_ v: Int) -> String {
    switch v {
    case 0: return "Normal"
    case 1: return "Soft"
    case 2: return "Hard"
    default: return "Normal"
    }
  }

  private func saturationString(_ v: Int) -> String {
    switch v {
    case 0: return "Normal"
    case 1: return "Low"
    case 2: return "High"
    default: return "Normal"
    }
  }

  private func sharpnessString(_ v: Int) -> String {
    switch v {
    case 0: return "Normal"
    case 1: return "Soft"
    case 2: return "Hard"
    default: return "Normal"
    }
  }

  private func colorSpaceString(_ v: Int) -> String {
    switch v {
    case 1: return "sRGB"
    case 2: return "Adobe RGB"
    case 0: return "Uncalibrated"
    default: return "Uncalibrated"
    }
  }
}

// MARK: - Date Extension

private extension Date {
  var year: Int {
    return Calendar.current.component(.year, from: self)
  }
}
