package com.minicore.bookshelf.di

import android.content.Context
import androidx.room.Room
import com.minicore.bookshelf.core.database.BookshelfDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DATABASE_NAME = "bookshelf.db"

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BookshelfDatabase =
        Room.databaseBuilder(context, BookshelfDatabase::class.java, DATABASE_NAME).build()
}
