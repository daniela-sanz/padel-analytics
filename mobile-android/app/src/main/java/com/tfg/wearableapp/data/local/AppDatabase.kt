package com.tfg.wearableapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tfg.wearableapp.data.local.session.SessionBlockEntity
import com.tfg.wearableapp.data.local.session.SessionDao
import com.tfg.wearableapp.data.local.session.SessionEntity

@Database(
    entities = [SessionEntity::class, SessionBlockEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wearable_app.db",
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
