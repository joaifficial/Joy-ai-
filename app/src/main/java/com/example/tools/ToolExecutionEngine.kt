package com.example.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import com.example.permission.PermissionManager

data class ToolExecutionResult(
    val success: Boolean,
    val message: String,
    val voiceConfirmation: String,
    val requiresPermission: Boolean = false,
    val missingPermission: String? = null
)

class ToolExecutionEngine(
    private val context: Context,
    private val permissionManager: PermissionManager
) {
    companion object {
        private const val TAG = "ToolExecutionEngine"

        private val COMMON_PACKAGE_MAP = mapOf(
            "youtube" to listOf("com.google.android.youtube"),
            "instagram" to listOf("com.instagram.android"),
            "calculator" to listOf(
                "com.google.android.calculator",
                "com.android.calculator2",
                "com.sec.android.app.popupcalculator",
                "com.miui.calculator",
                "com.coloros.calculator",
                "com.oneplus.calculator"
            ),
            "calc" to listOf("com.google.android.calculator", "com.android.calculator2"),
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "gmail" to listOf("com.google.android.gm"),
            "camera" to listOf("com.google.android.GoogleCamera", "com.android.camera", "com.android.camera2"),
            "maps" to listOf("com.google.android.apps.maps"),
            "chrome" to listOf("com.android.chrome"),
            "settings" to listOf("com.android.settings"),
            "playstore" to listOf("com.android.vending"),
            "phone" to listOf("com.google.android.dialer", "com.android.dialer"),
            "spotify" to listOf("com.spotify.music"),
            "clock" to listOf("com.google.android.deskclock", "com.android.deskclock")
        )
    }

    /**
     * Launch any app by package name or alias (e.g. YouTube, Instagram, Calculator).
     */
    fun openApp(targetApp: String): ToolExecutionResult {
        Log.d(TAG, "Executing openApp: $targetApp")
        val cleanName = targetApp.trim().lowercase()

        // 1. Check mapped aliases
        val candidatePackages = COMMON_PACKAGE_MAP[cleanName] ?: listOf(targetApp.trim())

        val packageManager = context.packageManager

        // Try direct launch for candidate packages
        for (pkg in candidatePackages) {
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                val friendlyName = targetApp.replaceFirstChar { it.uppercase() }
                return ToolExecutionResult(
                    success = true,
                    message = "Opened $friendlyName ($pkg)",
                    voiceConfirmation = "Done bhai, $friendlyName khol raha hoon."
                )
            }
        }

        // 2. Search installed applications by display label or package contains
        try {
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in installedApps) {
                val label = packageManager.getApplicationLabel(appInfo).toString().lowercase()
                if (label.contains(cleanName) || appInfo.packageName.contains(cleanName)) {
                    val launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        val appLabel = packageManager.getApplicationLabel(appInfo).toString()
                        return ToolExecutionResult(
                            success = true,
                            message = "Opened $appLabel",
                            voiceConfirmation = "Bilkul bhai, $appLabel khol raha hoon."
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying packages", e)
        }

        // 3. Fallback: try calculator category if requested
        if (cleanName.contains("calc")) {
            val calcIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALCULATOR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (calcIntent.resolveActivity(packageManager) != null) {
                context.startActivity(calcIntent)
                return ToolExecutionResult(
                    success = true,
                    message = "Opened Calculator",
                    voiceConfirmation = "Done bhai, calculator khol raha hoon."
                )
            }
        }

        return ToolExecutionResult(
            success = false,
            message = "App '$targetApp' not found on this device",
            voiceConfirmation = "Bhai, $targetApp phone mein nahi mil raha. Ek baar check kar lo."
        )
    }

    /**
     * Query Contacts Provider and trigger ACTION_CALL (or fallback to DIAL).
     */
    fun searchAndCallContact(contactName: String): ToolExecutionResult {
        Log.d(TAG, "Executing searchAndCallContact: $contactName")

        if (!permissionManager.hasContactsPermission()) {
            return ToolExecutionResult(
                success = false,
                message = "Missing READ_CONTACTS permission",
                voiceConfirmation = "Bhai, is kaam ke liye mujhe Contacts permission chahiye. Permission on kar do, phir main kar deta hoon.",
                requiresPermission = true,
                missingPermission = Manifest.permission.READ_CONTACTS
            )
        }

        val trimmedName = contactName.trim()
        val (foundName, phoneNumber) = findContactPhoneNumber(trimmedName)

        if (phoneNumber == null) {
            return ToolExecutionResult(
                success = false,
                message = "No contact found for '$contactName'",
                voiceConfirmation = "Bhai, contacts mein $contactName ka number nahi mila."
            )
        }

        // Check CALL_PHONE permission
        if (permissionManager.hasCallPhonePermission()) {
            try {
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:${Uri.encode(phoneNumber)}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)
                val displayName = foundName ?: contactName
                return ToolExecutionResult(
                    success = true,
                    message = "Calling $displayName ($phoneNumber)",
                    voiceConfirmation = "Haan bhai, $displayName ko call karta hoon."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error initiating ACTION_CALL", e)
            }
        }

        // Fallback to ACTION_DIAL if CALL_PHONE is not granted or failed
        try {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${Uri.encode(phoneNumber)}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            val displayName = foundName ?: contactName
            return ToolExecutionResult(
                success = true,
                message = "Dialing $displayName ($phoneNumber)",
                voiceConfirmation = "Bhai, dialer mein number laga diya hai, call dial kar lo."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating ACTION_DIAL", e)
            return ToolExecutionResult(
                success = false,
                message = "Call failed: ${e.message}",
                voiceConfirmation = "Bhai, ye kaam nahi ho paya. Ek baar dobara try karte hain."
            )
        }
    }

    /**
     * Send WhatsApp message: locate contact number or deep link into WhatsApp.
     */
    fun sendWhatsAppMessage(contactName: String, message: String): ToolExecutionResult {
        Log.d(TAG, "Executing sendWhatsAppMessage to: $contactName, msg: $message")

        var targetPhone: String? = null

        // Check if contactName already looks like a phone number
        val digitsOnly = contactName.filter { it.isDigit() }
        if (digitsOnly.length >= 7) {
            targetPhone = digitsOnly
        } else if (permissionManager.hasContactsPermission()) {
            val (_, phone) = findContactPhoneNumber(contactName)
            if (phone != null) {
                targetPhone = phone.filter { it.isDigit() }
            }
        }

        try {
            if (!targetPhone.isNullOrBlank()) {
                // Ensure international format if possible (e.g. if 10 digits in India, prefix 91)
                val formattedPhone = if (targetPhone.length == 10) "91$targetPhone" else targetPhone
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$formattedPhone&text=${Uri.encode(message)}")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return ToolExecutionResult(
                        success = true,
                        message = "WhatsApp message prepared for $contactName",
                        voiceConfirmation = "Haan bhai, WhatsApp par $contactName ke liye message ready kar diya hai."
                    )
                }
            }

            // General WhatsApp share intent if phone couldn't be resolved or direct chat URI not handled
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (shareIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(shareIntent)
                return ToolExecutionResult(
                    success = true,
                    message = "Opening WhatsApp to send message",
                    voiceConfirmation = "Haan bhai, WhatsApp khol raha hoon message bhejne ke liye."
                )
            } else {
                return ToolExecutionResult(
                    success = false,
                    message = "WhatsApp is not installed on this device",
                    voiceConfirmation = "Bhai, WhatsApp phone mein install nahi hai."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending WhatsApp message", e)
            return ToolExecutionResult(
                success = false,
                message = "WhatsApp action failed: ${e.message}",
                voiceConfirmation = "Bhai, WhatsApp message nahi bhej paya. Ek baar dobara try karte hain."
            )
        }
    }

    /**
     * Send Gmail: compose an email using Intent.
     */
    fun sendGmail(recipientEmail: String, subject: String, body: String): ToolExecutionResult {
        Log.d(TAG, "Executing sendGmail to: $recipientEmail, subject: $subject")
        try {
            val mailtoUri = Uri.parse("mailto:${Uri.encode(recipientEmail)}")
            val emailIntent = Intent(Intent.ACTION_SENDTO, mailtoUri).apply {
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Prefer Gmail package if available
            val gmailIntent = Intent(emailIntent).apply {
                setPackage("com.google.android.gm")
            }
            if (gmailIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(gmailIntent)
                return ToolExecutionResult(
                    success = true,
                    message = "Gmail composed for $recipientEmail",
                    voiceConfirmation = "Bilkul bhai, Gmail mein mail draft kar diya hai."
                )
            }

            // Fallback to any email handler
            if (emailIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(emailIntent)
                return ToolExecutionResult(
                    success = true,
                    message = "Email client opened for $recipientEmail",
                    voiceConfirmation = "Done bhai, email draft kar diya hai."
                )
            }

            return ToolExecutionResult(
                success = false,
                message = "No email app found on this device",
                voiceConfirmation = "Bhai, email bhejne ke liye koi email app nahi mila."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error opening email", e)
            return ToolExecutionResult(
                success = false,
                message = "Failed to open Gmail: ${e.message}",
                voiceConfirmation = "Bhai, ye kaam nahi ho paya. Ek baar dobara try karte hain."
            )
        }
    }

    private fun findContactPhoneNumber(searchQuery: String): Pair<String?, String?> {
        var contactName: String? = null
        var phoneNumber: String? = null

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$searchQuery%")

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (nameIndex >= 0 && numberIndex >= 0) {
                    contactName = cursor.getString(nameIndex)
                    phoneNumber = cursor.getString(numberIndex)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading contacts", e)
        } finally {
            cursor?.close()
        }

        return Pair(contactName, phoneNumber)
    }
}
