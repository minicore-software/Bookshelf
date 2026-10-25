package com.minicore.bookshelf.features.library.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * F8 (plan section 6/FF1): a mutable test [Clock], advanced in lockstep with the test scheduler's
 * virtual time, proves the ticker re-emits exactly once at local midnight, not before.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CurrentDateProviderTest {

    private class MutableTestClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = instant
    }

    @Test
    fun emitsToday_thenRollsOverExactlyOnceAtMidnight_notBefore() = runTest(StandardTestDispatcher()) {
        // One millisecond before local midnight.
        val oneMsBeforeMidnight = LocalDate.of(2026, 6, 16).atStartOfDay(ZoneOffset.UTC).toInstant()
            .minusMillis(1)
        val clock = MutableTestClock(oneMsBeforeMidnight)
        val provider = CurrentDateProvider(clock)

        val emissions = mutableListOf<LocalDate>()
        val job = backgroundScope.launch {
            provider.observe().collect { emissions.add(it) }
        }
        runCurrent()

        assertEquals(listOf(LocalDate.of(2026, 6, 15)), emissions)

        // Advance the virtual clock's own reading to match the scheduler's advance, then cross
        // the 1ms boundary: the delay must complete and re-emit exactly once, not before.
        clock.instant = clock.instant.plusMillis(1)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(
            "must emit exactly once at rollover, no earlier and no more than once",
            listOf(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 16)),
            emissions,
        )

        job.cancel()
    }
}
