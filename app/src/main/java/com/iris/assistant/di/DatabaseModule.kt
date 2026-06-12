package com.iris.assistant.di

import android.content.Context
import androidx.room.Room
import com.iris.assistant.data.local.db.IrisDatabase
import com.iris.assistant.util.Constants
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
    fun provideIrisDatabase(
        @ApplicationContext context: Context
    ): IrisDatabase = Room.databaseBuilder(
        context,
        IrisDatabase::class.java,
        Constants.DATABASE_NAME
    ).build()
}