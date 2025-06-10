package com.example.exoplayerdownloaddemo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import java.io.File
import java.util.concurrent.Executors

@UnstableApi
class ExoPlayerDownloadDemoApp : Application() {

    companion object {
        private const val TAG = "ExoPlayerDownloadDemoApp"
        private const val DOWNLOAD_CONTENT_DIRECTORY = "downloads"
        const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel"
        
        lateinit var downloadManager: DownloadManager
            private set
        
        lateinit var downloadCache: Cache
            private set
        
        lateinit var httpDataSourceFactory: DataSource.Factory
            private set
        
        lateinit var databaseProvider: DatabaseProvider
            private set
    }

    override fun onCreate() {
        super.onCreate()
        
        // Create notification channel for downloads
        createNotificationChannel()
        
        databaseProvider = StandaloneDatabaseProvider(this)

        // Initialize the download cache
        val downloadContentDirectory = File(getExternalFilesDir(null), DOWNLOAD_CONTENT_DIRECTORY)
        downloadCache = SimpleCache(
            downloadContentDirectory,
            NoOpCacheEvictor(),
            databaseProvider
        )
        
        // Create a factory for HTTP data source
        httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
        
        // Create and configure the download manager
        downloadManager = DownloadManager(
            this,
            databaseProvider,
            downloadCache,
            httpDataSourceFactory,
            Executors.newFixedThreadPool(6)
        ).apply {
            maxParallelDownloads = 3
        }
        
        Log.d(TAG, "Download manager initialized")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.download_notification_channel_name)
            val description = getString(R.string.download_notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(DOWNLOAD_NOTIFICATION_CHANNEL_ID, name, importance).apply {
                this.description = description
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            Log.d(TAG, "Notification channel created")
        }
    }
} 