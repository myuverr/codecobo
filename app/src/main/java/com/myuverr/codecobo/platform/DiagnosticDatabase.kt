// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform

import androidx.room.*

@Entity(tableName = "events")
internal data class DiagnosticEvent(
    val line: String,
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
)

@Entity(tableName = "journal_state")
internal data class JournalState(
    @PrimaryKey val id: Int = 1,
    val dropped: Long = 0,
)

@Dao
internal abstract class DiagnosticDao {
    @Insert abstract fun insert(event: DiagnosticEvent)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract fun putState(state: JournalState)
    @Query("SELECT * FROM journal_state WHERE id = 1") abstract fun state(): JournalState?
    @Query("SELECT * FROM events ORDER BY sequence") abstract fun events(): List<DiagnosticEvent>
    @Query("DELETE FROM events WHERE sequence NOT IN (SELECT sequence FROM events ORDER BY sequence DESC LIMIT :limit)")
    abstract fun trim(limit: Int): Int
    @Query("UPDATE journal_state SET dropped = dropped + :count WHERE id = 1")
    abstract fun addDropped(count: Int)
    @Query("DELETE FROM events") abstract fun deleteEvents()
    @Query("UPDATE journal_state SET dropped = 0 WHERE id = 1") abstract fun resetDropped()

    @Transaction
    open fun append(line: String, limit: Int) {
        insert(DiagnosticEvent(line))
        val removed = trim(limit)
        if (removed > 0) addDropped(removed)
    }

    @Transaction
    open fun clear() {
        deleteEvents()
        resetDropped()
    }
}

@Database(entities = [DiagnosticEvent::class, JournalState::class], version = 1)
internal abstract class DiagnosticDatabase : RoomDatabase() {
    abstract fun events(): DiagnosticDao
}
