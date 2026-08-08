package com.ykatchou.ylauncher.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewEligibilityTest {

    private val fiveDays = REVIEW_PROMPT_ELIGIBLE_AFTER_MS
    private val oneWeek = ONE_WEEK_MS

    @Test
    fun `not eligible before onboarding tour is seen`() {
        val now = 2_000_000_000L
        val eligible = shouldShowReviewPrompt(
            hasSeenOnboardingTour = false,
            reviewNeverAsk = false,
            firstLaunchTimestamp = now - fiveDays,
            reviewSnoozedUntil = 0L,
            now = now,
        )
        assertFalse(eligible)
    }

    @Test
    fun `not eligible when never-ask is set`() {
        val now = 2_000_000_000L
        val eligible = shouldShowReviewPrompt(
            hasSeenOnboardingTour = true,
            reviewNeverAsk = true,
            firstLaunchTimestamp = now - fiveDays,
            reviewSnoozedUntil = 0L,
            now = now,
        )
        assertFalse(eligible)
    }

    @Test
    fun `not eligible before first launch has been recorded`() {
        val eligible = shouldShowReviewPrompt(
            hasSeenOnboardingTour = true,
            reviewNeverAsk = false,
            firstLaunchTimestamp = 0L,
            reviewSnoozedUntil = 0L,
            now = 10_000_000L,
        )
        assertFalse(eligible)
    }

    @Test
    fun `not eligible before five days have elapsed`() {
        val now = 2_000_000_000L
        val eligible = shouldShowReviewPrompt(
            hasSeenOnboardingTour = true,
            reviewNeverAsk = false,
            firstLaunchTimestamp = now - (fiveDays - 1),
            reviewSnoozedUntil = 0L,
            now = now,
        )
        assertFalse(eligible)
    }

    @Test
    fun `eligible exactly at the five day boundary`() {
        val now = 2_000_000_000L
        val eligible = shouldShowReviewPrompt(
            hasSeenOnboardingTour = true,
            reviewNeverAsk = false,
            firstLaunchTimestamp = now - fiveDays,
            reviewSnoozedUntil = 0L,
            now = now,
        )
        assertTrue(eligible)
    }

    @Test
    fun `not eligible while snoozed`() {
        val now = 2_000_000_000L
        val eligible = shouldShowReviewPrompt(
            hasSeenOnboardingTour = true,
            reviewNeverAsk = false,
            firstLaunchTimestamp = now - fiveDays - oneWeek,
            reviewSnoozedUntil = now + 1_000L,
            now = now,
        )
        assertFalse(eligible)
    }

    @Test
    fun `eligible again once the snooze window has passed`() {
        val now = 2_000_000_000L
        val eligible = shouldShowReviewPrompt(
            hasSeenOnboardingTour = true,
            reviewNeverAsk = false,
            firstLaunchTimestamp = now - fiveDays - oneWeek,
            reviewSnoozedUntil = now - 1_000L,
            now = now,
        )
        assertTrue(eligible)
    }
}
