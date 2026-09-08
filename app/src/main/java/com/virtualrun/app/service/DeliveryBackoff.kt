package com.virtualrun.app.service

/** Retry state is owned by one delivery thread, never by the UI. */
internal class DeliveryBackoff {
    private var failures = 0
    fun nextDelayMs(): Long = DELAYS[(failures++).coerceAtMost(DELAYS.lastIndex)]
    fun reset() { failures = 0 }
    private companion object { val DELAYS = longArrayOf(250, 500, 1000, 2000, 5000) }
}
