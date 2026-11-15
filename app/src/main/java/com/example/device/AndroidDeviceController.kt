package com.example.device

import android.Manifest
import android.annotation.SuppressLint
import android.app.SearchManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import java.util.Locale

import android.content.ComponentName

data class InstalledAppInfo(
    val label: String,
    val packageName: String,
    val activityClassName: String? = null
)

sealed class AppLaunchOutcome {
    data class Launched(val appLabel: String, val packageName: String) : AppLaunchOutcome()
    data class OpenedUrl(val url: String) : AppLaunchOutcome()
    data class Ambiguous(val requestedName: String, val candidates: List<InstalledAppInfo>) : AppLaunchOutcome()
    data class NotInstalled(val requestedName: String) : AppLaunchOutcome()
    data class Failed(val reason: String) : AppLaunchOutcome()
}

sealed class InAppSearchOutcome {
    data class SearchedInApp(val appLabel: String, val query: String) : InAppSearchOutcome()
    data class OpenedDestination(val appLabel: String, val query: String, val honestReason: String) : InAppSearchOutcome()
    data class OpenedWebsite(val appLabel: String, val url: String, val usedDefaultBrowser: Boolean = false) : InAppSearchOutcome()
    data class AppNotInstalled(val appLabel: String, val query: String) : InAppSearchOutcome()
    data class Failed(val reason: String) : InAppSearchOutcome()
}

sealed class SystemControlOutcome {
    data class Applied(val message: String) : SystemControlOutcome()
    data class OpenedSettingsFallback(val settingsLabel: String, val reason: String) : SystemControlOutcome()
    data class Failed(val reason: String) : SystemControlOutcome()
}

private data class KnownAppSpec(
    val key: String,
    val displayName: String,
    val aliases: List<String>,
    val packages: List<String>,
    val systemLauncherFallback: (() -> Pair<Boolean, String>)? = null
)

