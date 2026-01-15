package com.example.camerax.server.permisson

import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import com.example.camerax.MainActivity

class PermissionHelper(
    private val activity: MainActivity,
    private val launcher: ActivityResultLauncher<Array<String>>
) {
    fun requestPermissions() {
        launcher.launch(MainActivity.REQUIRED_PERMISSIONS)
    }

    fun allPermissionsGranted(): Boolean = MainActivity.REQUIRED_PERMISSIONS.all{
        ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
    }

    fun handlePermissionResult(permissions: Map<String, Boolean>): Boolean {
        var permissionGranted = true
        permissions.entries.forEach {
            if (it.key in MainActivity.REQUIRED_PERMISSIONS && !it.value) {
                permissionGranted = false
            }
        }
        return permissionGranted
    }
}