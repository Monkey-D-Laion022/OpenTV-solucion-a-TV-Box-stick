package com.opentv.homenova.core

import androidx.compose.ui.graphics.ImageBitmap

/**
 * A launchable application installed on the device.
 *
 * The icon is decoded off the main thread and stored as an [ImageBitmap] so the
 * Compose layer never touches PackageManager during composition.
 */
data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val isGame: Boolean,
    val isSystem: Boolean,
    val updatedAt: Long,
)

/** A piece of playable content shown on the home / discover / live rails. */
data class MediaEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val year: Int,
    val rating: String,
    val durationMinutes: Int,
    val genres: List<String>,
    val paletteSeed: Int,
    val videoUrl: String? = null,
    val progress: Float = 0f,
    val badge: String? = null,
    val isLive: Boolean = false,
    val liveProgram: String? = null,
)

/** One physical or virtual source exposed by the platform TV input framework. */
data class InputEntry(
    val id: String,
    val label: String,
    val kind: InputKind,
    val status: String,
    val isLaunchable: Boolean,
)

enum class InputKind { HDMI, TUNER, COMPOSITE, OTHER }

/** A notification surfaced by the launcher's own notification centre. */
data class LauncherNotification(
    val id: Long,
    val title: String,
    val body: String,
    val timestamp: Long,
    val kind: NotificationKind,
    val read: Boolean = false,
)

enum class NotificationKind { RECOMMENDATION, SYSTEM, UPDATE, REMINDER }