class AndroidDeviceController(
    private val context: Context,
    private val canWriteSystemSettings: (() -> Boolean)? = null
) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private var isTorchOn: Boolean = false

    private val knownApps: List<KnownAppSpec> by lazy {
        listOf(
            KnownAppSpec(
                key = "whatsapp",
                displayName = "WhatsApp",
                aliases = listOf(
                    "whatsapp", "whats app", "what's app", "what's up", "whats up",
                    "watsapp", "watsup", "whatsap", "whatsapp messenger", "wa",
                    "व्हाट्सएप", "व्हाट्सऐप", "वाट्सएप", "वाट्सअप", "व्हाट्सअप", "वटसप"
                ),
                packages = listOf("com.whatsapp", "com.whatsapp.w4b")
            ),
            KnownAppSpec(
                key = "facebook",
                displayName = "Facebook",
                aliases = listOf(
                    "facebook", "face book", "fb", "facebook lite", "fb lite",
                    "फेसबुक", "फेस बुक", "एफबी"
                ),
                packages = listOf("com.facebook.katana", "com.facebook.lite")
            ),
            KnownAppSpec(
                key = "instagram",
                displayName = "Instagram",
                aliases = listOf(
                    "instagram", "insta", "insta gram", "ig", "instagram lite",
                    "इंस्टाग्राम", "इंस्टा", "इंस्टाग्राम ऐप"
                ),
                packages = listOf("com.instagram.android", "com.instagram.lite")
            ),
            KnownAppSpec(
                key = "youtube_music",
                displayName = "YouTube Music",
                aliases = listOf("youtube music", "yt music", "यूट्यूब म्यूजिक"),
                packages = listOf("com.google.android.apps.youtube.music")
            ),
            KnownAppSpec(
                key = "youtube",
                displayName = "YouTube",
                aliases = listOf(
                    "youtube", "yt", "you tube", "u tube", "utube",
                    "यूट्यूब", "यू ट्यूब", "युट्युब", "यूटुब"
                ),
                packages = listOf("com.google.android.youtube")
            ),
            KnownAppSpec(
                key = "google",
                displayName = "Google",
                aliases = listOf("google", "google app", "google search", "गूगल", "गूगल ऐप"),
                packages = listOf("com.google.android.googlequicksearchbox")
            ),
            KnownAppSpec(
                key = "chrome",
                displayName = "Chrome",
                aliases = listOf(
                    "chrome", "google chrome", "chrome browser", "browser",
                    "क्रोम", "गूगल क्रोम", "क्रोम ब्राउज़र"
                ),
                packages = listOf("com.android.chrome", "com.chrome.beta", "com.chrome.dev")
            ),
            KnownAppSpec(
                key = "gmail",
                displayName = "Gmail",
                aliases = listOf("gmail", "google mail", "g mail", "email app", "जीमेल"),
                packages = listOf("com.google.android.gm")
            ),
            KnownAppSpec(
                key = "maps",
                displayName = "Google Maps",
                aliases = listOf("google maps", "maps", "map", "gmaps", "नक्शा", "मैप्स", "गूगल मैप्स"),
                packages = listOf("com.google.android.apps.maps")
            ),
            KnownAppSpec(
                key = "telegram",
                displayName = "Telegram",
                aliases = listOf("telegram", "tele gram", "टेलीग्राम", "टेलीग्राम ऐप"),
                packages = listOf("org.telegram.messenger", "org.telegram.plus", "org.thunderdog.challegram")
            ),
            KnownAppSpec(
                key = "spotify",
                displayName = "Spotify",
                aliases = listOf("spotify", "spotify music", "स्पॉटिफाई", "स्पॉटिफ़ाई"),
                packages = listOf("com.spotify.music", "com.spotify.lite")
            ),
            KnownAppSpec(
                key = "snapchat",
                displayName = "Snapchat",
                aliases = listOf("snapchat", "snap chat", "snap", "स्नैपचैट"),
                packages = listOf("com.snapchat.android")
            ),
            KnownAppSpec(
                key = "phone",
                displayName = "Phone",
                aliases = listOf("phone", "dialer", "phone app", "call app", "फोन", "फ़ोन", "डायलर"),
                packages = listOf(
                    "com.google.android.dialer",
                    "com.android.dialer",
                    "com.samsung.android.dialer",
                    "com.oneplus.dialer",
                    "com.android.contacts"
                ),
                systemLauncherFallback = { openPhoneApp() }
            ),
            KnownAppSpec(
                key = "messages",
                displayName = "Messages",
                aliases = listOf("messages", "message", "messaging", "sms", "text messages", "मैसेज", "मैसेजेस", "संदेश"),
                packages = listOf("com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging"),
                systemLauncherFallback = { openMessagesApp() }
            ),
            KnownAppSpec(
                key = "camera",
                displayName = "Camera",
                aliases = listOf("camera", "cam", "कैमरा"),
                packages = listOf("com.google.android.GoogleCamera", "com.android.camera2", "com.android.camera", "com.sec.android.app.camera"),
                systemLauncherFallback = { openCameraApp() }
            ),
            KnownAppSpec(
                key = "gallery",
                displayName = "Gallery",
                aliases = listOf("gallery", "photos", "google photos", "गैलरी", "फोटो", "तस्वीरें"),
                packages = listOf(
                    "com.google.android.apps.photos",
                    "com.android.gallery3d",
                    "com.sec.android.gallery3d",
                    "com.miui.gallery",
                    "com.oneplus.gallery",
                    "com.coloros.gallery3d"
                ),
                systemLauncherFallback = { openGalleryApp() }
            ),
            KnownAppSpec(
                key = "settings",
                displayName = "Settings",
                aliases = listOf("settings", "phone settings", "system settings", "device settings", "सेटिंग", "सेटिंग्स"),
                packages = listOf("com.android.settings"),
                systemLauncherFallback = { openSettings(Settings.ACTION_SETTINGS, "Settings") }
            ),
            KnownAppSpec(
                key = "playstore",
                displayName = "Play Store",
                aliases = listOf("play store", "playstore", "google play", "google play store", "प्ले स्टोर"),
                packages = listOf("com.android.vending")
            ),
            KnownAppSpec(
                key = "calculator",
                displayName = "Calculator",
                aliases = listOf("calculator", "calc", "कैल्कुलेटर", "कैलकुलेटर"),
                packages = listOf(
                    "com.google.android.calculator",
                    "com.android.calculator2",
                    "com.sec.android.app.popupcalculator",
                    "com.miui.calculator",
                    "com.oneplus.calculator"
                ),
                systemLauncherFallback = { openCalculatorApp() }
            ),
            KnownAppSpec(
                key = "clock",
                displayName = "Clock",
                aliases = listOf("clock", "alarm", "alarms", "घड़ी"),
                packages = listOf("com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage"),
                systemLauncherFallback = { openClockApp() }
            )
        )
    }

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
     * Discovers all user-launchable applications installed on the device using MATCH_ALL where available.
     */
    fun discoverInstalledApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            }
        } catch (_: Exception) {
            emptyList()
        }

        val seenPackages = linkedMapOf<String, InstalledAppInfo>()
        for (info in resolved) {
            val activityInfo = info.activityInfo ?: continue
            val pkg = activityInfo.packageName ?: continue
            if (pkg == context.packageName) continue
            val label = try {
                info.loadLabel(pm).toString().trim()
            } catch (_: Exception) {
                pkg
            }.ifBlank { pkg }
            if (!seenPackages.containsKey(pkg)) {
                seenPackages[pkg] = InstalledAppInfo(
                    label = label,
                    packageName = pkg,
                    activityClassName = activityInfo.name
                )
            }
        }
        return seenPackages.values.toList()
    }

    fun isPackageInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: Exception) {
            resolveLaunchIntent(packageName, null) != null
        }
    }

    fun launchInstalledApp(app: InstalledAppInfo): AppLaunchOutcome {
        return launchPackageInternal(app.packageName, app.label, app.activityClassName)
    }

    private fun resolveLaunchIntent(packageName: String, fallbackActivityName: String?): Intent? {
        val pm = context.packageManager
        val direct = try {
            pm.getLaunchIntentForPackage(packageName)
        } catch (_: Exception) {
            null
        }
        if (direct != null) return direct

        // Fallback 1: Explicit ACTION_MAIN + CATEGORY_LAUNCHER query for this package
        val targeted = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(packageName)
        }
        val matches = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(targeted, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(targeted, PackageManager.MATCH_DEFAULT_ONLY)
            }
        } catch (_: Exception) {
            emptyList()
        }
        val firstActivity = matches.firstOrNull()?.activityInfo
        if (firstActivity != null && !firstActivity.name.isNullOrBlank()) {
            return Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(packageName, firstActivity.name)
            }
        }

        // Fallback 2: Use previously discovered activity component if present
        if (!fallbackActivityName.isNullOrBlank()) {
            return Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(packageName, fallbackActivityName)
            }
        }
        return null
    }

    private fun launchPackageInternal(
        packageName: String,
        displayLabel: String,
        fallbackActivityName: String? = null
    ): AppLaunchOutcome {
        val launchIntent = resolveLaunchIntent(packageName, fallbackActivityName)
            ?: return AppLaunchOutcome.NotInstalled(displayLabel)

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

        // Verify resolved component actually exists on this device before declaring success
        val pm = context.packageManager
        val resolved = try {
            launchIntent.resolveActivity(pm)
        } catch (_: Exception) {
            null
        }
        if (resolved == null && launchIntent.component == null) {
            return AppLaunchOutcome.NotInstalled(displayLabel)
        }

        return try {
            context.startActivity(launchIntent)
            val resolvedLabel = try {
                val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(packageName, 0)
                }
                pm.getApplicationLabel(appInfo).toString().trim().ifBlank { displayLabel }
            } catch (_: Exception) {
                displayLabel
            }
            Log.d("SiriDevice", "Verified app launch: $resolvedLabel ($packageName)")
            AppLaunchOutcome.Launched(resolvedLabel, packageName)
        } catch (e: SecurityException) {
            Log.e("SiriDevice", "SecurityException launching $packageName", e)
            AppLaunchOutcome.Failed("$displayLabel blocked the launch request due to app permissions.")
        } catch (e: Exception) {
            Log.e("SiriDevice", "Launch failed for $packageName", e)
            AppLaunchOutcome.Failed("Could not open $displayLabel: ${e.localizedMessage ?: "launch error"}")
        }
    }

    /**
     * Smart app launcher supporting all 17+ popular apps, any dynamically installed app,
     * bilingual Hindi/Hinglish/English aliases, ASR phonetic typo resilience, disambiguation,
     * and verified launch confirmation.
     */
    fun openAppSmart(target: String): AppLaunchOutcome {
        val trimmedTarget = target.trim()
        if (trimmedTarget.isBlank()) {
            return AppLaunchOutcome.Failed("Please specify an app name.")
        }
        if (trimmedTarget.startsWith("http://", ignoreCase = true) || trimmedTarget.startsWith("https://", ignoreCase = true)) {
            val (ok, msg) = openUrl(trimmedTarget)
            return if (ok) AppLaunchOutcome.OpenedUrl(trimmedTarget) else AppLaunchOutcome.Failed(msg)
        }

        val cleanedQuery = normalizeAppQuery(trimmedTarget)
        val installedApps = discoverInstalledApps()

        // 1. Exact label match among dynamically installed launcher apps
        val exactLabelMatches = installedApps.filter { normalizeLabel(it.label) == cleanedQuery }
        if (exactLabelMatches.size == 1) {
            return launchInstalledApp(exactLabelMatches.first())
        }
        if (exactLabelMatches.size > 1) {
            return AppLaunchOutcome.Ambiguous(trimmedTarget, exactLabelMatches.take(4))
        }

        // 2. Curated popular apps & phonetic/Hindi/Hinglish aliases (checked early so "what's up" -> WhatsApp works!)
        val knownSpec = findKnownAppSpec(cleanedQuery)
        if (knownSpec != null) {
            for (pkg in knownSpec.packages) {
                val installedMatch = installedApps.firstOrNull { it.packageName == pkg }
                if (installedMatch != null) {
                    return launchInstalledApp(installedMatch)
                }
                if (resolveLaunchIntent(pkg, null) != null) {
                    return launchPackageInternal(pkg, knownSpec.displayName)
                }
            }
            val fuzzyForKnown = findMatchingInstalledApps(normalizeLabel(knownSpec.displayName), installedApps)
            if (fuzzyForKnown.size == 1) {
                return launchInstalledApp(fuzzyForKnown.first())
            }
            if (fuzzyForKnown.size > 1) {
                return AppLaunchOutcome.Ambiguous(trimmedTarget, fuzzyForKnown.take(4))
            }
            knownSpec.systemLauncherFallback?.let { fallback ->
                val (ok, msg) = fallback()
                if (ok) {
                    return AppLaunchOutcome.Launched(knownSpec.displayName, knownSpec.packages.firstOrNull() ?: knownSpec.key)
                }
                return AppLaunchOutcome.NotInstalled(knownSpec.displayName.ifBlank { msg })
            }
            return AppLaunchOutcome.NotInstalled(knownSpec.displayName)
        }

        // 3. Direct package name lookup
        if (trimmedTarget.contains('.') && !trimmedTarget.contains(' ')) {
            if (resolveLaunchIntent(trimmedTarget, null) != null) {
                return launchPackageInternal(trimmedTarget, trimmedTarget)
            }
        }

        // 4. Dynamic installed app fuzzy & typo-tolerant matching
        val fuzzyMatches = findMatchingInstalledApps(cleanedQuery, installedApps)
        return when {
            fuzzyMatches.size == 1 -> launchInstalledApp(fuzzyMatches.first())
            fuzzyMatches.size > 1 -> AppLaunchOutcome.Ambiguous(trimmedTarget, fuzzyMatches.take(4))
            else -> AppLaunchOutcome.NotInstalled(trimmedTarget)
        }
    }

    /**
     * Backward-compatible Pair<Boolean, String> wrapper for callers such as routines & URL opens.
     */
    fun openApp(target: String): Pair<Boolean, String> {
        return when (val outcome = openAppSmart(target)) {
            is AppLaunchOutcome.Launched -> Pair(true, "Opening ${outcome.appLabel}.")
            is AppLaunchOutcome.OpenedUrl -> Pair(true, "Opening link.")
            is AppLaunchOutcome.Ambiguous -> {
                val options = outcome.candidates.joinToString(", ") { it.label }
                Pair(false, "I found multiple matching apps: $options. Which one should I open?")
            }
            is AppLaunchOutcome.NotInstalled -> Pair(false, "${outcome.requestedName} is not installed on this device.")
            is AppLaunchOutcome.Failed -> Pair(false, outcome.reason)
        }
    }

    /**
     * Searches inside a supported application or opens the closest supported destination honestly.
     */
    fun searchInsideApp(appQuery: String, rawSearchQuery: String): InAppSearchOutcome {
        val cleanAppKey = normalizeAppQuery(appQuery)
        val searchQuery = rawSearchQuery.trim()
        if (searchQuery.isBlank()) {
            return InAppSearchOutcome.Failed("Please tell me what to search for.")
        }
        val encoded = Uri.encode(searchQuery)

        return when {
            cleanAppKey in listOf("youtube", "yt", "you tube", "यूट्यूब") -> {
                val ytPkg = "com.google.android.youtube"
                if (isPackageInstalled(ytPkg)) {
                    val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                        setPackage(ytPkg)
                        putExtra(SearchManager.QUERY, searchQuery)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded")).apply {
                        setPackage(ytPkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (tryStartActivity(searchIntent) || tryStartActivity(viewIntent)) {
                        InAppSearchOutcome.SearchedInApp("YouTube", searchQuery)
                    } else {
                        val webFallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        if (tryStartActivity(webFallback)) {
                            InAppSearchOutcome.OpenedWebsite("YouTube", "https://www.youtube.com/results?search_query=$encoded", usedDefaultBrowser = true)
                        } else {
                            InAppSearchOutcome.Failed("Could not open YouTube search.")
                        }
                    }
                } else {
                    val webFallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (tryStartActivity(webFallback)) {
                        InAppSearchOutcome.OpenedDestination(
                            appLabel = "YouTube",
                            query = searchQuery,
                            honestReason = "YouTube app is not installed, so I opened YouTube search for \"$searchQuery\" in your browser."
                        )
                    } else {
                        InAppSearchOutcome.AppNotInstalled("YouTube", searchQuery)
                    }
                }
            }

            cleanAppKey in listOf("google", "google app", "google search", "गूगल") -> {
                val googlePkg = "com.google.android.googlequicksearchbox"
                val googleSearchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    setPackage(googlePkg)
                    putExtra(SearchManager.QUERY, searchQuery)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (isPackageInstalled(googlePkg) && tryStartActivity(googleSearchIntent)) {
                    InAppSearchOutcome.SearchedInApp("Google", searchQuery)
                } else {
                    val url = "https://www.google.com/search?q=$encoded"
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val genericWebSearch = Intent(Intent.ACTION_WEB_SEARCH).apply {
                        putExtra(SearchManager.QUERY, searchQuery)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (tryStartActivity(genericWebSearch) || tryStartActivity(browserIntent)) {
                        InAppSearchOutcome.SearchedInApp("Google", searchQuery)
                    } else {
                        InAppSearchOutcome.Failed("No browser or Google Search handler available.")
                    }
                }
            }

            cleanAppKey in listOf("chrome", "google chrome", "क्रोम") -> {
                openOrSearchInChrome(searchQuery)
            }

            cleanAppKey in listOf("instagram", "insta", "ig", "इंस्टाग्राम") -> {
                val instaPkg = "com.instagram.android"
                val tagSlug = searchQuery.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_]"), "")
                if (isPackageInstalled(instaPkg)) {
                    val exploreIntent = if (tagSlug.isNotBlank()) {
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/explore/tags/$tagSlug/")).apply {
                            setPackage(instaPkg)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    } else null
                    val launchedExplore = exploreIntent != null && tryStartActivity(exploreIntent)
                    val launchedApp = launchedExplore || (openAppSmart("instagram") is AppLaunchOutcome.Launched)
                    if (launchedApp) {
                        val reason = if (launchedExplore) {
                            "Instagram doesn't support direct external keyword search queries, so I opened Instagram Explore for #$tagSlug."
                        } else {
                            "Instagram doesn't allow external apps to run internal searches directly, so I opened Instagram for you to search \"$searchQuery\"."
                        }
                        InAppSearchOutcome.OpenedDestination("Instagram", searchQuery, reason)
                    } else {
                        InAppSearchOutcome.Failed("Could not open Instagram.")
                    }
                } else {
                    InAppSearchOutcome.AppNotInstalled("Instagram", searchQuery)
                }
            }

            cleanAppKey in listOf("facebook", "fb", "फेसबुक") -> {
                val fbPkg = listOf("com.facebook.katana", "com.facebook.lite").firstOrNull { isPackageInstalled(it) }
                if (fbPkg != null) {
                    val fbSearchWebIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/search/top/?q=$encoded")).apply {
                        setPackage(fbPkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val openedTarget = tryStartActivity(fbSearchWebIntent)
                    val openedApp = openedTarget || (openAppSmart("facebook") is AppLaunchOutcome.Launched)
                    if (openedApp) {
                        val reason = if (openedTarget) {
                            "Opened Facebook search destination for \"$searchQuery\" (Facebook may require completing or confirming search inside the app)."
                        } else {
                            "Facebook doesn't support external direct search queries on this device, so I opened Facebook for you to search \"$searchQuery\"."
                        }
                        InAppSearchOutcome.OpenedDestination("Facebook", searchQuery, reason)
                    } else {
                        InAppSearchOutcome.Failed("Could not open Facebook.")
                    }
                } else {
                    val webFbSearch = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/search/top/?q=$encoded")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (tryStartActivity(webFbSearch)) {
                        InAppSearchOutcome.OpenedDestination(
                            appLabel = "Facebook",
                            query = searchQuery,
                            honestReason = "Facebook app is not installed, so I opened Facebook web search for \"$searchQuery\" in your browser."
                        )
                    } else {
                        InAppSearchOutcome.AppNotInstalled("Facebook", searchQuery)
                    }
                }
            }

            cleanAppKey in listOf("maps", "google maps", "map", "नक्शा", "मैप्स") -> {
                val mapsPkg = "com.google.android.apps.maps"
                val geoUri = Uri.parse("geo:0,0?q=$encoded")
                val mapsIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                    if (isPackageInstalled(mapsPkg)) setPackage(mapsPkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (tryStartActivity(mapsIntent)) {
                    InAppSearchOutcome.SearchedInApp("Google Maps", searchQuery)
                } else {
                    InAppSearchOutcome.Failed("Could not search in Google Maps.")
                }
            }

            cleanAppKey in listOf("spotify", "स्पॉटिफाई") -> {
                val spotifyPkg = listOf("com.spotify.music", "com.spotify.lite").firstOrNull { isPackageInstalled(it) }
                if (spotifyPkg != null) {
                    val spotifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$encoded")).apply {
                        setPackage(spotifyPkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (tryStartActivity(spotifyIntent)) {
                        InAppSearchOutcome.SearchedInApp("Spotify", searchQuery)
                    } else if (openAppSmart("spotify") is AppLaunchOutcome.Launched) {
                        InAppSearchOutcome.OpenedDestination(
                            "Spotify",
                            searchQuery,
                            "Opened Spotify, though direct search intent wasn't supported on this version."
                        )
                    } else {
                        InAppSearchOutcome.Failed("Could not open Spotify.")
                    }
                } else {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/$encoded")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (tryStartActivity(webIntent)) {
                        InAppSearchOutcome.OpenedDestination(
                            "Spotify",
                            searchQuery,
                            "Spotify app is not installed, so I opened Spotify search in your browser."
                        )
                    } else {
                        InAppSearchOutcome.AppNotInstalled("Spotify", searchQuery)
                    }
                }
            }

            cleanAppKey in listOf("play store", "playstore", "google play", "प्ले स्टोर") -> {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$encoded&c=apps")).apply {
                    setPackage("com.android.vending")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val webStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$encoded&c=apps")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (isPackageInstalled("com.android.vending") && tryStartActivity(marketIntent)) {
                    InAppSearchOutcome.SearchedInApp("Play Store", searchQuery)
                } else if (tryStartActivity(webStoreIntent)) {
                    InAppSearchOutcome.OpenedDestination(
                        "Play Store",
                        searchQuery,
                        "Opened Play Store search in your browser for \"$searchQuery\"."
                    )
                } else {
                    InAppSearchOutcome.AppNotInstalled("Play Store", searchQuery)
                }
            }

            else -> {
                when (val launched = openAppSmart(appQuery)) {
                    is AppLaunchOutcome.Launched -> InAppSearchOutcome.OpenedDestination(
                        appLabel = launched.appLabel,
                        query = searchQuery,
                        honestReason = "Direct external search is not supported for ${launched.appLabel}, so I opened ${launched.appLabel} for you to search \"$searchQuery\"."
                    )
                    is AppLaunchOutcome.Ambiguous -> InAppSearchOutcome.OpenedDestination(
                        appLabel = appQuery,
                        query = searchQuery,
                        honestReason = "Multiple apps match $appQuery (${launched.candidates.joinToString { it.label }}), and direct external search isn't supported."
                    )
                    is AppLaunchOutcome.NotInstalled -> InAppSearchOutcome.AppNotInstalled(appQuery, searchQuery)
                    is AppLaunchOutcome.OpenedUrl -> InAppSearchOutcome.OpenedWebsite("Browser", launched.url)
                    is AppLaunchOutcome.Failed -> InAppSearchOutcome.Failed(launched.reason)
                }
            }
        }
    }

    /**
     * Opens a website URL or searches inside Chrome (falling back cleanly to default browser).
     */
    fun openOrSearchInChrome(targetOrQuery: String): InAppSearchOutcome {
        val raw = targetOrQuery.trim()
        if (raw.isBlank() || raw.equals("website", ignoreCase = true) || raw.equals("a website", ignoreCase = true)) {
            return when (openAppSmart("chrome")) {
                is AppLaunchOutcome.Launched -> InAppSearchOutcome.OpenedDestination(
                    appLabel = "Chrome",
                    query = raw,
                    honestReason = "Opened Chrome. Tell me which website or topic you'd like to open."
                )
                is AppLaunchOutcome.NotInstalled -> InAppSearchOutcome.AppNotInstalled("Chrome", raw)
                else -> InAppSearchOutcome.Failed("Could not open Chrome.")
            }
        }

        val cleanedSite = raw
            .replace(Regex("""^(?:a\s+)?website\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+website$""", RegexOption.IGNORE_CASE), "")
            .trim()
        val chromeInstalled = isPackageInstalled("com.android.chrome")
        val targetUrl = if (looksLikeWebsiteOrUrl(cleanedSite)) {
            if (cleanedSite.startsWith("http://", ignoreCase = true) || cleanedSite.startsWith("https://", ignoreCase = true)) {
                cleanedSite
            } else {
                "https://$cleanedSite"
            }
        } else {
            "https://www.google.com/search?q=${Uri.encode(raw)}"
        }

        if (chromeInstalled) {
            val chromeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                setPackage("com.android.chrome")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryStartActivity(chromeIntent)) {
                return if (looksLikeWebsiteOrUrl(cleanedSite)) {
                    InAppSearchOutcome.OpenedWebsite("Chrome", targetUrl, usedDefaultBrowser = false)
                } else {
                    InAppSearchOutcome.SearchedInApp("Chrome", raw)
                }
            }
        }

        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (tryStartActivity(browserIntent)) {
            if (looksLikeWebsiteOrUrl(cleanedSite)) {
                InAppSearchOutcome.OpenedWebsite("Browser", targetUrl, usedDefaultBrowser = true)
            } else {
                InAppSearchOutcome.OpenedDestination(
                    appLabel = "Chrome",
                    query = raw,
                    honestReason = "Chrome was unavailable, so I opened your default browser to search \"$raw\"."
                )
            }
        } else {
            InAppSearchOutcome.Failed("Chrome and default browser are unavailable.")
        }
    }

    /**
     * Enables or disables Bluetooth where Android permits; on Android 13+ or when restricted,
     * opens Bluetooth Settings honestly for manual toggle.
     */
    @SuppressLint("MissingPermission")
    fun setBluetoothEnabled(enable: Boolean): SystemControlOutcome {
        val adapter = bluetoothManager?.adapter
            ?: return SystemControlOutcome.Failed("Bluetooth hardware is not available on this device.")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val (opened, _) = openBluetoothSettings()
            val actionWord = if (enable) "turn on" else "turn off"
            return if (opened) {
                SystemControlOutcome.OpenedSettingsFallback(
                    settingsLabel = "Bluetooth Settings",
                    reason = "Android 13 and newer restricts apps from changing Bluetooth directly. I opened Bluetooth Settings so you can $actionWord Bluetooth."
                )
            } else {
                SystemControlOutcome.Failed("Could not open Bluetooth Settings.")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnectPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasConnectPerm) {
                val (opened, _) = openBluetoothSettings()
                return if (opened) {
                    SystemControlOutcome.OpenedSettingsFallback(
                        settingsLabel = "Bluetooth Settings",
                        reason = "Bluetooth permission is required to toggle Bluetooth directly. I opened Bluetooth Settings for you."
                    )
                } else {
                    SystemControlOutcome.Failed("Bluetooth permission not granted and Settings could not be opened.")
                }
            }
        }

        return try {
            @Suppress("DEPRECATION")
            val succeeded = if (enable) {
                if (adapter.isEnabled) true else adapter.enable()
            } else {
                if (!adapter.isEnabled) true else adapter.disable()
            }
            if (succeeded) {
                val stateLabel = if (enable) "turned on" else "turned off"
                SystemControlOutcome.Applied("Bluetooth has been $stateLabel.")
            } else {
                openBluetoothSettings()
                SystemControlOutcome.OpenedSettingsFallback(
                    settingsLabel = "Bluetooth Settings",
                    reason = "Your device prevented automatic Bluetooth toggling. I opened Bluetooth Settings for you."
                )
            }
        } catch (e: SecurityException) {
            openBluetoothSettings()
            SystemControlOutcome.OpenedSettingsFallback(
                settingsLabel = "Bluetooth Settings",
                reason = "Bluetooth permission is restricted. I opened Bluetooth Settings for you."
            )
        } catch (e: Exception) {
            openBluetoothSettings()
            SystemControlOutcome.OpenedSettingsFallback(
                settingsLabel = "Bluetooth Settings",
                reason = "Could not toggle Bluetooth directly. Opened Bluetooth Settings for manual control."
            )
        }
    }

    /**
     * Adjusts screen brightness when Settings.System.canWrite(context) is permitted,
     * otherwise opens the Write Settings permission / Display Settings screen.
     */
    fun adjustBrightness(action: String, levelPercent: Int? = null): SystemControlOutcome {
        val canWrite = canWriteSystemSettings?.invoke() ?: try {
            Settings.System.canWrite(context)
        } catch (_: Exception) {
            false
        }

        if (!canWrite) {
            val manageIntent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val openedManage = tryStartActivity(manageIntent)
            if (!openedManage) {
                openDisplaySettings()
            }
            return SystemControlOutcome.OpenedSettingsFallback(
                settingsLabel = if (openedManage) "Modify System Settings" else "Display Settings",
                reason = "Android requires Modify System Settings permission before apps can change brightness. I opened the settings screen so you can allow it or adjust brightness manually."
            )
        }

        return try {
            val resolver = context.contentResolver
            when (action.lowercase(Locale.ROOT)) {
                "auto" -> {
                    Settings.System.putInt(
                        resolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                    )
                    SystemControlOutcome.Applied("Adaptive brightness has been enabled.")
                }
                else -> {
                    Settings.System.putInt(
                        resolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                    )
                    val current = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128).coerceIn(0, 255)
                    val target = when (action.lowercase(Locale.ROOT)) {
                        "up", "increase" -> (current + 51).coerceAtMost(255)
                        "down", "decrease", "lower" -> (current - 51).coerceAtLeast(20)
                        "max", "full" -> 255
                        "min", "low" -> 26
                        "set" -> {
                            val pct = (levelPercent ?: 50).coerceIn(5, 100)
                            (pct * 255) / 100
                        }
                        else -> current
                    }
                    Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, target)
                    val pctDisplay = ((target * 100.0) / 255.0).toInt().coerceIn(1, 100)
                    SystemControlOutcome.Applied("Screen brightness set to $pctDisplay%.")
                }
            }
        } catch (e: Exception) {
            openDisplaySettings()
            SystemControlOutcome.OpenedSettingsFallback(
                settingsLabel = "Display Settings",
                reason = "Could not update screen brightness directly. I opened Display Settings for you."
            )
        }
    }

    fun openUrl(url: String): Pair<Boolean, String> {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return Pair(false, "Invalid empty link.")
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Pair(true, "Opening link.")
        } catch (e: Exception) {
            Pair(false, "No browser or download handler available.")
        }
    }

    fun openPhoneApp(): Pair<Boolean, String> {
        val pkgIntent = listOf("com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer")
            .firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
        val intent = (pkgIntent ?: Intent(Intent.ACTION_DIAL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Pair(true, "Opening Phone.")
        } catch (e: Exception) {
            Pair(false, "Phone app could not be opened.")
        }
    }

    fun openMessagesApp(): Pair<Boolean, String> {
        val pkgIntent = listOf("com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging")
            .firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
        val categoryIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_MESSAGING)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val smsFallback = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val candidates = listOfNotNull(
            pkgIntent?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            categoryIntent,
            smsFallback
        )
        return if (candidates.any { tryStartActivity(it) }) {
            Pair(true, "Opening Messages.")
        } else {
            Pair(false, "Messages app could not be opened.")
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
        val pkgIntent = listOf(
            "com.google.android.apps.photos",
            "com.sec.android.gallery3d",
            "com.android.gallery3d",
            "com.miui.gallery"
        ).firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            type = "image/*"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val candidates = listOfNotNull(
            pkgIntent?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            viewIntent
        )
        return if (candidates.any { tryStartActivity(it) }) {
            Pair(true, "Opening Gallery.")
        } else {
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
                if (tryStartActivity(intent)) {
                    return Pair(true, "Opening Calculator.")
                }
            }
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=calculator")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (tryStartActivity(intent)) {
            Pair(true, "Calculator app wasn't found, so I opened an online calculator.")
        } else {
            Pair(false, "Calculator application not found.")
        }
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

    private fun openFirstSupportedSettings(intents: List<Intent>, label: String): Pair<Boolean, String> {
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (tryStartActivity(intent)) {
                return Pair(true, "Opening $label.")
            }
        }
        return Pair(false, "Could not open $label on this device.")
    }

    // --- All 14 Android System Settings Shortcuts ---

    fun openBluetoothSettings(): Pair<Boolean, String> =
        openSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth Settings")

    fun openWifiSettings(): Pair<Boolean, String> =
        openSettings(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi Settings")

    fun openMobileDataSettings(): Pair<Boolean, String> {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(Intent(Settings.ACTION_DATA_USAGE_SETTINGS))
            }
            add(Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS))
            add(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS))
            add(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            add(Intent(Settings.ACTION_SETTINGS))
        }
        return openFirstSupportedSettings(candidates, "Mobile Data Settings")
    }

    fun openHotspotSettings(): Pair<Boolean, String> {
        val candidates = listOf(
            Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings"),
            Intent().setClassName("com.android.settings", "com.android.settings.Settings\$TetherSettingsActivity"),
            Intent("android.settings.TETHER_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        return openFirstSupportedSettings(candidates, "Hotspot Settings")
    }

    fun openLocationSettings(): Pair<Boolean, String> =
        openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS, "Location Settings")

    fun openDisplaySettings(): Pair<Boolean, String> =
        openSettings(Settings.ACTION_DISPLAY_SETTINGS, "Display Settings")

    fun openSoundSettings(): Pair<Boolean, String> =
        openSettings(Settings.ACTION_SOUND_SETTINGS, "Sound Settings")

    fun openBatterySettings(): Pair<Boolean, String> {
        val candidates = listOf(
            Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        return openFirstSupportedSettings(candidates, "Battery Settings")
    }

    fun openBatteryOptimizationSettings(): Pair<Boolean, String> {
        val candidates = listOf(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        return openFirstSupportedSettings(candidates, "Battery Optimization Settings")
    }

    fun openAppPermissionsSettings(): Pair<Boolean, String> {
        val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        val candidates = buildList {
            add(appDetailsIntent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Intent(Settings.ACTION_PRIVACY_SETTINGS))
            }
            add(Intent(Settings.ACTION_APPLICATION_SETTINGS))
        }
        return openFirstSupportedSettings(candidates, "App Permissions")
    }

    fun openNotificationSettings(): Pair<Boolean, String> {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                )
            }
            add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            add(Intent(Settings.ACTION_SOUND_SETTINGS))
        }
        return openFirstSupportedSettings(candidates, "Notification Settings")
    }

    fun openAirplaneModeSettings(): Pair<Boolean, String> =
        openSettings(Settings.ACTION_AIRPLANE_MODE_SETTINGS, "Airplane Mode Settings")

    fun openLanguageSettings(): Pair<Boolean, String> {
        val candidates = listOf(
            Intent(Settings.ACTION_LOCALE_SETTINGS),
            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        return openFirstSupportedSettings(candidates, "Language Settings")
    }

    fun openAccessibilitySettings(): Pair<Boolean, String> =
        openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS, "Accessibility Settings")

    /**
     * Sets a countdown timer via standard Android Clock
     */
    fun setTimer(durationSeconds: Int, message: String = "Siri Timer"): Pair<Boolean, String> {
        val safeDuration = durationSeconds.coerceIn(1, 86400)
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, safeDuration)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            val minutes = safeDuration / 60
            val seconds = safeDuration % 60
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
        if (hour !in 0..23 || minute !in 0..59) {
            return Pair(false, "Invalid alarm time. Please specify a valid hour and minute.")
        }
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
            val minStr = String.format(Locale.US, "%02d", minute)
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
            when (direction.lowercase(Locale.ROOT)) {
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
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(webIntent)
                Pair(true, "Searching Google for $query.")
            } catch (ex: Exception) {
                Pair(false, "No browser available to search for $query.")
            }
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
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+*#]"), "")
        if (cleanNumber.isBlank()) {
            return Pair(false, "Please provide a valid phone number to dial.")
        }
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
    fun createCalendarEvent(
        title: String,
        description: String = "",
        beginTimeMillis: Long = System.currentTimeMillis() + 3600000L
    ): Pair<Boolean, String> {
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

    // --- Internal helpers ---

    private fun tryStartActivity(intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun normalizeAppQuery(raw: String): String {
        return raw.lowercase(Locale.ROOT)
            .replace('’', '\'')
            .replace(Regex("""^(?:the|my|zara|mera|mujhe|please|bhai|yaar)\s+"""), "")
            .replace(Regex("""\s+(?:app|application|ko|please|abhi|jaldi|जरा|ऐप|को)$"""), "")
            .replace(Regex("""\s+(?:app|ko)$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun normalizeLabel(raw: String): String {
        return raw.lowercase(Locale.ROOT)
            .replace('’', '\'')
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun findKnownAppSpec(cleanQuery: String): KnownAppSpec? {
        val compactQuery = cleanQuery.replace(" ", "").replace("'", "")
        return knownApps.firstOrNull { spec ->
            spec.aliases.any { alias ->
                alias.equals(cleanQuery, ignoreCase = true) ||
                    alias.replace(" ", "").replace("'", "").equals(compactQuery, ignoreCase = true)
            }
        }
    }

    private fun findMatchingInstalledApps(cleanQuery: String, installedApps: List<InstalledAppInfo>): List<InstalledAppInfo> {
        if (cleanQuery.isBlank()) return emptyList()
        val compactQuery = cleanQuery.replace(" ", "").replace("'", "")

        val exactCompact = installedApps.filter { app ->
            normalizeLabel(app.label).replace(" ", "").replace("'", "") == compactQuery
        }
        if (exactCompact.isNotEmpty()) return exactCompact

        val startsWithMatches = installedApps.filter { app ->
            val normLabel = normalizeLabel(app.label)
            normLabel.startsWith(cleanQuery) || normLabel.replace(" ", "").startsWith(compactQuery)
        }
        if (startsWithMatches.isNotEmpty()) return startsWithMatches

        val wordMatches = installedApps.filter { app ->
            val words = normalizeLabel(app.label).split(' ', '-', '_')
            words.any { it == cleanQuery || it.startsWith(cleanQuery) }
        }
        if (wordMatches.isNotEmpty()) return wordMatches

        if (cleanQuery.length < 3) return emptyList()
        val containsMatches = installedApps.filter { app ->
            val normLabel = normalizeLabel(app.label)
            normLabel.contains(cleanQuery) || normLabel.replace(" ", "").contains(compactQuery)
        }
        if (containsMatches.isNotEmpty()) return containsMatches

        // Bounded Levenshtein tolerance for slight voice transcription typos on app names >= 5 chars
        if (compactQuery.length >= 5) {
            val maxDist = if (compactQuery.length >= 8) 2 else 1
            return installedApps.filter { app ->
                val compactLabel = normalizeLabel(app.label).replace(" ", "")
                kotlin.math.abs(compactLabel.length - compactQuery.length) <= maxDist &&
                    levenshteinDistance(compactQuery, compactLabel) <= maxDist
            }
        }
        return emptyList()
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in a.indices) {
            curr[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                curr[j + 1] = minOf(curr[j] + 1, prev[j + 1] + 1, prev[j] + cost)
            }
            System.arraycopy(curr, 0, prev, 0, prev.size)
        }
        return prev[b.length]
    }

    private fun looksLikeWebsiteOrUrl(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return true
        }
        if (trimmed.contains(' ')) return false
        return Regex("""^[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)+(?:/.*)?$""").matches(trimmed)
    }
}
