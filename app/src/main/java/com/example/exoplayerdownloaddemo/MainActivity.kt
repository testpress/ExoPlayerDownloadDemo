package com.example.exoplayerdownloaddemo

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.MappingTrackSelector
import androidx.media3.ui.PlayerView
import com.example.exoplayerdownloaddemo.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@UnstableApi
class MainActivity : AppCompatActivity(), DownloadTracker.Listener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var player: ExoPlayer
    private lateinit var downloadTracker: DownloadTracker
    private lateinit var videoUrl: String
    private lateinit var mediaItem: MediaItem
    private lateinit var trackSelector: DefaultTrackSelector

    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                "Notification permission denied. Download notifications may not work properly.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Request notification permission for Android 13+
        requestNotificationPermissionIfNeeded()

        // Initialize download tracker
        downloadTracker = DownloadTracker(this)
        downloadTracker.addListener(this)

        // Set up the video URL
        videoUrl = "https://dlbdnoa93s0gw.cloudfront.net/transcoded/ACGhHuD7DEa/video.m3u8"
        
        val uri = Uri.parse(videoUrl)
        val downloadRequest = downloadTracker.getDownloadRequest(uri)
        mediaItem = if (downloadRequest != null) {
            // If downloaded, create MediaItem from download request
            downloadRequest.toMediaItem()
        } else {
            // If not downloaded, create normal MediaItem
            MediaItem.Builder()
                .setUri(uri)
                .setMediaId(videoUrl)
                .build()
        }

        initializePlayer()
        
        // Set up download button
        updateDownloadButton()
        binding.btnDownload.setOnClickListener {
            downloadTracker.toggleDownload(mediaItem)
        }
        
        // Start download progress monitor
        startDownloadProgressMonitor()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun initializePlayer() {
        // Create a factory for loading from cache
        val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(this)
        
        // Create a cache data source factory for reading downloaded content
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(ExoPlayerDownloadDemoApp.downloadCache)
            .setUpstreamDataSourceFactory(dataSourceFactory)
            .setCacheWriteDataSinkFactory(null) // Disable writing in player (we handle this in download service)
        
        // Create track selector
        trackSelector = DefaultTrackSelector(this)
        
        // Create the player with the cache data source factory
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setTrackSelector(trackSelector)
            .build()
        
        // Attach player to the view
        binding.playerView.player = player
        
        // Add a player listener to update UI when playback state changes
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                updatePlaybackState(state)
            }
        })
        
        // Set the media item and prepare player
        player.setMediaItem(mediaItem)
        player.prepare()
    }
    
    private fun updatePlaybackState(state: Int) {
        when (state) {
            Player.STATE_BUFFERING -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.tvStatus.text = "Buffering..."
            }
            Player.STATE_READY -> {
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = "Ready"
            }
            Player.STATE_ENDED -> {
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = "Playback completed"
            }
            Player.STATE_IDLE -> {
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = "Idle"
            }
        }
    }
    
    private fun updateDownloadButton() {
        val downloadState = downloadTracker.getDownloadState(mediaItem)
        
        binding.btnDownload.text = when (downloadState) {
            Download.STATE_DOWNLOADING -> "Cancel Download"
            Download.STATE_QUEUED -> "Cancel Download"
            Download.STATE_COMPLETED -> "Delete Download"
            else -> "Download"
        }
        
        val isDownloaded = downloadTracker.isDownloaded(mediaItem)
        binding.tvDownloadStatus.text = if (isDownloaded) {
            "Downloaded (offline playback available)"
        } else if (downloadState == Download.STATE_DOWNLOADING || downloadState == Download.STATE_QUEUED) {
            "Downloading..."
        } else {
            "Not downloaded"
        }
    }
    
    private fun startDownloadProgressMonitor() {
        lifecycleScope.launch(Dispatchers.Main) {
            while (isActive) {
                // Get current downloads
                val downloads = ExoPlayerDownloadDemoApp.downloadManager.currentDownloads
                
                // Check if our media item is being downloaded
                val download = downloads.find { it.request.uri.toString() == videoUrl }
                
                if (download != null && download.state == Download.STATE_DOWNLOADING) {
                    val progress = download.percentDownloaded
                    binding.downloadProgressBar.progress = progress.toInt()
                    binding.downloadProgressBar.visibility = View.VISIBLE
                    binding.tvDownloadProgress.visibility = View.VISIBLE
                    binding.tvDownloadProgress.text = "${progress.toInt()}%"
                } else {
                    binding.downloadProgressBar.visibility = View.GONE
                    binding.tvDownloadProgress.visibility = View.GONE
                }
                
                // Update download button state
                updateDownloadButton()
                
                delay(1000) // Update every second
            }
        }
    }

    override fun onDownloadsChanged() {
        // Update UI when downloads change
        updateDownloadButton()
        Log.d(Companion.TAG, "Downloads changed")
    }
    
    override fun showTrackSelectionDialog(helper: DownloadHelper, mediaItem: MediaItem) {
        // Get track groups for video renderer
        val mappedTrackInfo = helper.getMappedTrackInfo(0)
        val rendererIndex = getFirstVideoRendererIndex(mappedTrackInfo)
        
        if (rendererIndex == C.INDEX_UNSET) {
            Toast.makeText(this, "No video tracks available", Toast.LENGTH_SHORT).show()
            helper.release()
            return
        }
        
        val trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex)
        
        // Create list of available video qualities
        val trackItems = mutableListOf<TrackItem>()
        
        for (groupIndex in 0 until trackGroups.length) {
            val trackGroup = trackGroups[groupIndex]
            for (trackIndex in 0 until trackGroup.length) {
                val format = trackGroup.getFormat(trackIndex)
                if (format.height > 0) { // Only include tracks with valid height
                    val trackName = "${format.width}x${format.height} (${format.bitrate / 1024} kbps)"
                    trackItems.add(TrackItem(groupIndex, trackIndex, trackName, format.height))
                }
            }
        }
        
        // Sort by resolution (highest first)
        trackItems.sortByDescending { it.height }
        
        // Add "All resolutions" option at the beginning
        trackItems.add(0, TrackItem(-1, -1, "All resolutions (default)", Int.MAX_VALUE))
        
        // Create dialog items
        val items = trackItems.map { it.name }.toTypedArray()
        
        // Show dialog
        AlertDialog.Builder(this)
            .setTitle("Select Video Quality")
            .setItems(items) { _, which ->
                val selectedTrack = trackItems[which]
                
                if (selectedTrack.groupIndex == -1) {
                    // Download all tracks (default behavior)
                    downloadTracker.startDownloadWithTrackSelection(helper, mediaItem)
                } else {
                    // Set track selection for specific resolution
                    helper.clearTrackSelections(rendererIndex)
                    
                    // Create track override for the selected track
                    val trackGroup = trackGroups[selectedTrack.groupIndex]
                    val trackIndices = listOf(selectedTrack.trackIndex)
                    val override = TrackSelectionOverride(trackGroup, trackIndices)
                    
                    // Create parameters with the override
                    val parameters = TrackSelectionParameters.Builder()
                        .addOverride(override)
                        .build()
                    
                    // Apply track selection
                    helper.addTrackSelection(rendererIndex, parameters)
                    downloadTracker.startDownloadWithTrackSelection(helper, mediaItem)
                }
            }
            .setOnCancelListener {
                // Release helper if dialog is dismissed
                helper.release()
            }
            .show()
    }
    
    private fun getFirstVideoRendererIndex(mappedTrackInfo: MappingTrackSelector.MappedTrackInfo): Int {
        for (i in 0 until mappedTrackInfo.rendererCount) {
            if (mappedTrackInfo.getRendererType(i) == C.TRACK_TYPE_VIDEO) {
                return i
            }
        }
        return C.INDEX_UNSET
    }
    
    // Data class to hold track information
    private data class TrackItem(
        val groupIndex: Int,
        val trackIndex: Int,
        val name: String,
        val height: Int
    )

    override fun onStart() {
        super.onStart()
        if (::player.isInitialized) {
            player.playWhenReady = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (::player.isInitialized) {
            player.playWhenReady = true
        }
    }

    override fun onPause() {
        super.onPause()
        if (::player.isInitialized) {
            player.playWhenReady = false
        }
    }

    override fun onStop() {
        super.onStop()
        if (::player.isInitialized) {
            player.playWhenReady = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::player.isInitialized) {
            player.release()
        }
        if (::downloadTracker.isInitialized) {
            downloadTracker.removeListener(this)
        }
    }

    companion object {
        private const val TAG: String = "MainActivity"
    }
} 