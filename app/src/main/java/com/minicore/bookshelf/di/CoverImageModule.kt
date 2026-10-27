package com.minicore.bookshelf.di

import com.minicore.bookshelf.core.coverimage.CoverImageLoader
import com.minicore.bookshelf.core.coverimage.CoverImageStore
import com.minicore.bookshelf.core.coverimage.FileCoverImageLoader
import com.minicore.bookshelf.core.coverimage.FileCoverImageStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CoverImageModule {

    @Binds
    @Singleton
    abstract fun bindCoverImageStore(impl: FileCoverImageStore): CoverImageStore

    @Binds
    @Singleton
    abstract fun bindCoverImageLoader(impl: FileCoverImageLoader): CoverImageLoader
}
