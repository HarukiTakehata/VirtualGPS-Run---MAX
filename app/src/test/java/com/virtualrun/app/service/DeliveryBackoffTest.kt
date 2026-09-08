package com.virtualrun.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class DeliveryBackoffTest {
    @Test fun retriesAreFastCappedAndResetAfterSuccess() {
        val retry = DeliveryBackoff()
        assertEquals(listOf(250L, 500L, 1000L, 2000L, 5000L, 5000L, 5000L),
            List(7) { retry.nextDelayMs() })
        retry.reset()
        assertEquals(250L, retry.nextDelayMs())
    }
}
