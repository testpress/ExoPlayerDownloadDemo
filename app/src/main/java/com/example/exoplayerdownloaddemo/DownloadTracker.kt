package com.example.exoplayerdownloaddemo

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import com.google.common.collect.ImmutableList
import java.util.concurrent.CopyOnWriteArraySet

@UnstableApi
class DownloadTracker(private val context: Context) {

    private val listeners = CopyOnWriteArraySet<Listener>()
    private val downloadIndex = ExoPlayerDownloadDemoApp.downloadManager.downloadIndex
    private var startDownloadDialogHelper: StartDownloadDialogHelper? = null
    
    // Maps the media URI to their download state
    private val downloads = mutableMapOf<Uri, Download>()

    init {
        ExoPlayerDownloadDemoApp.downloadManager.addListener(DownloadManagerListener())
        loadDownloads()
    }

    interface Listener {
        fun onDownloadsChanged()
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun getDownloadRequest(uri: Uri): DownloadRequest? {
        val download = downloads[uri]
        return download?.request
    }

    fun isDownloaded(mediaItem: MediaItem): Boolean {
        val download = downloads[mediaItem.localConfiguration?.uri]
        return download != null && download.state == Download.STATE_COMPLETED
    }

    fun getDownloadState(mediaItem: MediaItem): Int {
        val download = downloads[mediaItem.localConfiguration?.uri]
        return download?.state ?: Download.STATE_STOPPED
    }

    fun toggleDownload(mediaItem: MediaItem) {
        Log.d(TAG, "Toggle download called for ${mediaItem.mediaId}")
        val uri = mediaItem.localConfiguration?.uri ?: return
        Log.d(TAG, "Toggle download for $uri")

        if (downloads.containsKey(uri)) {
            // Media is already being downloaded or has been downloaded
            val download = downloads[uri]!!
            if (download.state == Download.STATE_COMPLETED) {
                // Remove the download
                ExoPlayerDownloadDemoApp.downloadManager.removeDownload(download.request.id)
                Log.d(TAG, "Download removed for $uri")
            } else {
                // Cancel the download
                ExoPlayerDownloadDemoApp.downloadManager.removeDownload(download.request.id)
                Log.d(TAG, "Download canceled for $uri")
            }
        } else {
            // Start a new download
            startDownload(mediaItem)
            Log.d(TAG, "Download started for $uri")
        }
    }

    private fun startDownload(mediaItem: MediaItem) {
        val downloadRequest = DownloadRequest.Builder(mediaItem.mediaId, mediaItem.localConfiguration?.uri!!)
            .setData(mediaItem.mediaId.toByteArray())
            .setCustomCacheKey(mediaItem.mediaId)
            .build()
        
        TPDownloadService.sendAddDownload(context, downloadRequest)
    }

    private fun loadDownloads() {
        try {
            downloadIndex.getDownloads().use { loadedDownloads ->
                while (loadedDownloads.moveToNext()) {
                    val download = loadedDownloads.download
                    downloads[download.request.uri] = download
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load downloads", e)
        }
    }

    private inner class DownloadManagerListener : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            Log.d(TAG, "Download changed: ${download.request.uri}")
            downloads[download.request.uri] = download
            for (listener in listeners) {
                listener.onDownloadsChanged()
            }
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            Log.d(TAG, "Download removed: ${download.request.uri}")
            downloads.remove(download.request.uri)
            for (listener in listeners) {
                listener.onDownloadsChanged()
            }
        }
    }

    /**
     * Helper for download dialogs - not used in this simple example but useful for real apps
     */
    private class StartDownloadDialogHelper(
        private val mediaItem: MediaItem,
        private val tracker: DownloadTracker
    ) {
        fun startDownload() {
            tracker.startDownload(mediaItem)
        }
    }

    companion object {
        private const val TAG = "DownloadTracker"
    }
} 