package com.jarvis.assistant.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.jarvis.assistant.core.model.ConversationTurn
import com.jarvis.assistant.core.model.TurnRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Persistent conversation storage kept strictly separate from personal user MemoryRepository.
 * Uses Android's built-in SQLiteOpenHelper with explicit schema versioning and migrations.
 */
interface ConversationHistoryStore {
    suspend fun appendTurn(turn: ConversationTurn): Result<Unit>
    suspend fun loadRecentTurns(limit: Int = 50): Result<List<ConversationTurn>>
    suspend fun deleteTurn(turnId: String): Result<Unit>
    suspend fun clearHistory(): Result<Unit>
}

class ConversationDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_TURNS (
                id TEXT PRIMARY KEY,
                role TEXT NOT NULL,
                text TEXT NOT NULL,
                timestamp_millis INTEGER NOT NULL,
                action_id TEXT,
                action_status TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_turns_ts ON $TABLE_TURNS (timestamp_millis DESC)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Explicit incremental migration path preserved for future schema versions.
        if (oldVersion < 1) {
            onCreate(db)
        }
    }

    companion object {
        const val DATABASE_NAME = "jarvis_conversations.db"
        const val DATABASE_VERSION = 1
        const val TABLE_TURNS = "conversation_turns"
    }
}

class SqliteConversationHistoryStore(context: Context) : ConversationHistoryStore {

    private val helper = ConversationDatabaseHelper(context)

    override suspend fun appendTurn(turn: ConversationTurn): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put("id", turn.id)
                put("role", turn.role.name)
                put("text", turn.text)
                put("timestamp_millis", turn.timestampMillis)
                put("action_id", turn.actionId)
                put("action_status", turn.actionStatus)
            }
            helper.writableDatabase.insertOrThrow(
                ConversationDatabaseHelper.TABLE_TURNS,
                null,
                values
            )
            Unit
        }
    }

    override suspend fun loadRecentTurns(limit: Int): Result<List<ConversationTurn>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val safeLimit = limit.coerceIn(1, 200)
                val rows = mutableListOf<ConversationTurn>()
                helper.readableDatabase.rawQuery(
                    """
                    SELECT id, role, text, timestamp_millis, action_id, action_status
                    FROM ${ConversationDatabaseHelper.TABLE_TURNS}
                    ORDER BY timestamp_millis DESC
                    LIMIT ?
                    """.trimIndent(),
                    arrayOf(safeLimit.toString())
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val roleName = cursor.getString(1)
                        val role = runCatching { TurnRole.valueOf(roleName) }.getOrDefault(TurnRole.SYSTEM)
                        rows.add(
                            ConversationTurn(
                                id = cursor.getString(0),
                                role = role,
                                text = cursor.getString(2),
                                timestampMillis = cursor.getLong(3),
                                actionId = cursor.getString(4),
                                actionStatus = cursor.getString(5)
                            )
                        )
                    }
                }
                rows.reversed()
            }
        }

    override suspend fun deleteTurn(turnId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            helper.writableDatabase.delete(
                ConversationDatabaseHelper.TABLE_TURNS,
                "id = ?",
                arrayOf(turnId)
            )
            Unit
        }
    }

    override suspend fun clearHistory(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            helper.writableDatabase.delete(ConversationDatabaseHelper.TABLE_TURNS, null, null)
            Unit
        }
    }
}
