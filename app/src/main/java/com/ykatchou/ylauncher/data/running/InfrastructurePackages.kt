package com.ykatchou.ylauncher.data.running

/**
 * Packages the running-apps column always hides.
 *
 * These are the launcher's own plumbing, not apps the user opened. The keyboard and Shizuku in
 * particular are load-bearing — the cockpit depends on them — so they appear as "open" constantly
 * and would crowd out the actual apps. The stock launcher is here because it stays resident only
 * to provide the system's gesture handler, and launcher3 as a defensive catch-all.
 */
object InfrastructurePackages {
    val ALL: Set<String> = setOf(
        "com.ykatchou.ylauncher",       // ourselves — always "running"
        "helium314.keyboard",           // HeliBoard — the keyboard, infrastructure
        "moe.shizuku.privileged.api",   // Shizuku — the privileged bridge the cockpit runs on
        "com.blackview.launcher",       // stock launcher, kept only for QuickStep
        "com.android.launcher3",
    )
}
