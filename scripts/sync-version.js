#!/usr/bin/env node
// Syncs version from package.json into Android build.gradle.kts, the npm
// lockfile, and podspec (which is dynamic).
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

// Keep package-lock.json's own version fields in step with package.json.
// npm only rewrites these on install/publish, so a manual `npm version` plus a
// fresh `npm i` could otherwise leave the lockfile advertising a stale version
// (this is what drifted the lockfile to 3.5.3 while package.json read 3.5.4).
const lockPath = path.join(__dirname, '..', 'package-lock.json');
if (fs.existsSync(lockPath)) {
  const lock = JSON.parse(fs.readFileSync(lockPath, 'utf8'));
  const changed = [];
  if (lock.version !== version) { changed.push(`version ${lock.version} -> ${version}`); lock.version = version; }
  const root = lock.packages && lock.packages[''];
  if (root && root.version !== version) {
    changed.push(`packages[""].version ${root.version} -> ${version}`);
    root.version = version;
  }
  if (changed.length > 0) {
    fs.writeFileSync(lockPath, JSON.stringify(lock, null, 2) + '\n');
    console.log(`[sync-version] package-lock.json ${changed.join(', ')}`);
  }
}

console.log(`[sync-version] package.json version is ${version} (android reads dynamically, ios podspec reads package.json)`);
