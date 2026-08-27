package com.ykatchou.ylauncher.data.model

import android.os.UserHandle

/**
 * An installed app entry.
 *
 * Deliberately holds no Drawable. The list covers every launchable activity on the device — a few
 * hundred entries — and an adaptive icon keeps foreground and background bitmaps sized to screen
 * density in native memory. Holding them all cost ~123 MB resident and nearly doubled that during
 * startup. Icons are loaded on demand through AppIconCache, which keeps only the small rasterised
 * bitmaps it actually needs.
 */
data class AppInfo(
    val appLabel: String,
    val packageName: String,
    val activityClassName: String?,
    val userHandle: UserHandle,
    /** Pre-computed NFD-normalized, punctuation-stripped label for fast search. */
    val normalizedLabel: String = appLabel,
) : Comparable<AppInfo> {
    override fun compareTo(other: AppInfo): Int =
        appLabel.compareTo(other.appLabel, ignoreCase = true)
}
