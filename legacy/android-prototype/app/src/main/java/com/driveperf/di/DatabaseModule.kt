package com.driveperf.di

import android.content.Context
import androidx.room.Room
import com.driveperf.data.local.AppDatabase
import com.driveperf.data.local.dao.DrivingEventDao
import com.driveperf.data.local.dao.LocationPointDao
import com.driveperf.data.local.dao.MotionWindowDao
import com.driveperf.data.local.dao.TripScoreDao
import com.driveperf.data.local.dao.TripSessionDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideTripSessionDao(db: AppDatabase): TripSessionDao = db.tripSessionDao()

    @Provides
    fun provideLocationPointDao(db: AppDatabase): LocationPointDao = db.locationPointDao()

    @Provides
    fun provideMotionWindowDao(db: AppDatabase): MotionWindowDao = db.motionWindowDao()

    @Provides
    fun provideDrivingEventDao(db: AppDatabase): DrivingEventDao = db.drivingEventDao()

    @Provides
    fun provideTripScoreDao(db: AppDatabase): TripScoreDao = db.tripScoreDao()
}
