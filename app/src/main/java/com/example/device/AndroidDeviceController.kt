package com.example.device

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log

class AndroidDeviceController(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    
    private var isTorchOn: Boolean = false

    /**
     * Toggles or sets the device flashlight/torch
     */
    fun setFlashlight(enable: Boolean): Pair<Boolean, String> {
        val manager = cameraManager ?: return Pair(false, "Camera hardware is not available on this device.")
        return try {
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                val chars = manager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId == null) {
                Pair(false, "No camera with flashlight found on this device.")
            } else {
                manager.setTorchMode(cameraId, enable)
                isTorchOn = enable
                val status = if (enable) "turned on" else "turned off"
                Pair(true, "Flashlight has been $status.")
            }
        } catch (e: Exception) {
            Log.e("SiriDevice", "Flashlight error", e)
            Pair(false, "Could not control flashlight: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    fun isFlashlightActive(): Boolean = isTorchOn

    /**
     * Launches an app by package name or common app alias
     */
    fun openApp(target: String): Pair<Boolean, String> {
        val packageMap = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "chrome" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "spotify" to "com.spotify.music",
            "camera" to "camera_internal",
            "settings" to "settings_internal",
            "gallery" to "gallery_internal",
            "calculator" to "calculator_internal",
            "clock" to "clock_internal"
        )

        val cleanTarget = target.lowercase().trim()
        val pkg = packageMap[cleanTarget] ?: cleanTarget

        return when (pkg) {
            "camera_internal" -> openCameraApp()
            "settings_internal" -> openSettings(Settings.ACTION_SETTINGS, "Settings")
            "gallery_internal" -> openGalleryApp()
            "calculator_internal" -> openCalculatorApp()
            "clock_internal" -> openClockApp()
            else -> {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    Pair(true, "Opening $target.")
                } else {
                    // Try web search fallback or store link
                    val searchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$target")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(searchIntent)
                        Pair(true, "App $target is not installed. Searching for $target online.")
                    } catch (e: Exception) {
                        Pair(false, "$target is not installed and browser is unavailable.")
                    }
                }
            }
        }
    }

    fun openCameraApp(): Pair<Boolean, String> {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Pair(true, "Opening Camera.")
        } catch (e: Exception) {
            Pair(false, "Camera app could not be opened.")
        }
    }

    fun openGalleryApp(): Pair<Boolean, String> {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = "image/*"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Pair(true, "Opening Gallery.")
        } catch (e: Exception) {
            Pair(false, "Gallery could not be opened.")
        }
    }

    fun openCalculatorApp(): Pair<Boolean, String> {
        val calcPackages = listOf(
            "com.google.android.calculator",
            "com.android.calculator2",
            "com.sec.android.app.popupcalculator"
        )
        for (pkg in calcPackages) {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return Pair(true, "Opening Calculator.")
            }
        }
        // Fallback to web calculator
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=calculator")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return Pair(true, "Opening online calculator.")
    }

    fun openClockApp(): Pair<Boolean, String> {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Pair(true, "Opening Clock and Alarms.")
        } catch (e: Exception) {
            Pair(false, "Clock application not found.")
        }
    }

    fun openSettings(action: String = Settings.ACTION_SETTINGS, label: String = "Settings"): Pair<Boolean, String> {
        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Pair(true, "Opening $label.")
        } catch (e: Exception) {
            Pair(false, "Could not open $label on this device.")
        }
    }

    /**
     * Sets a countdown timer via standard Android Clock
     */
    fun setTimer(durationSeconds: Int, message: String = "Siri Timer"): Pair<Boolean, String> {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, durationSeconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            val timeStr = if (minutes > 0) "$minutes minutes${if (seconds > 0) " $seconds seconds" else ""}" else "$seconds seconds"
            Pair(true, "Timer set for $timeStr.")
        } catch (e: Exception) {
            Pair(false, "Could not set timer: no clock app found.")
        }
    }

    /**
     * Sets an alarm for specified hour (0-23) and minute
     */
    fun setAlarm(hour: Int, minute: Int, message: String = "Siri Alarm"): Pair<Boolean, String> {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            val amPm = if (hour >= 12) "PM" else "AM"
            val displayHour = if (hour % 12 == 0) 12 else hour % 12
            val minStr = String.format("%02d", minute)
            Pair(true, "Alarm set for $displayHour:$minStr $amPm.")
        } catch (e: Exception) {
            Pair(false, "Could not set alarm: no clock app available.")
        }
    }

    /**
     * Queries battery percentage and charging state
     */
    fun getBatteryInfo(): String {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, ifilter)
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        return if (isCharging) {
            "Your battery is at $pct% and is currently charging."
        } else {
            "Your battery is at $pct%."
        }
    }

    /**
     * Queries connectivity state
     */
    fun getNetworkInfo(): String {
        val cm = connectivityManager ?: return "Network status is unavailable."
        val activeNetwork = cm.activeNetwork ?: return "You are currently offline."
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return "You are currently offline."

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Connected to Wi-Fi with active internet."
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Connected via mobile data."
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Connected via ethernet."
            else -> "Connected to network."
        }
    }

    /**
     * Adjusts media volume up, down, or mute
     */
    fun adjustVolume(direction: String): Pair<Boolean, String> {
        val am = audioManager ?: return Pair(false, "Audio manager is unavailable.")
        return try {
            when (direction.lowercase()) {
                "up", "increase" -> {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    Pair(true, "Volume raised.")
                }
                "down", "lower", "decrease" -> {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    Pair(true, "Volume lowered.")
                }
                "mute" -> {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                    Pair(true, "Media audio muted.")
                }
                "unmute" -> {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                    Pair(true, "Media audio unmuted.")
                }
                else -> Pair(false, "Unknown volume action.")
            }
        } catch (e: Exception) {
            Pair(false, "Unable to adjust volume.")
        }
    }

    /**
     * Performs a web search with the query
     */
    fun searchWeb(query: String): Pair<Boolean, String> {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Pair(true, "Searching the web for $query.")
        } catch (e: Exception) {
            // Browser fallback
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            Pair(true, "Searching Google for $query.")
        }
    }

    /**
     * Shares text through Android's standard share sheet
     */
    fun shareContent(text: String, title: String = "Share via Siri"): Pair<Boolean, String> {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(shareIntent)
            Pair(true, "Opening sharing options.")
        } catch (e: Exception) {
            Pair(false, "Could not open share sheet.")
        }
    }

    /**
     * Opens the dialer with a phone number for user-confirmed calling
     */
    fun dialPhoneNumber(phoneNumber: String): Pair<Boolean, String> {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$cleanNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Pair(true, "Opening phone dialer for $phoneNumber.")
        } catch (e: Exception) {
            Pair(false, "Phone dialer could not be opened.")
        }
    }

    /**
     * Opens the SMS composer with phone number and message
     */
    fun composeSms(phoneNumber: String, messageText: String): Pair<Boolean, String> {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        val uri = if (cleanNumber.isNotBlank()) Uri.parse("smsto:$cleanNumber") else Uri.parse("smsto:")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", messageText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Pair(true, "Opening SMS composer with prepared message.")
        } catch (e: Exception) {
            Pair(false, "SMS application not available.")
        }
    }

    /**
     * Opens turn-by-turn map navigation to a destination
     */
    fun openNavigation(destination: String): Pair<Boolean, String> {
        val encodedDest = Uri.encode(destination)
        val navIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$encodedDest")).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(navIntent)
            Pair(true, "Starting navigation to $destination.")
        } catch (e: Exception) {
            // General geo intent fallback
            val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encodedDest")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(geoIntent)
                Pair(true, "Opening maps for $destination.")
            } catch (ex: Exception) {
                Pair(false, "Maps application not found.")
            }
        }
    }

    /**
     * Creates a calendar event via standard calendar insert intent
     */
    fun createCalendarEvent(title: String, description: String = "", beginTimeMillis: Long = System.currentTimeMillis() + 3600000L): Pair<Boolean, String> {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = android.provider.CalendarContract.Events.CONTENT_URI
            putExtra(android.provider.CalendarContract.Events.TITLE, title)
            putExtra(android.provider.CalendarContract.Events.DESCRIPTION, description)
            putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTimeMillis)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Pair(true, "Opening Calendar to add event '$title'.")
        } catch (e: Exception) {
            Pair(false, "Calendar application not found.")
        }
    }

    /**
     * Copies text to system clipboard
     */
    fun copyToClipboard(label: String, text: String): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText(label, text)
            clipboard?.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Dispatches media key event (Play/Pause, Next, Previous)
     */
    fun sendMediaKeyEvent(keyCode: Int): Pair<Boolean, String> {
        return try {
            val am = audioManager ?: return Pair(false, "Audio manager unavailable")
            val down = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
            val up = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
            am.dispatchMediaKeyEvent(down)
            am.dispatchMediaKeyEvent(up)
            Pair(true, "Media playback controlled.")
        } catch (e: Exception) {
            Pair(false, "Could not control media session.")
        }
    }

    fun openAccessibilitySettings(): Pair<Boolean, String> =
        openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS, "Accessibility Settings")

    fun openNotificationSettings(): Pair<Boolean, String> =
        openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, "Notification Settings")

    fun openWifiSettings(): Pair<Boolean, String> =
        openSettings(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi Settings")

    fun openBluetoothSettings(): Pair<Boolean, String> =
        openSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth Settings")

    fun openDisplaySettings(): Pair<Boolean, String> =
        openSettings(Settings.ACTION_DISPLAY_SETTINGS, "Display Settings")

    fun openSoundSettings(): Pair<Boolean, String> =
        openSettings(Settings.ACTION_SOUND_SETTINGS, "Sound Settings")
}
