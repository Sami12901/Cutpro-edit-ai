package com.cutpro.core.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.cutpro.core.data.database.converter.CutproTypeConverters
import com.cutpro.core.data.database.dao.CaptionCueDao
import com.cutpro.core.data.database.dao.ClipDao
import com.cutpro.core.data.database.dao.KeyframeDao
import com.cutpro.core.data.database.dao.ProjectDao
import com.cutpro.core.data.database.dao.TrackDao
import com.cutpro.core.data.database.entity.CaptionCueEntity
import com.cutpro.core.data.database.entity.ClipEntity
import com.cutpro.core.data.database.entity.KeyframeEntity
import com.cutpro.core.data.database.entity.ProjectEntity
import com.cutpro.core.data.database.entity.TrackEntity

@Database(
    entities = [
        ProjectEntity::class,
        TrackEntity::class,
        ClipEntity::class,
        KeyframeEntity::class,
        CaptionCueEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(CutproTypeConverters::class)
abstract class CutproDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun trackDao(): TrackDao
    abstract fun clipDao(): ClipDao
    abstract fun keyframeDao(): KeyframeDao
    abstract fun captionCueDao(): CaptionCueDao

    companion object {
        private const val DATABASE_NAME = "cutpro.db"

        @Volatile
        private var instance: CutproDatabase? = null

        fun getInstance(context: Context): CutproDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CutproDatabase::class.java,
                    DATABASE_NAME
                ).build()
                 .also { instance = it }
            }
        }

        fun createInMemory(context: Context): CutproDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                CutproDatabase::class.java
            ).allowMainThreadQueries()
                .build()
        }
    }
}
