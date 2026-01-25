package com.shadowmaster.di

import android.content.Context
import com.shadowmaster.data.local.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ShadowDatabase {
        return ShadowDatabase.getDatabase(context)
    }

    @Provides
    fun provideShadowItemDao(database: ShadowDatabase): ShadowItemDao {
        return database.shadowItemDao()
    }

    @Provides
    fun provideShadowPlaylistDao(database: ShadowDatabase): ShadowPlaylistDao {
        return database.shadowPlaylistDao()
    }

    @Provides
    fun provideImportJobDao(database: ShadowDatabase): ImportJobDao {
        return database.importJobDao()
    }

    @Provides
    fun providePracticeSessionDao(database: ShadowDatabase): PracticeSessionDao {
        return database.practiceSessionDao()
    }
}
