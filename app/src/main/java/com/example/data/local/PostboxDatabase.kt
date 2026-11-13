package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [EmailEntity::class, ContactEntity::class],
  version = 1,
  exportSchema = false,
)
abstract class PostboxDatabase : RoomDatabase() {
  abstract fun mailDao(): MailDao

  companion object {
    @Volatile private var instance: PostboxDatabase? = null

    fun getInstance(context: Context): PostboxDatabase {
      return instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(
                context.applicationContext,
                PostboxDatabase::class.java,
                "postbox_offline.db",
              )
              .build()
              .also { instance = it }
        }
    }
  }
}
