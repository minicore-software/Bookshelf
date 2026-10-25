package com.minicore.bookshelf.features.library.domain

import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * Emits [LocalDate.now] against a fresh [Clock] fetched from [clockProvider] at the *start of
 * each collection* (plan R8), then re-emits at every local midnight so a screen left open
 * overnight does not show a stale due/overdue caption.
 *
 * [clockProvider] - not an injected `Clock` - is deliberate: `di/ClockModule` provides a fresh
 * [Clock.systemDefaultZone] per `get()`, but a single [Clock] value captured once (even an
 * "unscoped" one injected into a constructor) is still frozen for the lifetime of *this*
 * instance. A zone change while the app stays in memory (e.g. a flight) is only picked up
 * because every new collection - including the one `WhileSubscribed(5_000)` triggers when the
 * screen returns from background - re-fetches the clock here, not because of Hilt scoping.
 */
class CurrentDateProvider @Inject constructor(private val clockProvider: Provider<Clock>) {

    fun observe(): Flow<LocalDate> = flow {
        val clock = clockProvider.get()
        while (true) {
            val today = LocalDate.now(clock)
            emit(today)
            val startOfTomorrow = today.plusDays(1).atStartOfDay(clock.zone)
            val millisUntilMidnight = startOfTomorrow.toInstant().toEpochMilli() - clock.millis()
            delay(millisUntilMidnight.coerceAtLeast(1L))
        }
    }.distinctUntilChanged()
}
