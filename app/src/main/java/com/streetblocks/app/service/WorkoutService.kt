package com.streetblocks.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.streetblocks.app.MainActivity
import com.streetblocks.app.R
import com.streetblocks.app.StreetBlocksApp
import com.streetblocks.app.data.model.timeLabel
import com.streetblocks.app.engine.EngineState
import com.streetblocks.app.engine.StepKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * Service de premier plan : garde la séance vivante écran éteint (wake lock),
 * affiche la progression en notification et, si l'appli est en arrière-plan,
 * pousse une alerte plein écran à chaque changement d'étape.
 */
class WorkoutService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastIndex = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as StreetBlocksApp).container
        if (intent?.action == ACTION_STOP) {
            container.engine.stop()
            stopClean()
            return START_NOT_STICKY
        }
        val notif = Notifications.progress(this, container.engine.state.value)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifications.ONGOING_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifications.ONGOING_ID, notif)
        }
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StreetBlocks:session")
                ?.apply { acquire(4 * 60 * 60 * 1000L) }
        }
        if (job == null) {
            job = scope.launch {
                container.engine.state
                    .distinctUntilChangedBy { Triple(it.index, it.remainingSec, it.running to it.paused) }
                    .collect { st ->
                        if (!st.running) {
                            stopClean()
                            return@collect
                        }
                        val nm = NotificationManagerCompat.from(this@WorkoutService)
                        runCatching { nm.notify(Notifications.ONGOING_ID, Notifications.progress(this@WorkoutService, st)) }
                        if (st.index != lastIndex) {
                            val first = lastIndex == -1
                            lastIndex = st.index
                            val background = !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                            if (!first && background && container.settings.settings.value.fullScreenAlerts) {
                                runCatching { nm.notify(Notifications.ALERT_ID, Notifications.alert(this@WorkoutService, st)) }
                            }
                        }
                    }
            }
        }
        return START_NOT_STICKY
    }

    private fun stopClean() {
        job?.cancel()
        job = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        NotificationManagerCompat.from(this).cancel(Notifications.ALERT_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.streetblocks.app.STOP"
    }
}

object Notifications {
    const val CH_ONGOING = "session_ongoing"
    const val CH_ALERT = "session_alerts"
    const val ONGOING_ID = 42
    const val ALERT_ID = 43
    const val EXTRA_OPEN_SESSION = "open_session"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_ONGOING, "Séance en cours", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progression de la séance"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Alertes de séance", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Changement d'exercice, fin de repos (plein écran)"
                enableVibration(true)
                setSound(null, null) // le son est géré par l'appli (flux alarme)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    private fun openIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_SESSION, true)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun stopIntent(ctx: Context): PendingIntent = PendingIntent.getService(
        ctx, 1,
        Intent(ctx, WorkoutService::class.java).setAction(WorkoutService.ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun describe(st: EngineState): Pair<String, String> {
        val step = st.current ?: return "Street Blocks" to "Séance en cours"
        val title = when (step.kind) {
            StepKind.REST -> "REPOS ${timeLabel(st.remainingSec)}"
            StepKind.PREP -> "PRÉPARE-TOI · ${step.title}"
            StepKind.WARMUP -> step.title
            StepKind.WORK -> "${step.title} · ${step.headline}"
            StepKind.END -> "Séance terminée"
        }
        val text = when (step.kind) {
            StepKind.REST -> st.next?.let { "À suivre : ${it.title} ${it.headline.lowercase()}" } ?: "Dernière récupération"
            else -> listOfNotNull(step.detail, step.load, timeLabel(st.remainingSec)).joinToString(" · ")
        } + if (st.paused) " (pause)" else ""
        return title to text
    }

    fun progress(ctx: Context, st: EngineState): Notification {
        val (title, text) = describe(st)
        return NotificationCompat.Builder(ctx, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(1000, (st.overallProgress * 1000).toInt(), false)
            .setContentIntent(openIntent(ctx))
            .addAction(0, "Arrêter", stopIntent(ctx))
            .build()
    }

    fun alert(ctx: Context, st: EngineState): Notification {
        val (title, text) = describe(st)
        return NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(10_000)
            .setContentIntent(openIntent(ctx))
            .setFullScreenIntent(openIntent(ctx), true)
            .build()
    }
}
