package com.example.spotatlas.data

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Clock whose time only moves when a test moves it. */
class MutableClock(private var now: Instant, private val zone: ZoneId = ZoneId.of("UTC")) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now

    fun advanceBy(duration: Duration) {
        now = now.plus(duration)
    }
}
