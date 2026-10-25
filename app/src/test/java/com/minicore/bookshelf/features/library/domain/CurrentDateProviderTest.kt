package com.minicore.bookshelf.features.library.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Provider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * F8 (plan section 6/FF1/R8): a mutable test [Clock], advanced in lockstep with the test
 * scheduler's virtual time, proves the ticker re-emits exactly once at local midnight, not
 * before; that it honours the clock's own zone (not a hardcoded UTC); and that it still rolls
 * over exactly once per day across a DST transition, including the 23-hour local day that
 * produces.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CurrentDateProviderTest {

    private class MutableTestClock(var instant: Instant, private val zoneId: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zoneId
        override fun withZone(zone: ZoneId): Clock = MutableTestClock(instant, zone)
        override fun instant(): Instant = instant
    }

    /** Advances the test clock's own reading and the scheduler's virtual time together. */
    private fun TestScope.advance(clock: MutableTestClock, millis: Long) {
        clock.instant = clock.instant.plusMillis(millis)
        advanceTimeBy(millis)
    }

    @Test
    fun emitsToday_thenRollsOverExactlyOnceAtMidnight_notBefore() = runTest(StandardTestDispatcher()) {
        // One millisecond before local midnight.
        val oneMsBeforeMidnight = LocalDate.of(2026, 6, 16).atStartOfDay(ZoneOffset.UTC).toInstant()
            .minusMillis(1)
        val clock = MutableTestClock(oneMsBeforeMidnight, ZoneOffset.UTC)
        val provider = CurrentDateProvider(Provider { clock })

        val emissions = mutableListOf<LocalDate>()
        val job = backgroundScope.launch {
            provider.observe().collect { emissions.add(it) }
        }
        runCurrent()

        assertEquals(listOf(LocalDate.of(2026, 6, 15)), emissions)

        // Cross the 1ms boundary: the delay must complete and re-emit exactly once, not before.
        advance(clock, 1)
        runCurrent()

        assertEquals(
            "must emit exactly once at rollover, no earlier and no more than once",
            listOf(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 16)),
            emissions,
        )

        job.cancel()
    }

    // F8 regression guard: a hardcoded atStartOfDay(ZoneOffset.UTC) would still pass the test
    // above (clock.zone happens to be UTC there). America/New_York is 4 hours behind UTC in
    // June 2026 (EDT), so local midnight there is 04:00 UTC - a wrong-zone implementation would
    // roll over 4 hours too early or too late.
    @Test
    fun rollsOverAtLocalMidnight_inTheClocksOwnNonUtcZone() = runTest(StandardTestDispatcher()) {
        val zone = ZoneId.of("America/New_York")
        val oneMsBeforeLocalMidnight = LocalDate.of(2026, 6, 16).atStartOfDay(zone).toInstant()
            .minusMillis(1)
        val clock = MutableTestClock(oneMsBeforeLocalMidnight, zone)
        val provider = CurrentDateProvider(Provider { clock })

        val emissions = mutableListOf<LocalDate>()
        val job = backgroundScope.launch {
            provider.observe().collect { emissions.add(it) }
        }
        runCurrent()

        assertEquals(listOf(LocalDate.of(2026, 6, 15)), emissions)

        advance(clock, 1)
        runCurrent()

        assertEquals(
            listOf(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 16)),
            emissions,
        )

        job.cancel()
    }

    // F8/Q2: a DST spring-forward day (Europe/Lisbon, last Sunday of March 2026 = the 29th,
    // clocks jump 01:00 -> 02:00 local) still rolls over exactly once per local midnight, both
    // into the 23-hour local day and out of it - atStartOfDay(zone) must resolve correctly on
    // both sides, not drift by the lost hour.
    @Test
    fun rollsOverExactlyOnce_acrossADstSpringForwardDay() = runTest(StandardTestDispatcher()) {
        val lisbon = ZoneId.of("Europe/Lisbon")
        val dayBeforeSwitch = LocalDate.of(2026, 3, 28)
        val switchDay = LocalDate.of(2026, 3, 29) // the 23-hour local day
        val dayAfterSwitch = LocalDate.of(2026, 3, 30)

        val startInstant = dayBeforeSwitch.atStartOfDay(lisbon).toInstant().plusSeconds(12 * 3600)
        val clock = MutableTestClock(startInstant, lisbon)
        val provider = CurrentDateProvider(Provider { clock })

        val emissions = mutableListOf<LocalDate>()
        val job = backgroundScope.launch {
            provider.observe().collect { emissions.add(it) }
        }
        runCurrent()
        assertEquals(listOf(dayBeforeSwitch), emissions)

        // Up to 1ms before the first midnight: no rollover yet.
        val msUntilFirstMidnight = switchDay.atStartOfDay(lisbon).toInstant().toEpochMilli() -
            clock.instant.toEpochMilli()
        advance(clock, msUntilFirstMidnight - 1)
        runCurrent()
        assertEquals(listOf(dayBeforeSwitch), emissions)

        // The 1ms that crosses into the 23-hour DST day: exactly one new emission.
        advance(clock, 1)
        runCurrent()
        assertEquals(listOf(dayBeforeSwitch, switchDay), emissions)

        // Up to 1ms before the second midnight (leaving the 23-hour day): no rollover yet,
        // despite the lost hour inside this day.
        val msUntilSecondMidnight = dayAfterSwitch.atStartOfDay(lisbon).toInstant().toEpochMilli() -
            clock.instant.toEpochMilli()
        advance(clock, msUntilSecondMidnight - 1)
        runCurrent()
        assertEquals(listOf(dayBeforeSwitch, switchDay), emissions)

        // Crossing out of the 23-hour day: exactly one new emission, no double-rollover.
        advance(clock, 1)
        runCurrent()
        assertEquals(
            "must roll over exactly once per local midnight even across the DST day",
            listOf(dayBeforeSwitch, switchDay, dayAfterSwitch),
            emissions,
        )

        job.cancel()
    }

    // 39d75db regression guard: the clock must be re-fetched from the Provider on every
    // collection, not captured once (e.g. as a property of observe()'s Flow and reused across
    // collections of it). This is exactly how production relies on it: ObserveLibraryRowsUseCase
    // builds the combine() over observe() once, and LibraryViewModel's
    // stateIn(WhileSubscribed) re-collects that same Flow instance on return to foreground - so
    // the test collects one Flow instance from a single observe() call twice, rather than calling
    // observe() twice. A Provider that returns a UTC clock on its first get() and an
    // America/New_York clock on its second must therefore see those two zones reflected across
    // the two collections of that one Flow - a single shared clock, or a clockProvider.get()
    // hoisted out of the flow{} builder into observe() itself, would make both collections report
    // the same date.
    @Test
    fun reFetchesClockFromProviderOnEachCollection() = runTest {
        // 2026-06-16T02:00Z is 2026-06-16 in UTC but still 2026-06-15 in America/New_York
        // (EDT, UTC-4).
        val instant = Instant.parse("2026-06-16T02:00:00Z")
        val utcClock = Clock.fixed(instant, ZoneOffset.UTC)
        val newYorkClock = Clock.fixed(instant, ZoneId.of("America/New_York"))
        var getCallCount = 0
        val provider = CurrentDateProvider(
            Provider {
                getCallCount++
                if (getCallCount == 1) utcClock else newYorkClock
            },
        )

        val flow = provider.observe()
        val firstCollectionDate = flow.first()
        val secondCollectionDate = flow.first()

        assertEquals(LocalDate.of(2026, 6, 16), firstCollectionDate)
        assertEquals(LocalDate.of(2026, 6, 15), secondCollectionDate)
        assertEquals(
            "clockProvider.get() must be called exactly once per collection, not reused " +
                "across collections",
            2,
            getCallCount,
        )
    }
}
