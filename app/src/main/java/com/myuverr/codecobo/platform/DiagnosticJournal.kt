// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform

import android.content.Context
import android.util.Log
import androidx.room.Room
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

/** Owns diagnostic I/O only; profile commits never share this queue or database. */
internal object DiagnosticJournal {
    const val MAX_ENTRIES = 160
    const val MAX_LINE = 480
    private val writer = ThreadPoolExecutor(
        1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(512),
        { task -> Thread(task, "diagnostics-io").apply { isDaemon = true } },
    )
    private val queueDrops = AtomicLong()
    private val storageFailures = AtomicLong()
    private var database: DiagnosticDatabase? = null
    data class Snapshot(
        val lines: List<String>, val dropped: Long,
        val queueDropped: Long, val failures: Long,
    )

    private fun database(context: Context): DiagnosticDatabase {
        database?.let { return it }
        val opened = Room.databaseBuilder(context.applicationContext, DiagnosticDatabase::class.java, "diagnostics.db").build()
        try {
            opened.runInTransaction {
                val dao = opened.events()
                if (dao.state() == null) dao.putState(JournalState())
            }
        } catch (error: RuntimeException) {
            opened.close()
            throw error
        }
        database = opened
        return opened
    }

    @JvmStatic
    fun append(context: Context, line: String) {
        val app = context.applicationContext
        try {
            writer.execute {
                try {
                    database(app).events().append(line, MAX_ENTRIES)
                } catch (_: RuntimeException) {
                    storageFailures.incrementAndGet()
                    Log.w("RestoreTrace", "Diagnostic write unavailable")
                }
            }
        } catch (_: RejectedExecutionException) {
            queueDrops.incrementAndGet()
        }
    }

    @JvmStatic
    fun snapshot(context: Context): Snapshot {
        val app = context.applicationContext
        val future = writer.submit(Callable {
            val dao = database(app).events()
            val state = checkNotNull(dao.state())
            val lines = dao.events().map(DiagnosticEvent::line)
            Snapshot(lines, state.dropped, queueDrops.get(), storageFailures.get())
        })
        try {
            return future.get(10, TimeUnit.SECONDS)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Diagnostic snapshot interrupted", error)
        } catch (error: ExecutionException) {
            throw IllegalStateException("Diagnostic snapshot unavailable", error.cause)
        } catch (error: TimeoutException) {
            future.cancel(false)
            throw IllegalStateException("Diagnostic snapshot deadline exceeded", error)
        }
    }

    @JvmStatic
    fun clear(context: Context) {
        val app = context.applicationContext
        try {
            writer.execute {
                try {
                    database(app).events().clear()
                    queueDrops.set(0)
                    storageFailures.set(0)
                } catch (_: RuntimeException) {
                    storageFailures.incrementAndGet()
                    Log.w("RestoreTrace", "Diagnostic clear unavailable")
                }
            }
        } catch (_: RejectedExecutionException) {
            storageFailures.incrementAndGet()
            Log.w("RestoreTrace", "Diagnostic clear queue full")
        }
    }
}
