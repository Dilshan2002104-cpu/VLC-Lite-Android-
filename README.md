# Onyx Player

A fast, lightweight, and offline Android Video Player powered by LibVLC. 
It is designed to smoothly play any video format (including heavy MKV and HEVC 10-bit files) without stuttering or crashing, offering a highly stable alternative to default system players.

## Features

* **LibVLC Engine:** Plays MKV, MP4, HEVC, AC3, and all heavy native formats out-of-the-box.
* **Secure Video Vault:** A built-in PIN-protected secure folder to hide your private videos securely away from the standard system Android gallery.
* **Smart UI Gestures:** Swipe controls for Volume (right edge), Brightness (left edge), and Double-Tap to seek +10 or -10 seconds perfectly.
* **Offline Custom Subtitles (.srt):** Load your local subtitle files directly. You can also dynamically change subtitle colors (White/Yellow) and sizes.
* **Glide Thumbnail Caching:** Instantly loads heavy video thumbnails using an efficient Glide caching system without lagging the UI scroll.
* **Bulk Media Management:** Multi-select videos to either easily throw them into the Vault or natively delete them from your internal storage.
* **Deep Advanced Properties:** Instantly view exact path, resolution, total file size, exact duration, formatting, and file counts for any video or folder.
* **Native Lightweight Splash:** A native 100% Android hardware-accelerated animated splash screen with zero heavy dependencies (No Lottie).

## Technical Requirements
* Minimum SDK: Android 7.0 (API 24)
* Target SDK: Android 14 (API 34)
* Dependencies: LibVLC (3.5.1), Glide (4.16.0)

## How to Build
Clone this repository and build using Gradle:
`gradle assembleDebug`
