package com.example.spotatlas.ui

import java.time.Duration
import java.util.Locale

/** Shared display helpers so money, distance and durations read the same on every screen. */

fun formatMoney(amount: Double, currencyCode: String): String =
    String.format(Locale.getDefault(), "%.2f %s", amount, currencyCode)

fun formatPricePerHour(amount: Double, currencyCode: String): String =
    if (amount <= 0.0) "Free" else "${formatMoney(amount, currencyCode)}/h"

fun formatDistance(meters: Int): String = when {
    meters < 1_000 -> "$meters m"
    else -> String.format(Locale.getDefault(), "%.1f km", meters / 1_000.0)
}

fun formatDuration(duration: Duration): String {
    val total = duration.coerceAtLeast(Duration.ZERO)
    val hours = total.toHours()
    val minutes = total.toMinutes() % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "${total.seconds}s"
    }
}

fun formatMinutes(minutes: Int): String = formatDuration(Duration.ofMinutes(minutes.toLong()))
