require 'json'

package = JSON.parse(File.read(File.join(__dir__, '..', 'package.json')))

Pod::Spec.new do |s|
  s.name           = 'RNMediaStore'
  s.version        = package['version']
  s.summary        = package['description']
  s.homepage       = package['repository']['url']
  s.license        = package['license']
  s.author         = package['author']
  s.source         = { git: package['repository']['url'] }
  s.platform       = :ios, '13.0'
  s.swift_version  = '5.0'

  s.dependency 'React-Core'

  s.source_files = '*.swift', '*.m'
  s.frameworks   = 'Photos', 'AVFoundation', 'UIKit', 'ImageIO', 'PDFKit', 'CoreMedia'
end
