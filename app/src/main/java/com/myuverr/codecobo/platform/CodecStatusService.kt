// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import com.myuverr.codecobo.R
import com.myuverr.codecobo.presentation.AudioText
import java.util.concurrent.Executors

/** One observer/reader; refresh requests coalesce without invalidating the connection's observations. */
class CodecStatusService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var bluetooth: BluetoothRepository? = null
    private var bluetoothLease: BluetoothRuntime.Lease? = null
    @Volatile private var closed = false
    private var foreground = false
    private var policyRegistered = false
    private var presentationRegistered = false
    private var reading = false
    private var dirty = false
    private var timedOut = false
    private var epoch = 0L
    private var workId = 0L
    private var sequence = 0L
    private var lastStarted = Long.MIN_VALUE / 2
    private var scheduledAt: Long? = null
    private var retryAttempt = 0
    private var scope: ReadScope? = null
    private var latest: Frame? = null
    private var posted: Frame? = null
    private var watchdog: Runnable? = null
    private val readScheduled = Runnable { scheduledAt = null; readNext() }

    private data class ReadScope(val connection: Long, val activity: Long, val locales: String)
    private data class Frame(
        val title: String = "", val body: String, val error: String = "",
        val observedAtMillis: Long = System.currentTimeMillis(),
        val ready: Boolean = false, val retryable: Boolean = false, val deviceCount: Int = 0,
    )

    private val policy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.getBooleanExtra(NotificationManager.EXTRA_BLOCKED_STATE, false) != true) return
            if (intent.action == NotificationManager.ACTION_APP_BLOCK_STATE_CHANGED ||
                intent.action == NotificationManager.ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED &&
                intent.getStringExtra(NotificationManager.EXTRA_NOTIFICATION_CHANNEL_ID) == StatusNotifications.CHANNEL)
                stopCurrent("Status notifications blocked by Android")
        }
    }
    private val presentation = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_ON && intent?.action != Intent.ACTION_USER_PRESENT) return
            refresh()
            if (allowed()) latest?.let { publish(it, force = true) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ServiceDiagnostics.record(this, "codec_status_service", "notification.handler_created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requested = intent?.getLongExtra("generation", -1) ?: StatusNotifications.generation(this)
        ServiceDiagnostics.record(this, "codec_status_service",
            if (intent == null) "notification.handler_recreated" else "notification.handler_entered")
        if (requested != StatusNotifications.generation(this)) {
            if (!foreground) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (closed || !StatusNotifications.enabled(this)) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        try {
            if (!StatusNotifications.visible(this) || !StatusNotifications.nearby(this)) {
                stopCurrent("Status notification not visible or Nearby devices permission revoked")
                return START_NOT_STICKY
            }
            if (epoch != requested) {
                epoch = requested
                latest = null
                posted = null
                retryAttempt = 0
            }
            if (!foreground) {
                getSystemService(NotificationManager::class.java).createNotificationChannel(
                    NotificationChannel(StatusNotifications.CHANNEL, getString(R.string.notification_status_channel),
                        NotificationManager.IMPORTANCE_LOW))
                if (!StatusNotifications.visible(this)) {
                    stopCurrent("Status notification channel blocked")
                    return START_NOT_STICKY
                }
                val initial = Frame(body = getString(R.string.notification_status_reading))
                startForeground(StatusNotifications.ID,
                    CodecStatusNotification.build(this, initial.title, initial.body, initial.observedAtMillis),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                foreground = true
                latest = initial
                posted = initial
                ServiceDiagnostics.record(this, "codec_status_service", "notification.foreground_promoted")
            }
            if (!policyRegistered) {
                registerReceiver(policy, IntentFilter(NotificationManager.ACTION_APP_BLOCK_STATE_CHANGED).apply {
                    addAction(NotificationManager.ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED)
                }, Context.RECEIVER_NOT_EXPORTED)
                policyRegistered = true
            }
            if (!presentationRegistered) {
                // USER_PRESENT is sent by SystemUI; all accepted actions are protected system broadcasts.
                registerReceiver(presentation, IntentFilter(Intent.ACTION_SCREEN_ON).apply {
                    addAction(Intent.ACTION_USER_PRESENT)
                }, Context.RECEIVER_EXPORTED)
                presentationRegistered = true
            }
            if (bluetooth == null) {
                bluetoothLease = BluetoothRuntime.acquire(this, ::refresh)
                bluetooth = requireNotNull(bluetoothLease).repository()
            }
            requireNotNull(bluetooth).bind()
            refresh()
        } catch (error: RuntimeException) {
            failCurrent("Status service start failed", error)
        } catch (error: LinkageError) {
            failCurrent("Status service start failed", error)
        }
        return if (foreground) START_STICKY else START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!allowed()) return
        try {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(StatusNotifications.CHANNEL, getString(R.string.notification_status_channel),
                    NotificationManager.IMPORTANCE_LOW))
            refresh()
        } catch (error: RuntimeException) {
            RestoreTrace.failure(this, "", "codec_status_service", "notification.locale_refresh_failed", error)
        }
    }

    private fun allowed() = !closed && foreground && StatusNotifications.enabled(this) &&
        epoch == StatusNotifications.generation(this)

    private fun currentScope(): ReadScope {
        val repository = requireNotNull(bluetooth)
        return ReadScope(repository.connectionGeneration(), repository.activityGeneration(), resources.configuration.locales.toLanguageTags())
    }

    private fun syncScope() {
        val current = currentScope()
        if (scope == current) return
        scope = current
        latest = null
        posted = null
        retryAttempt = 0
        main.removeCallbacks(readScheduled)
        scheduledAt = null
    }

    private fun refresh() {
        if (!allowed()) return
        if (!StatusNotifications.visible(this) || !StatusNotifications.nearby(this)) {
            stopCurrent("Notification or Nearby devices permission revoked")
            return
        }
        val repository = bluetooth ?: return
        syncScope()
        if (!repository.enabled()) {
            dirty = false
            main.removeCallbacks(readScheduled)
            scheduledAt = null
            publish(idleFrame())
            return
        }
        dirty = true
        sequence++
        RestoreTrace.event(this, "", "codec_observer", "notification.read_requested",
            "sequence=$sequence generation=${repository.connectionGeneration()}")
        if (latest == null) publish(Frame(body = getString(R.string.notification_status_reading)))
        if (!reading) scheduleRead(0)
    }

    private fun scheduleRead(delay: Long) {
        if (!allowed() || reading || !dirty) return
        val now = SystemClock.uptimeMillis()
        val at = maxOf(now + delay, lastStarted + MIN_READ_INTERVAL)
        if (scheduledAt?.let { it <= at } == true) return
        main.removeCallbacks(readScheduled)
        scheduledAt = at
        if (at <= now) {
            scheduledAt = null
            readNext()
        } else main.postDelayed(readScheduled, at - now)
    }

    private fun readNext() {
        if (!allowed() || !dirty || reading) return
        if (!StatusNotifications.visible(this) || !StatusNotifications.nearby(this)) {
            stopCurrent("Notification or Nearby devices permission revoked")
            return
        }
        syncScope()
        dirty = false
        reading = true
        timedOut = false
        lastStarted = SystemClock.uptimeMillis()
        val job = ++workId
        val requestedEpoch = epoch
        val requestedScope = currentScope()
        val timeout = Runnable {
            if (allowed() && reading && workId == job && requestedEpoch == epoch && requestedScope == currentScope()) {
                timedOut = true
                StatusNotifications.note(this, "System query timed out; waiting for return. Status monitoring can be disabled.")
                publish(Frame(title = latest?.title.orEmpty(), body = getString(R.string.notification_status_timeout)))
            }
        }
        watchdog = timeout
        main.postDelayed(timeout, 15_000)
        worker.execute {
            val result = try { readFrame() }
            catch (error: Exception) {
                Frame(body = getString(R.string.notification_status_read_error, error.javaClass.simpleName),
                    error = error.toString(), retryable = true)
            } catch (error: LinkageError) {
                Frame(body = getString(R.string.notification_status_read_error, error.javaClass.simpleName),
                    error = error.toString(), retryable = true)
            }
            main.post {
                if (workId == job) complete(result, requestedScope, requestedEpoch, timeout)
            }
        }
    }

    private fun complete(result: Frame, requestedScope: ReadScope, requestedEpoch: Long, timeout: Runnable) {
        main.removeCallbacks(timeout)
        watchdog = null
        reading = false
        if (!allowed()) return
        if (requestedEpoch != epoch) {
            if (dirty) scheduleRead(0)
            return
        }
        val followUp = dirty
        dirty = false
        if (requestedScope != currentScope()) {
            RestoreTrace.event(this, "", "codec_observer", "notification.read_discarded", "reason=scope_changed")
            syncScope()
            dirty = true
            scheduleRead(0)
            return
        }
        if (timedOut) {
            StatusNotifications.note(this, "Timed-out query returned; stale result discarded")
            publish(Frame(title = latest?.title.orEmpty(), body = getString(R.string.notification_status_expired_retry)))
            if (followUp) { dirty = true; scheduleRead(0) } else recover()
            return
        }
        StatusNotifications.note(this, result.error)
        publish(result)
        RestoreTrace.event(this, "", "codec_observer", "notification.observation_published",
            "count=${result.deviceCount} observationTime=${result.observedAtMillis}")
        if (result.ready) retryAttempt = 0
        if (followUp) { dirty = true; scheduleRead(0) }
        else if (result.retryable) recover()
    }

    private fun recover() {
        if (retryAttempt >= RETRY_DELAYS.size || !allowed()) return
        val delay = RETRY_DELAYS[retryAttempt++]
        dirty = true
        RestoreTrace.event(this, "", "codec_observer", "notification.retry_scheduled",
            "attempt=$retryAttempt delay_ms=$delay")
        scheduleRead(delay)
    }

    private fun readFrame(): Frame {
        val repository = requireNotNull(bluetooth)
        if (!repository.enabled()) return idleFrame()
        var devices = emptyList<BluetoothRepository.DeviceInfo>()
        for (attempt in 0 until 20) {
            if (closed) break
            try {
                devices = repository.devices().filterNot { repository.disconnectedHint(it.address()) }
                break
            } catch (error: SecurityException) { throw error }
            catch (error: IllegalStateException) {
                if (attempt == 19) throw error
                Thread.sleep(500)
            }
        }
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        if (devices.isEmpty()) return idleFrame()
        val device = devices.firstOrNull { it.active() }
            ?: return Frame(body = getString(R.string.notification_active_unavailable), retryable = true, deviceCount = devices.size)
        if (closed || Thread.currentThread().isInterrupted) throw InterruptedException()
        return try {
            val actual = repository.read(device.address())
            val audio = AudioText(resources)
            Frame(device.name(), getString(R.string.notification_audio_line, actual.codecName(),
                audio.rate(actual.actual().sampleRate()), audio.suffix(actual.actual())), ready = true, deviceCount = devices.size)
        } catch (error: Exception) {
            Frame(device.name(), getString(R.string.notification_device_unreadable), error.toString(),
                retryable = true, deviceCount = devices.size)
        } catch (error: LinkageError) {
            Frame(device.name(), getString(R.string.notification_device_unreadable), error.toString(),
                retryable = true, deviceCount = devices.size)
        }
    }

    private fun idleFrame() = Frame(
        title = getString(R.string.app_name), body = getString(R.string.home_disconnected),
        observedAtMillis = 0, ready = true,
    )

    private fun publish(frame: Frame, force: Boolean = false) {
        if (!allowed()) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ||
            !StatusNotifications.visible(this)) {
            stopCurrent("Status notifications blocked by Android")
            return
        }
        latest = frame
        if (!force && posted?.let { it.title == frame.title && it.body == frame.body } == true) return
        try {
            getSystemService(NotificationManager::class.java).notify(StatusNotifications.ID,
                CodecStatusNotification.build(this, frame.title, frame.body, frame.observedAtMillis))
            posted = frame
        } catch (error: RuntimeException) {
            failCurrent("Status notification posting failed", error)
        }
    }

    private fun cancelScheduled() {
        dirty = false
        main.removeCallbacks(readScheduled)
        scheduledAt = null
        watchdog?.let(main::removeCallbacks)
        watchdog = null
    }

    private fun failCurrent(reason: String, error: Throwable) {
        RestoreTrace.failure(this, "", "codec_status_service", "notification.runtime_failed", error)
        try {
            StatusNotifications.note(this, "$reason: $error")
        } finally {
            stopObserver()
        }
    }

    private fun stopCurrent(reason: String) {
        try {
            StatusNotifications.disable(this, reason)
        } finally {
            stopObserver()
        }
    }

    private fun stopObserver() {
        cancelScheduled()
        foreground = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(StatusNotifications.ID)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        closed = true
        cancelScheduled()
        worker.shutdownNow()
        if (policyRegistered) unregisterReceiver(policy)
        if (presentationRegistered) unregisterReceiver(presentation)
        bluetoothLease?.close()
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(StatusNotifications.ID)
        ServiceDiagnostics.record(this, "codec_status_service", "notification.handler_destroyed")
        super.onDestroy()
    }

    companion object {
        const val REFRESH = "com.myuverr.codecobo.REFRESH_STATUS"
        private const val MIN_READ_INTERVAL = 500L
        private val RETRY_DELAYS = longArrayOf(500, 1500, 3000)
    }
}
