# MediaStore — keep rules for release (R8)
# Media3 Inspector is accessed via reflection in Media3InspectorExtractor.kt so we keep the APIs
-keep class androidx.media3.inspector.** { *; }
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.extractor.** { *; }
# ExifInterface tags are read via string keys
-keep class androidx.exifinterface.media.ExifInterface { *; }
# Coroutines + Guava ListenableFuture bridge
-keep class kotlinx.coroutines.guava.** { *; }
-keep class com.google.common.util.concurrent.** { *; }
# Do not obfuscate model maps (toMap() keys are bridge protocol)
-keep class com.obsidian_north.mediastore.models.** { *; }
-keep class com.obsidian_north.mediastore.metadata.** { *; }
