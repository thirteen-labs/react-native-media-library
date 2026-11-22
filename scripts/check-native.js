#!/usr/bin/env node
/**
 * Static parity checks between the JS native spec and the platform bridges.
 * This does not replace a real iOS/Android build, but it catches the class of
 * regressions that CI previously missed (duplicate Swift methods, missing
 * bridge declarations, methods present in the TS spec but not on a platform).
 */
const fs = require("fs");
const path = require("path");

const root = path.join(__dirname, "..");
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");

const errors = [];
const warn = (msg) => errors.push(msg);

// --- iOS: Swift exports vs ObjC bridge declarations -----------------------
const swift = read("ios/MediaStoreModule.swift");
const bridge = read("ios/MediaStoreModule.m");

const swiftFns = [...swift.matchAll(/^\s+func\s+([a-zA-Z0-9_]+)\s*\(/gm)].map((m) => m[1]);
const duplicates = [...new Set(swiftFns.filter((n, i) => swiftFns.indexOf(n) !== i))];
if (duplicates.length) warn(`iOS: duplicate Swift methods: ${duplicates.join(", ")}`);

const swiftExported = swiftFns.filter(
  (n) => !["supportedEvents", "startObserving", "stopObserving", "requiresMainQueueSetup", "wrap", "failed"].includes(n)
);
const bridged = [...bridge.matchAll(/RCT_EXTERN_METHOD\(([a-zA-Z0-9_]+):/g)].map((m) => m[1]);

const missingBridge = swiftExported.filter((n) => !bridged.includes(n));
const staleBridge = bridged.filter((n) => !swiftExported.includes(n));
if (missingBridge.length) warn(`iOS: Swift methods missing from bridge: ${missingBridge.join(", ")}`);
if (staleBridge.length) warn(`iOS: bridge declares methods that do not exist in Swift: ${staleBridge.join(", ")}`);
if (!/RCT_EXTERN_REMAP_MODULE\(MediaStore,\s*MediaStoreModule/.test(bridge)) {
  warn("iOS: MediaStoreModule.m does not register the module as 'MediaStore'");
}
if (!/s\.source_files[^\n]*\.m/.test(read("ios/RNMediaStore.podspec"))) {
  warn("iOS: podspec does not compile the .m bridge file");
}

// --- Android: @ReactMethod names cover the TS spec ------------------------
const spec = read("src/MediaStoreModule.ts");
const specNames = [...spec.matchAll(/^\s{2}([a-zA-Z0-9_]+)\??\(/gm)].map((m) => m[1]);
const kotlin = read("android/src/main/java/com/obsidian_north/mediastore/MediaStoreModule.kt");
const kotlinMethods = [...kotlin.matchAll(/@ReactMethod\s+fun\s+([a-zA-Z0-9_]+)/g)].map((m) => m[1]);
// getLibrary is intentionally implemented in JS (src/index.ts) rather than natively.
const jsOnly = new Set(["getLibrary", "addListener", "removeListeners"]);
const missingAndroid = specNames.filter((n) => !jsOnly.has(n) && !kotlinMethods.includes(n));
if (missingAndroid.length) warn(`Android: spec methods with no @ReactMethod: ${missingAndroid.join(", ")}`);

if (errors.length) {
  console.error(errors.map((e) => `ERROR: ${e}`).join("\n"));
  process.exit(1);
}
console.log(
  `native parity OK: ${swiftExported.length} iOS methods, ${bridged.length} bridge declarations, ${kotlinMethods.length} Android @ReactMethod`
);
