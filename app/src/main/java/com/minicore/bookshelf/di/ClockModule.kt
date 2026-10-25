package com.minicore.bookshelf.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/**
 * Provides [Clock.systemDefaultZone] unscoped (plan R8) - a fresh clock, with the current
 * default zone, on each injection, so an unscoped consumer (e.g. a recreated ViewModel via
 * `WhileSubscribed`) picks up a zone change the next time it is created. Never `@Singleton`:
 * that would freeze the zone for the process lifetime.
 */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
