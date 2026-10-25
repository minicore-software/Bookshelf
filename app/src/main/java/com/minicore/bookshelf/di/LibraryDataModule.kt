package com.minicore.bookshelf.di

import com.minicore.bookshelf.features.library.data.RoomLibraryRepository
import com.minicore.bookshelf.features.library.domain.LibraryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryDataModule {

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: RoomLibraryRepository): LibraryRepository
}
