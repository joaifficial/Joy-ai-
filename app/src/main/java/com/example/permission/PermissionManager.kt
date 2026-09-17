package com.example.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class PermissionManager(private val context: Context) {

    companion object {
        val REQUIRED_PERMISSIONS = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.CALL_PHONE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

        const val PERMISSION_REQUIRED_VOICE_MESSAGE =
            "Bhai, is kaam ke liye mujhe permission chahiye. Permission on kar do, phir main kar deta hoon."
    }

    fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasRecordAudioPermission(): Boolean =
        isPermissionGranted(Manifest.permission.RECORD_AUDIO)

    fun hasContactsPermission(): Boolean =
        isPermissionGranted(Manifest.permission.READ_CONTACTS)

    fun hasCallPhonePermission(): Boolean =
        isPermissionGranted(Manifest.permission.CALL_PHONE)

    fun hasNotificationPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }

    fun areAllPermissionsGranted(): Boolean {
        return REQUIRED_PERMISSIONS.all { isPermissionGranted(it) }
    }

    fun getMissingPermissions(): List<String> {
        return REQUIRED_PERMISSIONS.filter { !isPermissionGranted(it) }
    }

    fun getMissingPermissionVoiceResponse(toolName: String): String {
        return when (toolName) {
            "searchAndCallContact" ->
                "Bhai, call karne ke liye Contacts aur Phone call permission chahiye. Permission on kar do, phir main call laga deta hoon."
            "sendWhatsAppMessage" ->
                "Bhai, contact dhundne ke liye Contacts permission chahiye. Permission on kar do, phir message bhej deta hoon."
            "openApp" ->
                PERMISSION_REQUIRED_VOICE_MESSAGE
            "sendGmail" ->
                PERMISSION_REQUIRED_VOICE_MESSAGE
            else ->
                PERMISSION_REQUIRED_VOICE_MESSAGE
        }
    }
}
