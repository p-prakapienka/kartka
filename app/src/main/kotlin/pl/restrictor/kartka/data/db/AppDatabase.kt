package pl.restrictor.kartka.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TopicEntity::class, CollectionEntity::class, CardEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun topics(): TopicDao
    abstract fun collections(): CollectionDao
    abstract fun cards(): CardDao

    companion object {
        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, "kartka.db")
                .build()
        }
    }
}
