// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.room.*
import com.myuverr.codecobo.core.CodecRequest
import com.myuverr.codecobo.core.CodecRules
import com.myuverr.codecobo.core.DeviceProfile
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException

@Entity(tableName = "profiles")
internal data class StoredProfile(
    @PrimaryKey val address: String,
    val name: String, val codecName: String,
    val codec: Int, val rate: Int, val bits: Int, val channel: Int, val quality: Long,
    val enabled: Boolean, val revision: Long,
) {
    fun profile(): DeviceProfile? {
        val request = CodecRequest(codec, rate, bits, channel, quality)
        if (ProfileStore.address(address) != address || revision <= 0 || !CodecRules.sane(request)) return null
        return DeviceProfile(address, name, codecName, request, enabled, revision)
    }
}

@Entity(tableName = "sessions")
internal data class StoredSession(
    @PrimaryKey val address: String,
    val connected: Boolean, val generation: Long, val attempted: Long,
)

@Entity(tableName = "restore_results")
internal data class StoredResult(@PrimaryKey val address: String, val text: String)

@Entity(tableName = "restore_state")
internal data class RestoreState(
    @PrimaryKey val id: Int = 1,
    val paused: Boolean = false,
    val revision: Long = 0,
    val generation: Long = 0,
    val backgroundError: String = "",
)

@Dao
internal interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE address = :address") fun profile(address: String): StoredProfile?
    @Query("SELECT * FROM profiles ORDER BY address") fun profiles(): List<StoredProfile>
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun put(profile: StoredProfile)
    @Query("DELETE FROM profiles WHERE address = :address AND revision = :revision")
    fun deleteProfile(address: String, revision: Long): Int
    @Query("SELECT * FROM restore_state WHERE id = 1") fun state(): RestoreState?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun put(state: RestoreState)
    @Query("SELECT * FROM sessions WHERE address = :address") fun session(address: String): StoredSession?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun put(session: StoredSession)
    @Query("DELETE FROM sessions WHERE address = :address") fun deleteSession(address: String)
    @Query("DELETE FROM sessions") fun deleteSessions()
    @Query("SELECT text FROM restore_results WHERE address = :address") fun result(address: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun put(result: StoredResult)
    @Query("DELETE FROM restore_results WHERE address = :address") fun deleteResult(address: String)
}

@Database(
    entities = [StoredProfile::class, StoredSession::class, StoredResult::class, RestoreState::class],
    version = 1,
)
internal abstract class ProfileDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
}

/** Immutable committed facts; the observable holder belongs to ProfilePersistence. */
data class ProfileFacts(val profiles: List<DeviceProfile>, val paused: Boolean) {
    fun profile(address: String?): DeviceProfile? = profiles.firstOrNull { it.address() == address }
}

internal class ProfilePersistence private constructor(private val context: Context) {
    private var database: ProfileDatabase? = null
    private val committed = MutableLiveData<ProfileFacts>()

    private fun database(): ProfileDatabase {
        database?.let { return it }
        val opened = Room.databaseBuilder(context, ProfileDatabase::class.java, "profiles.db").build()
        try {
            opened.runInTransaction {
                val dao = opened.profiles()
                if (dao.state() == null) dao.put(RestoreState())
            }
        } catch (error: RuntimeException) {
            opened.close()
            throw error
        }
        database = opened
        return opened
    }

    private fun readFacts(db: ProfileDatabase): ProfileFacts {
        val dao = db.profiles()
        return ProfileFacts(java.util.List.copyOf(dao.profiles().mapNotNull { it.profile() }), checkNotNull(dao.state()).paused)
    }

    fun facts(): LiveData<ProfileFacts> {
        execute {
            val db = database()
            val snapshot = db.runInTransaction(Callable { readFacts(db) })
            committed.postValue(snapshot)
        }
        return committed
    }

    fun <T> access(changed: Boolean = false, action: (ProfileDao) -> T): T {
        val work = Callable {
            val db = database()
            var snapshot: ProfileFacts? = null
            val result = db.runInTransaction(Callable {
                val value = action(db.profiles())
                if (changed || committed.value == null) snapshot = readFacts(db)
                value
            })
            snapshot?.let(committed::postValue)
            result
        }
        if (Thread.currentThread() === ioThread) return work.call()
        val future = io.submit(work)
        try {
            return future.get()
        } catch (error: InterruptedException) {
            future.cancel(false)
            Thread.currentThread().interrupt()
            throw IllegalStateException("Profile operation interrupted", error)
        } catch (error: ExecutionException) {
            throw (error.cause as? RuntimeException ?: IllegalStateException("Profile operation failed", error.cause))
        }
    }

    fun execute(action: () -> Unit): Boolean {
        val task = Runnable {
            try { action() }
            catch (error: RuntimeException) {
                RestoreTrace.failure(context, "", "profile_storage", "storage.operation_failed", error)
            }
        }
        return try {
            if (Thread.currentThread() === ioThread) task.run() else io.execute(task)
            true
        } catch (error: RejectedExecutionException) {
            RestoreTrace.failure(context, "", "profile_storage", "storage.queue_full", error)
            false
        }
    }

    companion object {
        @Volatile private var ioThread: Thread? = null
        private val io = ThreadPoolExecutor(
            1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(128),
            { task -> Thread(task, "profiles-io").apply { isDaemon = true; ioThread = this } },
        )
        @android.annotation.SuppressLint("StaticFieldLeak") // Only applicationContext is retained, never an Activity.
        private var current: ProfilePersistence? = null

        @Synchronized
        fun get(context: Context): ProfilePersistence {
            return current ?: ProfilePersistence(context.applicationContext).also { current = it }
        }
    }
}
