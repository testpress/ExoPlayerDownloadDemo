# ExoPlayer Download Demo

A sample Android application demonstrating how to use Media3 ExoPlayer to play and download media for offline playback.

## Features

- Stream video using ExoPlayer
- Download videos for offline playback
- Track download progress
- Delete downloaded content
- Automatic switching between online and offline sources

## Video Sample

The application plays the Big Buck Bunny sample video from:
```
https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4
```

## Implementation Details

This application demonstrates several key features of Media3 ExoPlayer:

### Media Playback
- Using ExoPlayer to stream media content
- UI controls via PlayerView
- Playback state management

### Download Functionality
- DownloadManager to handle media downloads
- TPDownloadService for background download operations
- DownloadTracker to manage download state
- Cache management for offline content

### Offline Playback
- CacheDataSource to prioritize cached content
- Download progress tracking
- Download state management

## Architecture

- **ExoPlayerDownloadDemoApp**: Application class that initializes components
- **MainActivity**: Main UI with player and download controls
- **TPDownloadService**: Background service for download operations
- **DownloadTracker**: Manages download state and operations

## Requirements

- Android 7.0 (API level 24) or higher
- Internet permission for streaming
- Storage access for downloads
- Notification permission for Android 13+

## Libraries Used

- Media3 ExoPlayer: For media playback and download
- AndroidX: Core, AppCompat, Lifecycle components
- OkHttp: For network operations

## Getting Started

1. Clone the repository
2. Open in Android Studio
3. Run the application on a device or emulator
4. Press the "Download" button to download the video
5. Once downloaded, you can play the video offline

## License

This project is licensed under the MIT License - see the LICENSE file for details. 