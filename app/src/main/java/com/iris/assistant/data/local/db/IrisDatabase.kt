package com.iris.assistant.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.iris.assistant.data.local.db.dao.MessageDao
import com.iris.assistant.data.local.db.entity.MessageEntity
import com.iris.assistant.util.Constants

@Database(
    entities  = [MessageEntity::class],
    version   = Constants.DATABASE_VERSION,
    exportSchema = true
)
abstract class IrisDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}