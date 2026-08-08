package com.ykatchou.ylauncher.util

const val REVIEW_PROMPT_ELIGIBLE_AFTER_MS = 5L * 24 * 60 * 60 * 1000

fun shouldShowReviewPrompt(
    hasSeenOnboardingTour: Boolean,
    reviewNeverAsk: Boolean,
    firstLaunchTimestamp: Long,
    reviewSnoozedUntil: Long,
    now: Long,
): Boolean {
    if (!hasSeenOnboardingTour || reviewNeverAsk || firstLaunchTimestamp <= 0L) return false
    if (now - firstLaunchTimestamp < REVIEW_PROMPT_ELIGIBLE_AFTER_MS) return false
    return now >= reviewSnoozedUntil
}
