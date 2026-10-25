package com.minicore.bookshelf.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/**
 * Provides [Clock.systemDefaultZone] unscoped (plan R8) - a fresh clock, with the current
 * default zone, on each call. This must stay unscoped: [com.minicore.bookshelf.features.library.domain.CurrentDateProvider]
 * depends on `javax.inject.Provider<Clock>` precisely so it can call `get()` at the start of
 * every collection (not just once per instance), and a `@Singleton`-scoped provider would make
 * every such `get()` return the same frozen-zone `Clock`, defeating that.
 */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
