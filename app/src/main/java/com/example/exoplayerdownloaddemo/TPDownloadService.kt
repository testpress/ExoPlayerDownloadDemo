package com.example.exoplayerdownloaddemo

import android.Manifest
import android.app.Notification
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.RequiresPermission
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
class TPDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    ExoPlayerDownloadDemoApp.DOWNLOAD_NOTIFICATION_CHANNEL_ID,
    R.string.download_notification_channel_name,
    0
) {
    private lateinit var downloadNotificationHelper: DownloadNotificationHelper
    
    override fun onCreate() {
        downloadNotificationHelper = DownloadNotificationHelper(
            this,
            ExoPlayerDownloadDemoApp.DOWNLOAD_NOTIFICATION_CHANNEL_ID
        )
        super.onCreate()
    }

    override fun getDownloadManager(): DownloadManager {
        Log.d(TAG, "getDownloadManager called")
        return ExoPlayerDownloadDemoApp.downloadManager
            .apply {
                addListener(TerminalStateNotificationHelper(this@TPDownloadService, downloadNotificationHelper))
            }
    }

    @RequiresPermission(Manifest.permission.RECEIVE_BOOT_COMPLETED)
    override fun getScheduler(): Scheduler? {
        return if (DOWNLOAD_SCHEDULER_JOB_ID != 0) {
            PlatformScheduler(this, DOWNLOAD_SCHEDULER_JOB_ID)
        } else {
            null
        }
    }

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        return downloadNotificationHelper.buildProgressNotification(
            this,
            R.drawable.ic_download,
            null,
            null,
            downloads,
            notMetRequirements
        )
    }

    /**
     * Helper class to notify about completed downloads
     */
    private class TerminalStateNotificationHelper(
        private val context: Context,
        private val notificationHelper: DownloadNotificationHelper
    ) : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            Log.d(TAG, "Download changed state: ${download.state}")
            if (download.state == Download.STATE_COMPLETED) {
                val notification = notificationHelper.buildDownloadCompletedNotification(
                    context,
                    R.drawable.ic_download_done,
                    null,
                    download.request.id
                )
                val notificationId = download.request.id.hashCode()
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.notify(notificationId, notification)
            }
        }
    }

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 1
        private const val DOWNLOAD_SCHEDULER_JOB_ID = 1000
        private const val TAG = "TPDownloadService"

        /**
         * Start a download using this service.
         */
        fun sendAddDownload(context: Context, downloadRequest: androidx.media3.exoplayer.offline.DownloadRequest) {
            Log.d(TAG, "Sending add download request: ${downloadRequest.uri}")
            sendAddDownload(
                context,
                TPDownloadService::class.java,
                downloadRequest,
                false
            )
        }
    }
} 