package com.minicore.bookshelf.features.library.domain

import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * Emits [LocalDate.now] against the injected [clock], then re-emits at every local midnight
 * (plan R8) so a screen left open overnight does not show a stale due/overdue caption. [clock]
 * is provided unscoped by `di/ClockModule` - a fresh [Clock.systemDefaultZone] per use, so a
 * zone change is picked up the next time this is re-collected (known limit, plan Q5).
 */
class CurrentDateProvider @Inject constructor(private val clock: Clock) {

    fun observe(): Flow<LocalDate> = flow {
        while (true) {
            val today = LocalDate.now(clock)
            emit(today)
            val startOfTomorrow = today.plusDays(1).atStartOfDay(clock.zone)
            val millisUntilMidnight = startOfTomorrow.toInstant().toEpochMilli() - clock.millis()
            delay(millisUntilMidnight.coerceAtLeast(1L))
        }
    }.distinctUntilChanged()
}
