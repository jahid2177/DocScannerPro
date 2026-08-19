package com.scanner.pro.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity

/**
 * Centralizes the runtime-permission checks/requests the scanner needs
 * (camera always; storage only pre-API 29 where scoped storage doesn't apply).
 *
 * Registers its ActivityResultLauncher through the [caller] (normally the
 * Fragment itself) rather than the host Activity: the Fragment's own
 * lifecycle resets on every new instance, so it's safe to register even when
 * the host Activity is already RESUMED (e.g. after Navigation-Component
 * navigation to a new fragment). Registering directly against the Activity
 * in that situation throws "LifecycleOwner is attempting to register while
 * current state is RESUMED".
 */
class PermissionManager(
    private val caller: ActivityResultCaller,
    private val activity: FragmentActivity
) {

    private var onResult: ((Boolean) -> Unit)? = null

    private val requestLauncher: ActivityResultLauncher<Array<String>> =
        caller.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val granted = results.values.all { it }
            onResult?.invoke(granted)
        }

    fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        return permissions.toTypedArray()
    }

    fun hasCameraPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun hasAllRequiredPermissions(context: Context): Boolean =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun request(onResult: (Boolean) -> Unit) {
        this.onResult = onResult
        requestLauncher.launch(requiredPermissions())
    }

    fun shouldShowRationale(): Boolean = requiredPermissions().any {
        activity.shouldShowRequestPermissionRationale(it)
    }

    companion object {
        fun fromFragment(fragment: Fragment): PermissionManager =
            PermissionManager(fragment, fragment.requireActivity())
    }
}
