package com.ykatchou.ylauncher.data.claude

/**
 * Decides whether a shell command the model wants to run is destructive enough to need the user's
 * say-so first. Pure string work, kept apart from the executor so it can be tested and so the rule
 * is one obvious list rather than scattered `if`s.
 *
 * The bar is deliberately conservative: read-only diagnostics (getprop, dumpsys, cat, ls, ps,
 * top, `pm list`, `settings get`) run freely — that is the whole point of the page — while anything
 * that changes device state, uninstalls, force-stops, writes settings, or touches storage trips the
 * gate. Better to ask once too often than to let the model wipe something unattended.
 */
object DangerGate {

    /** Substrings that mark a state-changing or destructive command. Matched case-insensitively. */
    private val DESTRUCTIVE = listOf(
        "rm ", "rmdir", "reboot", "shutdown",
        "pm uninstall", "pm clear", "pm disable", "pm enable", "pm install", "pm grant", "pm revoke",
        "am force-stop", "am kill", "killall", "kill ",
        "settings put", "setprop", "resetprop",
        "svc ", "dd if=", "dd of=", "mkfs", "fastboot", "wipe", "format",
        "content delete", "content insert", "content update",
        "> /", ">/", "truncate", "shred",
        "ime disable", "ime set", "cmd package",
    )

    fun isDangerous(command: String): Boolean {
        val c = command.lowercase()
        return DESTRUCTIVE.any { it in c }
    }

    /** The matched token, for showing the user *why* it was flagged. Null when the command is safe. */
    fun reason(command: String): String? {
        val c = command.lowercase()
        return DESTRUCTIVE.firstOrNull { it in c }?.trim()
    }
}
