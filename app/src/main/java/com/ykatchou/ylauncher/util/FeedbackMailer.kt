package com.ykatchou.ylauncher.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

private const val FEEDBACK_EMAIL = "ykatchou+ylauncher@gmail.com"

fun Context.sendFeedbackEmail(body: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$FEEDBACK_EMAIL")).apply {
        putExtra(Intent.EXTRA_SUBJECT, "YLauncher feedback")
        putExtra(Intent.EXTRA_TEXT, body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        showToast("No email app found")
    }
}
