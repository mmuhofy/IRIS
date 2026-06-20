package com.iris.assistant.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.iris.assistant.data.local.db.dao.ConversationDao
import com.iris.assistant.data.local.db.dao.MessageDao
import com.iris.assistant.data.local.db.entity.ConversationEntity
import com.iris.assistant.data.local.db.entity.MessageEntity
import com.iris.assistant.util.Constants

@Database(
    entities     = [ConversationEntity::class, MessageEntity::class],
    version      = Constants.DATABASE_VERSION,
    exportSchema = true,
)
abstract class IrisDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
}

// -----------------------------------------------------------------------------
// Migration 1 → 2
//
// What changed:
//   • New table: conversations
//   • messages gains conversation_id column (FK → conversations.id)
//   • Existing messages are migrated into a single "Genel" conversation (id=1)
//     so no user data is lost.
// -----------------------------------------------------------------------------
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {

        // 1. Create the conversations table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `conversations` (
                `id`            INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `title`         TEXT    NOT NULL DEFAULT 'Yeni Sohbet',
                `created_at_ms` INTEGER NOT NULL DEFAULT 0,
                `updated_at_ms` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        // 2. Insert a default "Genel" conversation that will own all legacy messages
        val nowMs = System.currentTimeMillis()
        db.execSQL(
            """
            INSERT INTO conversations (id, title, created_at_ms, updated_at_ms)
            VALUES (1, 'Genel', $nowMs, $nowMs)
            """.trimIndent()
        )

        // 3. Add conversation_id column to messages (nullable first — required by SQLite
        //    when adding a column to a non-empty table)
        db.execSQL(
            "ALTER TABLE messages ADD COLUMN conversation_id INTEGER NOT NULL DEFAULT 1"
        )

        // 4. Recreate messages table with proper FK constraint.
        //    SQLite does not support ADD CONSTRAINT via ALTER TABLE,
        //    so we do the standard rename → recreate → copy → drop dance.
        db.execSQL("ALTER TABLE messages RENAME TO messages_old")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `messages` (
                `id`              INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `conversation_id` INTEGER NOT NULL,
                `role`            TEXT    NOT NULL,
                `content`         TEXT    NOT NULL,
                `timestamp_ms`    INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(`conversation_id`) REFERENCES `conversations`(`id`)
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO messages (id, conversation_id, role, content, timestamp_ms)
            SELECT id, conversation_id, role, content, timestamp_ms
            FROM messages_old
            """.trimIndent()
        )

        db.execSQL("DROP TABLE messages_old")

        // 5. Index for FK column (speeds up conversation-scoped queries)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_messages_conversation_id` ON `messages` (`conversation_id`)"
        )
    }
}