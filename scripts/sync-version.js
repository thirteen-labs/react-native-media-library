#!/usr/bin/env node
// Syncs version from package.json into Android build.gradle.kts and podspec is dynamic.
// Usage: npm run sync-version  (also runs on preversion)
const fs = require('fs');
const path = require('path');

const pkgPath = path.join(__dirname, '..', 'package.json');
const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf8'));
const version = pkg.version;

const gradlePath = path.join(__dirname, '..', 'android', 'build.gradle.kts');
if (fs.existsSync(gradlePath)) {
  let content = fs.readFileSync(gradlePath, 'utf8');
  // build.gradle.kts now reads package.json dynamically, but keep fallbacks in sync
  let updated = false;
  if (content.includes('?: "')) {
    const newFallback = `?: "${version}"`;
    const next = content.replace(/\?: "[^"]+"/g, newFallback);
    if (next !== content) { content = next; updated = true; }
  }
  // also sync the `else "x.y.z"` fallback
  if (content.includes('} else "')) {
    const next2 = content.replace(/} else "[^"]+"/g, `} else "${version}"`);
    if (next2 !== content) { content = next2; updated = true; }
  }
  if (updated) {
    fs.writeFileSync(gradlePath, content);
    console.log(`[sync-version] android/build.gradle.kts fallback -> ${version}`);
  }
}
console.log(`[sync-version] package.json version is ${version} (android reads dynamically, ios podspec reads package.json)`);
