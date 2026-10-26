// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform

import android.content.Context
import androidx.lifecycle.LiveData
import com.myuverr.codecobo.core.*
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.function.BooleanSupplier
import java.util.concurrent.CancellationException

/** Synchronous operations belong on workers; UI consumes facts() and never waits for database I/O. */
class ProfileStore(context: Context) {
    private val persistence = ProfilePersistence.get(context)
    fun facts(): LiveData<ProfileFacts> = persistence.facts()
    fun execute(action: Runnable): Boolean = persistence.execute { action.run() }

    fun get(value: String?): DeviceProfile? {
        val address = address(value) ?: return null
        return persistence.access { it.profile(address)?.profile() }
    }

    fun list(): List<DeviceProfile> = persistence.access { java.util.List.copyOf(it.profiles().mapNotNull(StoredProfile::profile)) }
    fun paused(): Boolean = persistence.access { checkNotNull(it.state()).paused }
    fun active(): Boolean = persistence.access { dao ->
        !checkNotNull(dao.state()).paused && dao.profiles().any { it.enabled && it.profile() != null }
    }

    fun saveVerified(
        value: String?,
        name: String?,
        codecName: String?,
        target: CodecRequest?,
        actual: CodecState?,
        enableRestore: Boolean,
        eligible: BooleanSupplier,
    ): Boolean {
        val address = address(value) ?: return false
        if (target == null || !CodecRules.sane(target) || !CodecRules.matches(target, actual)) return false
        return persistence.access(changed = true) { dao ->
            if (!eligible.asBoolean) return@access false
            val state = checkNotNull(dao.state())
            val revision = Math.addExact(state.revision, 1)
            dao.put(StoredProfile(
                address, name ?: address, codecName ?: "System codec #" + target.type(),
                target.type(), target.sampleRate(), target.bitsPerSample(), target.channelMode(), target.ldacQuality(),
                enableRestore, revision,
            ))
            dao.put(state.copy(revision = revision))
            if (!eligible.asBoolean) throw CancellationException("Profile request expired")
            true
        }
    }

    fun setEnabled(value: String?, enabled: Boolean): Boolean {
        val address = address(value) ?: return false
        return persistence.access(changed = true) { dao ->
            val previous = dao.profile(address) ?: return@access false
            if (previous.profile() == null) return@access false
            if (previous.enabled != enabled) {
                val state = checkNotNull(dao.state())
                val revision = Math.addExact(state.revision, 1)
                dao.put(previous.copy(enabled = enabled, revision = revision))
                dao.put(state.copy(revision = revision))
            }
            true
        }
    }

    fun remove(value: String?, revision: Long): Boolean {
        val address = address(value) ?: return false
        return persistence.access(changed = true) { dao ->
            if (dao.deleteProfile(address, revision) == 0) return@access false
            dao.deleteSession(address)
            dao.deleteResult(address)
            true
        }
    }

    fun setPaused(value: Boolean) {
        persistence.access(changed = true) { dao ->
            val state = checkNotNull(dao.state())
            if (state.paused != value) {
                dao.deleteSessions()
                dao.put(state.copy(paused = value))
            }
        }
    }

    fun connection(value: String?, connected: Boolean): Long {
        val address = address(value) ?: return 0
        return persistence.access { dao ->
            val previous = dao.session(address)
            if (previous?.connected == connected && previous.generation > 0)
                return@access previous.generation
            val state = checkNotNull(dao.state())
            val generation = Math.addExact(state.generation, 1)
            dao.put(StoredSession(address, connected, generation, 0))
            dao.put(state.copy(generation = generation))
            generation
        }
    }

    fun generation(value: String?): Long {
        val address = address(value) ?: return 0
        return persistence.access { dao ->
            val session = dao.session(address)
            if (session?.connected == true) session.generation else 0
        }
    }

    fun resetConnections() { persistence.access { it.deleteSessions() } }

    private fun current(dao: ProfileDao, address: String, generation: Long, revision: Long): Boolean {
        val profile = dao.profile(address) ?: return false
        val session = dao.session(address) ?: return false
        return profile.profile() != null && profile.enabled && profile.revision == revision &&
            !checkNotNull(dao.state()).paused && session.connected && session.generation == generation
    }

    fun current(value: String?, generation: Long, revision: Long): Boolean {
        val address = address(value) ?: return false
        return persistence.access { current(it, address, generation, revision) }
    }

    fun claim(value: String?, generation: Long, revision: Long): Boolean {
        val address = address(value) ?: return false
        return persistence.access { dao ->
            if (!current(dao, address, generation, revision)) return@access false
            val session = checkNotNull(dao.session(address))
            if (session.attempted == revision) return@access false
            dao.put(session.copy(attempted = revision))
            true
        }
    }

    fun recordResult(value: String?, generation: Long, revision: Long, message: String?): Boolean {
        val address = address(value) ?: return false
        return persistence.access { dao ->
            if (!current(dao, address, generation, revision)) return@access false
            dao.put(StoredResult(address, DateFormat.getDateTimeInstance().format(Date()) + "\n" + bounded(message)))
            true
        }
    }

    fun describeSession(value: String?): String {
        val address = address(value) ?: return "stored=none"
        return try {
            persistence.access { dao ->
                val session = dao.session(address) ?: return@access "stored=none"
                "stored={connected=${session.connected} generation=${session.generation} attempted=${session.attempted}}"
            }
        } catch (_: RuntimeException) { "stored=unavailable" }
    }

    fun result(value: String?): String? {
        val address = address(value) ?: return null
        return persistence.access { it.result(address) }
    }

    fun backgroundError(): String = persistence.access { checkNotNull(it.state()).backgroundError }
    fun backgroundError(message: String?) {
        persistence.execute {
            persistence.access { dao -> dao.put(checkNotNull(dao.state()).copy(backgroundError = bounded(message))) }
        }
    }

    companion object {
        @JvmStatic
        fun address(value: String?): String? =
            value?.takeIf { it.matches(Regex("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}")) }?.uppercase(Locale.ROOT)

        private fun bounded(value: String?) = value.orEmpty().take(2048)
    }
}
