package com.obsidian_north.mediastore

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.PermissionAwareActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class MediaStorePermissions(private val context: Context, private val reactContext: ReactApplicationContext? = null) {
  fun checkStatus(): Map<String, Any?> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      return mapOf(
        "granted" to (isPermissionGranted(Manifest.permission.READ_MEDIA_AUDIO) &&
          isPermissionGranted(Manifest.permission.READ_MEDIA_VIDEO) &&
          isPermissionGranted(Manifest.permission.READ_MEDIA_IMAGES)),
        "audio" to isPermissionGranted(Manifest.permission.READ_MEDIA_AUDIO),
        "video" to isPermissionGranted(Manifest.permission.READ_MEDIA_VIDEO),
        "images" to isPermissionGranted(Manifest.permission.READ_MEDIA_IMAGES)
      )
    } else {
      return mapOf(
        "granted" to isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE),
        "audio" to isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE),
        "video" to isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE),
        "images" to isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)
      )
    }
  }

  suspend fun request(): Map<String, Any?> {
    if (isAlreadyGranted()) return checkStatus()

    val activity = withContext(Dispatchers.Main) {
      reactContext?.currentActivity as? PermissionAwareActivity
    } ?: return checkStatus()

    // Wait for the user's answer instead of guessing with a fixed delay.
    // PermissionAwareActivity is what ReactActivity implements; the listener
    // is called by Activity.onRequestPermissionsResult.
    val answered = CompletableDeferred<Unit>()
    withContext(Dispatchers.Main) {
      activity.requestPermissions(getRequiredPermissions(), PERMISSION_REQUEST_CODE) { requestCode, _, _ ->
        if (requestCode == PERMISSION_REQUEST_CODE) answered.complete(Unit)
        true // remove the listener
      }
    }
    withTimeoutOrNull(PERMISSION_TIMEOUT_MS) { answered.await() }

    return checkStatus()
  }

  private fun isAlreadyGranted(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      return isPermissionGranted(Manifest.permission.READ_MEDIA_AUDIO) &&
        isPermissionGranted(Manifest.permission.READ_MEDIA_VIDEO) &&
        isPermissionGranted(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
      return isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
  }

  private fun getRequiredPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES)
    } else {
      arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
  }

  private fun isPermissionGranted(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
  }

  companion object {
    private const val PERMISSION_REQUEST_CODE = 1984
    private const val PERMISSION_TIMEOUT_MS = 60_000L
  }
}
