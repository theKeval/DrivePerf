package com.driveperf.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.driveperf.data.local.dao.DrivingEventDao
import com.driveperf.data.local.dao.LocationPointDao
import com.driveperf.data.local.dao.MotionWindowDao
import com.driveperf.data.local.dao.TripScoreDao
import com.driveperf.data.local.dao.TripSessionDao
import com.driveperf.data.local.entity.DrivingEventEntity
import com.driveperf.data.local.entity.LocationPointEntity
import com.driveperf.data.local.entity.MotionWindowEntity
import com.driveperf.data.local.entity.TripScoreEntity
import com.driveperf.data.local.entity.TripSessionEntity

@Database(
    entities = [
        TripSessionEntity::class,
        LocationPointEntity::class,
        MotionWindowEntity::class,
        DrivingEventEntity::class,
        TripScoreEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripSessionDao(): TripSessionDao
    abstract fun locationPointDao(): LocationPointDao
    abstract fun motionWindowDao(): MotionWindowDao
    abstract fun drivingEventDao(): DrivingEventDao
    abstract fun tripScoreDao(): TripScoreDao

    companion object {
        const val DATABASE_NAME = "driveperf.db"
    }
}
