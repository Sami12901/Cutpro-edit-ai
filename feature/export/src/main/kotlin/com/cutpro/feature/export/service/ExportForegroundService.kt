package com.cutpro.feature.export.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.cutpro.core.export.Media3VideoExportEngine
import com.cutpro.core.export.VideoExportEngine
import com.cutpro.core.export.model.ExportPlan
import com.cutpro.core.export.model.ExportProgress
import com.cutpro.feature.export.model.ExportProgressState
import com.cutpro.feature.export.model.ExportStatus
import com.cutpro.feature.export.storage.ExportShareHelper
import com.cutpro.feature.export.storage.MediaStoreVideoSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Android Foreground Service executing Media3 Transformer exports with partial WakeLock,
 * real-time progress reporting, system notifications, MediaStore scoped storage, and atomic cancellation.
 * Strictly zero emojis.
 */
@OptIn(UnstableApi::class)
class ExportForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var wakeLockManager: ExportWakeLockManager
    private lateinit var notificationManager: ExportNotificationManager
    private lateinit var mediaStoreSaver: MediaStoreVideoSaver

    private var activeExportJob: Job? = null
    private var activeExportEngine: VideoExportEngine? = null
    private var activeExportPlan: ExportPlan? = null
    private var tempOutputFile: File? = null
    private var activeProjectTitle: String = "Project"

    override fun onCreate() {
        super.onCreate()
        wakeLockManager = ExportWakeLockManager(this)
        notificationManager = ExportNotificationManager(this)
        mediaStoreSaver = MediaStoreVideoSaver(this)
        activeInstance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_EXPORT -> {
                val plan = pendingExportPlan ?: activeExportPlan
                if (plan != null) {
                    startExportInternal(plan)
                } else {
                    startServiceForeground()
                    wakeLockManager.acquire()
                }
            }
            ACTION_CANCEL_EXPORT -> {
                cancelExportInternal()
            }
        }
        return START_NOT_STICKY
    }

    private fun startServiceForeground() {
        val initialNotif = notificationManager.buildOngoingNotification(
            projectTitle = activeProjectTitle,
            progressPercent = 0,
            elapsedMs = 0L,
            etaMs = null
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                ExportNotificationManager.NOTIFICATION_ID,
                initialNotif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            )
        } else {
            startForeground(ExportNotificationManager.NOTIFICATION_ID, initialNotif)
        }
    }

    private fun startExportInternal(plan: ExportPlan) {
        activeExportPlan = plan
        activeProjectTitle = plan.project.title
        tempOutputFile = plan.outputFile
        pendingExportPlan = null

        startServiceForeground()
        wakeLockManager.acquire()

        _exportState.value = ExportProgressState(status = ExportStatus.PREPARING)

        val engine = exportEngineFactory(this)
        activeExportEngine = engine

        activeExportJob?.cancel()
        activeExportJob = serviceScope.launch {
            try {
                engine.exportVideo(plan).collect { progress ->
                    when (progress) {
                        is ExportProgress.Starting -> {
                            _exportState.update { it.copy(status = ExportStatus.PREPARING) }
                        }
                        is ExportProgress.Processing -> {
                            val isFinalizing = progress.percentage >= 100
                            _exportState.update {
                                it.copy(
                                    status = if (isFinalizing) ExportStatus.FINALIZING else ExportStatus.RENDERING,
                                    progressPercentage = progress.percentage,
                                    elapsedTimeMs = progress.elapsedMs,
                                    estimatedRemainingMs = progress.estimatedRemainingMs
                                )
                            }
                            notificationManager.updateThrottled(
                                projectTitle = activeProjectTitle,
                                progressPercent = progress.percentage,
                                elapsedMs = progress.elapsedMs,
                                etaMs = progress.estimatedRemainingMs
                            )
                        }
                        is ExportProgress.Completed -> {
                            val savedUriResult = mediaStoreSaver.saveVideoToMovies(plan.outputFile, activeProjectTitle)
                            cleanupTempFile()
                            wakeLockManager.release()
                            stopServiceForeground()

                            savedUriResult.onSuccess { uri ->
                                _exportState.value = ExportProgressState(
                                    status = ExportStatus.COMPLETED,
                                    progressPercentage = 100,
                                    outputFile = plan.outputFile,
                                    outputUri = uri
                                )
                                val shareIntent = ExportShareHelper.createShareIntent(uri, activeProjectTitle)
                                val sharePendingIntent = PendingIntent.getActivity(
                                    this@ExportForegroundService,
                                    0,
                                    shareIntent,
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                )
                                notificationManager.notifyCompleted(activeProjectTitle, sharePendingIntent)
                            }.onFailure { err ->
                                _exportState.value = ExportProgressState(
                                    status = ExportStatus.FAILED,
                                    errorMessage = err.message ?: "Failed to save video to storage"
                                )
                                notificationManager.notifyFailed(activeProjectTitle, err.message ?: "Storage failure")
                            }
                            stopSelf()
                        }
                        is ExportProgress.Failed -> {
                            cleanupTempFile()
                            wakeLockManager.release()
                            stopServiceForeground()
                            _exportState.value = ExportProgressState(
                                status = ExportStatus.FAILED,
                                errorMessage = progress.message
                            )
                            notificationManager.notifyFailed(activeProjectTitle, progress.message)
                            stopSelf()
                        }
                        is ExportProgress.Cancelled -> {
                            cleanupTempFile()
                            wakeLockManager.release()
                            stopServiceForeground()
                            _exportState.value = ExportProgressState(status = ExportStatus.CANCELLED)
                            notificationManager.cancelNotification()
                            stopSelf()
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cooperative cancellation; do not treat as export failure
                throw e
            } catch (e: Exception) {
                cleanupTempFile()
                wakeLockManager.release()
                stopServiceForeground()
                _exportState.value = ExportProgressState(
                    status = ExportStatus.FAILED,
                    errorMessage = e.message ?: "Unexpected export failure"
                )
                notificationManager.notifyFailed(activeProjectTitle, e.message ?: "Export failure")
                stopSelf()
            }
        }
    }

    fun cancelExport() {
        cancelExportInternal()
    }

    private fun cancelExportInternal() {
        activeExportPlan?.let { plan ->
            activeExportEngine?.cancelExport(plan.exportId)
            cleanupFile(plan.outputFile)
        }
        cleanupTempFile()
        activeExportJob?.cancel()
        activeExportJob = null

        wakeLockManager.release()
        stopServiceForeground()

        _exportState.value = ExportProgressState(status = ExportStatus.CANCELLED)
        notificationManager.cancelNotification()
        stopSelf()
    }

    private fun cleanupTempFile() {
        try {
            tempOutputFile?.let {
                if (it.exists()) {
                    it.delete()
                }
            }
        } catch (_: Exception) {}
        tempOutputFile = null
    }

    private fun cleanupFile(file: File) {
        try {
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {}
    }

    private fun stopServiceForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        cancelExportInternal()
        serviceScope.cancel()
        if (activeInstance == this) {
            activeInstance = null
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_EXPORT = "com.cutpro.action.START_EXPORT"
        const val ACTION_CANCEL_EXPORT = "com.cutpro.action.CANCEL_EXPORT"

        internal var pendingExportPlan: ExportPlan? = null
        internal var activeInstance: ExportForegroundService? = null

        var exportEngineFactory: (Context) -> VideoExportEngine = { Media3VideoExportEngine(it) }

        private val _exportState = MutableStateFlow(ExportProgressState())
        val exportState: StateFlow<ExportProgressState> = _exportState.asStateFlow()

        fun start(context: Context) {
            _exportState.value = ExportProgressState(status = ExportStatus.PREPARING)
            val intent = Intent(context, ExportForegroundService::class.java).apply {
                action = ACTION_START_EXPORT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startExport(context: Context, plan: ExportPlan) {
            pendingExportPlan = plan
            _exportState.value = ExportProgressState(status = ExportStatus.PREPARING)
            val intent = Intent(context, ExportForegroundService::class.java).apply {
                action = ACTION_START_EXPORT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context) {
            val intent = Intent(context, ExportForegroundService::class.java).apply {
                action = ACTION_CANCEL_EXPORT
            }
            context.startService(intent)
            activeInstance?.cancelExport()
            _exportState.value = ExportProgressState(status = ExportStatus.CANCELLED)
        }

        fun resetState() {
            _exportState.value = ExportProgressState()
            pendingExportPlan = null
            activeInstance = null
            exportEngineFactory = { Media3VideoExportEngine(it) }
        }
    }
}
